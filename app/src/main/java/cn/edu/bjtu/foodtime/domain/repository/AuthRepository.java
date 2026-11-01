package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.User;

public interface AuthRepository {
  User currentUser();

  User login(String account, String password);

  String sendVerificationCode(String email);

  void register(String email, String password, String code);
  long sendPasswordResetCode(String email);
  cn.edu.bjtu.foodtime.domain.model.PasswordResetGrant verifyPasswordResetCode(String email,String code);
  void resetPassword(String token,String newPassword);

  void logout();
}
