package kr.godri.malbus.repository;

import kr.godri.malbus.domain.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StopRepository extends JpaRepository<Stop, String> {

    List<Stop> findAllByOrderByIdAsc();
}
