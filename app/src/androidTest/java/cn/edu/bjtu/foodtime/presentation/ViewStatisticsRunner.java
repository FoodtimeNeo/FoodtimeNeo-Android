package cn.edu.bjtu.foodtime.presentation;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Opt-in device test: a stalled statistics request cannot occupy the normal read queue. */
public final class ViewStatisticsRunner extends Instrumentation {
  @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
  @Override public void onStart() {
    MainActivity activity=null;
    CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),read=new CountDownLatch(1);
    Bundle result=new Bundle();
    int code=Activity.RESULT_CANCELED;
    try {
      Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity=(MainActivity)startActivitySync(intent);
      MainActivity target=activity;
      runOnMainSync(() -> target.recordPostView("statistics-test",() -> {
        started.countDown();
        try { if(!release.await(8,TimeUnit.SECONDS)) throw new IllegalStateException("Statistics test timed out"); }
        catch(InterruptedException failure) { Thread.currentThread().interrupt(); }
      },() -> {}));
      if(!started.await(4,TimeUnit.SECONDS)) throw new AssertionError("Statistics work never started");
      runOnMainSync(() -> target.read("read-during-statistics",null,null,read::countDown,() -> {},() -> {}));
      if(!read.await(3,TimeUnit.SECONDS)) throw new AssertionError("Normal reads blocked behind statistics");
      result.putString("result","PASS: normal reads execute while statistics remains blocked");
      result.putInt("checks",2);
      code=Activity.RESULT_OK;
    } catch(Throwable failure) {
      result.putString("failure",failure.toString());
    } finally {
      release.countDown();
      if(activity!=null) { MainActivity target=activity; runOnMainSync(target::finish); }
    }
    finish(code,result);
  }
}
