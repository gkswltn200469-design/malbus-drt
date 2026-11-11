package kr.godri.malbus.service;

import kr.godri.malbus.common.ApiException;
import kr.godri.malbus.common.Times;
import kr.godri.malbus.domain.Reservation;
import kr.godri.malbus.domain.ReservationField;
import kr.godri.malbus.domain.TranscriptLine;
import kr.godri.malbus.dto.CreateReservationRequest;
import kr.godri.malbus.dto.ReservationDto;
import kr.godri.malbus.dto.ReservationSummary;
import kr.godri.malbus.dto.TranscriptLineDto;
import kr.godri.malbus.repository.ReservationFieldRepository;
import kr.godri.malbus.repository.ReservationRepository;
import kr.godri.malbus.repository.TranscriptLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationFieldRepository fieldRepository;
    private final TranscriptLineRepository lineRepository;
    private final StopService stopService;

    public ReservationService(ReservationRepository reservationRepository, ReservationFieldRepository fieldRepository,
                              TranscriptLineRepository lineRepository, StopService stopService) {
        this.reservationRepository = reservationRepository;
        this.fieldRepository = fieldRepository;
        this.lineRepository = lineRepository;
        this.stopService = stopService;
    }

    public ReservationDto create(CreateReservationRequest req) {
        String now = Times.now();
        String receivedAt = req == null || req.receivedAt() == null ? now : Times.format(Times.parse(req.receivedAt()));
        Reservation r = reservationRepository.save(new Reservation("RULE", receivedAt, now));
        fieldRepository.saveAll(ReservationField.NAMES.stream().map(n -> ReservationField.blank(r.getId(), n)).toList());
        return toDto(r, fields(r.getId()));
    }

    @Transactional(readOnly = true)
    public ReservationDto get(Long id) {
        return toDto(find(id), fields(id));
    }

    @Transactional(readOnly = true)
    public List<ReservationSummary> list(String status) {
        if (status != null && !status.equals("DRAFT") && !status.equals("CONFIRMED")) {
            throw ApiException.invalid("status는 DRAFT 또는 CONFIRMED");
        }
        List<Reservation> rs = status == null
                ? reservationRepository.findAllByOrderByIdDesc()
                : reservationRepository.findByStatusOrderByIdDesc(status);
        return rs.stream().map(r -> {
            Map<String, ReservationField> f = fields(r.getId());
            String people = f.get("people").value();
            return new ReservationSummary(r.getId(), r.getStatus(), r.getReceivedAt(), f.get("from").value(),
                    f.get("to").value(), f.get("date").value(), f.get("time").value(),
                    people == null ? null : Integer.valueOf(people));
        }).toList();
    }

    private Reservation find(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> ApiException.notFound("예약 없음: " + id));
    }

    private Map<String, ReservationField> fields(Long id) {
        return fieldRepository.findByReservationId(id).stream()
                .collect(toMap(ReservationField::getName, Function.identity()));
    }

    private ReservationDto toDto(Reservation r, Map<String, ReservationField> f) {
        List<TranscriptLineDto> lines = lineRepository.findByReservationIdOrderByLineNo(r.getId()).stream()
                .map(TranscriptLine::toDto).toList();
        return new ReservationDto(r.getId(), r.getChannel(), r.getStatus(), r.getMode(), r.getReceivedAt(),
                f.get("purpose").toDto(s -> s), f.get("from").toStopDto(), f.get("to").toStopDto(),
                f.get("date").toDto(s -> s), f.get("time").toDto(s -> s), f.get("people").toDto(Integer::valueOf),
                List.of(), lines, r.getCreatedAt(), r.getUpdatedAt(), r.getConfirmedAt(), r.getConfirmedBy());
    }
}
