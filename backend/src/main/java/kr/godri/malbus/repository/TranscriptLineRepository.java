package kr.godri.malbus.repository;

import kr.godri.malbus.domain.TranscriptLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptLineRepository extends JpaRepository<TranscriptLine, TranscriptLine.Key> {

    List<TranscriptLine> findByReservationIdOrderByLineNo(Long reservationId);
}
