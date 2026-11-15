package kr.godri.malbus.dto;

public record PatchFieldResponse(ReservationDto reservation, AliasSuggestion aliasSuggestion) {

    public record AliasSuggestion(String said, String stopId) {
    }
}
