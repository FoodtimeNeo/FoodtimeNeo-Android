package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.validation.CommunityPermissions;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.concurrent.atomic.AtomicReference;

final class CommunityRemoval {
  static void confirm(MainActivity a,String type,String id,String authorId,String summary,Runnable success){
    var actor=a.repo.currentUser();var location=a.route();
    if(!CommunityPermissions.canRemove(actor,authorId)||a.operationInFlight())return;
    boolean post=type.equals("posts"),administrative=actor.isAdmin()&&!actor.id().equals(authorId);
    if(!post&&!type.equals("comments"))throw new IllegalArgumentException("不支持的内容类型");
    LinearLayout content=Ui.col(a);int padding=Ui.dp(a,20);content.setPadding(padding,padding,padding,padding);
    String excerpt=summary==null?"":summary.strip();if(excerpt.length()>140)excerpt=excerpt.substring(0,140)+"…";
    content.addView(Ui.text(a,excerpt,15,Design.INK));Ui.gap(content,12);
    content.addView(Ui.text(a,post?"帖子将从社区隐藏，相关讨论将无法访问。此操作无法在客户端撤销。":"仅删除所选评论，已通过的楼内回复会保留。此操作无法在客户端撤销。",14,Design.MUTED));
    TextView error=Ui.text(a,"",14,Design.RED);error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(error);
    AlertDialog dialog=new AlertDialog.Builder(a).setTitle(post?"删除帖子":"删除评论").setView(content)
        .setNegativeButton("取消",null).setPositiveButton("确认删除",null).create();
    boolean[] uncertain={false};
    dialog.setOnShowListener(ignored->{
      var positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);var negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
      positive.setOnClickListener(button->{
        if(a.operationInFlight())return;
        var current=a.repo.currentUser();
        if(current==null||!current.active()||!current.id().equals(actor.id())||!current.role().equals(actor.role())||!a.route().equals(location)){
          dialog.dismiss();a.message("登录状态或当前内容已变化，请重新操作");return;
        }
        if(uncertain[0]){dialog.dismiss();a.render();a.message("正在重新读取内容，请根据最新状态确认结果");return;}
        negative.setEnabled(false);dialog.setCancelable(false);dialog.setCanceledOnTouchOutside(false);positive.setText("删除中…");
        AtomicReference<RuntimeException> failed=new AtomicReference<>();boolean[] absent={false};
        a.request(positive,error,()->{
          try{if(administrative)a.repo.removeCommunityAsAdmin(type,id,null);else if(post)a.repo.deleteCommunityPost(id);else a.repo.deleteCommunityComment(id);}
          catch(RepositoryException e){if(e.code().equals("CONTENT_NOT_FOUND"))absent[0]=true;else failed.set(e);}
          catch(RuntimeException e){failed.set(e);}
        },()->{
          negative.setEnabled(true);dialog.setCancelable(true);positive.setText("确认删除");
          if(a.repo.currentUser()==null){dialog.dismiss();a.signedOut();return;}
          RuntimeException failure=failed.get();
          if(failure==null){dialog.dismiss();a.message(absent[0]?"内容已不可见，已更新页面":post?"帖子已删除":"评论已删除");success.run();return;}
          if(failure instanceof RepositoryException e){
            uncertain[0]=e.status()==0||e.status()>=500;
            error.setText(uncertain[0]?"结果暂未确认，请先刷新确认，避免重复删除。":e.getMessage());
            if(e.status()==403)positive.setEnabled(false);
          }else error.setText("删除暂时失败，请稍后重试。");
          if(uncertain[0])positive.setText("刷新确认结果");
        });
      });
    });
    dialog.show();dialog.setCanceledOnTouchOutside(false);
  }
  private CommunityRemoval(){}
}
