package kr.godri.malbus.repository;

import kr.godri.malbus.domain.LearnedAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LearnedAliasRepository extends JpaRepository<LearnedAlias, Long> {

    Optional<LearnedAlias> findBySaidAndStopId(String said, String stopId);

    List<LearnedAlias> findBySaid(String said);

    List<LearnedAlias> findByStatusIn(Collection<String> statuses);
}
