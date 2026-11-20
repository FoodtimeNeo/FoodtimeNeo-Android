package cn.edu.bjtu.foodtime.domain.map;

import org.junit.Test;
import static org.junit.Assert.*;

public class CampusMapGeometryTest {
  @Test public void concaveFootprintRejectsOutsideAndGap() {
    float[][] polygon = {{0,0},{10,0},{10,4},{4,4},{4,10},{0,10}};
    assertTrue(CampusMapGeometry.contains(polygon,2,8));
    assertTrue(CampusMapGeometry.contains(polygon,8,2));
    assertFalse(CampusMapGeometry.contains(polygon,8,8));
    assertFalse(CampusMapGeometry.contains(polygon,-1,2));
  }
}
