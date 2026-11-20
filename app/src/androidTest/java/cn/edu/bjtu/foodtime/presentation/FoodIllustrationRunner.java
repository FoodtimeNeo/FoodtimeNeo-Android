package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import cn.edu.bjtu.foodtime.BuildConfig;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

public final class FoodIllustrationRunner extends Instrumentation {
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".campusreview"))throw new AssertionError("Isolated build required");
    int[] checks={0};Throwable[] error={null};
    runOnMainSync(()->{try{
      for(int variant=0;variant<9;variant++)for(int[] size:new int[][]{{240,42},{132,88},{48,160}}){
        ImageView view=(ImageView)Ui.foodDoodle(getTargetContext(),variant,Design.RED);
        view.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));view.layout(0,0,size[0],size[1]);
        var d=view.getDrawable();RectF bounds=new RectF(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());view.getImageMatrix().mapRect(bounds);
        if(bounds.left<-.5||bounds.top<-.5||bounds.right>size[0]-view.getPaddingLeft()-view.getPaddingRight()+.5||bounds.bottom>size[1]-view.getPaddingTop()-view.getPaddingBottom()+.5)throw new AssertionError("Illustration leaves frame");
        if(Math.abs(bounds.width()/bounds.height()-1.2)>.01)throw new AssertionError("Illustration distorted");
        Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));int nonempty=0;for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++)if(Color.alpha(bitmap.getPixel(x,y))>0)nonempty++;bitmap.recycle();
        if(nonempty==0)throw new AssertionError("Empty vector");checks[0]+=3;
      }
    }catch(Throwable t){error[0]=t;}});
    if(error[0]!=null)throw new AssertionError(error[0]);result.putString("stream","Food illustration PASS: "+checks[0]+" frame, aspect and actual drawing checks\n");finish(-1,result);
  }catch(Throwable t){result.putString("stream","Food illustration FAIL: "+android.util.Log.getStackTraceString(t));finish(1,result);}}
}
