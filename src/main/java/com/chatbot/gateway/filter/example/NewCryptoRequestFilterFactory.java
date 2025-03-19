package com.chatbot.gateway.filter.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Component
public class NewCryptoRequestFilterFactory extends AbstractModifyFilterFactory {


  public NewCryptoRequestFilterFactory() {
    super();
  }

  @Override
  protected String modifyRequestBody(String originalBody, ServerWebExchange exchange) {
    log.info("필터 진입 - 요청 본문 수정 시작");
    log.info("원본 요청 본문: {}", originalBody);

    // 하드코딩된 요청 본문
    String modifiedBody = """
        {
            "model": "llama3.2:1b",
            "messages": [
              {"role": "system", "content": "you are a salty pirate"},
              {"role": "user", "content": "why is the sky blue"}
            ],
            "stream": false
        }
        """.trim(); // 불필요한 공백 제거

    byte[] originalBytes = originalBody.getBytes();
    byte[] modifiedBytes = modifiedBody.getBytes();

    log.info("원본 본문 크기: {} 바이트, 수정된 본문 크기: {} 바이트",
        originalBytes.length, modifiedBytes.length);

    return modifiedBody;
  }

  @Override
  protected String modifyResponseBody(String originalBody, ServerWebExchange exchange) {
    try {
      // 상세한 로그 추가
      log.info("응답 본문 수정 시작 - 길이: {}", originalBody != null ? originalBody.length() : 0);

      // null 체크
      if (originalBody == null || originalBody.isEmpty()) {
        log.info("응답 본문이 비어있음");
        return "";
      }

      // 응답 헤더 정보 로깅
      MediaType contentType = exchange.getResponse().getHeaders().getContentType();
      log.info("응답 컨텐츠 타입: {}", contentType);

      // exchange에서 요청 정보 로깅 (어떤 요청에 대한 응답인지)
      log.info("요청 URI: {}, 메서드: {}",
          exchange.getRequest().getURI(),
          exchange.getRequest().getMethod());

      // 원본 응답 로깅 (긴 응답은 잘라서 로깅)
      String logBody = originalBody;
      if (logBody.length() > 1000) {
        logBody = logBody.substring(0, 1000) + "... (잘림)";
      }
      log.info("응답 원본 본문: {}", logBody);

      // 여기서 실제 응답 수정 로직 구현
      // 예를 들어, 특정 키워드 변경, 응답 포맷 변경 등
      // 지금은 예시로 원본 그대로 반환

      return originalBody;
    } catch (Exception e) {
      log.error("응답 본문 수정 중 예외 발생", e);
      // 오류 발생 시 원본 반환
      return originalBody;
    }
  }

  @Override
  protected int getFilterOrder() {
    // 다른 필터보다 먼저 실행되도록 낮은 값 설정
    return -1;
  }
}
