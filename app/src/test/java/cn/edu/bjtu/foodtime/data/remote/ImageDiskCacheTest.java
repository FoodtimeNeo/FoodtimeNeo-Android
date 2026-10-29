package cn.edu.bjtu.foodtime.data.remote;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class ImageDiskCacheTest {
  @Rule public TemporaryFolder temp=new TemporaryFolder();
  private String key(String scope,String value){return scope+"-"+ImageDiskCache.hash(value);}
  private ImageDiskCache.Entry entry(int bytes){return new ImageDiskCache.Entry(new byte[bytes],"etag","image/png",12345);}
  @Test public void restartRestoresBytesAndSeparatesThumbAndFull() throws Exception {
    File folder=temp.newFolder();ImageDiskCache cache=new ImageDiskCache(folder,20000);
    cache.put(key("p","thumb"),entry(123));cache.put(key("p","full"),entry(321));
    ImageDiskCache restarted=new ImageDiskCache(folder,20000);
    assertEquals(123,restarted.get(key("p","thumb")).bytes().length);
    assertEquals(321,restarted.get(key("p","full")).bytes().length);
  }
  @Test public void corruptedBodyFallsBackAndRemovesBrokenEntry() throws Exception {
    File folder=temp.newFolder();ImageDiskCache cache=new ImageDiskCache(folder,20000);String key=key("s","actorA");cache.put(key,entry(20));
    try(var out=new RandomAccessFile(new File(folder,key+".cache"),"rw")){out.seek(out.length()-1);out.write(7);}
    assertNull(cache.get(key));assertEquals(0,cache.size());
  }
  @Test public void capacityEvictsLeastRecentlyUsedAndPrivateClearKeepsPublic() throws Exception {
    File folder=temp.newFolder();ImageDiskCache cache=new ImageDiskCache(folder,1500);
    String old=key("p","old"),newer=key("p","new"),privateKey=key("s","actorA");
    cache.put(old,entry(500));new File(folder,old+".cache").setLastModified(System.currentTimeMillis()-10000);
    cache.put(newer,entry(500));cache.put(privateKey,entry(500));
    assertTrue(cache.size()<=1500);assertNull(cache.get(old));
    Files.write(new File(folder,"draft.txt").toPath(),"draft".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    cache.clear(true);assertNotNull(cache.get(newer));assertNull(cache.get(privateKey));
    cache.clear(false);assertEquals(0,cache.size());assertTrue(new File(folder,"draft.txt").exists());
  }
  @Test public void differentActorsCannotReadEachOthersPrivateEntry() throws Exception {
    ImageDiskCache cache=new ImageDiskCache(temp.newFolder(),20000);cache.put(key("s","actorA"),entry(123));
    assertNull(cache.get(key("s","actorB")));assertNotNull(cache.get(key("s","actorA")));
  }
}
