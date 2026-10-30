package cn.edu.bjtu.foodtime.domain.model;

import static org.junit.Assert.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import org.junit.Test;

public class AdminDashboardTest {
  @Test public void previewProvidesAllDashboardSections() {
    AdminDashboard dashboard=AdminDashboard.sample();
    assertTrue(dashboard.preview());assertTrue(dashboard.registeredUsers()>0);assertTrue(dashboard.onlineUsers()>0);
    assertEquals(7,dashboard.trend().size());assertTrue(dashboard.trend().get(6).newReviews()>0);
  }
}
