package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.CapabilitySnapshot;
import cn.edu.bjtu.foodtime.domain.model.Models.CaptchaChallenge;
import cn.edu.bjtu.foodtime.domain.model.Models.RegistrationPolicy;
import cn.edu.bjtu.foodtime.domain.model.Models.User;

public interface AuthRepository {
  User currentUser();

  /** "normal" or "appeal_only"; restricted sessions only expose the appeal surface. */
  default String sessionMode() { return "normal"; }

  /** Server-projected capabilities; empty until refreshed from the server. */
  default CapabilitySnapshot capabilities() { return CapabilitySnapshot.empty(); }

  /** Explicit refresh for callers that want to retry after a transient failure. */
  default void loadCapabilities() { }

  /** One-time login challenge; implementations without CAPTCHA can keep the default failure. */
  default CaptchaChallenge captcha() {
    throw new cn.edu.bjtu.foodtime.domain.repository.RepositoryException(
        404, "CAPTCHA_DISABLED", "登录验证码未启用", 0);
  }

  default User login(String account, String password, String challengeId, String challengeAnswer) {
    return login(account, password);
  }

  User login(String account, String password);

  String sendVerificationCode(String email);

  void register(String email, String password, String code);

  /** Reads the deployment's public registration capabilities before rendering the register form. */
  default RegistrationPolicy registrationPolicy() { return RegistrationPolicy.open(); }

  /** Optional invitation code; implementations that do not support it keep the legacy overload. */
  default void register(String email, String password, String code, String invitationCode) {
    register(email, password, code);
  }
  long sendPasswordResetCode(String email);
  cn.edu.bjtu.foodtime.domain.model.PasswordResetGrant verifyPasswordResetCode(String email,String code);
  void resetPassword(String token,String newPassword);

  void logout();
}
