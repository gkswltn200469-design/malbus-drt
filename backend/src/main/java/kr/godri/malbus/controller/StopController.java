package kr.godri.malbus.controller;

import kr.godri.malbus.dto.HealthResponse;
import kr.godri.malbus.dto.StopDto;
import kr.godri.malbus.service.StopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StopController {

    private final StopService stopService;

    public StopController(StopService stopService) {
        this.stopService = stopService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        // llm 추출기 붙기 전까지 규칙 모드
        return new HealthResponse("ok", "RULE", stopService.count());
    }

    @GetMapping("/stops")
    public List<StopDto> stops() {
        return stopService.findAll();
    }
}
