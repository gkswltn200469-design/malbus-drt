package kr.godri.malbus.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import kr.godri.malbus.dto.TranscriptLineDto;

import java.io.Serializable;

@Entity
@Table(name = "transcript_line")
@IdClass(TranscriptLine.Key.class)
public class TranscriptLine {

    @Id
    private Long reservationId;
    @Id
    private int lineNo;
    private String at;
    private String speaker;
    private String text;

    protected TranscriptLine() {
    }

    public TranscriptLine(Long reservationId, int lineNo, String at, String speaker, String text) {
        this.reservationId = reservationId;
        this.lineNo = lineNo;
        this.at = at;
        this.speaker = speaker;
        this.text = text;
    }

    public TranscriptLineDto toDto() {
        return new TranscriptLineDto(lineNo, at, speaker, text);
    }

    public int getLineNo() { return lineNo; }
    public String getSpeaker() { return speaker; }
    public String getText() { return text; }

    public record Key(Long reservationId, int lineNo) implements Serializable {
    }
}
