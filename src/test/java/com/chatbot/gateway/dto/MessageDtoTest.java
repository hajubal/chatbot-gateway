package com.chatbot.gateway.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MessageDtoTest {

  @Test
  void fromJson() {
    String originalBody = "{\"encrypted\": true, \"signed\": true, \"message\": \"UApkRyBz5ePsbpnCn8k1x7J6kU9sISNo110dMyxNQdlMNpl536t72vgre7daFKQ7wLy+l8abSoE7DUu1yVJA/x2okpwv7WoaVaP/JElJrBxydu8o9HJm3lgSET+EjDP674dUcPlfh/6iR/BhJdpuhQ==\", \"signature\": \"fSRsv2uHrGNK06cipXzNdWVyQEYnjaevvzi8SVzKxP8uT7soItfrpmE0WePzALB0BcPH6TRiyxxKLm5VM4YY3zMNeiqKR/EiZZRVWbX6hppwoX6Tw44OWbtLXRC+hKYCZcWLHttMw3DT1k7ebguvzMHikAmxxMz8f7xY6QrLeiUhrwVvo3O8r1p05M/bJA8iUUjRYpeScyD9yyf6Ua32/fNFiPHdJR3bpvF19gMe8PtQvq2OPoFeFwR1hdIXsWiLYUgJat39vohrnyOWs6+PrWUrVZrXtcKGwa8Gm75mg1haDIvAIoF7usuqunkKMT/AoKlPCm44d/Y7umePXu6LCg==\"}";

    MessageDto messageDto = MessageDto.fromJson(originalBody);

    System.out.println("messageDto = " + messageDto);

  }
}