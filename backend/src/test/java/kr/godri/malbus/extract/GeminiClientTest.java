package kr.godri.malbus.extract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 실제 Gemini 대신 가짜 서버로 요청 모양·응답 해석 확인
class GeminiClientTest {

    private static final String URL = GeminiClient.BASE_URL + "/models/gemini-3.8-flash:generateContent";

    private MockRestServiceServer server;
    private GeminiClient gemini;

    @BeforeEach
    void setUp() {
        RestClient.Builder b = GeminiClient.http("test-key");
        server = MockRestServiceServer.bindTo(b).build();
        // 재시도 3번, 테스트라 쉬지 않음
        gemini = new GeminiClient(b.build(), "gemini-3.8-flash", Collections.nCopies(3, Duration.ZERO));
    }

    @Test
    void 요청_모양과_응답_해석() {
        server.expect(requestTo(GeminiClient.BASE_URL + "/models/gemini-3.8-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("시스템"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("통화"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("LOW"))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.mimeType").value("APPLICATION_JSON"))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.required[0]").value("purpose"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[
                          {"text":"생각 요약","thought":true},
                          {"text":"{\\"purpose\\":"},{"text":"null}"}]},
                          "finishReason":"STOP"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(gemini.complete("시스템", "통화")).isEqualTo("{\"purpose\":null}");
        server.verify();
    }

    @Test
    void 끝까지_안_오면_실패() {
        server.expect(requestTo(GeminiClient.BASE_URL + "/models/gemini-3.8-flash:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"pur"}]},"finishReason":"MAX_TOKENS"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gemini.complete("s", "u")).hasMessageContaining("MAX_TOKENS");
    }

    @Test
    void 차단되면_이유를_알려줌() {
        server.expect(requestTo(GeminiClient.BASE_URL + "/models/gemini-3.8-flash:generateContent"))
                .andRespond(withSuccess("""
                        {"promptFeedback":{"blockReason":"SAFETY"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gemini.complete("s", "u")).hasMessageContaining("SAFETY");
    }

    @Test
    void 붐비면_다시_시도해서_성공() {
        server.expect(ExpectedCount.twice(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[{"text":"{}"}]},"finishReason":"STOP"}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(gemini.complete("s", "u")).isEqualTo("{}");
        server.verify();
    }

    @Test
    void 다시_시도해도_계속_안_되면_예외() {
        server.expect(ExpectedCount.times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> gemini.complete("s", "u")).isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        server.verify(); // 처음 1번 + 재시도 3번
    }

    @Test
    void 요청_형식_오류는_다시_시도_안_함() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> gemini.complete("s", "u")).isInstanceOf(HttpClientErrorException.BadRequest.class);
        server.verify();
    }
}
