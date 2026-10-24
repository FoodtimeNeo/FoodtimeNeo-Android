package cn.edu.bjtu.foodtime.presentation;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import cn.edu.bjtu.foodtime.data.remote.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Opt-in native-screen acceptance test using the real loopback Backend and app composition. */
public class CommunityUiRunner extends Instrumentation {
 private MainActivity activity;private int checks;private boolean evidenceOnly;private String userAccount,adminAccount,testPassword;
 private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 private void main(Runnable action){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
 private void await(BooleanSupplier condition,String message)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){AtomicReference<Boolean> ready=new AtomicReference<>(false);main(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(80);}throw new AssertionError("Timeout: "+message);}
 private View find(View root,String value,boolean description){if(description?value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()):root instanceof TextView text&&text.getText().toString().equals(value))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),value,description);if(found!=null)return found;}return null;}
 private View text(String value){return find(activity.root,value,false);}
 private void loaded(String route)throws Exception{await(()->activity.route().kind().equals(route)&&text("加载中…")==null&&text("正在连接…")==null&&!activity.operationInFlight(),route);}
 private void click(String value)throws Exception{await(()->text(value)!=null,value);main(()->{View view=text(value);while(view!=null&&!view.isClickable())view=view.getParent() instanceof View parent?parent:null;if(view==null)throw new AssertionError("Not clickable "+value);view.performClick();});}
 private void field(String label,String value){main(()->{View found=find(activity.root,label,true);if(!(found instanceof EditText edit))throw new AssertionError("Missing field "+label);edit.setText(value);});}
 private boolean hasButton(View root,String value){if(root instanceof TextView t&&t.getText().toString().equals(value)&&root.isClickable())return true;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasButton(group.getChildAt(i),value))return true;return false;}
 private void cardButton(String content,String button){main(()->{View scope=text(content);while(scope!=null&&!hasButton(scope,button))scope=scope.getParent() instanceof View parent?parent:null;if(scope==null)throw new AssertionError("Missing scoped button "+button);View target=find(scope,button,false);target.performClick();});}
 private void dialog(String label)throws Exception{long end=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<end){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(label))if(node.isClickable()&&label.contentEquals(node.getText())&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;Thread.sleep(80);}throw new AssertionError("Missing dialog button "+label);}
 private void capture(String name)throws Exception{waitForIdleSync();Thread.sleep(350);Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Screenshot unavailable");File folder=new File(getTargetContext().getFilesDir(),"community-ui-evidence");folder.mkdirs();try(var out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
 private String argument(Bundle args,String name){String value=args==null?null:args.getString(name);if(value==null||value.isBlank())throw new IllegalArgumentException("请通过 -e "+name+" 提供本地测试参数");return value;}
 @Override public void onCreate(Bundle args){userAccount=argument(args,"userAccount");adminAccount=argument(args,"adminAccount");testPassword=argument(args,"testPassword");evidenceOnly="true".equals(args.getString("evidenceOnly"));super.onCreate(args);start();}
 private void sampleEvidence(FoodtimeRepository real)throws Exception{
  main(()->{activity.selectTab("community");while(!activity.route().kind().equals("community"))activity.back();});loaded("community");capture("feed");
  CommunityPost sample=real.communityPosts().stream().filter(p->p.title().startsWith("社区联调样例：")).findFirst().orElse(null);
  if(sample!=null){
   click(sample.title());loaded("community-detail");
   if(sample.imageUrls().size()>1){
    click("›");check(find(activity.root,"第2张，共"+sample.imageUrls().size()+"张",true)!=null,"Native gallery advances to the next image");
    main(()->{View target=find(activity.root,"食记图片 2 / "+sample.imageUrls().size()+"，点击放大",true);while(target!=null&&!target.isClickable())target=target.getParent() instanceof View parent?parent:null;if(target==null)throw new AssertionError("Missing gallery image");target.performClick();});
    dialog("放大");dialog("缩小");dialog("关闭");check(true,"Full-screen zoom controls and close are usable");click("‹");
   }
   capture("sample-detail");main(()->activity.scroll.fullScroll(View.FOCUS_DOWN));capture("sample-replies");
  }
 }
 @Override public void onStart(){
  Bundle result=new Bundle();RemoteRepository administrator=null;String postId=null;List<File> files=new ArrayList<>();
  try{
   String base="http://127.0.0.1:8080/api/v1/",run=UUID.randomUUID().toString().substring(0,8),title="页面社区验收 "+run;
   if(!cn.edu.bjtu.foodtime.BuildConfig.DEBUG
       ||!cn.edu.bjtu.foodtime.BuildConfig.APPLICATION_ID.endsWith(".integration")
       ||!base.equals(cn.edu.bjtu.foodtime.BuildConfig.API_BASE_URL)
       ||cn.edu.bjtu.foodtime.BuildConfig.COMMUNITY_PROTOTYPE_ENABLED)
    throw new IllegalStateException("社区页面验收仅允许连接本机真实接口的独立 .integration 调试包");
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   await(()->text("正在连接…")==null,"application composition ready");
   FoodtimeRepository real=activity.repo;
   if(real.currentUser()!=null)real.logout();real.login(userAccount,testPassword);
   administrator=new RemoteRepository(base,new HttpTransport(base),false);administrator.login(adminAccount,testPassword);
   String authorName=real.currentUser().displayName(),adminName=administrator.currentUser().displayName();
   main(activity::restore);
   await(()->text("正在连接…")==null&&activity.repo.currentUser()!=null,"real app startup");
   if(evidenceOnly){sampleEvidence(real);result.putString("stream","Community gallery PASS: "+checks+" checks; UI evidence refreshed without content writes\n");finish(-1,result);return;}
   main(()->activity.selectTab("community"));loaded("community");
   main(()->activity.go("community-create",""));loaded("community-create");field("食记标题",title);field("动态正文","真实页面多图与审核验收 "+run);
   List<Uri> photos=new ArrayList<>();for(int i=0;i<2;i++){File file=new File(getTargetContext().getCacheDir(),"community-ui-"+run+"-"+i+".jpg");files.add(file);Bitmap image=Bitmap.createBitmap(i==0?64:40,i==0?40:64,Bitmap.Config.ARGB_8888);image.eraseColor(i==0?0xffb56535:0xff5c744c);try(var out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.JPEG,90,out);}finally{image.recycle();}photos.add(Uri.fromFile(file));}
   main(()->{activity.pickingKey=activity.draftKey("photos");ClipData clip=ClipData.newRawUri("验收照片",photos.get(0));clip.addItem(new ClipData.Item(photos.get(1)));Intent selected=new Intent();selected.setClipData(clip);activity.onActivityResult(102,Activity.RESULT_OK,selected);});
   await(()->!activity.drafts.getString(activity.draftKey("photo:1:image"),"").isBlank()&&!activity.operationInFlight(),"two photos imported");
   String first=activity.drafts.getString(activity.draftKey("photo:0:image"));click("前移");check(!first.equals(activity.drafts.getString(activity.draftKey("photo:0:image"))),"Native cover reorder changes actual draft order");capture("create");
   click("盖章发布");loaded("community");main(()->activity.go("community-mine",""));loaded("community-mine");
   CommunityPost post=real.communityCollection(false).items().stream().filter(p->p.title().equals(title)).findFirst().orElseThrow();postId=post.id();String id=postId;
   check(post.status().equals("pending")&&post.images().size()==2&&post.images().get(0).width()==40,"Native form sends title, ordered photos and real pending state");
   click(title);loaded("community-detail");check(text("这篇食记正在审核，通过前仅自己可见。")!=null,"Pending detail is labelled private");check(find(activity.root,"评论内容",true)==null,"Pending post has no public comment composer");
   administrator.moderateCommunity("posts",id,"approve","");main(activity::render);loaded("community-detail");
   field("评论内容","页面一级评论 "+run);click("提交评论");loaded("community-detail");
   CommunityComment root=real.communityRoots(id).items().stream().filter(c->c.content().equals("页面一级评论 "+run)).findFirst().orElseThrow();check(root.status().equals("pending"),"Native submitted comment stays pending until approved");
   administrator.moderateCommunity("comments",root.id(),"approve","");administrator.submitCommunityComment(id,"另一位同学的楼内回复 "+run,root.id(),root.id(),UUID.randomUUID().toString());administrator.refresh(new ContentRequest(ContentRequest.Query.COMMUNITY_DETAIL,id));
   CommunityComment other=administrator.communityReplies(root.id()).items().stream().filter(c->c.content().contains(run)).findFirst().orElseThrow();administrator.moderateCommunity("comments",other.id(),"approve","");main(activity::render);loaded("community-detail");
   String otherText="回复 "+authorName+"：另一位同学的楼内回复 "+run;cardButton(otherText,"回复");
   check(activity.drafts.getString(activity.draftKey("replyRoot")).equals(root.id())&&activity.drafts.getString(activity.draftKey("replyTarget")).equals(other.id()),"Native reply selection carries root and actual target IDs");
   field("评论内容","页面互相回复 "+run);click("提交回复");loaded("community-detail");
   CommunityComment nested=real.communityReplies(root.id()).items().stream().filter(c->c.content().equals("页面互相回复 "+run)).findFirst().orElseThrow();check(nested.replyToUserId().equals(administrator.currentUser().id()),"Reply targets the other author on the server");administrator.moderateCommunity("comments",nested.id(),"approve","");main(activity::render);loaded("community-detail");
   int[] firstPosition={0,0},nestedPosition={0,0};main(()->{text(otherText).getLocationOnScreen(firstPosition);text("回复 "+adminName+"：页面互相回复 "+run).getLocationOnScreen(nestedPosition);});check(firstPosition[0]==nestedPosition[0],"Both replies have exactly one UI indentation level");
   cardButton(otherText,"♡ 0");await(()->real.communityReplies(root.id()).items().stream().anyMatch(c->c.id().equals(other.id())&&c.likedByMe()&&c.likeCount()==1),"native reply like");check(true,"Reply like updates from server count");capture("replies");
   cardButton("页面一级评论 "+run,"删除");dialog("删除");loaded("community-detail");check(real.communityRoots(id).items().stream().anyMatch(c->c.id().equals(root.id())&&c.deleted()&&c.replyCount()==2),"Root deletion keeps tombstone and children in native screen");capture("deleted-root");
   real.logout();real.login(adminAccount,testPassword);main(activity::restore);await(()->activity.repo.currentUser()!=null&&activity.repo.currentUser().isAdmin()&&text("正在连接…")==null,"admin session");
   main(()->activity.selectTab("admin"));loaded("admin");main(()->activity.go("admin-community","posts~approved~1"));loaded("admin-community");
   cardButton(title,"查看完整内容与配图");loaded("admin-community-detail");check(real.communityAuditDetail().post().id().equals(id)&&real.communityAuditDetail().post().images().size()==2,"Native moderation reads complete selected post");capture("admin-full-post");field("下架原因","页面验收完成，清理本次测试内容");click("下架违规内容");loaded("admin-community");postId=null;check(real.communityAudit().items().stream().noneMatch(s->s.id().equals(id)),"Native reasoned removal updates real audit queue");
   real.logout();real.login(userAccount,testPassword);main(activity::restore);await(()->activity.repo.currentUser()!=null&&!activity.repo.currentUser().isAdmin()&&text("正在连接…")==null,"restore ordinary account");main(()->{activity.selectTab("community");while(!activity.route().kind().equals("community"))activity.back();});loaded("community");capture("feed");
   CommunityPost sample=real.communityPosts().stream().filter(p->p.title().startsWith("社区联调样例：")).findFirst().orElse(null);if(sample!=null){click(sample.title());loaded("community-detail");capture("sample-detail");main(()->activity.scroll.fullScroll(View.FOCUS_DOWN));capture("sample-replies");}
   result.putString("stream","Community native UI PASS: "+checks+" checks\n");finish(-1,result);
  }catch(Throwable error){StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));result.putString("stream","Community native UI FAIL after "+checks+" checks\n"+stack);finish(1,result);}
  finally{if(administrator!=null&&postId!=null)try{administrator.removeCommunityAsAdmin("posts",postId,"页面验收失败后清理本次测试内容");}catch(RuntimeException ignored){}if(administrator!=null)try{administrator.logout();}catch(RuntimeException ignored){}for(File file:files)file.delete();}
 }
}
