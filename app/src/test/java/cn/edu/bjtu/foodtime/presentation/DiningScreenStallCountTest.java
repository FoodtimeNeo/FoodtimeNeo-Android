package cn.edu.bjtu.foodtime.presentation;

import static org.junit.Assert.assertEquals;

import cn.edu.bjtu.foodtime.domain.model.Models.Hall;
import org.junit.Test;

public class DiningScreenStallCountTest {
  @Test
  public void hallMetaLabelUsesServerStallCountWithoutHardcodedFallback() {
    Hall liuyuan = new Hall("h1", "留园食堂", "", true, null, 0, null, null, 1);
    assertEquals("主校区 · 1个档口", DiningScreen.hallMetaLabel(null, liuyuan));

    Hall emptyDongqu = new Hall("h2", "东区学生餐厅", "", true, null, 0, null, null, 0);
    assertEquals("东区 · 0个档口", DiningScreen.hallMetaLabel(null, emptyDongqu));

    Hall unknownMinghu = new Hall("h3", "明湖餐厅1楼", "", true);
    assertEquals("明湖 · 1层", DiningScreen.hallMetaLabel(null, unknownMinghu));
  }
}
