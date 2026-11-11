package kr.godri.malbus.dto;

// 목록용
public record ReservationSummary(Long id, String status, String receivedAt, String from, String to,
                                 String date, String time, Integer people) {
}
