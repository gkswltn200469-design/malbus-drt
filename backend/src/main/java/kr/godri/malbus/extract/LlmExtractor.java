package kr.godri.malbus.extract;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.godri.malbus.domain.TranscriptLine;
import kr.godri.malbus.dto.StopDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Claude로 통화 내용 -> AI 모듈 출력 (docs/api.md). 정류장 확정·needsCheck·날짜 계산은 여기서 안 하고 BE(ExtractionService)가 함
// 프롬프트는 resources/llm/system-prompt.md. 채점용 대본 20개는 프롬프트에 넣지 않음
public class LlmExtractor implements Extractor {

    private static final Logger log = LoggerFactory.getLogger(LlmExtractor.class);
    private static final String PROMPT_PATH = "/llm/system-prompt.md";
    private static final Set<String> PURPOSES = Set.of("RESERVE", "CHANGE", "CANCEL", "INQUIRY");

    private final AnthropicClient client;
    private final String model;
    private final Extractor fallback; // null이면 실패를 그대로 던짐
    private final String template;
    private final ObjectMapper om = new ObjectMapper();

    public LlmExtractor(AnthropicClient client, String model, Extractor fallback) {
        this.client = client;
        this.model = model;
        this.fallback = fallback;
        this.template = loadTemplate();
    }

    @Override
    public String mode() {
        return "LLM";
    }

    @Override
    public AiOutput extract(List<TranscriptLine> transcript, List<StopDto> stops) {
        try {
            return parse(call(systemPrompt(stops), userMessage(transcript)), transcript, stops);
        } catch (RuntimeException e) {
            if (fallback == null) throw e;
            log.warn("LLM 추출 실패, 규칙 추출로 대신함: {}", e.toString());
            return fallback.extract(transcript, stops);
        }
    }

    private String call(String system, String user) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                // 정류장 목록·예시가 든 앞부분은 매번 같아서 캐시
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(system)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(user)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW) // 짧은 추출이라 낮게. 상담원이 기다리는 시간 줄임
                        .format(JsonOutputFormat.builder().schema(schema()).build())
                        .build())
                // 안전 분류기가 거절하면 서버가 다른 모델로 다시 시도
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
        Message res = client.messages().create(params);

        StopReason stop = res.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stop)) {
            throw new IllegalStateException("LLM 응답이 끝까지 오지 않음: stop_reason=" + stop);
        }
        String text = res.content().stream()
                .flatMap(b -> b.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        if (text.isBlank()) throw new IllegalStateException("LLM 응답에 내용이 없음");
        return text;
    }

    String systemPrompt(List<StopDto> stops) {
        String list = stops.stream()
                .map(s -> s.id() + " | " + s.name() + " | " + (s.aliases().isEmpty() ? "-" : String.join(", ", s.aliases())))
                .collect(Collectors.joining("\n"));
        return template.replace("{{STOPS}}", list);
    }

    static String userMessage(List<TranscriptLine> transcript) {
        StringBuilder sb = new StringBuilder("통화:\n");
        for (TranscriptLine l : transcript) {
            String who = "AGENT".equals(l.getSpeaker()) ? "상담원" : "고객";
            sb.append('[').append(l.getLineNo()).append("] ").append(who).append(": ").append(l.getText()).append('\n');
        }
        return sb.toString();
    }

    // 모델 출력 JSON -> AiOutput. 이상한 값은 버려서 BE 규칙이 '확인 필요'로 처리하게 둠
    AiOutput parse(String json, List<TranscriptLine> transcript, List<StopDto> stops) {
        JsonNode root;
        try {
            root = om.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("LLM 응답이 JSON이 아님", e);
        }
        Set<Integer> lineNos = transcript.stream().map(TranscriptLine::getLineNo).collect(Collectors.toSet());
        Set<String> stopIds = stops.stream().map(StopDto::id).collect(Collectors.toSet());

        JsonNode p = root.path("purpose");
        String pv = str(p.path("value"));
        AiOutput.Purpose purpose = new AiOutput.Purpose(pv != null && PURPOSES.contains(pv) ? pv : "RESERVE",
                lineNo(p, lineNos));

        JsonNode d = root.path("date");
        AiOutput.DateSaid date = str(d.path("said")) == null ? null : new AiOutput.DateSaid(str(d.path("said")), lineNo(d, lineNos));

        JsonNode t = root.path("time");
        AiOutput.TimeSaid time = null;
        if (str(t.path("said")) != null) {
            String mer = str(t.path("meridiem"));
            time = new AiOutput.TimeSaid(str(t.path("said")), num(t.path("hour")), num(t.path("minute")),
                    "AM".equals(mer) || "PM".equals(mer) ? mer : null, lineNo(t, lineNos));
        }
        return new AiOutput(purpose, place(root.path("from"), lineNos, stopIds), place(root.path("to"), lineNos, stopIds),
                date, time, null);
    }

    private static AiOutput.Place place(JsonNode n, Set<Integer> lineNos, Set<String> stopIds) {
        String said = str(n.path("said"));
        if (said == null) return null;
        List<String> ranked = new ArrayList<>();
        n.path("rankedStopIds").forEach(id -> {
            if (stopIds.contains(id.asText()) && !ranked.contains(id.asText())) ranked.add(id.asText()); // 목록 밖 ID는 버림
        });
        return new AiOutput.Place(said, lineNo(n, lineNos), ranked, str(n.path("askHint")));
    }

    private static Integer lineNo(JsonNode n, Set<Integer> lineNos) {
        Integer no = num(n.path("evidenceLineNo"));
        return no != null && lineNos.contains(no) ? no : null;
    }

    private static String str(JsonNode n) {
        if (!n.isTextual()) return null;
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer num(JsonNode n) {
        return n.isIntegralNumber() ? n.asInt() : null;
    }

    // 구조화 출력 스키마. 모든 칸 required + null 허용이라 "말 안 함"은 null로 옴
    // 순서가 바뀌면 프롬프트 캐시가 깨져서 Map.of 대신 순서 고정(ordered)
    static JsonOutputFormat.Schema schema() {
        Map<String, Object> place = object(
                "said", nullable("string"),
                "evidenceLineNo", nullable("integer"),
                "rankedStopIds", ordered("type", "array", "items", ordered("type", "string")),
                "askHint", nullable("string"));

        Map<String, Object> root = object(
                "purpose", object(
                        "value", ordered("type", "string", "enum", List.of("RESERVE", "CHANGE", "CANCEL", "INQUIRY")),
                        "evidenceLineNo", nullable("integer")),
                "from", place,
                "to", place,
                "date", object("said", nullable("string"), "evidenceLineNo", nullable("integer")),
                "time", object(
                        "said", nullable("string"),
                        "hour", nullable("integer"),
                        "minute", nullable("integer"),
                        "meridiem", ordered("type", List.of("string", "null"), "enum", List.of("AM", "PM")),
                        "evidenceLineNo", nullable("integer")));

        JsonOutputFormat.Schema.Builder b = JsonOutputFormat.Schema.builder();
        root.forEach((k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)));
        return b.build();
    }

    private static Map<String, Object> object(Object... kv) {
        Map<String, Object> props = ordered(kv);
        return ordered("type", "object", "properties", props,
                "required", new ArrayList<>(props.keySet()), "additionalProperties", false);
    }

    private static Map<String, Object> nullable(String type) {
        return ordered("type", List.of(type, "null"));
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String loadTemplate() {
        try (InputStream in = LlmExtractor.class.getResourceAsStream(PROMPT_PATH)) {
            if (in == null) throw new IllegalStateException("프롬프트 파일 없음: " + PROMPT_PATH);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
