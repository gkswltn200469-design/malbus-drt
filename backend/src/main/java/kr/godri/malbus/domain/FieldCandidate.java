package kr.godri.malbus.domain;

import jakarta.persistence.Embeddable;
import kr.godri.malbus.dto.StopFieldDto;

// 출발·도착 후보 한 줄 (field_candidate)
@Embeddable
public class FieldCandidate {

    private int rank;
    private String stopId;
    private String matchType;
    private String reason;

    protected FieldCandidate() {
    }

    public FieldCandidate(int rank, String stopId, String matchType, String reason) {
        this.rank = rank;
        this.stopId = stopId;
        this.matchType = matchType;
        this.reason = reason;
    }

    public StopFieldDto.Candidate toDto() {
        return new StopFieldDto.Candidate(stopId, matchType, reason);
    }
}
