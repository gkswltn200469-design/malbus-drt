package kr.godri.malbus.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import kr.godri.malbus.dto.FieldDto;
import kr.godri.malbus.dto.StopFieldDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// 예약 한 건의 칸 (예약당 6행)
@Entity
@Table(name = "reservation_field")
@IdClass(ReservationField.Key.class)
public class ReservationField {

    public static final List<String> NAMES = List.of("purpose", "from", "to", "date", "time", "people");
    public static final List<String> REQUIRED = List.of("from", "to", "date", "time", "people");

    private static final Map<String, String> ASK = Map.of(
            "from", "어디서 타세요?",
            "to", "어디로 가세요?",
            "date", "언제 타세요?",
            "time", "몇 시에 타세요?",
            "people", "몇 분 타세요?");

    @Id
    @Column(name = "reservation_id") // 후보 테이블 join에서 이 이름으로 찾음
    private Long reservationId;
    @Id
    private String name;
    private String value;    // 출발·도착 외
    private String stopId;   // 출발·도착만
    private String said;
    private boolean needsCheck = true;
    private String reason;
    private String askHint;
    private Integer evidenceLineNo;
    private String evidenceText;
    private String previousValue;
    private String source;
    private String decidedAt;
    private String note;
    private int moreCount;

    @ElementCollection
    @CollectionTable(name = "field_candidate", joinColumns = {
            @JoinColumn(name = "reservation_id", referencedColumnName = "reservation_id"),
            @JoinColumn(name = "field_name", referencedColumnName = "name")})
    @OrderBy("rank")
    private List<FieldCandidate> candidates = new ArrayList<>();

    protected ReservationField() {
    }

    // 빈칸. 목적만 기본 RESERVE
    public static ReservationField blank(Long reservationId, String name) {
        ReservationField f = new ReservationField();
        f.reservationId = reservationId;
        f.name = name;
        if (name.equals("purpose")) {
            f.value = "RESERVE";
            f.needsCheck = false;
        } else {
            f.reason = "MISSING";
            f.askHint = ASK.get(name);
        }
        return f;
    }

    public boolean isStop() {
        return name.equals("from") || name.equals("to");
    }

    public String value() {
        return isStop() ? stopId : value;
    }

    public boolean needsCheck() {
        return needsCheck;
    }

    // 상담원이 고르거나 고친 값
    public void decideByAgent(String newValue, String note, String now) {
        if (isStop()) stopId = newValue;
        else value = newValue;
        needsCheck = false;
        reason = null;
        askHint = null;
        source = "AGENT";
        decidedAt = now;
        this.note = note;
    }

    public <T> FieldDto<T> toDto(Function<String, T> conv) {
        return new FieldDto<>(convert(value(), conv), said, needsCheck, reason, askHint, evidence(),
                convert(previousValue, conv), source, decidedAt, note);
    }

    public StopFieldDto toStopDto() {
        return new StopFieldDto(stopId, said, needsCheck, reason, askHint, evidence(), previousValue, source,
                decidedAt, note, candidates.stream().map(FieldCandidate::toDto).toList(), moreCount);
    }

    private FieldDto.Evidence evidence() {
        return evidenceLineNo == null ? null : new FieldDto.Evidence(evidenceLineNo, evidenceText);
    }

    private static <T> T convert(String v, Function<String, T> conv) {
        return v == null ? null : conv.apply(v);
    }

    public String getName() { return name; }

    public record Key(Long reservationId, String name) implements Serializable {
    }
}
