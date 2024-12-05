package com.chatbot.gateway.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoUtil {
  private final SecretKeySpec secretKey;
  private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

  public CryptoUtil(String key) {
    // 32바이트로 키 패딩
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] paddedKey = new byte[32];
    System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
    secretKey = new SecretKeySpec(paddedKey, "AES");
  }

  public String encrypt(String plainText) throws Exception {
    // 16바이트 IV 생성
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    IvParameterSpec ivSpec = new IvParameterSpec(iv);

    // 암호화 초기화
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

    // UTF-8로 명시적 인코딩
    byte[] plainTextBytes = plainText.getBytes(StandardCharsets.UTF_8);

    // 암호화 수행
    byte[] encryptedBytes = cipher.doFinal(plainTextBytes);

    // IV + 암호문을 base64로 인코딩
    byte[] combinedBytes = new byte[iv.length + encryptedBytes.length];
    System.arraycopy(iv, 0, combinedBytes, 0, iv.length);
    System.arraycopy(encryptedBytes, 0, combinedBytes, iv.length, encryptedBytes.length);

    return Base64.getEncoder().encodeToString(combinedBytes);
  }

  public String decrypt(String encryptedText) throws Exception {
    // base64 디코딩
    byte[] combinedBytes = Base64.getDecoder().decode(encryptedText);

    // IV 추출 (첫 16바이트)
    byte[] iv = new byte[16];
    System.arraycopy(combinedBytes, 0, iv, 0, 16);
    IvParameterSpec ivSpec = new IvParameterSpec(iv);

    // 암호문 추출 (16바이트 이후)
    byte[] encryptedBytes = new byte[combinedBytes.length - 16];
    System.arraycopy(combinedBytes, 16, encryptedBytes, 0, encryptedBytes.length);

    // 복호화 초기화
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

    // 복호화 수행
    byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

    // UTF-8로 명시적 디코딩
    return new String(decryptedBytes, StandardCharsets.UTF_8);
  }

  public static void main(String[] args) throws Exception {
    String secretKey = "my_very_secret_key_32_bytes_long";
    CryptoUtil aesCipher = new CryptoUtil(secretKey);

    // 암호화
    String originalText = "Hello, World! 안녕하세요.";
    String encryptedText = aesCipher.encrypt(originalText);
    System.out.println("Encrypted: " + encryptedText);

    // 복호화
    String decryptedText = aesCipher.decrypt(encryptedText);
    System.out.println("Decrypted: " + decryptedText);
  }
}
