package kr.godri.malbus.extract;

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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// LLM으로 통화 내용 -> AI 모듈 출력 (docs/api.md). 정류장 확정·needsCheck·날짜 계산은 여기서 안 하고 BE(ExtractionService)가 함
// 프롬프트는 resources/llm/system-prompt.md. 채점용 대본 20개는 프롬프트에 넣지 않음
// 어느 LLM을 부를지는 LlmClient (GeminiClient 기본, ClaudeClient)
public class LlmExtractor implements Extractor {

    private static final Logger log = LoggerFactory.getLogger(LlmExtractor.class);
    private static final String PROMPT_PATH = "/llm/system-prompt.md";
    private static final Set<String> PURPOSES = Set.of("RESERVE", "CHANGE", "CANCEL", "INQUIRY");

    private final LlmClient client;
    private final Extractor fallback; // null이면 실패를 그대로 던짐
    private final String template;
    private final ObjectMapper om = new ObjectMapper();

    public LlmExtractor(LlmClient client, Extractor fallback) {
        this.client = client;
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
            String json = client.complete(systemPrompt(stops), userMessage(transcript));
            return parse(json, transcript, stops);
        } catch (RuntimeException e) {
            if (fallback == null) throw e;
            log.warn("LLM({}) 추출 실패, 규칙 추출로 대신함: {}", client.name(), e.toString());
            return fallback.extract(transcript, stops);
        }
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
        if (json == null || json.isBlank()) throw new IllegalStateException("LLM 응답에 내용이 없음");
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

    private static String loadTemplate() {
        try (InputStream in = LlmExtractor.class.getResourceAsStream(PROMPT_PATH)) {
            if (in == null) throw new IllegalStateException("프롬프트 파일 없음: " + PROMPT_PATH);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
