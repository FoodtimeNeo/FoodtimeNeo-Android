package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real production controls and loopback HTTP only; never installs as the original application. */
public final class FavoriteGroupsRunner extends Instrumentation {
  private MainActivity activity;private String postId,mode;private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);postId=args.getString("postId","");mode=args.getString("mode","favorites");start();}
  private void ui(Runnable action){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void await(String label,BooleanSupplier condition){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(70);}throw new AssertionError("Timeout: "+label);}
  private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
  private AccessibilityNodeInfo text(String value){var root=getUiAutomation().getRootInActiveWindow();if(root==null)return null;for(var node:root.findAccessibilityNodeInfosByText(value))if(value.contentEquals(node.getText())&&node.isVisibleToUser())return node;return null;}
  private void click(String label){long end=SystemClock.uptimeMillis()+10000;AccessibilityNodeInfo node=null;while(SystemClock.uptimeMillis()<end&&(node=text(label))==null)SystemClock.sleep(70);if(node==null)throw new AssertionError("Missing control: "+label);Rect box=new Rect();node.getBoundsInScreen(box);long time=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,box.centerX(),box.centerY(),0));sendPointerSync(MotionEvent.obtain(time,SystemClock.uptimeMillis()+30,MotionEvent.ACTION_UP,box.centerX(),box.centerY(),0));waitForIdleSync();}
  private static View find(View root,Class<?> type){if(type.isInstance(root))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View value=find(group.getChildAt(i),type);if(value!=null)return value;}return null;}
  private AccessibilityNodeInfo editable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isEditable())return node;for(int i=0;i<node.getChildCount();i++){var value=editable(node.getChild(i));if(value!=null)return value;}return null;}
  private void name(String value){var input=editable(getUiAutomation().getRootInActiveWindow());if(input==null)throw new AssertionError("No group name field");Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"Name field accepts input");}
  private void gallery() {
    ui(()->{activity.selectTab("community");activity.go("community-detail",postId);});
    await("post gallery",()->find(activity.content,CommunityGallery.class)!=null);
    for(int round=0;round<3;round++){
      if(round>0)ui(activity::redraw);
      await("decoded initial image",()->{
        View gallery=find(activity.content,CommunityGallery.class);if(gallery==null)return false;
        ImageView image=(ImageView)find(gallery,ImageView.class);return image!=null&&image.getDrawable()!=null&&image.getWidth()>0;
      });
      ui(()->{
        View gallery=find(activity.content,CommunityGallery.class);HorizontalScrollView viewport=(HorizontalScrollView)find(gallery,HorizontalScrollView.class);
        ViewGroup strip=(ViewGroup)viewport.getChildAt(0);ImageView image=(ImageView)find(strip.getChildAt(0),ImageView.class);
        check(strip.getChildAt(0).getWidth()==viewport.getWidth(),"First post image fills its viewport before any interaction");
        check(image.getWidth()==viewport.getWidth()&&image.getLeft()==0,"Decoded/cached image has no horizontal gap");
        check(viewport.getScrollX()==0,"Reopening cached detail has no offset");
      });
    }
    for(int round=0;round<4;round++){
      final int iteration=round;
      ui(()->{
        LinearLayout holder=Ui.col(activity);CommunityGallery gallery=new CommunityGallery(activity,java.util.List.of("",""));holder.addView(gallery,new LinearLayout.LayoutParams(-1,-2));
        int width=iteration%2==0?activity.content.getWidth():Ui.dp(activity,280);
        holder.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.AT_MOST));holder.layout(0,0,width,holder.getMeasuredHeight());
        HorizontalScrollView viewport=(HorizontalScrollView)find(gallery,HorizontalScrollView.class);ViewGroup strip=(ViewGroup)viewport.getChildAt(0);
        for(int i=0;i<strip.getChildCount();i++)check(strip.getChildAt(i).getMeasuredWidth()==viewport.getWidth(),"First layout frame equals viewport: "+strip.getChildAt(i).getMeasuredWidth()+" / "+viewport.getWidth());
        check(viewport.getScrollX()==0,"First layout has no initial offset");
      });
    }
  }
  private void favorites(){
    ui(()->{activity.selectTab("community");activity.go("community-favorites","");});
    await("favorites entry",()->text("+ 新建分组")!=null);check(text("默认分组")!=null,"Default group exists without custom groups");
    click("默认分组");click("+ 新建分组");name("午饭食记");click("新建");await("created post group",()->text("午饭食记 (0)")!=null);checks++;
    ui(()->activity.go("community-detail",postId));await("post detail",()->find(activity.content,CommunityGallery.class)!=null);
    click("分组");await("membership dialog",()->text("设置收藏分组")!=null);check(text("默认分组（未加入自建分组）")!=null,"Default membership is selectable");
    click("午饭食记");click("保存");await("membership saved",()->text("设置收藏分组")==null);
    click("分组");await("persisted choice",()->text("午饭食记")!=null);check(text("午饭食记").isChecked(),"Custom membership persisted after save and reopen");
    click("默认分组（未加入自建分组）");click("保存");await("default saved",()->text("设置收藏分组")==null);
    click("分组");await("default persisted",()->text("午饭食记")!=null);check(!text("午饭食记").isChecked(),"Moving to default removes the custom relation");
    check(text("默认分组（未加入自建分组）").isChecked(),"Default selected after reopen");
    click("+ 新建分组");name("晚饭食记");click("新建");await("created inline",()->text("晚饭食记")!=null);checks++;
    click("取消");ui(()->activity.go("favorite-dishes",""));
    await("dish favorites",()->text("+ 新建分组")!=null&&text("默认分组")!=null);check(text("默认分组")!=null,"Dish default group is separate");
    click("+ 新建分组");name("早餐菜品");click("新建");await("dish group created",()->text("早餐菜品 (0)")!=null);checks++;
    check(text("午饭食记 (0)")==null,"Post custom groups do not leak into dish favorites");
  }
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".favoritesreview")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18114/api/v1/"))throw new AssertionError("Only isolated local build allowed");
    activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await("local session",()->activity.repo.currentUser()!=null&&!activity.isPageLoading());
    if(mode.equals("gallery"))gallery();else favorites();
    result.putString("stream","Favorites/photo PASS "+checks+" checks ("+mode+")\n");finish(-1,result);
  }catch(Throwable error){result.putString("stream","Favorites/photo FAIL "+android.util.Log.getStackTraceString(error));finish(1,result);}finally{if(activity!=null)ui(activity::finish);}}
}
