package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import cn.edu.bjtu.foodtime.BuildConfig;
import android.os.Bundle;
import cn.edu.bjtu.foodtime.data.remote.EncryptedCookieStore;

/** Seeds only an isolated test package so the external runner can test a real process cold launch. */
public final class StartupSeedRunner extends Instrumentation {
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".campusreview")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18120/api/v1/"))throw new AssertionError("Isolated local build required");
    startActivitySync(new android.content.Intent(getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    var store=new EncryptedCookieStore(getTargetContext(),BuildConfig.API_BASE_URL);
    var cookie=new java.net.HttpCookie("FOODTIME_SESSION","cold-launch-test");cookie.setPath("/");cookie.setMaxAge(3600);
    store.add(java.net.URI.create(BuildConfig.API_BASE_URL),cookie);
    result.putString("stream","Startup seed PASS\n");finish(-1,result);
  }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(1,result);}}
}
