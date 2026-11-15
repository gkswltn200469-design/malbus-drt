package kr.godri.malbus.extract;

import kr.godri.malbus.dto.StopDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 고객이 한 말 -> 정류장 후보. 목록 안에서만 찾음
// 순서: 이름 -> 별칭 -> 배운 별칭 -> 부분 일치 -> 비슷한 글자
public final class StopMatcher {

    public record Candidate(String stopId, String matchType, String reason) {
    }

    // decided: 이름·별칭·승인된 배운 별칭 일치가 딱 1곳일 때만 그 정류장, 아니면 null
    public record Result(List<Candidate> candidates, String decided) {
    }

    // 상담원이 추가한 별칭. 승인 전(PENDING)은 후보 순서만 올림
    public record Learned(String said, String stopId, boolean approved) {
    }

    private StopMatcher() {
    }

    public static Result match(String said, List<StopDto> stops, List<Learned> learned) {
        String full = Texts.norm(said);
        String dir = Texts.direction(full);
        String core = Texts.withoutDirection(full);

        Map<String, Candidate> strong = new LinkedHashMap<>();
        for (StopDto s : stops) {
            String name = nameKey(s);
            if (full.equals(name) || full.equals(Texts.key(s.name()))
                    || (core.equals(Texts.withoutDirection(name)) && compatible(dir, Texts.direction(name)))) {
                strong.putIfAbsent(s.id(), new Candidate(s.id(), "NAME", "이름 '" + s.name() + "' 일치"));
            }
        }
        for (StopDto s : stops) {
            for (String alias : s.aliases()) {
                String a = Texts.key(alias);
                if (full.equals(a) || core.equals(a)) {
                    strong.putIfAbsent(s.id(), new Candidate(s.id(), "ALIAS", "별칭 '" + alias + "' 일치"));
                }
            }
        }
        Map<String, Candidate> pending = new LinkedHashMap<>();
        for (Learned l : learned) {
            String k = Texts.key(l.said());
            if (full.equals(k) || core.equals(Texts.withoutDirection(k))) {
                Candidate c = new Candidate(l.stopId(), "LEARNED",
                        "배운 별칭 '" + l.said() + "' 일치" + (l.approved() ? "" : " (승인 전)"));
                (l.approved() ? strong : pending).putIfAbsent(l.stopId(), c);
            }
        }

        List<Candidate> exact = narrow(new ArrayList<>(strong.values()), dir, stops);
        if (!exact.isEmpty()) {
            List<Candidate> out = new ArrayList<>(exact);
            addNew(out, pending.values());
            return new Result(out, exact.size() == 1 ? exact.get(0).stopId() : null);
        }
        List<Candidate> rest = new ArrayList<>(pending.values());
        addNew(rest, weak(core, stops));
        return new Result(narrow(rest, dir, stops), null);
    }

    private static void addNew(List<Candidate> to, Iterable<Candidate> more) {
        for (Candidate c : more) {
            if (to.stream().noneMatch(x -> x.stopId().equals(c.stopId()))) to.add(c);
        }
    }

    // 부분 일치, 비슷한 글자. 둘 다 확인 필요
    private static List<Candidate> weak(String core, List<StopDto> stops) {
        record Scored(Candidate c, double score) {
        }
        List<Scored> partial = new ArrayList<>();
        List<Scored> fuzzy = new ArrayList<>();
        for (StopDto s : stops) {
            List<String> keys = new ArrayList<>();
            keys.add(Texts.withoutDirection(nameKey(s)));
            s.aliases().forEach(a -> keys.add(Texts.key(a)));

            String bestCommon = "";
            double bestSim = 0;
            String simKey = null;
            for (String k : keys) {
                String common = Texts.longestCommon(core, k);
                boolean contains = common.length() >= 2 && (k.contains(core) || core.contains(k));
                if ((common.length() >= 3 || contains) && common.length() > bestCommon.length()) {
                    bestCommon = common;
                }
                double sim = Texts.similarity(core, k);
                if (sim > bestSim) {
                    bestSim = sim;
                    simKey = k;
                }
            }
            if (!bestCommon.isEmpty()) {
                partial.add(new Scored(new Candidate(s.id(), "PARTIAL", "'" + bestCommon + "' 부분 일치"), bestCommon.length()));
            } else if (core.length() >= 2 && bestSim >= 0.6) {
                fuzzy.add(new Scored(new Candidate(s.id(), "FUZZY", "'" + simKey + "'와 글자가 비슷함"), bestSim));
            }
        }
        Comparator<Scored> byScore = Comparator.comparingDouble(Scored::score).reversed();
        List<Candidate> out = new ArrayList<>();
        partial.stream().sorted(byScore).forEach(x -> out.add(x.c()));
        fuzzy.stream().sorted(byScore).forEach(x -> out.add(x.c()));
        return out;
    }

    // 앞/건너를 말했으면 반대쪽 정류장은 뺌. 다 빠지면 그대로
    private static List<Candidate> narrow(List<Candidate> list, String dir, List<StopDto> stops) {
        if (dir == null || list.size() < 2) return list;
        List<Candidate> kept = list.stream().filter(c -> {
            String stopDir = stops.stream().filter(s -> s.id().equals(c.stopId())).findFirst()
                    .map(s -> Texts.direction(nameKey(s))).orElse(null);
            return compatible(dir, stopDir);
        }).toList();
        return kept.isEmpty() ? list : kept;
    }

    private static boolean compatible(String said, String stop) {
        return said == null || stop == null || said.equals(stop);
    }

    // 괄호 뺀 정류장명 "범물1동행정복지센터건너(용지역)" -> "범물1동행정복지센터건너"
    private static String nameKey(StopDto s) {
        return Texts.key(s.name().replaceAll("\\(.*?\\)", ""));
    }
}
