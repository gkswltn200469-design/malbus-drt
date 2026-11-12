package kr.godri.malbus.repository;

import kr.godri.malbus.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByOrderByIdDesc();

    List<Reservation> findByStatusOrderByIdDesc(String status);
}
