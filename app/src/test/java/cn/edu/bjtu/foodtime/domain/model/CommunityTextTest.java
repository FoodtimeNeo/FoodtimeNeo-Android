package cn.edu.bjtu.foodtime.domain.model;

import static org.junit.Assert.*;
import org.junit.Test;

public class CommunityTextTest {
  @Test public void headlineUsesTheFirstShortSentenceAndNormalizesWhitespace() {
    assertEquals("明湖酸汤粉太香了！", CommunityText.headline("  明湖酸汤粉太香了！  排队也很快。 "));
    assertEquals("一页校园食记", CommunityText.headline("   "));
  }

  @Test public void headlineAndFallbackRatioStayBounded() {
    String title = CommunityText.headline("这是一段没有标点而且明显超过卡片两行展示长度的校园美食体验记录应该被安全截断");
    assertTrue(title.endsWith("…"));
    assertTrue(title.length() <= 36);
    float ratio = CommunityText.coverHeightRatio("post-42");
    assertTrue(ratio >= 0.82f && ratio <= 1.36f);
    assertEquals(ratio, CommunityText.coverHeightRatio("post-42"), 0f);
  }
}
