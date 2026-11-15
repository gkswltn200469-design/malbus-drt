package kr.godri.malbus.controller;

import kr.godri.malbus.dto.HealthResponse;
import kr.godri.malbus.dto.StopDto;
import kr.godri.malbus.extract.Extractor;
import kr.godri.malbus.service.StopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StopController {

    private final StopService stopService;
    private final Extractor extractor;

    public StopController(StopService stopService, Extractor extractor) {
        this.stopService = stopService;
        this.extractor = extractor;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", extractor.mode(), stopService.count());
    }

    @GetMapping("/stops")
    public List<StopDto> stops() {
        return stopService.findAll();
    }
}
