package kr.godri.malbus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stop_alias")
public class StopAlias {

    @Id
    private Long id;
    @Column(name = "stop_id")
    private String stopId;
    private String alias;

    protected StopAlias() {
    }

    public String getStopId() { return stopId; }
    public String getAlias() { return alias; }
}
