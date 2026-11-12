package kr.godri.malbus.extract;

import kr.godri.malbus.domain.TranscriptLine;
import kr.godri.malbus.dto.StopDto;

import java.util.List;

// 통화 내용 -> AI 모듈 출력. llm 구현은 곽도윤 담당, 키 없으면 RuleExtractor
public interface Extractor {

    String mode(); // LLM | RULE

    AiOutput extract(List<TranscriptLine> transcript, List<StopDto> stops);
}
