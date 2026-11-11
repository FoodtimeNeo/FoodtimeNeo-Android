package cn.edu.bjtu.foodtime.domain.model;

public final class AccountLifecycle {
  public record Summary(String verificationEmail,String schoolEmail,boolean canDelete,boolean canUnbind,String deletionCodeAvailableAt){}
  public record Sent(String expiresAt,String nextSendAt,String verificationEmail){}
  private AccountLifecycle(){}
}
