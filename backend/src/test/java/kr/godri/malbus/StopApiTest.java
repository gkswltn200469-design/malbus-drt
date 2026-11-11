package kr.godri.malbus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StopApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void health() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.mode").value("RULE"))
                .andExpect(jsonPath("$.stops").value(17));
    }

    @Test
    void 정류장_목록() throws Exception {
        mvc.perform(get("/stops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(17))
                .andExpect(jsonPath("$[1].id").value("S02"))
                .andExpect(jsonPath("$[1].name").value("범물청구타운앞"))
                .andExpect(jsonPath("$[1].lat").value(35.81943545))
                .andExpect(jsonPath("$[1].aliases", contains("청구타운", "청구아파트")));
    }

    @Test
    void 없는_주소는_에러_형식으로() throws Exception {
        mvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
