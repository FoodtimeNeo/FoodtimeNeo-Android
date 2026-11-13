package cn.edu.bjtu.foodtime.presentation;

import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Up to three private evidence images chosen through the shared ImagePicker flow. */
final class EvidenceSlots {
  private EvidenceSlots() {}

  static List<String> paths(MainActivity activity, String prefix) {
    List<String> paths = new ArrayList<>();
    for (int index = 0; index < 3; index++) {
      String path = activity.drafts.getString(key(prefix, index), "");
      if (!path.isBlank()) paths.add(path);
    }
    return paths;
  }

  static List<String> upload(MainActivity activity, String prefix) {
    return upload(activity,prefix,(previous,uploaded)->{});
  }
  static List<String> upload(MainActivity activity,String prefix,java.util.function.BiConsumer<String,String> progress) {
    var actor = activity.repo.currentUser();
    if (actor == null) throw new IllegalStateException("请先登录");
    List<String> urls = new ArrayList<>();
    for (String path : paths(activity, prefix)) {
      requireActor(activity, actor.id());
      String uploaded = activity.repo.uploadEvidence(path);
      requireActor(activity, actor.id());
      progress.accept(path,uploaded);
      // Keep successful references when a later upload or association fails.
      activity.runOnUiThread(() -> {
        var current = activity.repo.currentUser();
        if (current == null || !actor.id().equals(current.id())) return;
        for (int index = 0; index < 3; index++) {
          String slot = key(prefix, index);
          if (path.equals(activity.drafts.getString(slot, ""))) activity.drafts.putString(slot, uploaded);
        }
      });
      urls.add(uploaded);
    }
    return urls;
  }

  private static void requireActor(MainActivity activity, String expected) {
    var current = activity.repo.currentUser();
    if (current == null || !expected.equals(current.id()))
      throw new IllegalStateException("账号已变化，请重新选择证据图片");
  }

  static void show(MainActivity activity, LinearLayout parent, String prefix) {
    show(activity,parent,prefix,false);
  }
  static void show(MainActivity activity,LinearLayout parent,String prefix,boolean previews) {
    for (int index = 0; index < 3; index++) {
      String key = key(prefix, index);
      String path = activity.drafts.getString(key, "");
      LinearLayout row = Ui.row(activity);
      TextView choose = Ui.button(activity,
          path.isBlank() ? "＋ 添加证据 " + (index + 1) : previews?"更换 " + (index+1):"证据 " + (index + 1) + " 已选择",
          false, () -> activity.pickImage(key));
      choose.setTag("evidence-choose:"+index);
      row.addView(choose, new LinearLayout.LayoutParams(0, -2, 1));
      if (!path.isBlank()) {
        if(previews){
          var image=Ui.image(activity,Ui.thumb(path),"申诉证据",false);image.setTag("evidence-preview:"+index);
          image.setContentDescription("查看申诉证据 "+(index+1));image.setFocusable(true);image.setClickable(true);
          image.setOnClickListener(v->CommunityGallery.openFull(activity,path));
          var layout=new LinearLayout.LayoutParams(Ui.dp(activity,64),Ui.dp(activity,64));layout.leftMargin=Ui.dp(activity,8);row.addView(image,layout);
        }
        TextView remove = Ui.button(activity, "移除", false, () -> {
          if(activity.operationInFlight())return;
          activity.drafts.remove(key);
          activity.redraw();
        });
        remove.setTag("evidence-remove:"+index);
        var layout=new LinearLayout.LayoutParams(-2,-2);layout.leftMargin=Ui.dp(activity,8);row.addView(remove,layout);
      }
      parent.addView(row);
      Ui.gap(parent,8);
    }
  }

  private static String key(String prefix, int index) {
    return prefix + ":" + index;
  }
}
