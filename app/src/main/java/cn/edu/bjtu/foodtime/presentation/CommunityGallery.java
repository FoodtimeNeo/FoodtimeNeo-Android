package cn.edu.bjtu.foodtime.presentation;

import android.annotation.SuppressLint;
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
  private final TextView dots,previous,next;
  private int index,width;
  private float startX;
  private int startIndex;
  private CommunityPost post;
  private CommunityPhotoDialog.PostActions postActions;
  private CommunityPhotoDialog viewer;

  CommunityGallery(MainActivity activity,List<String> images){
    this(activity, images, List.of());
  }

  CommunityGallery(MainActivity activity, List<String> images, List<CommunityImage> dimensions) {
    super(activity);this.activity=activity;this.images=List.copyOf(images);setOrientation(VERTICAL);
    if(images.isEmpty()){addView(Ui.text(activity,"这篇食记没有配图",14,Design.MUTED));strip=null;viewport=null;dots=null;previous=null;next=null;return;}
    strip=Ui.row(activity);
    viewport=new HorizontalScrollView(activity){
      @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);width=w;
        for(int i=0;i<strip.getChildCount();i++)strip.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(w,-2));
        post(()->scrollTo(index*w,0));
      }
      @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getX();startIndex=index;}
        return super.onInterceptTouchEvent(event);
      }
      @SuppressLint("ClickableViewAccessibility") // Each image has a click action; navigation also has named buttons.
      @Override public boolean onTouchEvent(MotionEvent event){
        boolean handled=super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP){float dx=event.getX()-startX;show(Math.abs(dx)>Ui.dp(activity,40)?startIndex+(dx<0?1:-1):width==0?0:Math.round((float)getScrollX()/width));}
        return handled;
      }
      @Override public void fling(int velocity){show(index+(velocity>300?1:velocity< -300?-1:0));}
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
    previous=Ui.button(activity,"‹",false,()->show(index-1));next=Ui.button(activity,"›",false,()->show(index+1));
    previous.setContentDescription("上一张图片");next.setContentDescription("下一张图片");
    dots=Ui.text(activity,"",12,Design.RED);dots.setGravity(Gravity.CENTER);dots.setSingleLine(true);
    controls.addView(previous);controls.addView(dots,new LinearLayout.LayoutParams(0,-2,1));controls.addView(next);
    if(images.size()>1)addView(controls);update();
  }
  /** Open a single full-size image without loading it in the preview list. */
  static void openFull(MainActivity activity,String image){
    new CommunityPhotoDialog(activity,List.of(image),0,false,null,null,index -> {}).show();
  }

  void setPost(CommunityPost value) { post = value; }

  void setPostActions(CommunityPhotoDialog.PostActions value) { postActions = value; }

  @Override protected void onDetachedFromWindow() {
    if (viewer != null) viewer.dismiss();
    viewer = null;
    super.onDetachedFromWindow();
  }

  private void show(int page){index=Math.max(0,Math.min(images.size()-1,page));viewport.smoothScrollTo(index*width,0);update();}
  private void update(){
    StringBuilder value=new StringBuilder();for(int i=0;i<images.size();i++)value.append(i==index?"● ":"○ ");
    dots.setText(value.toString().trim());dots.setContentDescription("第"+(index+1)+"张，共"+images.size()+"张");previous.setEnabled(index>0);next.setEnabled(index<images.size()-1);
  }
  private void open(int page) {
    show(page);
    viewer = new CommunityPhotoDialog(activity, images, page, true, post, postActions, this::show);
    viewer.show();
  }
}
