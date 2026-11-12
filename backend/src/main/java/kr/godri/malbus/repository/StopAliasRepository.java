package kr.godri.malbus.repository;

import kr.godri.malbus.domain.StopAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StopAliasRepository extends JpaRepository<StopAlias, Long> {

    List<StopAlias> findAllByOrderByIdAsc();
}
