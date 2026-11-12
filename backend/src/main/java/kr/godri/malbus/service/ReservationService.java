package kr.godri.malbus.service;

import com.fasterxml.jackson.databind.JsonNode;
import kr.godri.malbus.common.ApiException;
import kr.godri.malbus.common.Times;
import kr.godri.malbus.domain.Reservation;
import kr.godri.malbus.domain.ReservationField;
import kr.godri.malbus.domain.TranscriptLine;
import kr.godri.malbus.dto.ConfirmRequest;
import kr.godri.malbus.dto.CreateReservationRequest;
import kr.godri.malbus.dto.LineRequest;
import kr.godri.malbus.dto.PatchFieldRequest;
import kr.godri.malbus.dto.PatchFieldResponse;
import kr.godri.malbus.dto.ReservationDto;
import kr.godri.malbus.dto.ReservationSummary;
import kr.godri.malbus.dto.TranscriptLineDto;
import kr.godri.malbus.repository.ReservationFieldRepository;
import kr.godri.malbus.repository.ReservationRepository;
import kr.godri.malbus.repository.TranscriptLineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toMap;

@Service
@Transactional
public class ReservationService {

    private static final Set<String> PURPOSES = Set.of("RESERVE", "CHANGE", "CANCEL", "INQUIRY");
    private static final Pattern HHMM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final ReservationRepository reservationRepository;
    private final ReservationFieldRepository fieldRepository;
    private final TranscriptLineRepository lineRepository;
    private final StopService stopService;
    private final ExtractionService extractionService;
    private final AliasService aliasService;

    public ReservationService(ReservationRepository reservationRepository, ReservationFieldRepository fieldRepository,
                              TranscriptLineRepository lineRepository, StopService stopService,
                              ExtractionService extractionService, AliasService aliasService) {
        this.reservationRepository = reservationRepository;
        this.fieldRepository = fieldRepository;
        this.lineRepository = lineRepository;
        this.stopService = stopService;
        this.extractionService = extractionService;
        this.aliasService = aliasService;
    }

    public ReservationDto create(CreateReservationRequest req) {
        String now = Times.now();
        String receivedAt = req == null || req.receivedAt() == null ? now : Times.format(Times.parse(req.receivedAt()));
        Reservation r = reservationRepository.save(new Reservation(extractionService.mode(), receivedAt, now));
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

    // 통화 한 줄 추가 -> 전체 통화로 다시 추출. 확정된 예약은 줄만 추가
    public ReservationDto addLine(Long id, LineRequest req) {
        if (req == null) throw ApiException.invalid("speaker, text가 필요함");
        req.validate();
        Reservation r = find(id);
        String now = Times.now();
        addLine(id, now, req.speaker(), req.text().trim());
        Map<String, ReservationField> fields = fields(id);
        if (!r.isConfirmed()) {
            Map<String, ReservationField> fresh = extractionService.extract(r.getReceivedAt(),
                    lineRepository.findByReservationIdOrderByLineNo(id));
            fields.forEach((name, f) -> f.applyExtracted(fresh.get(name)));
            ExtractionService.checkSameStop(fields.get("from"), fields.get("to"));
            r.touch(now);
        }
        return toDto(r, fields);
    }

    public PatchFieldResponse patchField(Long id, String name, PatchFieldRequest req) {
        if (!ReservationField.NAMES.contains(name)) {
            throw ApiException.invalid("없는 칸: " + name);
        }
        Reservation r = findDraft(id);
        Map<String, ReservationField> fields = fields(id);
        String value = validate(r, fields, name, req == null ? null : req.value());
        String now = Times.now();
        ReservationField field = fields.get(name);
        field.decideByAgent(value, req.note(), now);
        r.touch(now);
        // 출발·도착에서 고객 말이 그 정류장 이름·별칭에 없으면 별칭 추가 모달용 제안
        PatchFieldResponse.AliasSuggestion suggestion = field.isStop() ? aliasService.suggest(field.said(), value) : null;
        return new PatchFieldResponse(toDto(r, fields), suggestion);
    }

    public ReservationDto confirm(Long id, ConfirmRequest req) {
        Reservation r = findDraft(id);
        if (req == null || req.confirmedBy() == null || req.confirmedBy().isBlank()) {
            throw ApiException.invalid("confirmedBy(상담원 이름)가 필요함");
        }
        Map<String, ReservationField> f = fields(id);
        if (!"RESERVE".equals(f.get("purpose").value())) {
            throw new ApiException(HttpStatus.CONFLICT, "PURPOSE_NOT_SUPPORTED", "예약이 아닌 건은 확정하지 않음");
        }
        List<String> left = ReservationField.REQUIRED.stream()
                .filter(n -> f.get(n).value() == null || f.get(n).needsCheck())
                .toList();
        if (!left.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFIRM_BLOCKED", "확인 필요 " + left.size() + "칸 남음",
                    Map.of("fields", left));
        }
        // patch에서 이미 막지만 확정 전에 한 번 더 봄
        if (f.get("from").value().equals(f.get("to").value())) {
            throw ApiException.badRequest("SAME_STOP", "출발과 도착이 같음");
        }
        if (LocalDate.parse(f.get("date").value()).isBefore(Times.dateOf(r.getReceivedAt()))) {
            throw ApiException.badRequest("PAST_DATE", "접수일 이전 날짜");
        }
        if (Integer.parseInt(f.get("people").value()) < 1) {
            throw ApiException.badRequest("INVALID_PEOPLE", "인원은 1명 이상");
        }

        String now = Times.now();
        String by = req.confirmedBy().trim();
        r.confirm(by, now);
        addLine(id, now, "SYSTEM", by + " 상담원이 예약 확정");
        return toDto(r, f);
    }

    private String validate(Reservation r, Map<String, ReservationField> fields, String name, JsonNode node) {
        if (node == null || node.isNull()) {
            throw ApiException.invalid("value가 비었음");
        }
        if (name.equals("people")) {
            boolean number = node.isIntegralNumber() && node.canConvertToInt();
            boolean text = node.isTextual() && node.asText().trim().matches("-?\\d{1,9}");
            if (!number && !text) {
                throw ApiException.invalid("인원은 정수");
            }
            int n = number ? node.asInt() : Integer.parseInt(node.asText().trim());
            if (n < 1) {
                throw ApiException.badRequest("INVALID_PEOPLE", "인원은 1명 이상");
            }
            return String.valueOf(n);
        }
        if (!node.isTextual()) {
            throw ApiException.invalid(name + " 값은 문자열");
        }
        String v = node.asText().trim();
        switch (name) {
            case "purpose" -> {
                if (!PURPOSES.contains(v)) throw ApiException.invalid("목적은 RESERVE, CHANGE, CANCEL, INQUIRY 중 하나");
            }
            case "from", "to" -> {
                if (!stopService.exists(v)) throw ApiException.badRequest("STOP_NOT_FOUND", "목록에 없는 정류장: " + v);
                String other = fields.get(name.equals("from") ? "to" : "from").value();
                if (v.equals(other)) throw ApiException.badRequest("SAME_STOP", "출발과 도착이 같음");
            }
            case "date" -> {
                LocalDate d;
                try {
                    d = LocalDate.parse(v);
                } catch (DateTimeParseException e) {
                    throw ApiException.invalid("날짜 형식은 YYYY-MM-DD");
                }
                if (d.isBefore(Times.dateOf(r.getReceivedAt()))) {
                    throw ApiException.badRequest("PAST_DATE", "접수일 이전 날짜");
                }
            }
            case "time" -> {
                if (!HHMM.matcher(v).matches()) throw ApiException.invalid("시간 형식은 HH:MM");
            }
            default -> {
            }
        }
        return v;
    }

    private Reservation find(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> ApiException.notFound("예약 없음: " + id));
    }

    private Reservation findDraft(Long id) {
        Reservation r = find(id);
        if (r.isConfirmed()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CONFIRMED", "이미 확정된 예약");
        }
        return r;
    }

    private Map<String, ReservationField> fields(Long id) {
        return fieldRepository.findByReservationId(id).stream()
                .collect(toMap(ReservationField::getName, Function.identity()));
    }

    private void addLine(Long id, String at, String speaker, String text) {
        int next = lineRepository.findByReservationIdOrderByLineNo(id).size() + 1;
        lineRepository.save(new TranscriptLine(id, next, at, speaker, text));
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
