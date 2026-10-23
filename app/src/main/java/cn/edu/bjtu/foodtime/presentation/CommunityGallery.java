package cn.edu.bjtu.foodtime.presentation;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Paged native gallery. The full-screen viewer provides pinch, drag and accessible zoom buttons. */
final class CommunityGallery extends LinearLayout {
  private final MainActivity activity;
  private final List<String> images;
  private final LinearLayout strip;
  private final HorizontalScrollView viewport;
  private final TextView dots,previous,next;
  private int index,width;
  private float startX;
  private int startIndex;

  CommunityGallery(MainActivity activity,List<String> images){
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
      final int page=i;FrameLayout frame=(FrameLayout)Ui.image(activity,images.get(i),"食记图片 "+(i+1)+" / "+images.size()+"，点击放大",false);
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
  private void show(int page){index=Math.max(0,Math.min(images.size()-1,page));viewport.smoothScrollTo(index*width,0);update();}
  private void update(){
    StringBuilder value=new StringBuilder();for(int i=0;i<images.size();i++)value.append(i==index?"● ":"○ ");
    dots.setText(value.toString().trim());dots.setContentDescription("第"+(index+1)+"张，共"+images.size()+"张");previous.setEnabled(index>0);next.setEnabled(index<images.size()-1);
  }
  @SuppressLint("ClickableViewAccessibility") // Zoom/close/navigation have separate accessible buttons.
  private void open(int page){
    Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);
    FrameLayout screen=new FrameLayout(activity);screen.setBackgroundColor(Color.BLACK);
    FrameLayout photo=(FrameLayout)Ui.image(activity,images.get(page),"食记大图 "+(page+1),false);
    photo.setBackgroundColor(Color.BLACK);
    ImageView image=(ImageView)photo.getChildAt(0);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    screen.addView(photo,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
    float[] scale={1},last={0,0};
    ScaleGestureDetector detector=new ScaleGestureDetector(activity,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
      @Override public boolean onScale(ScaleGestureDetector gesture){scale[0]=Math.max(1,Math.min(5,scale[0]*gesture.getScaleFactor()));image.setScaleX(scale[0]);image.setScaleY(scale[0]);return true;}
    });
    photo.setOnTouchListener((view,event)->{
      detector.onTouchEvent(event);
      if(event.getActionMasked()==MotionEvent.ACTION_DOWN){last[0]=event.getX();last[1]=event.getY();}
      else if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!detector.isInProgress()&&scale[0]>1){image.setTranslationX(image.getTranslationX()+event.getX()-last[0]);image.setTranslationY(image.getTranslationY()+event.getY()-last[1]);last[0]=event.getX();last[1]=event.getY();}
      return true;
    });
    LinearLayout toolbar=Ui.row(activity);toolbar.setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12));
    toolbar.addView(Ui.button(activity,"关闭",false,dialog::dismiss));
    toolbar.addView(Ui.button(activity,"缩小",false,()->{scale[0]=Math.max(1,scale[0]/1.5f);image.setScaleX(scale[0]);image.setScaleY(scale[0]);if(scale[0]==1){image.setTranslationX(0);image.setTranslationY(0);}}));
    toolbar.addView(Ui.button(activity,"放大",false,()->{scale[0]=Math.min(5,scale[0]*1.5f);image.setScaleX(scale[0]);image.setScaleY(scale[0]);}));
    screen.addView(toolbar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
    LinearLayout navigation=Ui.row(activity);navigation.setGravity(Gravity.CENTER);
    if(page>0)navigation.addView(Ui.button(activity,"上一张",false,()->{dialog.dismiss();open(page-1);}));
    TextView progress=Ui.text(activity,(page+1)+" / "+images.size()+" · 双指缩放",13,Color.WHITE);progress.setPadding(Ui.dp(activity,12),0,Ui.dp(activity,12),0);navigation.addView(progress);
    if(page<images.size()-1)navigation.addView(Ui.button(activity,"下一张",false,()->{dialog.dismiss();open(page+1);}));
    screen.addView(navigation,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
    dialog.setContentView(screen);dialog.show();
  }
}
