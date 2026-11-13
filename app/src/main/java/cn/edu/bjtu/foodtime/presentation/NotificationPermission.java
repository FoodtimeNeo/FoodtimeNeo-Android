package cn.edu.bjtu.foodtime.presentation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Runtime permission is separate from the server's category choice. */
final class NotificationPermission {
  static final int REQUEST_CODE=1092;
  private Runnable pending;
  void request(MainActivity a,Runnable allowed){
    if(NotificationEventReminders.permitted(a)){allowed.run();return;}
    if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      pending=allowed;a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_CODE);
    }else openSettings(a);
  }
  void result(MainActivity a,int[] results){
    Runnable action=pending;pending=null;
    if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){if(action!=null)action.run();}
    else a.message("系统未允许通知，可在系统通知设置中开启。");
    NotificationSettingsScreen.refreshPermissionState(a);
  }
  static void openSettings(MainActivity a){
    a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));
  }
  void clear(){pending=null;}
}
