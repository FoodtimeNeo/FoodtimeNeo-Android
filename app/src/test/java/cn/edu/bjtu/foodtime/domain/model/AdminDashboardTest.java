package cn.edu.bjtu.foodtime.domain.model;

import static org.junit.Assert.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import org.junit.Test;
import java.time.LocalDate;
import java.util.*;

public class AdminDashboardTest {
  @Test public void snapshotOwnsItsListAndBusinessDate() {
    var points=new ArrayList<DashboardPoint>();
    points.add(new DashboardPoint(LocalDate.parse("2026-11-02"),0,0,0,0,0,0));
    var snapshot=new AdminDashboard(0,0,0,0,0,0,0,0,0,points,1234);
    points.clear();
    assertEquals("2026-11-02",snapshot.trend().get(0).day().toString());
    assertEquals(1,snapshot.trend().size());
    assertThrows(UnsupportedOperationException.class,()->snapshot.trend().clear());
  }
  @Test public void rangesAndAllThreeAdditionPeriodsUseTheSameSnapshot() {
    var points=new ArrayList<DashboardPoint>();
    for(int index=0;index<30;index++)points.add(new DashboardPoint(LocalDate.parse("2026-10-05").plusDays(index),100+index,1,2,3,4,5));
    var snapshot=new AdminDashboard(129,120,1,2,3,4,5,6,7,points,1234);
    assertEquals(7,snapshot.trend(7).size());assertEquals(30,snapshot.trend(30).size());
    assertEquals(LocalDate.parse("2026-10-28"),snapshot.trend(7).get(0).day());
    assertEquals(new DashboardAdditions(1,2,3,4),snapshot.additions(1));
    assertEquals(new DashboardAdditions(7,14,21,28),snapshot.additions(7));
    assertEquals(new DashboardAdditions(30,60,90,120),snapshot.additions(30));
    assertThrows(UnsupportedOperationException.class,()->snapshot.trend(7).clear());
    assertThrows(IllegalArgumentException.class,()->snapshot.trend(8));
  }
}
