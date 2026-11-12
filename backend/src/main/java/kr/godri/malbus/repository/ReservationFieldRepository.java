package kr.godri.malbus.repository;

import kr.godri.malbus.domain.ReservationField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationFieldRepository extends JpaRepository<ReservationField, ReservationField.Key> {

    List<ReservationField> findByReservationId(Long reservationId);
}
