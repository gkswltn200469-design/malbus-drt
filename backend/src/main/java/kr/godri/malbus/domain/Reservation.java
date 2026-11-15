package kr.godri.malbus.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String channel = "AGENT_CALL";
    private String status = "DRAFT";
    private String mode;
    private String receivedAt;
    private String createdAt;
    private String updatedAt;
    private String confirmedAt;
    private String confirmedBy;

    protected Reservation() {
    }

    public Reservation(String mode, String receivedAt, String now) {
        this.mode = mode;
        this.receivedAt = receivedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }

    public void confirm(String by, String now) {
        status = "CONFIRMED";
        confirmedBy = by;
        confirmedAt = now;
        updatedAt = now;
    }

    public void touch(String now) {
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public String getMode() { return mode; }
    public String getReceivedAt() { return receivedAt; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getConfirmedAt() { return confirmedAt; }
    public String getConfirmedBy() { return confirmedBy; }
}
