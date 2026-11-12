package kr.godri.malbus.dto;

import com.fasterxml.jackson.databind.JsonNode;

// value는 칸마다 타입이 달라서(인원은 숫자) JsonNode로 받음
public record PatchFieldRequest(JsonNode value, String note) {
}
