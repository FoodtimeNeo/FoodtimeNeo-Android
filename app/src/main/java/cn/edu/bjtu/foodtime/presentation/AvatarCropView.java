package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import cn.edu.bjtu.foodtime.domain.validation.AvatarCropGeometry;

/** Fixed square selection. Drag/pinch the image; the exported image uses the same transform. */
final class AvatarCropView extends View {
  private final Bitmap image;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  private final ScaleGestureDetector pinch;private Runnable changed=()->{};
  private float zoom=1,panX,panY,lastX,lastY;private int rotation;private boolean dragging;
  AvatarCropView(Context context,Bitmap image){
    super(context);this.image=image;setFocusable(true);setContentDescription("1:1方形头像裁剪预览，可拖动图片和双指缩放，键盘方向键可微调");
    pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){setZoom(zoom*detector.getScaleFactor());return true;}});
  }
  float zoom(){return zoom;}float panX(){return panX;}float panY(){return panY;}int rotation(){return rotation;}
  void setChanged(Runnable changed){this.changed=changed;}
  void restore(float zoom,float x,float y,int rotation){this.zoom=Math.max(1,Math.min(4,zoom));panX=x;panY=y;this.rotation=rotation;invalidate();}
  void setZoom(float value){zoom=Math.max(1,Math.min(4,value));update();}
  void rotate(){rotation=(rotation+90)%360;panX=0;panY=0;update();}
  void reset(){zoom=1;rotation=0;panX=0;panY=0;update();}
  private AvatarCropGeometry.Layout layout(){return AvatarCropGeometry.calculate(image.getWidth(),image.getHeight(),getWidth(),getHeight(),rotation,zoom,panX,panY);}
  private void update(){if(getWidth()>32&&getHeight()>32){var g=layout();panX=g.panX()/g.side();panY=g.panY()/g.side();}invalidate();changed.run();}
  private void paintImage(Canvas canvas,AvatarCropGeometry.Layout g){
    paint.setAlpha(255);
    canvas.save();canvas.translate(getWidth()/2f+g.panX(),getHeight()/2f+g.panY());canvas.rotate(rotation);canvas.scale(g.scale(),g.scale());
    canvas.drawBitmap(image,-image.getWidth()/2f,-image.getHeight()/2f,paint);canvas.restore();
  }
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);if(image.isRecycled()||getWidth()<=32||getHeight()<=32)return;
    var g=layout();canvas.drawColor(Design.PAPER);paintImage(canvas,g);
    paint.setStyle(Paint.Style.FILL);paint.setColor(0x99000000);
    canvas.drawRect(0,0,getWidth(),g.top(),paint);canvas.drawRect(0,g.top()+g.side(),getWidth(),getHeight(),paint);
    canvas.drawRect(0,g.top(),g.left(),g.top()+g.side(),paint);canvas.drawRect(g.left()+g.side(),g.top(),getWidth(),g.top()+g.side(),paint);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setColor(Color.WHITE);canvas.drawRect(g.left(),g.top(),g.left()+g.side(),g.top()+g.side(),paint);
    paint.setColor(0x80ffffff);paint.setStrokeWidth(Ui.dp(getContext(),1));
    for(int n=1;n<=2;n++){float d=g.side()*n/3;canvas.drawLine(g.left()+d,g.top(),g.left()+d,g.top()+g.side(),paint);canvas.drawLine(g.left(),g.top()+d,g.left()+g.side(),g.top()+d,paint);}paint.setStyle(Paint.Style.FILL);
  }
  @Override public boolean onTouchEvent(MotionEvent event){
    pinch.onTouchEvent(event);
    switch(event.getActionMasked()){
      case MotionEvent.ACTION_DOWN->{getParent().requestDisallowInterceptTouchEvent(true);lastX=event.getX();lastY=event.getY();dragging=true;return true;}
      case MotionEvent.ACTION_MOVE->{if(dragging&&!pinch.isInProgress()){var g=layout();panX+=(event.getX()-lastX)/g.side();panY+=(event.getY()-lastY)/g.side();update();}lastX=event.getX();lastY=event.getY();return true;}
      case MotionEvent.ACTION_POINTER_UP->{lastX=event.getX();lastY=event.getY();return true;}
      case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{dragging=false;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}
      default->{return true;}
    }
  }
  @Override public boolean performClick(){super.performClick();return true;}
  @Override public boolean onKeyDown(int code,KeyEvent event){
    float dx=code==KeyEvent.KEYCODE_DPAD_LEFT?-.02f:code==KeyEvent.KEYCODE_DPAD_RIGHT?.02f:0;
    float dy=code==KeyEvent.KEYCODE_DPAD_UP?-.02f:code==KeyEvent.KEYCODE_DPAD_DOWN?.02f:0;
    if(dx!=0||dy!=0){panX+=dx;panY+=dy;update();return true;}return super.onKeyDown(code,event);
  }
  Bitmap export(){
    var g=layout();Bitmap result=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.drawColor(Design.PAPER);
    canvas.scale(512/g.side(),512/g.side());canvas.translate(-g.left(),-g.top());paintImage(canvas,g);return result;
  }
  @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(!image.isRecycled())image.recycle();}
}
