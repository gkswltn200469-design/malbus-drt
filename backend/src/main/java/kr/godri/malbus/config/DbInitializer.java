package kr.godri.malbus.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// 서버 시작할 때 테이블 없으면 schema.sql + 정류장.csv로 만듦
@Component
public class DbInitializer {

    private static final Logger log = LoggerFactory.getLogger(DbInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Path stopsCsv;

    public DbInitializer(DataSource dataSource, JdbcTemplate jdbc, TransactionTemplate tx,
                         @Value("${malbus.data-dir}") String dataDir) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.tx = tx;
        this.stopsCsv = Path.of(dataDir, "정류장.csv");
    }

    @PostConstruct
    void init() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from sqlite_master where type = 'table' and name = 'stop'", Integer.class);
        if (tables != null && tables > 0) {
            return; // 이미 만든 db는 그대로 씀. 스키마 바뀌면 malbus.db 지우고 다시 실행
        }
        tx.executeWithoutResult(status -> {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            populator.setSqlScriptEncoding("UTF-8");
            populator.execute(dataSource);
            loadStops();
        });
        log.info("db 생성, 정류장 {}곳", jdbc.queryForObject("select count(*) from stop", Integer.class));
    }

    // 정류장ID,정류장명,위도,경도,별칭(| 구분)
    private void loadStops() {
        List<String> lines;
        try {
            lines = Files.readAllLines(stopsCsv);
        } catch (IOException e) {
            throw new UncheckedIOException("정류장 csv 못 읽음: " + stopsCsv.toAbsolutePath(), e);
        }
        for (String line : lines.subList(1, lines.size())) { // 첫 줄은 헤더(BOM 포함)
            if (line.isBlank()) continue;
            String[] c = line.split(",", -1);
            String id = c[0].trim();
            jdbc.update("insert into stop (id, name, lat, lng) values (?, ?, ?, ?)",
                    id, c[1].trim(), Double.parseDouble(c[2].trim()), Double.parseDouble(c[3].trim()));
            for (String alias : c[4].split("\\|")) {
                if (!alias.isBlank()) {
                    jdbc.update("insert or ignore into stop_alias (stop_id, alias) values (?, ?)", id, alias.trim());
                }
            }
        }
    }
}
