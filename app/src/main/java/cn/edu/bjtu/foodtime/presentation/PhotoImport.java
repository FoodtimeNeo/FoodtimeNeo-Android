package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.util.UUID;

/** Worker-only image import: bounded input, sampled decoding and JPEG conversion. */
final class PhotoImport {
  static String prepare(Context context,Uri source) {
    File file=new File(context.getFilesDir(),"photo-"+UUID.randomUUID()+".jpg");
    try {
      try(InputStream input=context.getContentResolver().openInputStream(source);var output=new FileOutputStream(file)) {
        if(input==null) throw new IOException();
        byte[] buffer=new byte[8192];int count;long total=0;
        while((count=input.read(buffer))!=-1) {
          total+=count;if(total>20*1024*1024) throw new IllegalArgumentException("图片不能超过20MB");
          output.write(buffer,0,count);
        }
      }
      BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
      BitmapFactory.decodeFile(file.getAbsolutePath(),options);
      if(options.outWidth<=0||options.outHeight<=0) throw new IllegalArgumentException("请选择可读取的图片");
      options.inJustDecodeBounds=false;options.inSampleSize=1;
      while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600) options.inSampleSize*=2;
      Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
      if(bitmap==null) throw new IllegalArgumentException("图片读取失败");
      try(var output=new FileOutputStream(file)) {if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,output)) throw new IOException();}
      finally {bitmap.recycle();}
      return Uri.fromFile(file).toString();
    } catch(IOException|RuntimeException failure) {
      file.delete();if(failure instanceof IllegalArgumentException e) throw e;
      throw new IllegalStateException("图片读取失败，请重新选择",failure);
    }
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
