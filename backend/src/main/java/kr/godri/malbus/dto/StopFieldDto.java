package kr.godri.malbus.dto;

import java.util.List;

// api.md StopField. value = 정류장 ID
public record StopFieldDto(
        String value,
        String said,
        boolean needsCheck,
        String reason,
        String askHint,
        FieldDto.Evidence evidence,
        String previous,
        String source,
        String decidedAt,
        String note,
        List<Candidate> candidates,
        int moreCount) {

    public record Candidate(String stopId, String matchType, String reason) {
    }
}
