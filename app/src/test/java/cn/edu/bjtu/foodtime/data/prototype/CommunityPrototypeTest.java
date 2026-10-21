package cn.edu.bjtu.foodtime.data.prototype;

import static org.junit.Assert.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.List;
import org.junit.Test;

public class CommunityPrototypeTest {
  private final User user=new User("me","me@example.com","测试同学","user",true);

  @Test public void shipsACompleteTwoColumnFeedWithoutBackend() {
    CommunityPrototype prototype=new CommunityPrototype();
    assertTrue(prototype.posts().size()>=8);
    assertTrue(prototype.posts().stream().allMatch(post -> !post.imageUrls().isEmpty()));
    assertTrue(prototype.posts().stream().flatMap(post -> post.imageUrls().stream())
        .allMatch(image -> image.startsWith("asset:")));
  }

  @Test public void prototypeInteractionsStayLocalAndUpdateSnapshots() {
    CommunityPrototype prototype=new CommunityPrototype();
    CommunityPost first=prototype.posts().get(0);
    CommunityAction liked=prototype.toggleLike(first.id());
    assertTrue(liked.active());
    assertEquals(first.likeCount()+1,prototype.post(first.id()).likeCount());

    int comments=prototype.post(first.id()).commentCount();
    prototype.comment(user,first.id(),"看起来很好吃");
    assertEquals(comments+1,prototype.post(first.id()).commentCount());
    assertEquals("看起来很好吃",prototype.comments(first.id()).get(prototype.comments(first.id()).size()-1).content());

    prototype.publish(user,"我新发的一篇食记",List.of("file:/tmp/demo.jpg"));
    assertEquals("我新发的一篇食记",prototype.posts().get(0).content());
  }
}
