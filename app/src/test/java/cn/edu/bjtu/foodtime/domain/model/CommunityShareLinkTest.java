package cn.edu.bjtu.foodtime.domain.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CommunityShareLinkTest {
  private static final String ID = "94061de3-347d-4368-890b-706d433f8478";

  @Test public void officialLinkRoundTripsWithoutUsingQueryAsNavigation() {
    assertEquals(ID, CommunityShareLink.postId(CommunityShareLink.postUrl(ID)));
    assertEquals(ID, CommunityShareLink.postId(CommunityShareLink.postUrl(ID) + "?redirect=https://example.org"));
  }

  @Test public void ignoresInvalidOriginAndPath() {
    String path = "/community/posts/" + ID;
    for (String url : new String[] {
        "http://bjtufoodtime.top" + path,
        "https://bjtufoodtime.top.example.org" + path,
        "https://example.org@bjtufoodtime.top" + path,
        "https://bjtufoodtime.top:8443" + path,
        "https://bjtufoodtime.top" + path + "/admin",
        "https://bjtufoodtime.top/community/posts/%2e%2e",
        "https://bjtufoodtime.top/community/posts/1-1-1-1-1",
        "invalid url",
    }) assertEquals(url, "", CommunityShareLink.postId(url));
  }
}
