package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.LinearLayout;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.List;

final class ReportManagementUi {
  private ReportManagementUi() {}
  static EditText field(MainActivity a,LinearLayout parent,String label,int limit) {
    EditText input=Ui.field(parent,label,false,true);
    input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
    return input;
  }
  static void requireActor(MainActivity a,String actor) {
    var current=a.repo.currentUser();
    if(current==null||!actor.equals(current.id())||"appeal_only".equals(a.repo.sessionMode()))
      throw new RepositoryException(409,"ACCOUNT_CHANGED","账号已变化，请重新打开管理页面",0);
  }
  static String nullable(EditText input) {String text=input.getText().toString().strip();return text.isEmpty()?null:text;}
  static void evidence(MainActivity a,LinearLayout parent,List<String> urls) {
    for(int i=0;i<urls.size();i++) {
      int index=i;
      var image=Ui.image(a,Ui.thumb(urls.get(i)),"证据图片 "+(i+1),true);
      image.setOnClickListener(v -> new CommunityPhotoDialog(a,urls,index,true,null,null,p -> {}).show());
      parent.addView(image,new LinearLayout.LayoutParams(-1,Ui.dp(a,160)));Ui.gap(parent,8);
    }
  }
  static String outcome(String value) {return switch(value){case "upheld" -> "成立";case "not_upheld","rejected" -> "不成立";default -> value;};}
  static String type(String value) {return switch(value){case "post" -> "食记";case "comment" -> "评论";case "message" -> "私信";default -> value;};}
  static AlertDialog form(MainActivity a,String title,LinearLayout form) {
    var scroll=new android.widget.ScrollView(a);scroll.setFillViewport(true);scroll.addView(form);
    form.setPadding(Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,12));
    AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setView(scroll)
        .setNegativeButton("取消",null).setPositiveButton("提交",null).create();dialog.show();return dialog;
  }
}
