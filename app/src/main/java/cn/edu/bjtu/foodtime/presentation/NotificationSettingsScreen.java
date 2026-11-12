package cn.edu.bjtu.foodtime.presentation;

import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreference;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Seven server-backed Android reminder switches. */
final class NotificationSettingsScreen {
  private final MainActivity activity;
  private final LinearLayout page, rows;
  private final TextView error;
  private final String requestKey = "notification-settings:" + UUID.randomUUID();
  private NotificationPreferences preferences;

  private NotificationSettingsScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    this.page = page;
    rows = Ui.col(activity);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(error);
    page.addView(rows);
    load();
  }

  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "05.1", "提醒设置", "站内消息始终可读；这里控制系统提醒");
    new NotificationSettingsScreen(activity, page);
  }

  private void load() {
    rows.removeAllViews();
    rows.addView(Ui.text(activity, "正在读取…", 14, Design.MUTED));
    AtomicReference<NotificationPreferences> result = new AtomicReference<>();
    activity.read(requestKey, null, error,
        () -> result.set(activity.repo.notificationPreferences()), () -> {
          preferences = result.get();
          render();
        }, () -> {});
  }

  private void render() {
    rows.removeAllViews();
    rows.addView(Ui.text(activity,
        "开启某类只影响之后产生的事件；站内消息和未读不受影响。", 12, Design.MUTED));
    for (NotificationPreference item : preferences.items()) {
      CheckBox box = new CheckBox(activity);
      box.setText(label(item.category()));
      box.setTextColor(Design.INK);
      box.setTextSize(15);
      box.setChecked(item.enabled());
      box.setOnCheckedChangeListener((button, checked) -> {
        if (checked == item.enabled()) return;
        activity.request(null, error,
            () -> activity.repo.updateNotificationPreference(item.category(), checked, item.revision()),
            this::load);
      });
      rows.addView(box);
    }
  }

  private static String label(String category) {
    return switch (category) {
      case "message" -> "私信";
      case "interaction" -> "互动";
      case "notice" -> "公告";
      case "feedback" -> "反馈";
      case "correction" -> "纠错";
      case "report" -> "举报";
      case "appeal" -> "申诉";
      default -> category;
    };
  }
}
