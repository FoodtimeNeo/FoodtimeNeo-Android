package cn.edu.bjtu.foodtime.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogSearchTest {
  @Test public void matchesChineseNamesAndMultipleTerms() {
    assertTrue(CatalogSearch.matches("鲜炒排骨小锅", " 排骨  小锅 "));
    assertFalse(CatalogSearch.matches("鲜炒鸡小锅", "排骨"));
  }

  @Test public void normalizesFullWidthAndCaseWithoutChangingNames() {
    assertTrue(CatalogSearch.matches("ABC 牛肉饭", "ａｂＣ"));
    assertTrue(CatalogSearch.matches("套餐（小）", "(小)"));
    assertFalse(CatalogSearch.matches("牛肉饭", ".*"));
  }

  @Test public void blankSearchRestoresTheFullList() {
    assertTrue(CatalogSearch.matches("鲜炒排骨小锅", " \t "));
    assertTrue(CatalogSearch.matches("鲜炒排骨小锅", null));
    assertFalse(CatalogSearch.matches(null, "排骨"));
  }
}
