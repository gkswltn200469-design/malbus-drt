package kr.godri.malbus.dto;

import java.util.List;

public record ReservationDto(
        Long id,
        String channel,
        String status,
        String mode,
        String receivedAt,
        FieldDto<String> purpose,
        StopFieldDto from,
        StopFieldDto to,
        FieldDto<String> date,
        FieldDto<String> time,
        FieldDto<Integer> people,
        List<Object> assist,
        List<TranscriptLineDto> transcript,
        String createdAt,
        String updatedAt,
        String confirmedAt,
        String confirmedBy) {
}
