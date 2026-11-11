package kr.godri.malbus.controller;

import kr.godri.malbus.dto.AddAliasRequest;
import kr.godri.malbus.dto.LearnedAliasDto;
import kr.godri.malbus.service.AliasService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/aliases")
public class AliasController {

    private final AliasService aliasService;

    public AliasController(AliasService aliasService) {
        this.aliasService = aliasService;
    }

    // 별칭 추가 모달. 새로 만들면 201, 있던 거면 200
    @PostMapping
    public ResponseEntity<LearnedAliasDto> add(@RequestBody AddAliasRequest req) {
        AliasService.Saved saved = aliasService.add(req);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.alias());
    }

    @GetMapping
    public List<LearnedAliasDto> list(@RequestParam(required = false) String status) {
        return aliasService.list(status);
    }

    @PostMapping("/{id}/approve")
    public LearnedAliasDto approve(@PathVariable Long id) {
        return aliasService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public LearnedAliasDto reject(@PathVariable Long id) {
        return aliasService.reject(id);
    }
}
