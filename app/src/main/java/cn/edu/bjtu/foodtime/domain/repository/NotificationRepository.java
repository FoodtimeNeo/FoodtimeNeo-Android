package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.NotificationEventPage;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreference;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;

/** Server notification preferences and stable events; independent of catalog and platform delivery. */
public interface NotificationRepository {
  default NotificationPreferences notificationPreferences(){throw new UnsupportedOperationException();}
  default NotificationPreference updateNotificationPreference(String category,boolean enabled,long revision){throw new UnsupportedOperationException();}
  default NotificationPreference adoptLegacyMessagePreference(boolean enabled){throw new UnsupportedOperationException();}
  default NotificationEventPage notificationEvents(long afterSeq,int limit){throw new UnsupportedOperationException();}
  default void markNotificationDelivered(String eventId){throw new UnsupportedOperationException();}
}
