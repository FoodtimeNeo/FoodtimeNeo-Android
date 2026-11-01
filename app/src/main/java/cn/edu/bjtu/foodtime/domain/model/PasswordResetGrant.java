package cn.edu.bjtu.foodtime.domain.model;
/** Flow-local capability. Never put this record in preferences, Bundles or logs. */
public record PasswordResetGrant(String token,long expiresAtMillis) {
  @Override public String toString(){return "PasswordResetGrant[token=<redacted>]";}
}
