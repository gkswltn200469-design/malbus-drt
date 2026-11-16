package kr.godri.malbus.extract;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Gemini API generateContent를 REST로 호출. 키는 x-goog-api-key 헤더로만 (URL에 안 붙임)
// 응답 형식: generationConfig.responseFormat.text (JSON Schema) - Gemini 문서 기준
// 무료 등급은 입력이 Google 서비스 개선에 쓰일 수 있음. 대본은 지어낸 예시라 실제 개인정보는 넣지 않음
public class GeminiClient implements LlmClient {

    public static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    // 붐빔(503)·호출 제한(429)·서버 오류(5xx)는 잠깐 쉬고 다시. 무료 등급에서 자주 남
    public static final List<Duration> DEFAULT_BACKOFF = List.of(Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10));

    private final RestClient http;
    private final String model;
    private final List<Duration> backoff;

    // http는 baseUrl·키 헤더·타임아웃이 설정된 것 (ExtractorConfig, 테스트에서 만듦)
    public GeminiClient(RestClient http, String model) {
        this(http, model, DEFAULT_BACKOFF);
    }

    GeminiClient(RestClient http, String model, List<Duration> backoff) {
        this.http = http;
        this.model = model;
        this.backoff = backoff;
    }

    public static RestClient.Builder http(String apiKey) {
        return RestClient.builder().baseUrl(BASE_URL).defaultHeader("x-goog-api-key", apiKey);
    }

    @Override
    public String name() {
        return "gemini:" + model;
    }

    @Override
    public String complete(String system, String user) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", Map.of(
                        // 기본(MEDIUM)은 오래 생각해서 30초를 넘기기도 함. 짧은 추출이라 LOW
                        "thinkingConfig", Map.of("thinkingLevel", "LOW"),
                        "responseFormat", Map.of("text", Map.of(
                                "mimeType", "APPLICATION_JSON",
                                "schema", LlmSchema.aiOutput()))));

        JsonNode res = post(body);
        JsonNode cand = res == null ? null : res.path("candidates").path(0);
        if (cand == null || cand.isMissingNode()) {
            String block = res == null ? null : res.path("promptFeedback").path("blockReason").asText(null);
            throw new IllegalStateException("Gemini 응답에 후보가 없음" + (block == null ? "" : ": blockReason=" + block));
        }
        String finish = cand.path("finishReason").asText(null);
        if (!"STOP".equals(finish)) {
            throw new IllegalStateException("Gemini 응답이 끝까지 오지 않음: finishReason=" + finish);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : cand.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue; // 생각 요약 조각은 뺌
            text.append(part.path("text").asText(""));
        }
        return text.toString();
    }

    private JsonNode post(Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            try {
                return http.post()
                        .uri("/models/{model}:generateContent", model)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests e) {
                if (attempt >= backoff.size()) throw e;
                log.info("Gemini {} 응답, {}초 뒤 다시 시도 ({}/{})", e.getStatusCode().value(),
                        backoff.get(attempt).toSeconds(), attempt + 1, backoff.size());
                sleep(backoff.get(attempt));
            }
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini 재시도 대기 중 중단됨", ie);
        }
    }
}
