package kr.godri.malbus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 연결마다_fk가_켜져있다() {
        assertThat(jdbc.queryForObject("pragma foreign_keys", Integer.class)).isEqualTo(1);
    }

    @Test
    void 없는_정류장을_가리키면_db가_막는다() {
        assertThatThrownBy(() -> jdbc.update("insert into stop_alias (stop_id, alias) values ('S99', '없는 곳')"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("FOREIGN KEY");
    }

    @Test
    void 정류장_csv가_적재된다() {
        assertThat(jdbc.queryForObject("select count(*) from stop", Integer.class)).isEqualTo(17);
        assertThat(jdbc.queryForObject("select name from stop where id = 'S01'", String.class))
                .isEqualTo("범물1동행정복지센터건너(용지역)");
        assertThat(jdbc.queryForObject("select count(*) from stop_alias where stop_id = 'S01'", Integer.class))
                .isEqualTo(6);
    }
}
