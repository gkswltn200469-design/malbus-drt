package kr.godri.malbus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

// conflicts, recommended는 저장 안 하고 조회할 때 계산
public record LearnedAliasDto(
        Long id,
        String said,
        String stopId,
        int count,
        Map<String, Integer> conflicts,
        String status,
        String firstSeen,
        String lastSeen,
        List<Long> reservationIds,
        boolean recommended,
        @JsonInclude(JsonInclude.Include.NON_NULL) String warning) {
}
