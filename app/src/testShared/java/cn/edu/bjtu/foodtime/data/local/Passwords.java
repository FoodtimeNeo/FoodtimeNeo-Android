package cn.edu.bjtu.foodtime.data.local;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Test simulator utility only; application authentication and password hashing belong to Backend.
 */
final class Passwords {
  static String hash(String password) {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(derive(password, salt));
  }

  static boolean matches(String password, String hash) {
    try {
      String[] parts = hash.split(":");
      return MessageDigest.isEqual(
          Base64.getDecoder().decode(parts[1]),
          derive(password, Base64.getDecoder().decode(parts[0])));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static byte[] derive(String password, byte[] salt) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      spec.clearPassword();
    }
  }
}
