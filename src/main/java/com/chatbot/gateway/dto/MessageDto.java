package com.chatbot.gateway.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class MessageDto {
  private boolean encrypted;
  private boolean signed;
  private String message;
  private String signature;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public String toJson() {
    try {
      return objectMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static MessageDto fromJson(String jsonText) {
    try {
      return objectMapper.readValue(jsonText, MessageDto.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
