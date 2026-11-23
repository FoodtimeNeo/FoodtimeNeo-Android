package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.content.res.Configuration;

/** Device preference, independent of accounts and of backend permissions. */
final class AppTheme {
  static final String SYSTEM = "system", LIGHT = "light", DARK = "dark";
  static String mode(Context context) {
    String value = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("mode", SYSTEM);
    return LIGHT.equals(value) || DARK.equals(value) ? value : SYSTEM;
  }
  static boolean dark(Context context) {
    return DARK.equals(mode(context)) || SYSTEM.equals(mode(context))
        && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
  }
  static void save(Context context, String mode) {
    if (!SYSTEM.equals(mode) && !LIGHT.equals(mode) && !DARK.equals(mode))
      throw new IllegalArgumentException("未知外观模式");
    context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", mode).apply();
  }
  static void choose(MainActivity activity) {
    String[] modes = {SYSTEM, LIGHT, DARK};
    int selected = java.util.List.of(modes).indexOf(mode(activity));
    new android.app.AlertDialog.Builder(activity).setTitle("外观")
        .setSingleChoiceItems(new String[]{"跟随系统", "浅色", "深色"}, selected, (dialog, which) -> {
          if (!modes[which].equals(mode(activity))) {
            save(activity, modes[which]);
            activity.recreate();
          }
          dialog.dismiss();
        }).setNegativeButton("取消", null).show();
  }
  private AppTheme() {}
}
