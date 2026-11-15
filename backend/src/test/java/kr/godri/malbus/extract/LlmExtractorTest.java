package kr.godri.malbus.extract;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import kr.godri.malbus.domain.TranscriptLine;
import kr.godri.malbus.dto.StopDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// API 호출 없이 프롬프트 만들기·응답 해석만 확인
class LlmExtractorTest {

    private static final List<StopDto> STOPS = List.of(
            new StopDto("S01", "범물1동행정복지센터건너(용지역)", 0, 0, List.of("동사무소", "용지역")),
            new StopDto("S09", "수성하늘채", 0, 0, List.of("하늘채")));

    private static final List<TranscriptLine> LINES = List.of(
            new TranscriptLine(null, 1, null, "AGENT", "네, 범물동 DRT입니다"),
            new TranscriptLine(null, 2, null, "CUSTOMER", "내일 오후 한 시에 하늘채에서 용지역 가요"));

    // 호출 안 하는 테스트라 가짜 키
    private final LlmExtractor llm = new LlmExtractor(
            AnthropicOkHttpClient.builder().apiKey("test").build(), "claude-sonnet-5-5", null);

    @Test
    void 프롬프트에_정류장_목록이_들어감() {
        String p = llm.systemPrompt(STOPS);
        assertThat(p).contains("S01 | 범물1동행정복지센터건너(용지역) | 동사무소, 용지역");
        assertThat(p).contains("S09 | 수성하늘채 | 하늘채");
        assertThat(p).doesNotContain("{{STOPS}}");
    }

    @Test
    void 통화_줄에_번호와_화자를_붙임() {
        assertThat(LlmExtractor.userMessage(LINES))
                .contains("[1] 상담원: 네, 범물동 DRT입니다")
                .contains("[2] 고객: 내일 오후 한 시에 하늘채에서 용지역 가요");
    }

    @Test
    void 응답_해석() {
        AiOutput out = llm.parse("""
                {"purpose":{"value":"RESERVE","evidenceLineNo":2},
                 "from":{"said":"하늘채","evidenceLineNo":2,"rankedStopIds":["S09"],"askHint":null},
                 "to":{"said":"용지역","evidenceLineNo":2,"rankedStopIds":["S12","S01","S99"],"askHint":"앞인지 건너인지"},
                 "date":{"said":"내일","evidenceLineNo":2},
                 "time":{"said":"오후 한 시","hour":1,"minute":0,"meridiem":"PM","evidenceLineNo":2}}
                """, LINES, STOPS);

        assertThat(out.purpose().value()).isEqualTo("RESERVE");
        assertThat(out.from().said()).isEqualTo("하늘채");
        assertThat(out.to().rankedStopIds()).containsExactly("S01"); // 목록 밖 S12·S99는 버림
        assertThat(out.to().askHint()).isEqualTo("앞인지 건너인지");
        assertThat(out.date().said()).isEqualTo("내일");
        assertThat(out.time().hour()).isEqualTo(1);
        assertThat(out.time().meridiem()).isEqualTo("PM");
        assertThat(out.people()).isNull();
    }

    @Test
    void 말_안_한_칸과_이상한_값은_비움() {
        AiOutput out = llm.parse("""
                {"purpose":{"value":"WHATEVER","evidenceLineNo":9},
                 "from":{"said":null,"evidenceLineNo":null,"rankedStopIds":[],"askHint":null},
                 "to":{"said":"  ","evidenceLineNo":2,"rankedStopIds":[],"askHint":null},
                 "date":{"said":null,"evidenceLineNo":null},
                 "time":{"said":"두 시","hour":2,"minute":null,"meridiem":"오후","evidenceLineNo":7}}
                """, LINES, STOPS);

        assertThat(out.purpose().value()).isEqualTo("RESERVE");
        assertThat(out.purpose().evidenceLineNo()).isNull(); // 없는 줄 번호
        assertThat(out.from()).isNull();
        assertThat(out.to()).isNull();
        assertThat(out.date()).isNull();
        assertThat(out.time().meridiem()).isNull(); // AM/PM 아니면 말 안 한 것으로
        assertThat(out.time().evidenceLineNo()).isNull();
    }

    @Test
    void JSON이_아니면_실패() {
        assertThatThrownBy(() -> llm.parse("죄송합니다", LINES, STOPS)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 호출_실패하면_규칙_추출로_대신함() {
        // 연결 안 되는 주소라 바로 실패 -> RuleExtractor 결과
        LlmExtractor withFallback = new LlmExtractor(
                AnthropicOkHttpClient.builder().apiKey("test").baseUrl("http://127.0.0.1:9").maxRetries(0).build(),
                "claude-sonnet-5-5", new RuleExtractor());
        AiOutput out = withFallback.extract(LINES, STOPS);
        assertThat(out.from().said()).isEqualTo("하늘채");
        assertThat(out.to().said()).isEqualTo("용지역");
    }
}
