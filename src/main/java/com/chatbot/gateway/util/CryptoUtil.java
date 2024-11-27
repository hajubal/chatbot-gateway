package com.chatbot.gateway.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
  private static final String SECRET_KEY = "ThisIsA32BitLongSecretKeyForAES"; // 32자 비밀키

  public static String encrypt(String value) throws Exception {
    SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
    byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  public static String decrypt(String encryptedValue) throws Exception {
    SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, secretKey);
    byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
    return new String(decryptedBytes, StandardCharsets.UTF_8);
  }
}
