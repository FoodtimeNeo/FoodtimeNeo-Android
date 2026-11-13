package cn.edu.bjtu.foodtime.domain.notification;
import org.junit.Test;
import static org.junit.Assert.*;
public class NotificationOpenTargetTest {
  private static final String ACTOR="00000000-0000-4000-8000-000000000001",REF="00000000-0000-4000-8000-000000000002";
  @Test public void rejectsUnownedOrUnknownNotificationData(){assertNull(NotificationOpenTarget.parse("bad","message","",""));assertNull(NotificationOpenTarget.parse(ACTOR,"unknown","",""));}
  @Test public void messageAndNoticeUseSeparateInboxTabs(){assertEquals("chat",NotificationOpenTarget.parse(ACTOR,"message","thread",REF).destination().inboxTab());assertEquals("notice",NotificationOpenTarget.parse(ACTOR,"notice","notice",REF).destination().inboxTab());}
  @Test public void mapsFeedbackAndReportOnlyWithSafeReference(){assertEquals("feedback-detail",NotificationOpenTarget.parse(ACTOR,"feedback","feedback",REF).destination().kind());assertEquals("inbox",NotificationOpenTarget.parse(ACTOR,"feedback","feedback","../../other").destination().kind());assertEquals("report-followups",NotificationOpenTarget.parse(ACTOR,"report","report",REF).destination().kind());assertEquals("decisions",NotificationOpenTarget.parse(ACTOR,"report","moderation-decision",REF).destination().id());}
  @Test public void receiptFallbacksExposeNoPrivateBody(){assertEquals("my-corrections",NotificationOpenTarget.parse(ACTOR,"correction","","").destination().kind());assertEquals("message",NotificationOpenTarget.parse(ACTOR,"appeal","appeal",REF).destination().inboxTab());assertEquals(ACTOR,NotificationOpenTarget.parse(ACTOR,"interaction","","").actor());}
}
