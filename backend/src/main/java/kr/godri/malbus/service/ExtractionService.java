package kr.godri.malbus.service;

import kr.godri.malbus.common.Times;
import kr.godri.malbus.domain.FieldCandidate;
import kr.godri.malbus.domain.ReservationField;
import kr.godri.malbus.domain.TranscriptLine;
import kr.godri.malbus.dto.StopDto;
import kr.godri.malbus.extract.AiOutput;
import kr.godri.malbus.extract.Extractor;
import kr.godri.malbus.extract.StopMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 추출기 출력(AI 모듈 출력)을 받아서 칸마다 값·확인 필요를 판정. 판정은 llm이든 규칙이든 여기서 함
@Service
@Transactional(readOnly = true)
public class ExtractionService {

    private static final Set<String> PURPOSES = Set.of("RESERVE", "CHANGE", "CANCEL", "INQUIRY");
    private static final int MAX_CANDIDATES = 5;

    private final Extractor extractor;
    private final StopService stopService;

    public ExtractionService(Extractor extractor, StopService stopService) {
        this.extractor = extractor;
        this.stopService = stopService;
    }

    public String mode() {
        return extractor.mode();
    }

    // 저장 안 된 칸 6개를 돌려줌 (reservationId 없음)
    public Map<String, ReservationField> extract(String receivedAt, List<TranscriptLine> lines) {
        List<StopDto> stops = stopService.findAll();
        AiOutput ai = extractor.extract(lines, stops);
        String src = mode().equals("LLM") ? "AI" : "RULE";

        Map<String, ReservationField> f = new LinkedHashMap<>();
        f.put("purpose", purpose(ai.purpose(), lines, src));
        f.put("from", stop("from", ai.from(), lines, stops, src));
        f.put("to", stop("to", ai.to(), lines, stops, src));
        f.put("date", date(ai.date(), Times.dateOf(receivedAt), lines, src));
        f.put("time", time(ai.time(), lines, src));
        f.put("people", people(ai.people(), lines, src));
        checkSameStop(f.get("from"), f.get("to"));
        return f;
    }

    public static void checkSameStop(ReservationField from, ReservationField to) {
        if (from.value() != null && from.value().equals(to.value())) {
            from.markSameStop();
            to.markSameStop();
        }
    }

    private ReservationField purpose(AiOutput.Purpose p, List<TranscriptLine> lines, String src) {
        String v = p == null || p.value() == null || !PURPOSES.contains(p.value()) ? "RESERVE" : p.value();
        Integer no = p == null ? null : p.evidenceLineNo();
        String text = lineText(lines, no);
        // 근거 줄이 없으면 기본값 RESERVE
        return blank("purpose").fill(v, text, false, null, null, text == null ? null : no, text, text == null ? null : src);
    }

    private ReservationField stop(String name, AiOutput.Place p, List<TranscriptLine> lines, List<StopDto> stops, String src) {
        if (p == null || p.said() == null || p.said().isBlank()) return blank(name);
        StopMatcher.Result m = StopMatcher.match(p.said(), stops);

        // ai가 준 순서는 BE 후보 안에서만 반영, 목록 밖 ID는 버림
        List<StopMatcher.Candidate> all = new ArrayList<>(m.candidates());
        List<String> ranked = p.rankedStopIds() == null ? List.of() : p.rankedStopIds();
        all.sort(Comparator.comparingInt(c -> ranked.contains(c.stopId()) ? ranked.indexOf(c.stopId()) : Integer.MAX_VALUE));

        Integer no = p.evidenceLineNo();
        ReservationField f = blank(name);
        if (m.decided() != null) {
            f.fill(m.decided(), p.said(), false, null, null, no, lineText(lines, no), src);
        } else if (all.isEmpty()) {
            f.fill(null, p.said(), true, "NOT_IN_LIST", hint(p, "목록에 없는 곳이에요. 가까운 정류장을 여쭤 보세요"),
                    no, lineText(lines, no), src);
        } else {
            f.fill(null, p.said(), true, "AMBIGUOUS", hint(p, "어느 정류장인지 여쭤 보세요"), no, lineText(lines, no), src);
        }
        List<FieldCandidate> top = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_CANDIDATES, all.size()); i++) {
            StopMatcher.Candidate c = all.get(i);
            top.add(new FieldCandidate(i + 1, c.stopId(), c.matchType(), c.reason()));
        }
        f.setCandidates(top, Math.max(0, all.size() - MAX_CANDIDATES));
        return f;
    }

    // 오늘/내일/모레만 바꿈. 요일, 다음 주 같은 건 확인 필요
    private ReservationField date(AiOutput.DateSaid d, LocalDate base, List<TranscriptLine> lines, String src) {
        if (d == null || d.said() == null || d.said().isBlank()) return blank("date");
        String said = d.said();
        Integer plus = said.contains("모레") ? Integer.valueOf(2) : said.contains("내일") ? Integer.valueOf(1)
                : said.contains("오늘") ? Integer.valueOf(0) : null;
        Integer no = d.evidenceLineNo();
        if (plus == null) {
            return blank("date").fill(null, said, true, "AMBIGUOUS", "며칠인지 여쭤 보세요", no, lineText(lines, no), src);
        }
        return blank("date").fill(base.plusDays(plus).toString(), said, false, null, null, no, lineText(lines, no), src);
    }

    private ReservationField time(AiOutput.TimeSaid t, List<TranscriptLine> lines, String src) {
        if (t == null || t.said() == null || t.said().isBlank()) return blank("time");
        Integer no = t.evidenceLineNo();
        String text = lineText(lines, no);
        if (t.hour() == null) {
            return blank("time").fill(null, t.said(), true, "AMBIGUOUS", "몇 시인지 여쭤 보세요", no, text, src);
        }
        int h = t.hour();
        int m = t.minute() == null ? 0 : t.minute();
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            return blank("time").fill(null, t.said(), true, "INVALID", "시간을 다시 여쭤 보세요", no, text, src);
        }
        if ("PM".equals(t.meridiem()) && h < 12) h += 12;
        if ("AM".equals(t.meridiem()) && h == 12) h = 0;
        boolean guess = t.meridiem() == null && h <= 12;
        if (guess && h >= 1 && h <= 6) h += 12; // 오전·오후 안 말하면 운행 시간 기준 제안: 1~6시는 오후
        String v = "%02d:%02d".formatted(h, m);
        return guess
                ? blank("time").fill(v, t.said(), true, "AMBIGUOUS", "오전인지 오후인지 여쭤 보세요", no, text, src)
                : blank("time").fill(v, t.said(), false, null, null, no, text, src);
    }

    // 말 안 하면 빈칸. 1명 기본값 안 넣음
    private ReservationField people(AiOutput.People p, List<TranscriptLine> lines, String src) {
        if (p == null || (p.said() == null && p.value() == null)) return blank("people");
        Integer no = p.evidenceLineNo();
        String text = lineText(lines, no);
        if (p.value() == null) {
            return blank("people").fill(null, p.said(), true, "AMBIGUOUS", "몇 분인지 여쭤 보세요", no, text, src);
        }
        if (p.value() < 1) {
            return blank("people").fill(String.valueOf(p.value()), p.said(), true, "INVALID", "인원을 다시 여쭤 보세요", no, text, src);
        }
        return blank("people").fill(String.valueOf(p.value()), p.said(), false, null, null, no, text, src);
    }

    private static ReservationField blank(String name) {
        return ReservationField.blank(null, name);
    }

    private static String hint(AiOutput.Place p, String fallback) {
        return p.askHint() == null ? fallback : p.askHint();
    }

    private static String lineText(List<TranscriptLine> lines, Integer no) {
        if (no == null) return null;
        return lines.stream().filter(l -> l.getLineNo() == no).map(TranscriptLine::getText).findFirst().orElse(null);
    }
}
