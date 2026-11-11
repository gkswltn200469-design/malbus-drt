package kr.godri.malbus;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationLineTest {

    @Autowired
    MockMvc mvc;

    long create() throws Exception {
        String body = mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-11-17T14:00:00+09:00\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions say(long id, String speaker, String text) throws Exception {
        return mvc.perform(post("/reservations/" + id + "/lines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speaker\":\"" + speaker + "\",\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 줄을_추가하면_양식이_채워진다() throws Exception {
        long id = create();
        say(id, "AGENT", "네, 범물동 DRT입니다");
        say(id, "CUSTOMER", "내일 오전 아홉 시 반에 범물청구타운 앞에서 동아백화점 수성점 앞까지 갈라고요")
                .andExpect(jsonPath("$.transcript.length()").value(2))
                .andExpect(jsonPath("$.from.value").value("S02"))
                .andExpect(jsonPath("$.from.candidates[0].matchType").value("NAME"))
                .andExpect(jsonPath("$.to.value").value("S03"))
                .andExpect(jsonPath("$.date.value").value("2026-11-18"))
                .andExpect(jsonPath("$.time.value").value("09:30"))
                .andExpect(jsonPath("$.people.value").isEmpty());
        say(id, "AGENT", "몇 분 타세요?");
        say(id, "CUSTOMER", "혼자요")
                .andExpect(jsonPath("$.people.value").value(1))
                .andExpect(jsonPath("$.people.evidence.lineNo").value(4))
                .andExpect(jsonPath("$.from.value").value("S02"));
    }

    @Test
    void 상담원이_고른_칸은_재추출로_안_덮음() throws Exception {
        long id = create();
        say(id, "CUSTOMER", "모레 오전 열 시에 동사무소에서 하늘채로 가요")
                .andExpect(jsonPath("$.from.reason").value("AMBIGUOUS"));
        mvc.perform(patch("/reservations/" + id + "/fields/from").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"S12\",\"note\":null}"))
                .andExpect(status().isOk());

        say(id, "AGENT", "범물1동 행정복지센터 앞 맞으시죠?");
        say(id, "CUSTOMER", "네 맞아요")
                .andExpect(jsonPath("$.from.value").value("S12"))
                .andExpect(jsonPath("$.from.source").value("AGENT"))
                .andExpect(jsonPath("$.from.needsCheck").value(false));

        // 고객이 다른 곳을 말하면 바뀜 표시
        say(id, "CUSTOMER", "아 아니다 청구타운에서 탈게요")
                .andExpect(jsonPath("$.from.value").value("S02"))
                .andExpect(jsonPath("$.from.previous").value("S12"))
                .andExpect(jsonPath("$.from.reason").value("CORRECTED"))
                .andExpect(jsonPath("$.from.needsCheck").value(true))
                .andExpect(jsonPath("$.from.source").value("RULE"));

        // 다른 줄이 더 들어와도 바뀜 표시는 남음
        say(id, "CUSTOMER", "둘이 갑니다")
                .andExpect(jsonPath("$.from.reason").value("CORRECTED"))
                .andExpect(jsonPath("$.people.value").value(2));
    }

    @Test
    void 고객이_값을_고치면_이전값_남김() throws Exception {
        long id = create();
        say(id, "CUSTOMER", "내일 아홉 시에 하늘채에서 백화점 가요")
                .andExpect(jsonPath("$.time.value").value("09:00"))
                .andExpect(jsonPath("$.time.needsCheck").value(true));
        say(id, "CUSTOMER", "아 아니다 오전 열 시 반으로 해 주세요")
                .andExpect(jsonPath("$.time.value").value("10:30"))
                .andExpect(jsonPath("$.time.previous").value("09:00"))
                .andExpect(jsonPath("$.time.needsCheck").value(false));
    }

    @Test
    void 확정된_예약은_줄만_추가() throws Exception {
        long id = create();
        say(id, "CUSTOMER", "내일 오전 열 시에 우방에서 백화점 가요. 혼자요");
        mvc.perform(post("/reservations/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedBy\":\"김채영\"}"))
                .andExpect(status().isOk());
        say(id, "CUSTOMER", "아니 하늘채에서 탈게요")
                .andExpect(jsonPath("$.transcript.length()").value(3))
                .andExpect(jsonPath("$.from.value").value("S10"));
    }

    @Test
    void 줄_형식_오류() throws Exception {
        long id = create();
        mvc.perform(post("/reservations/" + id + "/lines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speaker\":\"CUSTOMER\",\"text\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/reservations/999999/lines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speaker\":\"CUSTOMER\",\"text\":\"안녕\"}"))
                .andExpect(status().isNotFound());
    }
}
