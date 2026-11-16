package com.alphascope.identity.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * PBKDF2-HMAC-SHA256、600000次迭代与独立随机盐，比较采用恒定时间。
 *
 * @author chenxuefeng
 */
@Component
public class PasswordHasher {
  private static final int ITERATIONS = 600000;
  private static final SecureRandom RANDOM = new SecureRandom();

  public String hash(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    return "pbkdf2-sha256$"
        + ITERATIONS
        + "$"
        + Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
  }

  public boolean matches(String password, String encoded) {
    try {
      String[] parts = encoded.split("\\$");
      if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
      int count = Integer.parseInt(parts[1]);
      if (count < ITERATIONS || count > 2000000) return false;
      return MessageDigest.isEqual(
          Base64.getDecoder().decode(parts[3]),
          derive(password, Base64.getDecoder().decode(parts[2]), count));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private byte[] derive(String password, byte[] salt, int count) {
    var key = new PBEKeySpec(password.toCharArray(), salt, count, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("密码算法不可用", e);
    } finally {
      key.clearPassword();
    }
  }
}
