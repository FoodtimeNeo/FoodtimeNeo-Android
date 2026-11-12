package cn.edu.bjtu.foodtime.presentation;

import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.animation.PathInterpolator;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityImage;
import java.util.List;

/** Inline thumbnails and navigation. The dialog owns the full-screen image lifecycle. */
final class CommunityGallery extends LinearLayout {
  private final MainActivity activity;
  private final List<String> images;
  private final LinearLayout strip;
  private final HorizontalScrollView viewport;
  private final TextView dots;
  private int index,width;
  private float startX;
  private int startIndex;
  private ValueAnimator scrollAnimation;
  private CommunityPost post;
  private CommunityPhotoDialog.PostActions postActions;
  private CommunityPhotoDialog viewer;

  CommunityGallery(MainActivity activity,List<String> images){
    this(activity, images, List.of());
  }

  CommunityGallery(MainActivity activity, List<String> images, List<CommunityImage> dimensions) {
    super(activity);this.activity=activity;this.images=List.copyOf(images);setOrientation(VERTICAL);
    if(images.isEmpty()){addView(Ui.text(activity,"这篇食记没有配图",14,Design.MUTED));strip=null;viewport=null;dots=null;return;}
    strip=Ui.row(activity);
    viewport=new HorizontalScrollView(activity){
      @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        // Image decoding can change height during a drag; only width changes require re-paging.
        if(w==oldw)return;
        width=w;
        stopAnimation();
        for(int i=0;i<strip.getChildCount();i++)strip.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(w,-2));
        post(()->scrollTo(index*width,0));
      }
      @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
          stopAnimation();startX=event.getX();startIndex=width==0?index:Math.round((float)getScrollX()/width);
        }
        return super.onInterceptTouchEvent(event);
      }
      @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){
          if(index+1>=images.size())return false;
          show(index+1);return true;
        }
        if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){
          if(index==0)return false;
          show(index-1);return true;
        }
        return super.performAccessibilityAction(action,args);
      }
      @SuppressLint("ClickableViewAccessibility") // Images and the page selector have named click actions.
      @Override public boolean onTouchEvent(MotionEvent event){
        boolean handled=super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP){float dx=event.getX()-startX;show(Math.abs(dx)>Ui.dp(activity,40)?startIndex+(dx<0?1:-1):width==0?0:Math.round((float)getScrollX()/width));}
        else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)show(startIndex);
        return handled;
      }
      // ACTION_UP owns the single snap animation; native fling must not start another one.
      @Override public void fling(int velocity) {}
    };
    viewport.setHorizontalScrollBarEnabled(false);viewport.setFillViewport(true);viewport.addView(strip,new HorizontalScrollView.LayoutParams(-2,-2));
    addView(viewport,new LinearLayout.LayoutParams(-1,-2));
    int initial=activity.getResources().getDisplayMetrics().widthPixels-Ui.dp(activity,48);
    for(int i=0;i<images.size();i++){
      final int page=i;
      String label="食记图片 "+(i+1)+" / "+images.size()+"，点击放大";
      FrameLayout frame=(FrameLayout)Ui.communityDetailImage(activity,Ui.thumb(images.get(i)),label,
          i < dimensions.size() ? dimensions.get(i) : null);
      frame.setTag("community-gallery-image:" + i);
      ((ImageView)frame.getChildAt(0)).setScaleType(ImageView.ScaleType.FIT_CENTER);
      frame.setClickable(true);frame.setFocusable(true);frame.setOnClickListener(v->open(page));
      strip.addView(frame,new LinearLayout.LayoutParams(initial,-2));
    }
    LinearLayout controls=Ui.row(activity);
    dots=Ui.text(activity,"",12,Design.RED);dots.setGravity(Gravity.CENTER);dots.setSingleLine(true);
    dots.setMinHeight(Ui.dp(activity,44));dots.setFocusable(true);dots.setClickable(true);
    dots.setOnClickListener(view->{
      PopupMenu menu=new PopupMenu(activity,dots);
      for(int i=0;i<images.size();i++){
        final int selected=i;
        menu.getMenu().add("第"+(i+1)+"张图片").setOnMenuItemClickListener(item->{show(selected);return true;});
      }
      menu.show();
    });
    controls.addView(dots,new LinearLayout.LayoutParams(0,-2,1));
    if(images.size()>1)addView(controls);update();
  }
  /** Open a single full-size image without loading it in the preview list. */
  static void openFull(MainActivity activity,String image){
    new CommunityPhotoDialog(activity,List.of(image),0,false,null,null,index -> {}).show();
  }

  void setPost(CommunityPost value) { post = value; }

  void setPostActions(CommunityPhotoDialog.PostActions value) { postActions = value; }

  @Override protected void onDetachedFromWindow() {
    stopAnimation();
    if (viewer != null) viewer.dismiss();
    viewer = null;
    super.onDetachedFromWindow();
  }

  private void stopAnimation(){if(scrollAnimation!=null){scrollAnimation.cancel();scrollAnimation=null;}}
  private void show(int page){
    index=Math.max(0,Math.min(images.size()-1,page));stopAnimation();
    int target=index*width;
    if(width>0&&viewport.getScrollX()!=target){
      scrollAnimation=ValueAnimator.ofInt(viewport.getScrollX(),target);
      scrollAnimation.setDuration(280);
      scrollAnimation.setInterpolator(new PathInterpolator(.22f,.68f,0,1));
      scrollAnimation.addUpdateListener(animation->viewport.scrollTo((int)animation.getAnimatedValue(),0));
      scrollAnimation.start();
    }
    update();
  }
  private void update(){
    StringBuilder value=new StringBuilder();for(int i=0;i<images.size();i++)value.append(i==index?"● ":"○ ");
    dots.setText(value.toString().trim());dots.setContentDescription("第"+(index+1)+"张，共"+images.size()+"张，点按选择图片");
  }
  private void open(int page) {
    show(page);
    viewer = new CommunityPhotoDialog(activity, images, page, true, post, postActions, this::show);
    viewer.show();
  }
}
