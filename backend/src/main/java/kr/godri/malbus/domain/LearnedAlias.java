package kr.godri.malbus.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

// 상담원이 추가한 별칭
@Entity
@Table(name = "learned_alias")
public class LearnedAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String said;
    private String stopId;
    private int count = 1;
    private String status = "PENDING";
    private String firstSeen;
    private String lastSeen;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "learned_alias_reservation", joinColumns = @JoinColumn(name = "learned_alias_id"))
    @Column(name = "reservation_id")
    @OrderBy
    private Set<Long> reservationIds = new LinkedHashSet<>();

    protected LearnedAlias() {
    }

    public LearnedAlias(String said, String stopId, String now) {
        this.said = said;
        this.stopId = stopId;
        this.firstSeen = now;
        this.lastSeen = now;
    }

    // 같은 표현으로 같은 정류장을 또 고름
    public void seenAgain(String now) {
        count++;
        lastSeen = now;
    }

    public void addReservation(Long reservationId) {
        if (reservationId != null) reservationIds.add(reservationId);
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isApproved() {
        return "APPROVED".equals(status);
    }

    public Long getId() { return id; }
    public String getSaid() { return said; }
    public String getStopId() { return stopId; }
    public int getCount() { return count; }
    public String getStatus() { return status; }
    public String getFirstSeen() { return firstSeen; }
    public String getLastSeen() { return lastSeen; }
    public Set<Long> getReservationIds() { return reservationIds; }
}
