package kr.godri.malbus.dto;

import java.util.List;

public record ExtractRequest(String receivedAt, List<LineRequest> transcript) {
}
