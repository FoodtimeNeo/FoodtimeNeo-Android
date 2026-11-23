package cn.edu.bjtu.foodtime.data.remote;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public catalog replicas only. Atomic replacement, integrity checking and a bounded LRU. */
final class CatalogDiskCache {
  record Entry(String data,String etag) {}
  private static final int MAGIC=0x46544331,MAX_ENTRY=2*1024*1024;
  private final File directory;
  private final long limit;
  CatalogDiskCache(File directory,long limit){this.directory=directory;this.limit=limit;prune();}
  private File file(String key){return new File(directory,ImageDiskCache.hash(key)+".catalog");}
  synchronized Entry get(String key){
    File file=file(key);
    try(var input=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
      if(file.length()>MAX_ENTRY+65536L||input.readInt()!=MAGIC)throw new IOException();
      String etag=input.readUTF(),checksum=input.readUTF();int size=input.readInt();
      if(size<1||size>MAX_ENTRY)throw new IOException();
      byte[] bytes=new byte[size];input.readFully(bytes);
      if(input.read()!=-1||!checksum.equals(ImageDiskCache.contentHash(bytes)))throw new IOException();
      file.setLastModified(System.currentTimeMillis());
      return new Entry(new String(bytes,StandardCharsets.UTF_8),etag);
    }catch(IOException failure){file.delete();return null;}
  }
  synchronized void put(String key,Entry entry){
    byte[] bytes=entry.data().getBytes(StandardCharsets.UTF_8);
    if(bytes.length<1||bytes.length>MAX_ENTRY||bytes.length+512>limit)return;
    File temporary=null;
    try{
      if(!directory.isDirectory()&&!directory.mkdirs())return;
      temporary=File.createTempFile("catalog-",".tmp",directory);
      try(var output=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))){
        output.writeInt(MAGIC);output.writeUTF(entry.etag());output.writeUTF(ImageDiskCache.contentHash(bytes));
        output.writeInt(bytes.length);output.write(bytes);
      }
      try{Files.move(temporary.toPath(),file(key).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
      catch(AtomicMoveNotSupportedException failure){Files.move(temporary.toPath(),file(key).toPath(),StandardCopyOption.REPLACE_EXISTING);}
      prune();
    }catch(IOException ignored){/* Storage failure must not turn a successful read into an error. */}
    finally{if(temporary!=null)temporary.delete();}
  }
  synchronized void remove(String key){file(key).delete();}
  private synchronized void prune(){
    File[] files=directory.listFiles((dir,name)->name.endsWith(".catalog"));if(files==null)return;
    long bytes=0;for(File file:files)bytes+=file.length();
    Arrays.sort(files,Comparator.comparingLong(File::lastModified));
    for(File file:files)if(bytes>limit){long size=file.length();if(file.delete())bytes-=size;}
  }
}
