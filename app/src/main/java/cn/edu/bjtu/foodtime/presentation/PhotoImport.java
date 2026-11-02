package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.util.UUID;

/** Worker-only image import: bounded input, sampled decoding and JPEG conversion. */
final class PhotoImport {
  static String prepare(Context context,Uri source) {
    return prepare(context,source,false);
  }
  static String prepareAvatar(Context context,Uri source){return prepare(context,source,true);}
  private static String prepare(Context context,Uri source,boolean avatar) {
    File file=new File(context.getFilesDir(),"photo-"+UUID.randomUUID()+".jpg");
    try {
      try(InputStream input=context.getContentResolver().openInputStream(source);var output=new FileOutputStream(file)) {
        if(input==null) throw new IOException();
        byte[] buffer=new byte[8192];int count;long total=0;
        while((count=input.read(buffer))!=-1) {
          total+=count;if(total>(avatar?cn.edu.bjtu.foodtime.domain.validation.AvatarCropGeometry.MAX_BYTES:20*1024*1024)) throw new IllegalArgumentException(avatar?"头像原图不能超过10MB（10,485,760字节）":"图片不能超过20MB");
          output.write(buffer,0,count);
        }
      }
      BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
      BitmapFactory.decodeFile(file.getAbsolutePath(),options);
      if(options.outWidth<=0||options.outHeight<=0) throw new IllegalArgumentException("请选择可读取的图片");
      if(avatar&&(!java.util.Set.of("image/jpeg","image/png").contains(options.outMimeType==null?"":options.outMimeType)||(long)options.outWidth*options.outHeight>20_000_000))throw new IllegalArgumentException("头像只支持JPEG/PNG，且不能超过2000万像素");
      options.inJustDecodeBounds=false;options.inSampleSize=1;
      while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600) options.inSampleSize*=2;
      Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
      if(bitmap==null) throw new IllegalArgumentException("图片读取失败");
      try {
        if(avatar){Bitmap normalized=orientedAvatar(bitmap,file);if(normalized!=bitmap)bitmap.recycle();bitmap=normalized;}
        try(var output=new FileOutputStream(file)) {if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,output)) throw new IOException();}
      }
      finally {bitmap.recycle();}
      return Uri.fromFile(file).toString();
    } catch(IOException|RuntimeException failure) {
      file.delete();if(failure instanceof IllegalArgumentException e) throw e;
      throw new IllegalStateException("图片读取失败，请重新选择",failure);
    }
  }
  private static Bitmap orientedAvatar(Bitmap source,File file){
    int orientation=1;
    try{orientation=new android.media.ExifInterface(file.getAbsolutePath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
    Matrix matrix=new Matrix();
    switch(orientation){
      case 2->matrix.setScale(-1,1);case 3->matrix.setRotate(180);case 4->matrix.setScale(1,-1);
      case 5->{matrix.setRotate(90);matrix.postScale(-1,1);}case 6->matrix.setRotate(90);
      case 7->{matrix.setRotate(-90);matrix.postScale(-1,1);}case 8->matrix.setRotate(-90);default->{}
    }
    Bitmap transformed=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);
    try{Bitmap opaque=Bitmap.createBitmap(transformed.getWidth(),transformed.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(opaque);canvas.drawColor(Design.PAPER);canvas.drawBitmap(transformed,0,0,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return opaque;}
    finally{if(transformed!=source)transformed.recycle();}
  }
  static String saveAvatar(Context context,Bitmap bitmap){
    File file=new File(context.getFilesDir(),"photo-"+UUID.randomUUID()+".jpg");
    try(var output=new FileOutputStream(file)){
      if(bitmap.getWidth()!=512||bitmap.getHeight()!=512||!bitmap.compress(Bitmap.CompressFormat.JPEG,90,output))throw new IOException();
      if(file.length()>cn.edu.bjtu.foodtime.domain.validation.AvatarCropGeometry.MAX_BYTES)throw new IllegalArgumentException("头像不能超过10MB，请重新裁剪");
      return Uri.fromFile(file).toString();
    }catch(IOException|RuntimeException failure){file.delete();throw new IllegalArgumentException("头像生成失败，请重试",failure);}
  }
  static void discard(Context context,String reference) {
    if(reference==null||!reference.startsWith("file:")) return;
    try {
      File file=new File(java.net.URI.create(reference));
      if(file.getName().startsWith("photo-")&&file.getCanonicalFile().getParentFile().equals(context.getFilesDir().getCanonicalFile())) file.delete();
    } catch(IOException|IllegalArgumentException ignored) { }
  }
  private PhotoImport() {}
}
