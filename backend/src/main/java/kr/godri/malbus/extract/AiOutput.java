package kr.godri.malbus.extract;

import java.util.List;

// docs/api.md "AI 모듈 출력". llm이든 규칙이든 이 형식으로 넘기고 판정은 BE가 함
public record AiOutput(Purpose purpose, Place from, Place to, DateSaid date, TimeSaid time, People people) {

    public record Purpose(String value, Integer evidenceLineNo) {
    }

    public record Place(String said, Integer evidenceLineNo, List<String> rankedStopIds, String askHint) {
    }

    public record DateSaid(String said, Integer evidenceLineNo) {
    }

    // meridiem: AM | PM | null
    public record TimeSaid(String said, Integer hour, Integer minute, String meridiem, Integer evidenceLineNo) {
    }

    public record People(String said, Integer value, Integer evidenceLineNo) {
    }
}
