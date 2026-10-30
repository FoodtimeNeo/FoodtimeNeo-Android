package cn.edu.bjtu.foodtime.presentation;

import android.os.Handler;
import android.os.Looper;
import cn.edu.bjtu.foodtime.domain.model.Models.Notice;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.util.concurrent.Executor;

/** Retained across Activity recreation, reset on logout; process cold starts check again. */
final class LoginNoticeController {
  private final Handler main = new Handler(Looper.getMainLooper());
  private String actor="";
  private int epoch;
  private boolean checked, checking;
  private Object owner;
  private Runnable observer;
  Notice pending;
  boolean saving, dontShowAgain;
  String error="", checkError="";

  void attach(Object owner, Runnable observer) { this.owner=owner;this.observer=observer; }
  void detach(Object owner) { if(this.owner==owner){this.owner=null;observer=null;} }
  private void changed() { if(observer!=null)observer.run(); }
  void reset() {
    epoch++;actor="";checked=false;checking=false;pending=null;saving=false;dontShowAgain=false;error="";checkError="";
    changed();
  }
  void check(FoodtimeRepository repo, Executor executor) {
    if(repo.currentUser()==null)return;
    String user=repo.currentUser().id();
    if(!actor.equals(user)){reset();actor=user;}
    if(checked)return;
    checked=true;checking=true;int ticket=epoch;
    executor.execute(()->{
      Notice notice=null;RuntimeException failure=null;
      try { notice=repo.currentNotice(); } catch(RuntimeException e) { failure=e; }
      Notice result=notice;RuntimeException problem=failure;
      main.post(()->{
        if(ticket!=epoch||repo.currentUser()==null||!user.equals(repo.currentUser().id()))return;
        checking=false;pending=result;checkError=problem==null?"":"公告暂时无法加载，可在通知信箱重试";changed();
      });
    });
  }
  void retry(FoodtimeRepository repo,Executor executor){
    if(checking)return;
    epoch++;checked=false;checkError="";check(repo,executor);
  }
  void close(){if(!saving){pending=null;error="";dontShowAgain=false;changed();}}
  void confirm(FoodtimeRepository repo,Executor executor) {
    if(saving||pending==null)return;
    int ticket=epoch;String id=pending.id(),user=actor;boolean suppress=dontShowAgain;
    saving=true;error="";changed();
    executor.execute(()->{
      RuntimeException failure=null;
      try { repo.confirmNotice(id,suppress); } catch(RuntimeException e) { failure=e; }
      RuntimeException problem=failure;
      main.post(()->{
        if(ticket!=epoch||repo.currentUser()==null||!user.equals(repo.currentUser().id()))return;
        saving=false;
        if(problem==null){pending=null;dontShowAgain=false;error="";}
        else error="确认失败，请重试";
        changed();
      });
    });
  }
}
