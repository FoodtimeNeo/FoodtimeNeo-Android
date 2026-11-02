package cn.edu.bjtu.foodtime.presentation;
import android.graphics.*;
import android.widget.*;
import java.io.File;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

final class AvatarCropScreen {
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route){
    a.heading(page,"03.1","裁剪头像","固定1:1方形选框，拖动或双指缩放图片。");
    TextView loading=Ui.text(a,"正在读取图片…",14,Design.MUTED);page.addView(loading);
    TextView error=Ui.text(a,"",14,Design.RED);error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(error);
    AtomicReference<Bitmap> image=new AtomicReference<>();
    a.request(null,error,()->{
      Bitmap decoded=BitmapFactory.decodeFile(new File(URI.create(route.id())).getAbsolutePath());
      if(decoded==null)throw new IllegalArgumentException("图片无法读取，请返回重新选择");image.set(decoded);
    },()->{
      page.removeView(loading);
      AvatarCropView crop=new AvatarCropView(a,image.getAndSet(null));
      crop.restore(a.drafts.getFloat(a.draftKey("cropZoom"),1),a.drafts.getFloat(a.draftKey("cropX"),0),a.drafts.getFloat(a.draftKey("cropY"),0),a.drafts.getInt(a.draftKey("cropRotation"),0));
      page.addView(crop,new LinearLayout.LayoutParams(-1,Ui.dp(a,320)));
      TextView scale=Ui.text(a,"缩放 · "+String.format(java.util.Locale.ROOT,"%.2f×",crop.zoom()),14,Design.MUTED);page.addView(scale);
      SeekBar zoom=new SeekBar(a);zoom.setMinimumHeight(Ui.dp(a,48));zoom.setMax(300);zoom.setProgress(Math.round((crop.zoom()-1)*100));zoom.setContentDescription("头像缩放倍数");page.addView(zoom);
      crop.setChanged(()->{
        scale.setText("缩放 · "+String.format(java.util.Locale.ROOT,"%.2f×",crop.zoom()));zoom.setProgress(Math.round((crop.zoom()-1)*100));
        a.drafts.putFloat(a.draftKey("cropZoom"),crop.zoom());a.drafts.putFloat(a.draftKey("cropX"),crop.panX());a.drafts.putFloat(a.draftKey("cropY"),crop.panY());a.drafts.putInt(a.draftKey("cropRotation"),crop.rotation());
      });
      zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user)crop.setZoom(1+p/100f);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
      LinearLayout tools=Ui.row(a);tools.addView(Ui.button(a,"旋转90°",false,crop::rotate),new LinearLayout.LayoutParams(0,-2,1));tools.addView(Ui.button(a,"重置",false,crop::reset),new LinearLayout.LayoutParams(0,-2,1));page.addView(tools);Ui.gap(page,12);
      page.addView(Ui.text(a,"导出512×512方形头像，最终文件不超过10MB。保存资料后才会更新头像。",14,Design.MUTED));Ui.gap(page,14);
      TextView accept=Ui.button(a,"使用此头像",true,()->{});page.addView(accept);
      accept.setOnClickListener(v->{
        if(a.operationInFlight())return;
        try{
          Bitmap result=crop.export();String target=a.drafts.getString("avatarCropTarget","");
          if(target.isBlank()){result.recycle();throw new IllegalArgumentException("裁剪流程已失效，请返回重新选择头像");}
          a.finishAvatarCrop(accept,error,target,()->{try{return PhotoImport.saveAvatar(a,result);}finally{result.recycle();}});
        }catch(IllegalArgumentException failure){error.setText(failure.getMessage());}
      });
      Ui.gap(page,10);Ui.action(page,"取消",false,a::back);
    });
  }
  private AvatarCropScreen(){}
}
