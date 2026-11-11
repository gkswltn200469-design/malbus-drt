package kr.godri.malbus.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stop")
public class Stop {

    @Id
    private String id;
    private String name;
    private double lat;
    private double lng;

    protected Stop() {
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
}
