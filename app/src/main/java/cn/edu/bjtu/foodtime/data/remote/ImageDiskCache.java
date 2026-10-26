package cn.edu.bjtu.foodtime.data.remote;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** App-private, bounded image-only cache. No cookies, credentials or business records. */
public final class ImageDiskCache {
  public record Entry(byte[] bytes,String etag,String contentType,long freshUntil) {}
  private final File directory;
  private final long limit;
  private static final int MAGIC=0x46544932, MAX_IMAGE=20*1024*1024;
  public ImageDiskCache(File directory,long limit) { this.directory=directory;this.limit=limit; prune(); }
  public static String hash(String value) { return hex(digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
  public static String contentHash(byte[] value) { return hex(digest(value)); }
  private static byte[] digest(byte[] value) { try{return MessageDigest.getInstance("SHA-256").digest(value);}catch(Exception e){throw new IllegalStateException(e);} }
  private static String hex(byte[] bytes) {StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return text.toString();}
  private File path(String key) {
    if(!key.matches("[ps]-[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid image cache key");
    return new File(directory,key+".cache");
  }
  public synchronized Entry get(String key) {
    File file=path(key);
    try(var in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      if(file.length()>MAX_IMAGE+65536L||in.readInt()!=MAGIC)throw new IOException();
      String etag=in.readUTF(),type=in.readUTF();long expires=in.readLong();String checksum=in.readUTF();
      int size=in.readInt();if(size<1||size>MAX_IMAGE)throw new IOException();
      byte[] bytes=new byte[size];in.readFully(bytes);
      if(in.read()!=-1||!checksum.equals(hex(digest(bytes))))throw new IOException();
      file.setLastModified(System.currentTimeMillis());
      return new Entry(bytes,etag,type,expires);
    } catch(IOException e) { file.delete();return null; }
  }
  public synchronized void put(String key,Entry entry) {
    if(entry.bytes().length==0||entry.bytes().length>MAX_IMAGE||entry.bytes().length>limit)return;
    File temporary=null;
    try {
      if(!directory.isDirectory()&&!directory.mkdirs())return;
      temporary=File.createTempFile("image-",".tmp",directory);
      try(var out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
        out.writeInt(MAGIC);out.writeUTF(entry.etag());out.writeUTF(entry.contentType());out.writeLong(entry.freshUntil());
        out.writeUTF(hex(digest(entry.bytes())));out.writeInt(entry.bytes().length);out.write(entry.bytes());
      }
      try {Files.move(temporary.toPath(),path(key).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
      catch(AtomicMoveNotSupportedException e){Files.move(temporary.toPath(),path(key).toPath(),StandardCopyOption.REPLACE_EXISTING);}
      prune();
    } catch(IOException ignored) { /* Cache failure never prevents a successful network display. */ }
    finally {if(temporary!=null)temporary.delete();}
  }
  public synchronized void remove(String key) { path(key).delete(); }
  public synchronized long size() {File[] files=directory.listFiles((dir,name)->name.endsWith(".cache"));long size=0;if(files!=null)for(File file:files)size+=file.length();return size;}
  public synchronized void clear(boolean privateOnly) {
    File[] files=directory.listFiles();if(files!=null)for(File file:files)if((!privateOnly||file.getName().startsWith("s-"))&&(file.getName().endsWith(".cache")||file.getName().endsWith(".tmp")))file.delete();
  }
  private synchronized void prune() {
    File[] files=directory.listFiles();if(files==null)return;
    Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=size(),old=System.currentTimeMillis()-30L*24*60*60*1000;
    for(File file:files)if(file.getName().endsWith(".cache")&&(file.lastModified()<old||total>limit)) {long bytes=file.length();if(file.delete())total-=bytes;}
  }
}
