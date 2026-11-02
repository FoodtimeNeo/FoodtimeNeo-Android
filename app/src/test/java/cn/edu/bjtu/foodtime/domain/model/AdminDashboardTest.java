package cn.edu.bjtu.foodtime.domain.model;

import static org.junit.Assert.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import org.junit.Test;
import java.time.LocalDate;
import java.util.*;

public class AdminDashboardTest {
  @Test public void snapshotOwnsItsListAndBusinessDate() {
    var points=new ArrayList<DashboardPoint>();
    points.add(new DashboardPoint(LocalDate.parse("2026-11-02"),0,0,0,0,0));
    var snapshot=new AdminDashboard(0,0,0,0,0,0,0,0,points,1234);
    points.clear();
    assertEquals("2026-11-02",snapshot.trend().get(0).day().toString());
    assertEquals(1,snapshot.trend().size());
    assertThrows(UnsupportedOperationException.class,()->snapshot.trend().clear());
  }
}
