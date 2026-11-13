package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Unbinding consumes the server's effective ordinary role without granting administrative access. */
public class AccountParticipationRepositoryTest {
  private ServerSocket server;private ExecutorService workers;private RemoteRepository repo;
  private volatile String returnedRole="user",returnedId="owner";
  private volatile boolean stillBound;
  private volatile JSONObject submitted;
  @Before public void start()throws Exception{
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));workers=Executors.newCachedThreadPool();
    workers.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.submit(()->handle(socket));}catch(IOException stopped){if(!server.isClosed())throw new UncheckedIOException(stopped);}});
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  @After public void stop()throws Exception{server.close();workers.shutdownNow();}
  @Test public void openPolicyKeepsOrdinaryRoleAfterUnbinding()throws Exception{
    repo.restoreSession();assertEquals("2026-11-13T14:00:00Z",repo.unbindSchoolEmail("012345"));
    assertEquals("user",repo.currentUser().role());assertEquals("012345",submitted.getString("verificationCode"));
  }
  @Test public void schoolGatedPolicyCanReturnVisitorAfterUnbinding(){repo.restoreSession();returnedRole="visitor";repo.unbindSchoolEmail("012345");assertEquals("visitor",repo.currentUser().role());}
  @Test public void unbindingCannotGrantAnAdministratorRole(){repo.restoreSession();returnedRole="admin";assertThrows(RepositoryException.class,()->repo.unbindSchoolEmail("012345"));assertEquals("user",repo.currentUser().role());}
  @Test public void unbindingCannotReplaceTheAccount(){repo.restoreSession();returnedId="other";assertThrows(RepositoryException.class,()->repo.unbindSchoolEmail("012345"));assertEquals("owner",repo.currentUser().id());}
  @Test public void unbindingMustRemoveSchoolVerification(){repo.restoreSession();stillBound=true;assertThrows(RepositoryException.class,()->repo.unbindSchoolEmail("012345"));assertEquals("user",repo.currentUser().role());}
  private JSONObject profile(String id,String role)throws JSONException{return new JSONObject().put("id",id).put("email","member@gmail.com").put("role",role).put("schoolEmail",stillBound?"123@bjtu.edu.cn":JSONObject.NULL).put("schoolEmailVerified",stillBound);}
  private void handle(Socket socket){try(socket){
    socket.setSoTimeout(5000);var input=socket.getInputStream();String first=line(input);if(first==null)return;
    String path=first.split(" ")[1];int length=0;String line;
    while((line=line(input))!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).strip());
    byte[] body=input.readNBytes(length);Object data=new JSONObject();
    if(path.endsWith("/auth/csrf"))data=new JSONObject().put("token","fixture");
    else if(path.endsWith("/users/me/profile"))data=profile("owner","user");
    else if(path.endsWith("/capabilities"))data=new JSONObject().put("capabilities",new JSONArray().put("correction.submit")).put("revision","fixture");
    else if(path.endsWith("/account-lifecycle/unbind")){submitted=new JSONObject(new String(body,StandardCharsets.UTF_8));data=new JSONObject().put("profile",profile(returnedId,returnedRole)).put("schoolEmailReusableAt","2026-11-13T14:00:00Z");}
    byte[] bytes=new JSONObject().put("code","OK").put("message","fixture").put("data",data).toString().getBytes(StandardCharsets.UTF_8);
    var out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
  }catch(Exception e){if(!server.isClosed())throw new RuntimeException(e);}}
  private static String line(InputStream input)throws IOException{var bytes=new ByteArrayOutputStream();int value;while((value=input.read())!=-1&&value!='\n')if(value!='\r')bytes.write(value);return value==-1&&bytes.size()==0?null:bytes.toString(StandardCharsets.US_ASCII);}
}
