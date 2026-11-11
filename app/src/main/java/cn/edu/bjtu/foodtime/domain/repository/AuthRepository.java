package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.CapabilitySnapshot;
import cn.edu.bjtu.foodtime.domain.model.Models.RegistrationPolicy;
import cn.edu.bjtu.foodtime.domain.model.Models.User;

public interface AuthRepository {
  User currentUser();

  /** Server-projected capabilities; empty until refreshed from the server. */
  default CapabilitySnapshot capabilities() { return CapabilitySnapshot.empty(); }

  /** Explicit refresh for callers that want to retry after a transient failure. */
  default void loadCapabilities() { }

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
