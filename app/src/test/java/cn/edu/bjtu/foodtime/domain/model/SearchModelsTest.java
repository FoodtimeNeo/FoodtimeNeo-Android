package cn.edu.bjtu.foodtime.domain.model;
import org.junit.Test;
import static org.junit.Assert.*;
public class SearchModelsTest {
  @Test public void adaptsColumnsAndLabelsOnlyRealStraightLineDistances() {
    assertEquals(4,SearchModels.columns(348));assertEquals(2,SearchModels.columns(180));assertEquals(6,SearchModels.columns(760));
    assertEquals("",SearchModels.distance(null));assertEquals("",SearchModels.distance(Double.NaN));
    assertEquals("直线 <100m",SearchModels.distance(0d));assertEquals("直线 350m",SearchModels.distance(352d));
    assertEquals("直线 1.3km",SearchModels.distance(1310d));
  }
}
