package kr.godri.malbus.service;

import kr.godri.malbus.common.ApiException;
import kr.godri.malbus.common.Times;
import kr.godri.malbus.domain.LearnedAlias;
import kr.godri.malbus.dto.AddAliasRequest;
import kr.godri.malbus.dto.LearnedAliasDto;
import kr.godri.malbus.dto.PatchFieldResponse;
import kr.godri.malbus.extract.StopMatcher;
import kr.godri.malbus.extract.Texts;
import kr.godri.malbus.repository.LearnedAliasRepository;
import kr.godri.malbus.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@Transactional
public class AliasService {

    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final LearnedAliasRepository aliasRepository;
    private final ReservationRepository reservationRepository;
    private final StopService stopService;

    public AliasService(LearnedAliasRepository aliasRepository, ReservationRepository reservationRepository,
                        StopService stopService) {
        this.aliasRepository = aliasRepository;
        this.reservationRepository = reservationRepository;
        this.stopService = stopService;
    }

    public record Saved(boolean created, LearnedAliasDto alias) {
    }

    // 같은 표현 + 같은 정류장이면 count만 +1
    public Saved add(AddAliasRequest req) {
        if (req == null || req.said() == null || clean(req.said()).isEmpty()) {
            throw ApiException.invalid("said가 비었음");
        }
        if (!stopService.exists(req.stopId())) {
            throw ApiException.badRequest("STOP_NOT_FOUND", "목록에 없는 정류장: " + req.stopId());
        }
        if (req.reservationId() != null && !reservationRepository.existsById(req.reservationId())) {
            throw ApiException.notFound("예약 없음: " + req.reservationId());
        }
        String said = clean(req.said());
        String now = Times.now();
        LearnedAlias alias = aliasRepository.findBySaidAndStopId(said, req.stopId()).orElse(null);
        boolean created = alias == null;
        if (created) {
            alias = aliasRepository.save(new LearnedAlias(said, req.stopId(), now));
        } else {
            alias.seenAgain(now);
        }
        alias.addReservation(req.reservationId());
        return new Saved(created, toDto(alias, null));
    }

    @Transactional(readOnly = true)
    public List<LearnedAliasDto> list(String status) {
        if (status != null && !STATUSES.contains(status)) {
            throw ApiException.invalid("status는 PENDING, APPROVED, REJECTED 중 하나");
        }
        List<LearnedAlias> all = status == null ? aliasRepository.findAll() : aliasRepository.findByStatusIn(List.of(status));
        // 승인 추천을 위로
        return all.stream().map(a -> toDto(a, null))
                .sorted(Comparator.comparing(LearnedAliasDto::recommended).reversed()
                        .thenComparing(LearnedAliasDto::id))
                .toList();
    }

    public LearnedAliasDto approve(Long id) {
        LearnedAlias alias = find(id);
        alias.setStatus("APPROVED");
        boolean conflicted = !conflicts(alias).isEmpty();
        return toDto(alias, conflicted ? "다른 정류장 선택 기록 있음" : null);
    }

    public LearnedAliasDto reject(Long id) {
        LearnedAlias alias = find(id);
        alias.setStatus("REJECTED");
        return toDto(alias, null);
    }

    // 매칭에 쓸 배운 별칭. 거절된 건 뺌
    @Transactional(readOnly = true)
    public List<StopMatcher.Learned> forMatching() {
        return aliasRepository.findByStatusIn(List.of("PENDING", "APPROVED")).stream()
                .map(a -> new StopMatcher.Learned(a.getSaid(), a.getStopId(), a.isApproved()))
                .toList();
    }

    // 고객이 한 말이 고른 정류장의 이름·별칭에 없으면 별칭 추가 제안
    @Transactional(readOnly = true)
    public PatchFieldResponse.AliasSuggestion suggest(String said, String stopId) {
        if (said == null || clean(said).isEmpty()) return null;
        boolean known = StopMatcher.match(said, stopService.findAll(), List.of()).candidates().stream()
                .anyMatch(c -> c.stopId().equals(stopId) && !c.matchType().equals("PARTIAL") && !c.matchType().equals("FUZZY"));
        return known ? null : new PatchFieldResponse.AliasSuggestion(clean(said), stopId);
    }

    private LearnedAlias find(Long id) {
        return aliasRepository.findById(id).orElseThrow(() -> ApiException.notFound("별칭 없음: " + id));
    }

    // 같은 표현으로 다른 정류장을 고른 횟수
    private Map<String, Integer> conflicts(LearnedAlias alias) {
        Map<String, Integer> map = new TreeMap<>();
        for (LearnedAlias other : aliasRepository.findBySaid(alias.getSaid())) {
            if (!other.getStopId().equals(alias.getStopId())) map.merge(other.getStopId(), other.getCount(), Integer::sum);
        }
        return map;
    }

    private LearnedAliasDto toDto(LearnedAlias a, String warning) {
        Map<String, Integer> conflicts = conflicts(a);
        return new LearnedAliasDto(a.getId(), a.getSaid(), a.getStopId(), a.getCount(), conflicts, a.getStatus(),
                a.getFirstSeen(), a.getLastSeen(), new ArrayList<>(a.getReservationIds()),
                a.getCount() >= 2 && conflicts.isEmpty(), warning);
    }

    // 조사 떼고 공백 하나로
    private static String clean(String said) {
        return Texts.stripParticles(said).replaceAll("\\s+", " ").trim();
    }
}
