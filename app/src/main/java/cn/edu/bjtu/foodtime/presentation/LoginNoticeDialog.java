package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.*;

final class LoginNoticeDialog {
  final AlertDialog dialog;
  private final CheckBox choice;
  private final TextView failure;
  private final LoginNoticeController controller;
  LoginNoticeDialog(MainActivity activity, LoginNoticeController controller) {
    this.controller=controller;
    LinearLayout body=Ui.col(activity);
    body.setPadding(Ui.dp(activity,20),Ui.dp(activity,12),Ui.dp(activity,20),Ui.dp(activity,8));
    ScrollView scroll=new ScrollView(activity);
    TextView markdown=Ui.text(activity,"",16,Design.INK);
    NoticeMarkdown.render(markdown,controller.pending.content());
    scroll.addView(markdown);
    int max=(int)(activity.getResources().getDisplayMetrics().heightPixels*.5f);
    body.addView(scroll,new LinearLayout.LayoutParams(-1,max));
    choice=new CheckBox(activity);choice.setText("下次不再显示");choice.setTextColor(Design.INK);
    choice.setMinHeight(Ui.dp(activity,48));choice.setChecked(controller.dontShowAgain);
    choice.setOnCheckedChangeListener((button,checked)->controller.dontShowAgain=checked);
    body.addView(choice);
    failure=Ui.text(activity,"",14,Design.RED);failure.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(failure);
    dialog=new AlertDialog.Builder(activity).setTitle("系统公告").setView(body).setPositiveButton("确认",null).create();
    dialog.setCanceledOnTouchOutside(false);
    dialog.setOnCancelListener(d->controller.close());
    dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->activity.confirmLoginNotice());update();});
  }
  void show(){dialog.show();dialog.getWindow().setBackgroundDrawable(Ui.shape(dialog.getContext(),Design.PAPER,Design.INK,10));}
  void update(){
    choice.setEnabled(!controller.saving);
    failure.setText(controller.error);
    dialog.setCancelable(!controller.saving);
    if(dialog.getButton(-1)!=null){dialog.getButton(-1).setEnabled(!controller.saving);dialog.getButton(-1).setText(controller.saving?"保存中…":"确认");dialog.getButton(-1).setTextColor(Design.RED);}
  }
}
