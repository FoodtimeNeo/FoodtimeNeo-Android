package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.util.concurrent.CancellationException;

/** One-time local-to-server transition. The server decides whether a newer explicit choice already exists. */
final class NotificationPreferenceMigration {
  private static final Object LOCK=new Object();
  private NotificationPreferenceMigration(){}
  static void require(Context context,FoodtimeRepository repo,String actor){
    var current=repo.currentUser();
    if(current==null||!actor.equals(current.id())||"appeal_only".equals(repo.sessionMode())||!NotificationEventReminders.configured(context,actor))
      throw new CancellationException("Reminder account changed");
  }
  static NotificationPreferences prepare(Context context,FoodtimeRepository repo,String actor){
    synchronized(LOCK){
      require(context,repo,actor);
      if(!NotificationEventReminders.migrated(context,actor)){
        repo.adoptLegacyMessagePreference(PrivateMessageReminders.configured(context,actor));
        require(context,repo,actor);NotificationEventReminders.completeMigration(context,actor);
      }else PrivateMessageReminders.disable(context);
    }
    var preferences=repo.notificationPreferences();require(context,repo,actor);
    return NotificationEventReminders.accept(context,actor,preferences);
  }
}
