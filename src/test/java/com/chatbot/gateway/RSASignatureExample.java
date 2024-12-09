package com.chatbot.gateway;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSASignatureExample {

  private static final String publicKeyStr = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqipqES/bOTG7MUPkNbSNbeDnoIVSDfLbGFeE6Wdh0Pt1wpUlMal9+Bh2dbiR1NNGdjCIqTNpt55dq938Mp4T2bd4tMVK2S5f9VXVI0lgZJNlmWxWcQNu0Vri0ZPCzURjw5lM2yEU6l8rPkmHoq1lTLhekviCTAFNKs+aggZV5wc6YYAjc2L2Njv829r/lZlCRSFrIqyCI0HCC4BBT7mZ3E2lLWu2ZanK8JBclX8E3lTKaeHi0B6+eRkttsP3kTkZ8sT0EIAE9rTOT4TlVL6mpbZjfKcTQszJ38FIxgV/uCyz2IJjNDuaYUyzkFttXqQc7HWjTQZ0qhiUFUXDdrv5TQIDAQAB";
  private static final String privateKeyStr = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCqKmoRL9s5MbsxQ+Q1tI1t4OeghVIN8tsYV4TpZ2HQ+3XClSUxqX34GHZ1uJHU00Z2MIipM2m3nl2r3fwynhPZt3i0xUrZLl/1VdUjSWBkk2WZbFZxA27RWuLRk8LNRGPDmUzbIRTqXys+SYeirWVMuF6S+IJMAU0qz5qCBlXnBzphgCNzYvY2O/zb2v+VmUJFIWsirIIjQcILgEFPuZncTaUta7ZlqcrwkFyVfwTeVMpp4eLQHr55GS22w/eRORnyxPQQgAT2tM5PhOVUvqaltmN8pxNCzMnfwUjGBX+4LLPYgmM0O5phTLOQW21epBzsdaNNBnSqGJQVRcN2u/lNAgMBAAECggEAJ9tN4y8sn6GamRAwFnUvEC62yhYYEUzSDQpIxxuQIXEYlMY2w0JSD0d5jZq4y5rd3OaCx/DTIO8+vPf+b4OvnhXXd63jWWm/j5j2VnDEG2Kb0Dr8JzXY4b/yMwjzPn13iZOxWP0PZ1L5r7nsw0luWfEwM6fx6uf+IVoldDGUMsuQfQFR4sWbqGJvwUD6CjMgAiNnQefNpi7npxK9pt3X47LmXlx1DaQokW5qqV8EBvpIoGAob+M+HnHYdpUgZ+B5VjKVyk+GfvmeAVJ+pOl9lS75KmmPp2RBSj8D8uDwMXKMINgAEN3n4iSFknggjNZZ31feLusbNtQFzjaKwWBdQQKBgQDHdPR1OBkkJRn0m2Jztky6zDRfs8D4s5N/HtJbAzg0hlv8ABWBzE8qcOpRXkt4zGyXAcqlohgIHtvgrU18Zyfxk5resHYaC76DS27Yk449Cwm3s27SRA7RBo3ajmrc1mEZMpQZEjOJAM46c31dponm+MNQDpqhLN6U/vsv+0zXwQKBgQDaZ7su8wNvdnmQjDTukLeW6k/Yu8N/fzCNEEl5SUQXFMf4g8gyJ3hIsP8x1uqcYnP2eT4lXjqUFgFMNm+KFhPBe5ReKi2va/VwS3gM0qok39R4Ou4H2eyk/bPKxxjWBDE76BEFfulqQv9rBDcSXgSG3eIFjE62ueYLioJg85IkjQKBgANtSP3ylsv+LzH6sXhXe34CICw8xGYBf9lBSE/0ADU20cHEppnyTrHl+sCnJBjROlRl3Xt3C36oORLlJ12p0A/gf1qwIXdVGFLdKuxhrKHz3JjhZlgKf06sFCfbJo7gyA5MxiqgG26RKvnqHg9L2zays3hep915DeH1d49de/aBAoGAJy8hKCU1YpQQ71wYSwzvw0W6mZnmU0OQhF59sCLy8mkqD24lRspKDFClGF4ErZYEVB4ghjfHrrXb+b5yeIXJeZcgYVyT4bsux7zihvpsyDzYM9Huzr3MdTWHQkRCMnOCGcti8md4nTXz+VFCSCtSCJhaasBnhuUHXt600YwhlikCgYBfPGWq+gibeWeHlew6yJxep5RE38zKV5y9QOBc14efpxZSKLwuYwaGUfwVUbH0FKSB7K0iRnij8qec/D8Ou0zBjdBoj62TgNLMdu4RW+3Nc7OM8VDSq53z0ogHrxpBP1k0o+vPs6V7NYIqTZQB4VXb8wHQ/cO0j/39LuNXkcGyxA==";

  private static final String sign = "Q8+/iBXZqhGgjEm9Ztp7eLHvsQSFDvCCMNXau5vkME99EsUwIdhQteb+dxc5BBdjuTsWAaguZ9cxmA9Z1epxX9mT7zyIpEIf1DTs5NkN7fXDRhHS0wkfxAzpuDp3kdkCq8lO6TaHsp73g69xYbxzkceixgLZpW9SBve3VAx4i0s22Vuy90wHbbBVzuCzmzOHFvzZv7PwldsNCfv9Gb6CmzJ2TpU0s9sPT3ahtUiiAJezSZra5w+29N/Y0otkeVaeuUdt1nXz6W/mnUN02Rgxkvdq1DB7RuQiYvZpIvAlyz462zKwIylA15BHTaNOsqAn06eN2azACdNCYdVZeDfKWw==";

  private static final String message = "This is a secure message.";

  public static void main(String[] args) throws Exception {
    RSASignatureExample example = new RSASignatureExample();
    example.sign();
    example.verify();
  }

  private void keygen() throws Exception {
    // 키 쌍 생성
    KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
    keyPairGen.initialize(2048);
    KeyPair keyPair = keyPairGen.generateKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();
    PublicKey publicKey = keyPair.getPublic();

    System.out.println("Public Key: " + Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    System.out.println("Private Key: " + Base64.getEncoder().encodeToString(privateKey.getEncoded()));
  }

  private void sign() throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr.getBytes());
    PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

    byte[] messageBytes = message.getBytes();

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(messageBytes);

    byte[] digitalSignature = signature.sign();

    System.out.println("Original Message: " + message);
    System.out.println("Digital Signature: " + Base64.getEncoder().encodeToString(digitalSignature));
  }


  private void verify() throws Exception {
    byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr.getBytes());
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(publicKey);
    signature.update(message.getBytes());

    byte[] signatureBytes = Base64.getDecoder().decode(sign);

    boolean isValid = signature.verify(signatureBytes);

    System.out.println("isValid = " + isValid);
  }
}

