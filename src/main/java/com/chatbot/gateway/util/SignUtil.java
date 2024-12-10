package com.chatbot.gateway.util;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class SignUtil {

  private final String privateKey;

  private final String publicKey;

  public SignUtil(String privateKey, String publicKey) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  public KeyPair keygen() throws Exception {
    // 키 쌍 생성
    KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
    keyPairGen.initialize(2048);

    return keyPairGen.generateKeyPair();
  }

  public String sign(String message) throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    byte[] privateKeyBytes = Base64.getDecoder().decode(privateKey.getBytes());
    PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

    byte[] messageBytes = message.getBytes();

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(messageBytes);

    byte[] digitalSignature = signature.sign();

    return Base64.getEncoder().encodeToString(digitalSignature);
  }

  public boolean verify(String message, String sign) throws Exception {
    byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey.getBytes());
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(publicKey);
    signature.update(message.getBytes());

    byte[] signatureBytes = Base64.getDecoder().decode(sign);

    return signature.verify(signatureBytes);
  }
}
