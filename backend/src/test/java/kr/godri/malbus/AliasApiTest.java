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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 다른 테스트와 db를 같이 써서 표현은 여기서만 쓰는 말로
@SpringBootTest
@AutoConfigureMockMvc
class AliasApiTest {

    @Autowired
    MockMvc mvc;

    ResultActions add(String said, String stopId, Long reservationId) throws Exception {
        return mvc.perform(post("/aliases").contentType(MediaType.APPLICATION_JSON)
                .content("{\"said\":\"" + said + "\",\"stopId\":\"" + stopId + "\",\"reservationId\":" + reservationId + "}"));
    }

    long id(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    long reservation() throws Exception {
        String body = mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-11-17T14:00:00+09:00\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions extract(String text) throws Exception {
        return mvc.perform(post("/extract").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receivedAt\":\"2026-11-17T14:00:00+09:00\",\"transcript\":[{\"speaker\":\"CUSTOMER\",\"text\":\"" + text + "\"}]}"));
    }

    @Test
    void 같은_표현_두번이면_승인_추천() throws Exception {
        long rid = reservation();
        add("느티나무 쉼터", "S07", rid)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.recommended").value(false))
                .andExpect(jsonPath("$.reservationIds", contains((int) rid)));
        add("느티나무 쉼터에서", "S07", null) // 조사는 떼고 저장
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.said").value("느티나무 쉼터"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.conflicts").isEmpty())
                .andExpect(jsonPath("$.recommended").value(true))
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    void 다른_정류장_기록이_있으면_추천_안하고_승인시_경고() throws Exception {
        long a = id(add("방앗간 앞", "S10", null));
        add("방앗간 앞", "S10", null);
        add("방앗간 앞", "S08", null)
                .andExpect(jsonPath("$.conflicts.S10").value(2))
                .andExpect(jsonPath("$.recommended").value(false));
        mvc.perform(post("/aliases/" + a + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.conflicts.S08").value(1))
                .andExpect(jsonPath("$.recommended").value(false))
                .andExpect(jsonPath("$.warning").value("다른 정류장 선택 기록 있음"));
    }

    @Test
    void 거절과_상태별_목록() throws Exception {
        long a = id(add("빨래터", "S05", null));
        mvc.perform(post("/aliases/" + a + "/reject"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/aliases?status=REJECTED"))
                .andExpect(jsonPath("$[?(@.id == " + a + ")].said").value("빨래터"));
        mvc.perform(get("/aliases?status=APPROVED"))
                .andExpect(jsonPath("$[?(@.id == " + a + ")]").isEmpty());
    }

    @Test
    void 에러() throws Exception {
        add("없는곳", "S99", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STOP_NOT_FOUND"));
        add(" ", "S01", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/aliases/999999/approve")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 승인_전은_후보만_올리고_승인하면_확인_완료() throws Exception {
        long a = id(add("정자나무 앞", "S07", null));
        extract("내일 오전 열 시에 정자나무 앞에서 백화점 가요")
                .andExpect(jsonPath("$.from.value").isEmpty())
                .andExpect(jsonPath("$.from.needsCheck").value(true))
                .andExpect(jsonPath("$.from.candidates[0].stopId").value("S07"))
                .andExpect(jsonPath("$.from.candidates[0].matchType").value("LEARNED"));
        mvc.perform(post("/aliases/" + a + "/approve")).andExpect(status().isOk());
        extract("내일 오전 열 시에 정자나무 앞에서 백화점 가요")
                .andExpect(jsonPath("$.from.value").value("S07"))
                .andExpect(jsonPath("$.from.needsCheck").value(false));
    }

    @Test
    void 칸_수정할때_별칭_제안() throws Exception {
        long rid = reservation();
        mvc.perform(post("/reservations/" + rid + "/lines").contentType(MediaType.APPLICATION_JSON)
                .content("{\"speaker\":\"CUSTOMER\",\"text\":\"모레 오전 열 시에 큰 우물가에서 동사무소로 가요\"}"));
        patchField(rid, "from", "S10")
                .andExpect(jsonPath("$.aliasSuggestion.said").value("큰 우물가"))
                .andExpect(jsonPath("$.aliasSuggestion.stopId").value("S10"));
        // 이미 별칭에 있는 말이면 제안 없음
        patchField(rid, "to", "S12")
                .andExpect(jsonPath("$.aliasSuggestion").isEmpty());
    }

    ResultActions patchField(long id, String name, String value) throws Exception {
        return mvc.perform(patch("/reservations/" + id + "/fields/" + name).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\",\"note\":null}"))
                .andExpect(status().isOk());
    }
}
