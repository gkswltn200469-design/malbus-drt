package kr.godri.malbus.controller;

import kr.godri.malbus.dto.ExtractRequest;
import kr.godri.malbus.dto.ExtractResponse;
import kr.godri.malbus.service.ExtractionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 저장 없는 추출 (채점·테스트용)
@RestController
public class ExtractController {

    private final ExtractionService extractionService;

    public ExtractController(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping("/extract")
    public ExtractResponse extract(@RequestBody ExtractRequest req) {
        return extractionService.extract(req);
    }
}
