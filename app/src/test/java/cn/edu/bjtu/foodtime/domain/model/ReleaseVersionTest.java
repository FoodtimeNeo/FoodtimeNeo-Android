package cn.edu.bjtu.foodtime.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseVersionTest {
  @Test public void testingNamesKeepTheirSuffixAndExplicitLabel(){
    assertEquals("v2.0.0α（测试版）",ReleaseVersion.display("2.0.0α"));
    assertEquals("v2.0.0β（测试版）",ReleaseVersion.display("2.0.0β"));
    assertEquals("v2.0.0",ReleaseVersion.display("2.0.0"));
  }
  @Test public void testingUpdateIsOptionalEvenIfServerMinimumIsMisconfigured(){
    for(String name:new String[]{"2.0.0α","2.0.0β","2.0.0-alpha"})
      assertFalse(ReleaseVersion.forced(new Models.AppRelease(23,name,"https://example.test/app.apk","",23),22));
  }
  @Test public void formalMinimumPolicyIsPreserved(){
    assertTrue(ReleaseVersion.forced(new Models.AppRelease(24,"2.0.0","https://example.test/app.apk","",24),23));
    assertFalse(ReleaseVersion.forced(new Models.AppRelease(24,"2.0.0","https://example.test/app.apk","",0),23));
  }
}
