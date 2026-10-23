package cn.edu.bjtu.foodtime.data.remote;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Opt-in real-device community contract test; runs only against the local manual-test Backend. */
public class CommunityRepositoryRunner extends Instrumentation {
 private static final String BASE="http://127.0.0.1:8080/api/v1/";
 private int checks;
 private String userAccount,adminAccount,superadminAccount,testPassword;
 private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 private RemoteRepository repository(Context context,String suffix){return new RemoteRepository(BASE,new HttpTransport(BASE,new CookieManager(new EncryptedCookieStore(context,BASE,"community-e2e-"+suffix),CookiePolicy.ACCEPT_ORIGINAL_SERVER)),false);}
 private void load(FoodtimeRepository r,ContentRequest.Query query,String id){r.refresh(new ContentRequest(query,id));}
 private void rejects(Runnable action,int status){try{action.run();throw new AssertionError("Expected "+status);}catch(RepositoryException error){check(error.status()==status,"Expected "+status+", got "+error.status());}}
 private String argument(Bundle args,String name){String value=args==null?null:args.getString(name);if(value==null||value.isBlank())throw new IllegalArgumentException("请通过 -e "+name+" 提供本地测试参数");return value;}
 @Override public void onCreate(Bundle args){userAccount=argument(args,"userAccount");adminAccount=argument(args,"adminAccount");superadminAccount=argument(args,"superadminAccount");testPassword=argument(args,"testPassword");super.onCreate(args);start();}
 @Override public void onStart(){
  Bundle report=new Bundle();List<File> files=new ArrayList<>();RemoteRepository author=null,admin=null,viewer=null;String postId=null;
  try{
   Context context=getTargetContext();String run=UUID.randomUUID().toString();String title="设备社区验收 "+run.substring(0,8);
   author=repository(context,run);admin=repository(context,run+"-admin");viewer=repository(context,run+"-viewer");
   author.login(userAccount,testPassword);admin.login(adminAccount,testPassword);viewer.login(superadminAccount,testPassword);
   check(author.currentUser().role().equals("user")&&admin.currentUser().role().equals("admin")&&viewer.currentUser().role().equals("superadmin"),"Three server roles verified");
   List<String> images=new ArrayList<>();
   for(int i=0;i<2;i++){
    File photo=new File(context.getCacheDir(),"community-"+run+"-"+i+".jpg");files.add(photo);
    Bitmap bitmap=Bitmap.createBitmap(i==0?64:40,i==0?40:64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(i==0?0xffa25838:0xff507450);
    try(var out=new FileOutputStream(photo)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}finally{bitmap.recycle();}images.add(photo.toURI().toString());
   }
   String request=UUID.randomUUID().toString();author.submitCommunityPost(title,"真实 Android 多图发布验收",images,request);author.submitCommunityPost(title,"真实 Android 多图发布验收",images,request);
   load(author,ContentRequest.Query.MY_COMMUNITY_POSTS,"");List<CommunityPost> mine=author.communityCollection(false).items();
   check(mine.stream().filter(p->p.title().equals(title)).count()==1,"Retried post is deduplicated");
   CommunityPost post=mine.stream().filter(p->p.title().equals(title)).findFirst().orElseThrow();postId=post.id();
   check(post.status().equals("pending")&&post.reviewStage().equals("manual_pending"),"Disabled AI routes to manual queue");
   check(post.images().size()==2&&post.images().get(0).width()==64&&post.images().get(1).height()==64,"Ordered real image dimensions preserved");
   final RemoteRepository guest=viewer;final String id=postId;rejects(()->load(guest,ContentRequest.Query.COMMUNITY_DETAIL,id),404);
   load(admin,ContentRequest.Query.COMMUNITY_MODERATION_DETAIL,"posts~"+id);check(admin.communityAuditDetail().post().imageUrls().size()==2,"Admin reads every pending image");
   admin.moderateCommunity("posts",id,"approve","");load(author,ContentRequest.Query.COMMUNITY_DETAIL,id);load(viewer,ContentRequest.Query.COMMUNITY_FEED,"");
   check(viewer.communityPosts().stream().anyMatch(p->p.id().equals(id)),"Approved Android post enters real public feed");
   author.setCommunityLike("post",id,true);check(author.setCommunityLike("post",id,true).count()==1,"Repeated PUT like has one count");
   viewer.setCommunityFavorite(id,true);check(viewer.setCommunityFavorite(id,true).count()==1,"Repeated favorite has one count");
   load(viewer,ContentRequest.Query.MY_COMMUNITY_FAVORITES,"");check(viewer.communityCollection(true).items().stream().anyMatch(p->p.id().equals(id)),"Favorites collection uses server data");
   author.submitCommunityComment(id,"设备一级评论 "+run,"","",UUID.randomUUID().toString());load(author,ContentRequest.Query.COMMUNITY_DETAIL,id);
   CommunityComment root=author.communityRoots(id).items().stream().filter(c->c.content().contains(run)).findFirst().orElseThrow();
   check(root.status().equals("pending"),"Own pending comment is visible to author");load(viewer,ContentRequest.Query.COMMUNITY_DETAIL,id);check(viewer.communityRoots(id).items().isEmpty(),"Other users cannot read pending comment");
   admin.moderateCommunity("comments",root.id(),"approve","");
   String replyRequest=UUID.randomUUID().toString();viewer.submitCommunityComment(id,"设备楼内回复 "+run,root.id(),root.id(),replyRequest);viewer.submitCommunityComment(id,"设备楼内回复 "+run,root.id(),root.id(),replyRequest);
   load(viewer,ContentRequest.Query.COMMUNITY_DETAIL,id);CommunityComment first=viewer.communityReplies(root.id()).items().stream().filter(c->c.content().contains(run)).findFirst().orElseThrow();
   admin.moderateCommunity("comments",first.id(),"approve","");
   author.submitCommunityComment(id,"设备互相回复 "+run,root.id(),first.id(),UUID.randomUUID().toString());load(author,ContentRequest.Query.COMMUNITY_DETAIL,id);
   CommunityComment second=author.communityReplies(root.id()).items().stream().filter(c->c.replyToCommentId().equals(first.id())).findFirst().orElseThrow();
   check(second.rootCommentId().equals(root.id())&&second.replyToUserId().equals(viewer.currentUser().id()),"Flat replies preserve actual reply target");
   admin.moderateCommunity("comments",second.id(),"approve","");viewer.setCommunityLike("comment",second.id(),true);check(viewer.setCommunityLike("comment",second.id(),true).count()==1,"Reply like is idempotent");
   load(author,ContentRequest.Query.COMMUNITY_DETAIL,id);check(author.communityPost(id).commentCount()==3,"Post count includes approved roots and replies");
   author.deleteCommunityComment(root.id());load(viewer,ContentRequest.Query.COMMUNITY_DETAIL,id);CommunityComment tombstone=viewer.communityRoots(id).items().get(0);
   check(tombstone.deleted()&&tombstone.content().isEmpty()&&tombstone.replyCount()==2,"Root deletion retains tombstone and two legal replies");
   check(viewer.communityPost(id).commentCount()==2&&viewer.communityReplies(root.id()).items().size()==2,"Deletion counters and replies agree");
   rejects(()->guest.submitCommunityComment(id,"不能追加",root.id(),first.id(),UUID.randomUUID().toString()),409);
   admin.removeCommunityAsAdmin("posts",id,"设备验收结束，清理本次测试内容");postId=null;
   rejects(()->load(guest,ContentRequest.Query.COMMUNITY_DETAIL,id),404);rejects(()->guest.setCommunityLike("comment",second.id(),true),404);
   load(viewer,ContentRequest.Query.MY_COMMUNITY_FAVORITES,"");check(viewer.communityCollection(true).items().stream().noneMatch(p->p.id().equals(id)),"Hidden posts disappear from favorites");
   report.putString("stream","Community repository PASS: "+checks+" checks\n");finish(-1,report);
  }catch(Throwable failure){StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));report.putString("stream","Community repository FAIL after "+checks+" checks\n"+stack);finish(1,report);}
  finally{
   if(admin!=null&&postId!=null)try{admin.removeCommunityAsAdmin("posts",postId,"设备验收失败后清理本次测试内容");}catch(RuntimeException ignored){}
   for(File file:files)file.delete();
   for(RemoteRepository r:Arrays.asList(author,admin,viewer))if(r!=null)try{r.logout();}catch(RuntimeException ignored){}
  }
 }
}
