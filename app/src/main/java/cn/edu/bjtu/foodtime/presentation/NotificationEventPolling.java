package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import cn.edu.bjtu.foodtime.domain.notification.NotificationBatchPoller;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.util.function.BooleanSupplier;

/** Platform adapter shared by scheduled jobs and isolated device verification. */
final class NotificationEventPolling {
  private NotificationEventPolling(){}
  static boolean poll(Context context,FoodtimeRepository repo,BooleanSupplier running){
    var user=repo.currentUser();if(user==null)return false;
    String actor=user.id();var preferences=NotificationPreferenceMigration.prepare(context,repo,actor);
    long epoch=NotificationEventReminders.epoch(context,actor,preferences);
    BooleanSupplier active=()->running.getAsBoolean()&&repo.currentUser()!=null&&actor.equals(repo.currentUser().id())
        &&!"appeal_only".equals(repo.sessionMode())&&NotificationEventReminders.unchanged(context,actor,epoch);
    return new NotificationBatchPoller().poll(repo,preferences,NotificationEventReminders.journal(context,actor),
        (category,event)->NotificationEventReminders.post(context,actor,category,event,active),active);
  }
}
