package cn.edu.bjtu.foodtime.domain.notification;

import cn.edu.bjtu.foodtime.domain.model.Models.NotificationEvent;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;
import cn.edu.bjtu.foodtime.domain.repository.NotificationRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Stable-event delivery with category aggregation and a durable receipt journal. No unread-count dependency. */
public final class NotificationBatchPoller {
  public interface Journal {
    long cursor();
    boolean wasShown(String eventId);
    void shown(List<String> eventIds);
    void checkpoint(long seq,String eventId);
  }
  public interface Delivery {boolean post(String category,NotificationEvent latest);}
  private final int pageSize,maxPages;
  public NotificationBatchPoller(){this(50,10);}
  public NotificationBatchPoller(int pageSize,int maxPages){
    if(pageSize<1||maxPages<1)throw new IllegalArgumentException();this.pageSize=pageSize;this.maxPages=maxPages;
  }
  /** Returns true only when a bounded run still has work to retry. */
  public boolean poll(NotificationRepository repo,NotificationPreferences preferences,Journal journal,Delivery delivery,BooleanSupplier active){
    Map<String,Boolean> enabled=new HashMap<>();
    for(var value:preferences.items())enabled.put(value.category(),value.enabled());
    for(int page=0;page<maxPages;page++){
      if(!active.getAsBoolean())return false;
      long cursor=journal.cursor();var data=repo.notificationEvents(cursor,pageSize);
      if(!active.getAsBoolean())return false;
      long previous=cursor;
      for(var event:data.items()){
        if(event.eventId().isBlank()||event.seq()<=previous)throw invalid();previous=event.seq();
      }
      if(data.nextSeq()!=previous)throw invalid();
      Map<String,List<NotificationEvent>> groups=new LinkedHashMap<>();
      for(var event:data.items())if(wanted(event,enabled)&&!journal.wasShown(event.eventId()))
        groups.computeIfAbsent(event.category(),ignored->new ArrayList<>()).add(event);
      for(var group:groups.entrySet()){
        if(!active.getAsBoolean())return false;
        List<NotificationEvent> events=group.getValue();
        if(!delivery.post(group.getKey(),events.get(events.size()-1)))return true;
        if(!active.getAsBoolean())return false;
        var ids=new ArrayList<String>();for(var event:events)ids.add(event.eventId());
        journal.shown(ids);
      }
      for(var event:data.items()){
        if(!active.getAsBoolean())return false;
        if(wanted(event,enabled))repo.markNotificationDelivered(event.eventId());
        if(!active.getAsBoolean())return false;
        journal.checkpoint(event.seq(),event.eventId());
      }
      if(data.items().size()<pageSize)return false;
    }
    return true;
  }
  private static boolean wanted(NotificationEvent event,Map<String,Boolean> enabled){return Boolean.TRUE.equals(enabled.get(event.category()))&&"pending".equals(event.deliveryStatus());}
  private static RepositoryException invalid(){return new RepositoryException(502,"INVALID_RESPONSE","提醒事件顺序或游标异常，请重试",0);}
}
