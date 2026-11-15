package kr.godri.malbus.controller;

import kr.godri.malbus.dto.ConfirmRequest;
import kr.godri.malbus.dto.CreateReservationRequest;
import kr.godri.malbus.dto.LineRequest;
import kr.godri.malbus.dto.PatchFieldRequest;
import kr.godri.malbus.dto.PatchFieldResponse;
import kr.godri.malbus.dto.ReservationDto;
import kr.godri.malbus.dto.ReservationSummary;
import kr.godri.malbus.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // 통화 시작
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDto create(@RequestBody(required = false) CreateReservationRequest req) {
        return reservationService.create(req);
    }

    @GetMapping
    public List<ReservationSummary> list(@RequestParam(required = false) String status) {
        return reservationService.list(status);
    }

    @GetMapping("/{id}")
    public ReservationDto get(@PathVariable Long id) {
        return reservationService.get(id);
    }

    @PostMapping("/{id}/lines")
    public ReservationDto addLine(@PathVariable Long id, @RequestBody LineRequest req) {
        return reservationService.addLine(id, req);
    }

    @PatchMapping("/{id}/fields/{name}")
    public PatchFieldResponse patchField(@PathVariable Long id, @PathVariable String name,
                                         @RequestBody PatchFieldRequest req) {
        return reservationService.patchField(id, name, req);
    }

    @PostMapping("/{id}/confirm")
    public ReservationDto confirm(@PathVariable Long id, @RequestBody ConfirmRequest req) {
        return reservationService.confirm(id, req);
    }
}
