package kr.godri.malbus.dto;

import kr.godri.malbus.domain.ReservationField;

import java.util.Map;

public record ExtractResponse(String mode, FieldDto<String> purpose, StopFieldDto from, StopFieldDto to,
                              FieldDto<String> date, FieldDto<String> time, FieldDto<Integer> people) {

    public static ExtractResponse of(String mode, Map<String, ReservationField> f) {
        return new ExtractResponse(mode, f.get("purpose").toDto(s -> s), f.get("from").toStopDto(),
                f.get("to").toStopDto(), f.get("date").toDto(s -> s), f.get("time").toDto(s -> s),
                f.get("people").toDto(Integer::valueOf));
    }
}
