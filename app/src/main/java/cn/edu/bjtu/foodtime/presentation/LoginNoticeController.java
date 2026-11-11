package cn.edu.bjtu.foodtime.presentation;

import android.os.Handler;
import android.os.Looper;
import cn.edu.bjtu.foodtime.domain.model.Models.Notice;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.util.concurrent.Executor;
import java.util.*;

/** Retained across Activity recreation, reset on logout; process cold starts check again. */
final class LoginNoticeController {
  private final Handler main = new Handler(Looper.getMainLooper());
  private String actor="";
  private int epoch;
  private boolean checked, checking;
  private Object owner;
  private Runnable observer;
  private final Set<String> delivered=new HashSet<>();
  private boolean remaining;
  private FoodtimeRepository repository;
  private Executor executor;
  Notice pending;
  boolean saving, dontShowAgain;
  String error="", checkError="";

  void attach(Object owner, Runnable observer) { this.owner=owner;this.observer=observer; }
  void detach(Object owner) { if(this.owner==owner){this.owner=null;observer=null;} }
  private void changed() { if(observer!=null)observer.run(); }
  void reset() {
    epoch++;actor="";checked=false;checking=false;pending=null;saving=false;dontShowAgain=false;error="";checkError="";remaining=false;delivered.clear();
    changed();
  }
  void check(FoodtimeRepository repo, Executor executor) {
    this.repository=repo;this.executor=executor;
    if(repo.currentUser()==null)return;
    String user=repo.currentUser().id();
    if(!actor.equals(user)){reset();actor=user;}
    if(checked)return;
    checked=true;checking=true;int ticket=epoch;
    executor.execute(()->{
      List<Notice> notices=List.of();RuntimeException failure=null;
      try { notices=repo.currentNotices();if(notices==null||notices.size()>2||notices.stream().anyMatch(Objects::isNull))throw new IllegalStateException("公告响应异常"); }
      catch(RuntimeException e) { failure=e; }
      List<Notice> result=notices;RuntimeException problem=failure;
      main.post(()->{
        if(ticket!=epoch||repo.currentUser()==null||!user.equals(repo.currentUser().id()))return;
        checking=false;
        if(problem==null){List<Notice> fresh=result.stream().filter(item->!delivered.contains(item.id())).collect(java.util.stream.Collectors.toList());pending=fresh.isEmpty()?null:fresh.get(0);remaining=fresh.size()>1;}
        checkError=problem==null?"":"公告暂时无法加载，可在通知信箱重试";changed();
      });
    });
  }
  void retry(FoodtimeRepository repo,Executor executor){
    if(checking)return;
    epoch++;checked=false;checkError="";check(repo,executor);
  }
  void close(){if(!saving)advance();}
  private void advance(){
    if(pending!=null)delivered.add(pending.id());pending=null;error="";dontShowAgain=false;
    boolean next=remaining;remaining=false;changed();
    if(next&&repository!=null&&executor!=null){checked=false;check(repository,executor);}
  }
  void foreground(FoodtimeRepository repo,Executor executor){if(pending==null&&!checking){checked=false;check(repo,executor);}}
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
        if(problem==null){advance();}
        else error="确认失败，请重试";
        changed();
      });
    });
  }
}
