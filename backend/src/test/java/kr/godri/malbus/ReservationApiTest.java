package kr.godri.malbus;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiTest {

    @Autowired
    MockMvc mvc;

    long create() throws Exception {
        String body = mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-11-17T14:00:00+09:00\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions patchField(long id, String name, String valueJson) throws Exception {
        return mvc.perform(patch("/reservations/" + id + "/fields/" + name).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":" + valueJson + ",\"note\":null}"));
    }

    ResultActions confirm(long id) throws Exception {
        return mvc.perform(post("/reservations/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedBy\":\"김채영\"}"));
    }

    void fillAll(long id) throws Exception {
        patchField(id, "from", "\"S10\"").andExpect(status().isOk());
        patchField(id, "to", "\"S01\"").andExpect(status().isOk());
        patchField(id, "date", "\"2026-11-18\"").andExpect(status().isOk());
        patchField(id, "time", "\"10:00\"").andExpect(status().isOk());
    }

    @Test
    void 빈_예약_생성() throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-11-17T14:00:00+09:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.channel").value("AGENT_CALL"))
                .andExpect(jsonPath("$.receivedAt").value("2026-11-17T14:00:00+09:00"))
                .andExpect(jsonPath("$.purpose.value").value("RESERVE"))
                .andExpect(jsonPath("$.purpose.needsCheck").value(false))
                .andExpect(jsonPath("$.from.value").isEmpty())
                .andExpect(jsonPath("$.from.needsCheck").value(true))
                .andExpect(jsonPath("$.from.reason").value("MISSING"))
                .andExpect(jsonPath("$.from.candidates", hasSize(0)))
                .andExpect(jsonPath("$.people.value").value(1))
                .andExpect(jsonPath("$.people.needsCheck").value(false))
                .andExpect(jsonPath("$.people.source").value("RULE"))
                .andExpect(jsonPath("$.assist", hasSize(0)))
                .andExpect(jsonPath("$.transcript", hasSize(0)));
    }

    @Test
    void 접수시각_생략하면_서버_시각() throws Exception {
        mvc.perform(post("/reservations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());
    }

    @Test
    void 상담원이_고른_칸은_AGENT() throws Exception {
        long id = create();
        patchField(id, "time", "\"10:00\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.time.value").value("10:00"))
                .andExpect(jsonPath("$.reservation.time.needsCheck").value(false))
                .andExpect(jsonPath("$.reservation.time.reason").isEmpty())
                .andExpect(jsonPath("$.reservation.time.source").value("AGENT"))
                .andExpect(jsonPath("$.reservation.time.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.aliasSuggestion").isEmpty());
        mvc.perform(get("/reservations/" + id))
                .andExpect(jsonPath("$.time.value").value("10:00"));
    }

    @Test
    void 칸_수정_검증() throws Exception {
        long id = create();
        patchField(id, "from", "\"S99\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STOP_NOT_FOUND"));
        patchField(id, "from", "\"S01\"").andExpect(status().isOk());
        patchField(id, "to", "\"S01\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SAME_STOP"));
        patchField(id, "date", "\"2026-11-16\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAST_DATE"));
        patchField(id, "date", "\"2026-11-17\"").andExpect(status().isOk());
        patchField(id, "people", "2").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("인원은 이번 범위에서 고정 1"));
        patchField(id, "time", "\"25:00\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        patchField(id, "purpose", "\"BOOK\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        patchField(id, "seat", "\"1\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        patchField(999999, "time", "\"10:00\"").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 확인_필요가_남으면_확정_막힘() throws Exception {
        long id = create();
        patchField(id, "from", "\"S10\"");
        confirm(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRM_BLOCKED"))
                .andExpect(jsonPath("$.error.message").value("확인 필요 3칸 남음"))
                .andExpect(jsonPath("$.error.details.fields", contains("to", "date", "time")));
    }

    @Test
    void 확정하면_SYSTEM_줄이_남고_다시_못_고침() throws Exception {
        long id = create();
        fillAll(id);
        confirm(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedBy").value("김채영"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.transcript[-1].speaker").value("SYSTEM"));
        confirm(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CONFIRMED"));
        patchField(id, "time", "\"11:00\"").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CONFIRMED"));
        mvc.perform(get("/reservations?status=CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].from").value("S10"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].people").value(1));
    }

    @Test
    void 예약이_아닌_목적은_확정_안함() throws Exception {
        long id = create();
        fillAll(id);
        patchField(id, "purpose", "\"CHANGE\"").andExpect(status().isOk());
        confirm(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PURPOSE_NOT_SUPPORTED"));
        patchField(id, "purpose", "\"RESERVE\"");
        confirm(id).andExpect(status().isOk());
    }

    @Test
    void 목록_상태_검증() throws Exception {
        mvc.perform(get("/reservations?status=DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
