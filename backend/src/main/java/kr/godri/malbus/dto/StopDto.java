package kr.godri.malbus.dto;

import java.util.List;

public record StopDto(String id, String name, double lat, double lng, List<String> aliases) {
}
