package kr.godri.malbus.dto;

import kr.godri.malbus.common.ApiException;

import java.util.Set;

public record LineRequest(String speaker, String text) {

    private static final Set<String> SPEAKERS = Set.of("AGENT", "CUSTOMER", "SYSTEM");

    public void validate() {
        if (speaker == null || !SPEAKERS.contains(speaker)) {
            throw ApiException.invalid("speaker는 AGENT, CUSTOMER, SYSTEM 중 하나");
        }
        if (text == null || text.isBlank()) {
            throw ApiException.invalid("text가 비었음");
        }
    }
}
