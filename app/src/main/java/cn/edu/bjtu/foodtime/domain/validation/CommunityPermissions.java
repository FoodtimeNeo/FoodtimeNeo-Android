package cn.edu.bjtu.foodtime.domain.validation;

import cn.edu.bjtu.foodtime.domain.model.Models.User;

/** Display permissions only; the API independently checks the current account. */
public final class CommunityPermissions {
  public static boolean canRemove(User actor, String authorId) {
    return actor != null && actor.active()
        && (actor.isAdmin() || actor.id().equals(authorId));
  }

  public static boolean requiresReason(User actor, String authorId) {
    return false;
  }

  public static boolean administrativeRemoval(User actor, String authorId) {
    return canRemove(actor, authorId) && !actor.id().equals(authorId);
  }

  private CommunityPermissions() {}
}
