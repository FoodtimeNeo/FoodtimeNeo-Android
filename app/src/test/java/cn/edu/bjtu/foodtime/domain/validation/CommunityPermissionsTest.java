package cn.edu.bjtu.foodtime.domain.validation;

import cn.edu.bjtu.foodtime.domain.model.Models.User;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommunityPermissionsTest {
  private User user(String role, boolean active) {
    return new User("viewer", "viewer@bjtu.edu.cn", "Viewer", role, active);
  }

  @Test public void ordinaryUsersOnlyRemoveTheirOwnContent() {
    assertTrue(CommunityPermissions.canRemove(user("user", true), "viewer"));
    assertFalse(CommunityPermissions.canRemove(user("user", true), "author"));
    assertFalse(CommunityPermissions.requiresReason(user("user", true), "viewer"));
  }

  @Test public void bothAdministratorRolesCanRemoveOtherPeoplesContentWithoutAReason() {
    for (String role : new String[]{"admin", "superadmin"}) {
      assertTrue(CommunityPermissions.canRemove(user(role, true), "author"));
      assertFalse(CommunityPermissions.requiresReason(user(role, true), "author"));
      assertFalse(CommunityPermissions.requiresReason(user(role, true), "viewer"));
    }
  }

  @Test public void guestsAndInactiveAccountsCannotDelete() {
    assertFalse(CommunityPermissions.canRemove(null, "author"));
    for (String role : new String[]{"user", "admin", "superadmin"}) {
      assertFalse(CommunityPermissions.canRemove(user(role, false), "viewer"));
      assertFalse(CommunityPermissions.canRemove(user(role, false), "author"));
    }
  }
}
