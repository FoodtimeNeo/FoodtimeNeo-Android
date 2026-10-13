package cn.edu.bjtu.foodtime.data.remote;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.util.*;

/** Real Android JSON/Keystore/HTTP against the explicitly enabled loopback integration Backend. */
public class RemoteRepositoryRunner extends Instrumentation {
  private static final String BASE="http://127.0.0.1:8080/api/v1/";
  private static final String PASSWORD="Foodtime2026!";
  private int checks;
  private final String prefix="设备验收"+UUID.randomUUID().toString().substring(0,8);
  private void check(boolean condition,String message) { if(!condition) throw new AssertionError(message);checks++; }
  private void rejects(Runnable action,int status) {
    try { action.run();throw new AssertionError("Expected status "+status); }
    catch(RepositoryException expected) {check(expected.status()==status,"Expected status "+status+", got "+expected.status());}
  }
  private RemoteRepository repository(Context context,String name) {
    CookieManager manager=new CookieManager(new EncryptedCookieStore(context,BASE,name),CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    return new RemoteRepository(BASE,new HttpTransport(BASE,manager));
  }
  private void load(FoodtimeRepository r,ContentRequest.Query query,String id) { r.refresh(new ContentRequest(query,id)); }
  @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
  @Override public void onStart() {
    Bundle report=new Bundle();File photo=null;
    try {
      Context context=getTargetContext();String namespace="remote-test-"+UUID.randomUUID();
      RemoteRepository student=repository(context,namespace),admin=repository(context,namespace+"-admin"),platform=repository(context,namespace+"-platform");
      student.restoreSession();check(student.currentUser()==null,"Anonymous startup stays anonymous");
      student.login("20260001@bjtu.edu.cn",PASSWORD);check(student.currentUser().role().equals("user"),"Role comes from server");
      RemoteRepository restored=repository(context,namespace);restored.restoreSession();check(restored.currentUser().id().equals(student.currentUser().id()),"Encrypted cookies restore server session");
      check(!context.getSharedPreferences(namespace,0).getAll().toString().contains("FOODTIME_SESSION"),"Cookie name/value is not stored as plaintext");
      EncryptedCookieStore changedOrigin=new EncryptedCookieStore(context,"http://127.0.0.1:9999/api/v1/",namespace);
      check(changedOrigin.getCookies().isEmpty(),"Changing Backend origin cannot restore another origin's session cookie");
      load(student,ContentRequest.Query.HALLS,"");Hall hall=student.halls().stream().filter(h -> h.name().equals("联调示例食堂")).findFirst().orElseThrow();
      load(student,ContentRequest.Query.STALLS,hall.id());Stall stall=student.stalls(hall.id()).get(0);
      load(student,ContentRequest.Query.DISHES,stall.id());Dish dish=student.dishes(stall.id()).stream().filter(d -> d.name().equals("联调示例菜品")).findFirst().orElseThrow();
      load(student,ContentRequest.Query.DISH_DETAIL,dish.id());check(student.context(dish.id()).contains(hall.name()),"Catalog uses real relationship names");
      RatingSummary snapshot=student.rating(dish.id());
      rejects(() -> load(student,ContentRequest.Query.MODERATION,""),403);
      student.submitReview(dish.id(),4,prefix+"评论");load(student,ContentRequest.Query.MY_SUBMISSIONS,"");
      Submission review=student.submissions(student.currentUser().id()).stream().filter(s -> s.content().equals(prefix+"评论")).findFirst().orElseThrow();
      check(review.status().equals("pending")&&!review.context().isBlank(),"Own pending review with context");
      admin.login("20260002@bjtu.edu.cn",PASSWORD);load(admin,ContentRequest.Query.MODERATION,"");admin.moderate(review.id(),"approve","");
      load(student,ContentRequest.Query.DISH_DETAIL,dish.id());check(student.reviews(dish.id()).stream().anyMatch(r -> r.content().equals(prefix+"评论")),"Approved review reaches public detail");
      check(student.rating(dish.id()).equals(snapshot),"Client uses server rating snapshot without averaging new comments");
      load(student,ContentRequest.Query.MY_REVIEWS,"");Review own=student.myReviews().stream().filter(r -> r.content().equals(prefix+"评论")).findFirst().orElseThrow();student.deleteReview(own.id());
      load(student,ContentRequest.Query.MY_REVIEWS,"");check(student.myReviews().stream().noneMatch(r -> r.id().equals(own.id())),"Own deletion reaches server");
      photo=new File(context.getCacheDir(),prefix+".jpg");Bitmap bitmap=Bitmap.createBitmap(64,36,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xffa25838);
      try(var out=new FileOutputStream(photo)) {bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}finally {bitmap.recycle();}
      String image=photo.toURI().toString();student.submitPhoto(dish.id(),image);load(student,ContentRequest.Query.MY_SUBMISSIONS,"");
      Submission submittedPhoto=student.submissions(student.currentUser().id()).stream().filter(s -> s.type().equals("photo")&&s.status().equals("pending")).findFirst().orElseThrow();
      check(student.loadImage(submittedPhoto.imageUrl()).length>0,"Owner reads private uploaded bytes through loopback");
      load(admin,ContentRequest.Query.MODERATION,"");admin.moderate(submittedPhoto.id(),"reject","照片模糊");
      load(student,ContentRequest.Query.MY_SUBMISSIONS,"");check(student.submissions(student.currentUser().id()).stream().anyMatch(s -> s.id().equals(submittedPhoto.id())&&s.reason().equals("照片模糊")),"Image rejection reason comes from DB");
      student.submitDish(stall.id(),prefix+"菜品","介绍",null,image);load(student,ContentRequest.Query.MY_SUBMISSIONS,"");
      Submission submittedDish=student.submissions(student.currentUser().id()).stream().filter(s -> s.name().equals(prefix+"菜品")).findFirst().orElseThrow();check(submittedDish.price()==null,"Nullable submission price preserved");
      load(admin,ContentRequest.Query.MODERATION,"");rejects(() -> admin.moderate(submittedDish.id(),"approve",""),400);admin.moderate(submittedDish.id(),"approve","",new BigDecimal("9.80"));
      load(student,ContentRequest.Query.DISHES,stall.id());check(student.dishes(stall.id()).stream().anyMatch(d -> d.name().equals(prefix+"菜品")&&d.price().compareTo(new BigDecimal("9.80"))==0),"Approved dish reaches catalog with completed price");
      student.submitStall(hall.id(),prefix+"档口","介绍",image);load(student,ContentRequest.Query.MY_SUBMISSIONS,"");
      Submission submittedStall=student.submissions(student.currentUser().id()).stream().filter(s -> s.name().equals(prefix+"档口")).findFirst().orElseThrow();
      load(admin,ContentRequest.Query.MODERATION,"");admin.moderate(submittedStall.id(),"approve","");load(student,ContentRequest.Query.STALLS,hall.id());
      check(student.stalls(hall.id()).stream().anyMatch(s -> s.name().equals(prefix+"档口")),"Approved stall reaches parent list");
      load(admin,ContentRequest.Query.AUDITS,"");check(admin.audits().stream().anyMatch(a -> a.submissionId().equals(submittedPhoto.id())),"Image audit is readable");
      platform.login("20260003@bjtu.edu.cn",PASSWORD);load(platform,ContentRequest.Query.ADMIN_USERS,"");
      String studentId=student.currentUser().id();platform.updateUser(studentId,"admin",true);load(student,ContentRequest.Query.PROFILE,"");check(student.currentUser().isAdmin(),"Existing session receives new server role");
      platform.updateUser(studentId,"user",true);load(student,ContentRequest.Query.PROFILE,"");check(!student.currentUser().isAdmin(),"Revoked role removes privilege");
      student.logout();check(student.currentUser()==null,"Logout clears memory");restored=repository(context,namespace);restored.restoreSession();check(restored.currentUser()==null,"Logout removes persisted session");
      admin.logout();platform.logout();
      report.putString("stream","Remote repository PASS: "+checks+" checks, real Backend at 127.0.0.1:8080\n");finish(0,report);
    } catch(Throwable failure) {
      StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));report.putString("stream","Remote repository FAIL after "+checks+" checks\n"+stack);finish(1,report);
    } finally {if(photo!=null) photo.delete();}
  }
}
