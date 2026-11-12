package kr.godri.malbus.service;

import kr.godri.malbus.domain.StopAlias;
import kr.godri.malbus.dto.StopDto;
import kr.godri.malbus.repository.StopAliasRepository;
import kr.godri.malbus.repository.StopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Service
@Transactional(readOnly = true)
public class StopService {

    private final StopRepository stopRepository;
    private final StopAliasRepository aliasRepository;

    public StopService(StopRepository stopRepository, StopAliasRepository aliasRepository) {
        this.stopRepository = stopRepository;
        this.aliasRepository = aliasRepository;
    }

    // 17곳이라 캐시 없이 매번 읽음
    public List<StopDto> findAll() {
        Map<String, List<String>> aliases = aliasRepository.findAllByOrderByIdAsc().stream()
                .collect(groupingBy(StopAlias::getStopId, mapping(StopAlias::getAlias, toList())));
        return stopRepository.findAllByOrderByIdAsc().stream()
                .map(s -> new StopDto(s.getId(), s.getName(), s.getLat(), s.getLng(),
                        aliases.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    public long count() {
        return stopRepository.count();
    }

    public boolean exists(String id) {
        return id != null && stopRepository.existsById(id);
    }
}
