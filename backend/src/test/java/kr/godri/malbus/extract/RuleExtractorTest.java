package kr.godri.malbus.extract;

import kr.godri.malbus.domain.TranscriptLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleExtractorTest {

    @Test
    void 한글_시각() {
        assertThat(RuleExtractor.time("내일 오전 아홉 시 반에 갈라고요", 1))
                .extracting("hour", "minute", "meridiem").containsExactly(9, 30, "AM");
        assertThat(RuleExtractor.time("모레 오후 두 시 반에 수성하늘채에서", 1))
                .extracting("said", "hour", "minute", "meridiem").containsExactly("오후 두 시 반", 2, 30, "PM");
        assertThat(RuleExtractor.time("모레 열 시에 한라주택", 1))
                .extracting("hour", "minute", "meridiem").containsExactly(10, 0, null);
        assertThat(RuleExtractor.time("내일 오전 열한 시에 우방에서", 1))
                .extracting("hour", "meridiem").containsExactly(11, "AM");
        assertThat(RuleExtractor.time("2시 30분에 갈게요", 1))
                .extracting("hour", "minute").containsExactly(2, 30);
    }

    @Test
    void 시각을_고쳐_말하면_마지막_값() {
        assertThat(RuleExtractor.time("아홉 시요. 아 아니다 열 시 반에 갈게요", 1))
                .extracting("hour", "minute").containsExactly(10, 30);
    }

    @Test
    void 시각이_아닌_말() {
        assertThat(RuleExtractor.time("두 시간 걸려요", 1)).isNull();
        assertThat(RuleExtractor.time("백련사 가는 차는 몇 시에 있어요?", 1)).isNull();
    }

    @Test
    void 인원() {
        assertThat(RuleExtractor.people("혼자요", 1).value()).isEqualTo(1);
        assertThat(RuleExtractor.people("할매하고 둘이요", 1).value()).isEqualTo(2);
        assertThat(RuleExtractor.people("세 명이요", 1).value()).isEqualTo(3);
        assertThat(RuleExtractor.people("두 분 갑니다", 1).value()).isEqualTo(2);
        assertThat(RuleExtractor.people("제가 70살인데 내일 오전 아홉 시에", 1)).isNull();
        assertThat(RuleExtractor.people("내일 오전 열 시에 우방에서 백화점 가요", 1)).isNull();
    }

    @Test
    void 출발_도착_말() {
        assertThat(RuleExtractor.places("내일 오전 아홉 시 반에 범물청구타운 앞에서 동아백화점 수성점 앞까지 갈라고요"))
                .containsExactly("범물청구타운 앞", "동아백화점 수성점 앞");
        assertThat(RuleExtractor.places("내일 오후 한 시 반에 청구아파트에서 2동 주민센터요"))
                .containsExactly("청구아파트", "2동 주민센터");
        assertThat(RuleExtractor.places("모레 오후 두 시 반에 수성하늘채에서 범물2동 행정복지센터 건너편으로 가요. 둘이 갈 거예요"))
                .containsExactly("수성하늘채", "범물2동 행정복지센터 건너편");
        assertThat(RuleExtractor.places("내일 오전 열 시에 우리 큰딸네 집 앞에서 범물역 가요"))
                .containsExactly("우리 큰딸네 집 앞", "범물역");
    }

    @Test
    void 출발을_바꿔_말하면_마지막_말() {
        assertThat(RuleExtractor.places("모레 하늘채에서 타기로 한 거, 청구타운에서 타도 됩니꺼"))
                .containsExactly("청구타운", null);
        assertThat(RuleExtractor.places("아니 하늘채에서 탈게요")).containsExactly("하늘채", null);
    }

    @Test
    void 상담원_말은_안_보고_목적_분류() {
        RuleExtractor ex = new RuleExtractor();
        AiOutput out = ex.extract(List.of(
                line(1, "AGENT", "네, 범물동 DRT입니다"),
                line(2, "CUSTOMER", "내일 오전 열 시에 하늘채에서 백화점 가요"),
                line(3, "AGENT", "몇 분 타세요?")), List.of());
        assertThat(out.people()).isNull();
        assertThat(out.purpose().value()).isEqualTo("RESERVE");
        assertThat(out.from().evidenceLineNo()).isEqualTo(2);

        assertThat(ex.extract(List.of(line(1, "CUSTOMER", "내일 예약한 거 취소할게요")), List.of()).purpose().value())
                .isEqualTo("CANCEL");
        assertThat(ex.extract(List.of(line(1, "CUSTOMER", "예약한 거 열한 시로 좀 바꿔 주이소")), List.of()).purpose().value())
                .isEqualTo("CHANGE");
        assertThat(ex.extract(List.of(line(1, "CUSTOMER", "백련사 가는 차는 몇 시에 있어요?")), List.of()).purpose().value())
                .isEqualTo("INQUIRY");
    }

    private static TranscriptLine line(int no, String speaker, String text) {
        return new TranscriptLine(null, no, "2026-11-17T14:00:00+09:00", speaker, text);
    }
}
