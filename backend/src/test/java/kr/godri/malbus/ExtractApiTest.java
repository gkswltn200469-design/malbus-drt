package kr.godri.malbus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExtractApiTest {

    @Autowired
    MockMvc mvc;

    ResultActions extract(String customerText) throws Exception {
        String body = """
                {"receivedAt":"2026-11-17T14:00:00+09:00",
                 "transcript":[{"speaker":"AGENT","text":"네, 범물동 DRT입니다"},{"speaker":"CUSTOMER","text":"%s"}]}
                """.formatted(customerText);
        return mvc.perform(post("/extract").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void 상대_날짜는_접수일_기준() throws Exception {
        extract("내일 오전 아홉 시 반에 범물청구타운 앞에서 동아백화점 수성점 앞까지 갈라고요")
                .andExpect(jsonPath("$.mode").value("RULE"))
                .andExpect(jsonPath("$.date.value").value("2026-11-18"))
                .andExpect(jsonPath("$.date.said").value("내일"))
                .andExpect(jsonPath("$.date.needsCheck").value(false))
                .andExpect(jsonPath("$.date.evidence.lineNo").value(2))
                .andExpect(jsonPath("$.date.source").value("RULE"));
        extract("오늘 오후 네 시에 맨션에서 백화점 가요").andExpect(jsonPath("$.date.value").value("2026-11-17"));
        extract("모레 오후 한 시에 인앤인에서 수련원까지요").andExpect(jsonPath("$.date.value").value("2026-11-19"));
        extract("다음 주 월요일에 하늘채에서 백화점 가요")
                .andExpect(jsonPath("$.date.value").isEmpty())
                .andExpect(jsonPath("$.date.needsCheck").value(true))
                .andExpect(jsonPath("$.date.reason").value("AMBIGUOUS"));
    }

    @Test
    void 시간_오전오후_없으면_제안값과_확인필요() throws Exception {
        extract("모레 오후 두 시 반에 하늘채에서 백화점 가요")
                .andExpect(jsonPath("$.time.value").value("14:30"))
                .andExpect(jsonPath("$.time.needsCheck").value(false));
        extract("모레 열 시에 한라주택 건너에서 청구타운 가는 거 됩니꺼")
                .andExpect(jsonPath("$.time.value").value("10:00"))
                .andExpect(jsonPath("$.time.needsCheck").value(true))
                .andExpect(jsonPath("$.time.reason").value("AMBIGUOUS"));
        extract("내일 세 시에 보성송정타운에서 범물역 가요")
                .andExpect(jsonPath("$.time.value").value("15:00"))
                .andExpect(jsonPath("$.time.needsCheck").value(true));
    }

    @Test
    void 인원_말_안하면_빈칸() throws Exception {
        extract("내일 오전 열 시에 우방에서 백화점 가요")
                .andExpect(jsonPath("$.people.value").isEmpty())
                .andExpect(jsonPath("$.people.needsCheck").value(true))
                .andExpect(jsonPath("$.people.reason").value("MISSING"));
        extract("내일 오전 열 시에 우방에서 백화점 가요. 둘이요")
                .andExpect(jsonPath("$.people.value").value(2))
                .andExpect(jsonPath("$.people.needsCheck").value(false));
    }

    @Test
    void 같은_이름_정류장은_후보만() throws Exception {
        extract("모레 오전 열 시에 동사무소에서 하늘채로 가요")
                .andExpect(jsonPath("$.from.value").isEmpty())
                .andExpect(jsonPath("$.from.said").value("동사무소"))
                .andExpect(jsonPath("$.from.needsCheck").value(true))
                .andExpect(jsonPath("$.from.reason").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.from.candidates[*].stopId", contains("S01", "S12")))
                .andExpect(jsonPath("$.from.candidates[0].matchType").value("ALIAS"))
                .andExpect(jsonPath("$.from.moreCount").value(0))
                .andExpect(jsonPath("$.to.value").value("S09"))
                .andExpect(jsonPath("$.to.needsCheck").value(false));
    }

    @Test
    void 앞_건너로_좁힘() throws Exception {
        extract("내일 오전 열한 시에 우방에서 동사무소 건너편으로 가 주이소")
                .andExpect(jsonPath("$.to.value").value("S01"))
                .andExpect(jsonPath("$.to.needsCheck").value(false));
        extract("내일 오전 열 시에 우방에서 백련사 앞으로 가요")
                .andExpect(jsonPath("$.to.value").value("S14"));
        extract("내일 오전 열 시에 우방에서 백련사 가요")
                .andExpect(jsonPath("$.to.value").isEmpty())
                .andExpect(jsonPath("$.to.candidates[*].stopId", contains("S14", "S16")));
    }

    @Test
    void 목록에_없는_곳은_후보_없음() throws Exception {
        extract("내일 오전 열 시에 우리 큰딸네 집 앞에서 범물역 가요")
                .andExpect(jsonPath("$.from.value").isEmpty())
                .andExpect(jsonPath("$.from.said").value("우리 큰딸네 집 앞"))
                .andExpect(jsonPath("$.from.reason").value("NOT_IN_LIST"))
                .andExpect(jsonPath("$.from.candidates", hasSize(0)))
                .andExpect(jsonPath("$.to.value").value("S03"));
    }

    @Test
    void 부분_일치는_확인필요() throws Exception {
        extract("내일 오전 열한 시에 맨션에서 범물동 행정복지센터 갈라고예")
                .andExpect(jsonPath("$.to.value").isEmpty())
                .andExpect(jsonPath("$.to.reason").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.to.candidates[*].stopId", contains("S01", "S11", "S12")))
                .andExpect(jsonPath("$.to.candidates[0].matchType").value("PARTIAL"));
    }

    @Test
    void 출발과_도착이_같으면_확인필요() throws Exception {
        extract("내일 오전 열 시에 하늘채에서 수성하늘채로 가요")
                .andExpect(jsonPath("$.from.reason").value("INVALID"))
                .andExpect(jsonPath("$.to.needsCheck").value(true));
    }

    @Test
    void 목적_분류() throws Exception {
        extract("내일 예약한 거 취소할게요")
                .andExpect(jsonPath("$.purpose.value").value("CANCEL"))
                .andExpect(jsonPath("$.purpose.evidence.lineNo").value(2));
        extract("내일 오전 열 시에 하늘채에서 백화점 가요")
                .andExpect(jsonPath("$.purpose.value").value("RESERVE"))
                .andExpect(jsonPath("$.purpose.source").isEmpty());
    }

    @Test
    void 형식_오류() throws Exception {
        mvc.perform(post("/extract").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":[{\"speaker\":\"BOT\",\"text\":\"안녕\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
