package cn.edu.bjtu.foodtime.domain.notification;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.NotificationRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationBatchPollerTest {
  private static final NotificationPreferences ON=new NotificationPreferences(List.of(new NotificationPreference("message",true,2)));
  private static NotificationEvent event(long seq,String category,String status){return new NotificationEvent("e"+seq,seq,category,"private title","private body","message","",0,status);}
  private static class Memory implements NotificationBatchPoller.Journal {
    long seq;final Set<String> shown=new HashSet<>();
    public long cursor(){return seq;}
    public boolean wasShown(String id){return shown.contains(id);}
    public void shown(List<String> ids){shown.addAll(ids);}
    public void checkpoint(long next,String id){seq=next;shown.remove(id);}
  }
  private static class Port implements NotificationRepository {
    final List<NotificationEvent> events=new ArrayList<>();final List<String> receipts=new ArrayList<>();
    boolean failReceipt;
    public NotificationEventPage notificationEvents(long after,int size){var rows=events.stream().filter(e->e.seq()>after).limit(size).toList();return new NotificationEventPage(rows,rows.isEmpty()?after:rows.get(rows.size()-1).seq());}
    public void markNotificationDelivered(String id){if(failReceipt)throw new RepositoryException(503,"RETRY","retry",0);receipts.add(id);}
  }
  @Test public void stableEventsDetectMessagesWithoutAnyUnreadCountDependency(){
    var port=new Port();var journal=new Memory();var posts=new ArrayList<String>();var poller=new NotificationBatchPoller();
    port.events.add(event(1,"message","pending"));
    assertFalse(poller.poll(port,ON,journal,(c,e)->posts.add(e.eventId()),()->true));
    port.events.add(event(2,"message","pending"));
    assertFalse(poller.poll(port,ON,journal,(c,e)->posts.add(e.eventId()),()->true));
    assertEquals(List.of("e1","e2"),posts);assertEquals(2,journal.seq);
    assertFalse(poller.poll(port,ON,journal,(c,e)->{fail("repeated event");return false;},()->true));
  }
  @Test public void sameCategoryIsMergedButAllReceiptsAreAcknowledged(){
    var port=new Port();port.events.addAll(List.of(event(1,"message","pending"),event(2,"message","pending"),event(3,"feedback","pending")));
    var prefs=new NotificationPreferences(List.of(new NotificationPreference("message",true,2),new NotificationPreference("feedback",true,2)));
    var posts=new ArrayList<String>();var journal=new Memory();
    new NotificationBatchPoller().poll(port,prefs,journal,(c,e)->posts.add(c+":"+e.eventId()),()->true);
    assertEquals(List.of("message:e2","feedback:e3"),posts);assertEquals(List.of("e1","e2","e3"),port.receipts);assertTrue(journal.shown.isEmpty());
  }
  @Test public void receiptFailureRetriesWithoutReposting(){
    var port=new Port();port.events.add(event(1,"message","pending"));port.failReceipt=true;
    var journal=new Memory();var posts=new ArrayList<String>();var poller=new NotificationBatchPoller();
    assertThrows(RepositoryException.class,()->poller.poll(port,ON,journal,(c,e)->posts.add(e.eventId()),()->true));
    assertEquals(0,journal.seq);assertTrue(journal.wasShown("e1"));port.failReceipt=false;
    poller.poll(port,ON,journal,(c,e)->posts.add(e.eventId()),()->true);
    assertEquals(List.of("e1"),posts);assertEquals(List.of("e1"),port.receipts);assertEquals(1,journal.seq);assertTrue(journal.shown.isEmpty());
  }
  @Test public void disabledExpiredAndDeliveredEventsAdvanceWithoutAlerts(){
    var port=new Port();port.events.addAll(List.of(event(1,"notice","skipped"),event(2,"message","delivered"),event(3,"feedback","pending")));
    var journal=new Memory();new NotificationBatchPoller().poll(port,ON,journal,(c,e)->{fail("not eligible");return false;},()->true);
    assertEquals(3,journal.seq);assertTrue(port.receipts.isEmpty());
  }
  @Test public void failedSystemPostDoesNotConsumeTheEvent(){
    var port=new Port();port.events.add(event(1,"message","pending"));var journal=new Memory();
    assertTrue(new NotificationBatchPoller().poll(port,ON,journal,(c,e)->false,()->true));
    assertEquals(0,journal.seq);assertTrue(journal.shown.isEmpty());assertTrue(port.receipts.isEmpty());
  }
  @Test public void boundedPagesResumeExactlyAfterLastCheckpoint(){
    var port=new Port();for(int i=1;i<=7;i++)port.events.add(event(i,"message","pending"));var journal=new Memory();
    assertTrue(new NotificationBatchPoller(2,2).poll(port,ON,journal,(c,e)->true,()->true));assertEquals(4,journal.seq);
    assertFalse(new NotificationBatchPoller(2,2).poll(port,ON,journal,(c,e)->true,()->true));assertEquals(7,journal.seq);assertEquals(7,port.receipts.size());
  }
  @Test public void accountChangeDuringFetchPreventsAnyNotificationOrCheckpoint(){
    var active=new AtomicBoolean(true);var port=new Port(){public NotificationEventPage notificationEvents(long after,int size){active.set(false);return new NotificationEventPage(List.of(event(1,"message","pending")),1);}};
    var journal=new Memory();assertFalse(new NotificationBatchPoller().poll(port,ON,journal,(c,e)->{fail("old account");return false;},active::get));assertEquals(0,journal.seq);
  }
  @Test public void preferenceChangeDuringPostStopsReceiptsAndJournal(){
    var active=new AtomicBoolean(true);var port=new Port();port.events.add(event(1,"message","pending"));var journal=new Memory();
    assertFalse(new NotificationBatchPoller().poll(port,ON,journal,(c,e)->{active.set(false);return true;},active::get));assertEquals(0,journal.seq);assertTrue(port.receipts.isEmpty());assertTrue(journal.shown.isEmpty());
  }
  @Test public void corruptOrderOrCursorIsRejectedBeforeSideEffects(){
    for(var page:List.of(new NotificationEventPage(List.of(event(2,"message","pending"),event(1,"message","pending")),1),new NotificationEventPage(List.of(event(1,"message","pending")),2))){
      var port=new Port(){public NotificationEventPage notificationEvents(long after,int size){return page;}};var journal=new Memory();
      assertThrows(RepositoryException.class,()->new NotificationBatchPoller().poll(port,ON,journal,(c,e)->{fail("corrupt events");return false;},()->true));assertEquals(0,journal.seq);assertTrue(port.receipts.isEmpty());
    }
  }
  @Test public void durableJournalFailurePreventsAcknowledgement(){
    var port=new Port();port.events.add(event(1,"message","pending"));var journal=new Memory(){public void shown(List<String> ids){throw new IllegalStateException("storage full");}};
    assertThrows(IllegalStateException.class,()->new NotificationBatchPoller().poll(port,ON,journal,(c,e)->true,()->true));assertTrue(port.receipts.isEmpty());assertEquals(0,journal.seq);
  }
}
