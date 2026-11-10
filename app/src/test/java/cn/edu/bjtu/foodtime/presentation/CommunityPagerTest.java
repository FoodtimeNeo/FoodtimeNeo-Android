package cn.edu.bjtu.foodtime.presentation;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class CommunityPagerTest {
  @Test public void dragTracksTheFingerAndStopsAtTheNeighbor() {
    assertEquals(0.25f, CommunityPager.dragProgress(1, -100, 400), 0);
    assertEquals(-0.25f, CommunityPager.dragProgress(1, 100, 400), 0);
    assertEquals(1f, CommunityPager.dragProgress(1, -1200, 400), 0);
    assertEquals(-1f, CommunityPager.dragProgress(2, 1200, 400), 0);
    assertEquals(2, CommunityPager.neighbor(1, 1f));
    assertEquals(1, CommunityPager.neighbor(2, -1f));
  }

  @Test public void outwardDragAtEitherEdgeCannotMoveThePage() {
    assertEquals(0f, CommunityPager.dragProgress(0, 100, 400), 0);
    assertEquals(0f, CommunityPager.dragProgress(3, -100, 400), 0);
    assertEquals(-1, CommunityPager.neighbor(0, -1f));
    assertEquals(-1, CommunityPager.neighbor(3, 1f));
  }
}
