package cn.edu.bjtu.foodtime.presentation;
import android.app.Instrumentation;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

/** Local bitmap/codec test only. Does not sign in, call a network, or change existing user data. */
public class AvatarCropRunner extends Instrumentation {
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  @Override public void onStart(){
    Bundle result=new Bundle();
    try{
      AtomicReference<Throwable> failure=new AtomicReference<>();
      runOnMainSync(()->{try{
        Bitmap source=Bitmap.createBitmap(640,320,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);
        AvatarCropView crop=new AvatarCropView(getTargetContext(),source);crop.layout(0,0,350,320);
        Bitmap display=Bitmap.createBitmap(350,320,Bitmap.Config.ARGB_8888);crop.draw(new Canvas(display));
        crop.rotate();crop.setZoom(2);Bitmap output=crop.export();
        check(output.getWidth()==512&&output.getHeight()==512,"square export dimensions");
        check(output.getPixel(256,256)==Color.RED,"preview overlays must not tint the exported image");
        String saved=PhotoImport.saveAvatar(getTargetContext(),output);check(new File(java.net.URI.create(saved)).length()<=10485760,"bounded JPEG output");
        PhotoImport.discard(getTargetContext(),saved);output.recycle();display.recycle();crop.onDetachedFromWindow();
      }catch(Throwable e){failure.set(e);}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
      for(int orientation:new int[]{ExifInterface.ORIENTATION_FLIP_HORIZONTAL,ExifInterface.ORIENTATION_ROTATE_90}){
        File original=new File(getTargetContext().getCacheDir(),"avatar-crop-test-"+orientation+".jpg");
        Bitmap source=Bitmap.createBitmap(128,64,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(source);Paint paint=new Paint();paint.setColor(Color.RED);canvas.drawRect(0,0,64,64,paint);paint.setColor(Color.BLUE);canvas.drawRect(64,0,128,64,paint);
        try(var out=new FileOutputStream(original)){source.compress(Bitmap.CompressFormat.JPEG,95,out);}source.recycle();
        var exif=new ExifInterface(original.getAbsolutePath());exif.setAttribute(ExifInterface.TAG_ORIENTATION,Integer.toString(orientation));exif.saveAttributes();
        String prepared=PhotoImport.prepareAvatar(getTargetContext(),Uri.fromFile(original));
        Bitmap image=BitmapFactory.decodeFile(new File(java.net.URI.create(prepared)).getAbsolutePath());
        if(orientation==ExifInterface.ORIENTATION_FLIP_HORIZONTAL)check(Color.blue(image.getPixel(16,32))>200&&Color.red(image.getPixel(16,32))<50,"horizontal EXIF mirror");
        else check(image.getWidth()==64&&image.getHeight()==128&&Color.red(image.getPixel(32,16))>200&&Color.blue(image.getPixel(32,112))>200,"EXIF clockwise rotation");
        image.recycle();PhotoImport.discard(getTargetContext(),prepared);
        try(var out=new FileOutputStream(original,true)){out.write(new byte[10485761]);}
        boolean rejected=false;try{PhotoImport.prepareAvatar(getTargetContext(),Uri.fromFile(original));}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"original input size must be checked before decoding");original.delete();
      }
      result.putString("stream","Avatar crop PASS: square export, overlay isolation, EXIF mirror/rotation and original size limit\n");finish(-1,result);
    }catch(Throwable failure){result.putString("stream","Avatar crop FAIL: "+failure.getMessage()+"\n");finish(0,result);}
  }
}
