package cn.edu.bjtu.foodtime.presentation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.FeedbackModels.ImportResult;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** System document picker; CSV never leaves the selected document and authenticated server. */
final class FeedbackDocuments {
  private static final int EXPORT=106,IMPORT=107;
  private final MainActivity a;
  private int pending;
  private String actor="";
  private boolean reading;
  AlertDialog confirmation;
  FeedbackDocuments(MainActivity a,Bundle state){
    this.a=a;if(state!=null){pending=state.getInt("feedback-document-mode",0);actor=state.getString("feedback-document-actor","");}
  }
  void save(Bundle state){state.putInt("feedback-document-mode",pending);state.putString("feedback-document-actor",actor);}
  void reset(){pending=0;actor="";close();}
  void close(){if(confirmation!=null){confirmation.dismiss();confirmation=null;}}
  private boolean sameActor(String expected){return allowed()&&a.repo.currentUser().id().equals(expected)&&a.route().kind().equals("admin-feedback");}
  private boolean allowed(){return a.repo.currentUser()!=null&&"superadmin".equals(a.repo.currentUser().role());}
  void export(){
    if(!allowed()||pending!=0||reading||a.operationInFlight())return;
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(Intent.EXTRA_TITLE,"反馈建议.csv");open(intent,EXPORT);
  }
  void importCsv(){
    if(!allowed()||pending!=0||reading||a.operationInFlight())return;
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/comma-separated-values","application/csv","application/vnd.ms-excel","text/plain","application/octet-stream"});
    open(intent,IMPORT);
  }
  private void open(Intent intent,int mode){
    pending=mode;actor=a.repo.currentUser().id();
    try{a.startActivityForResult(intent,mode);}catch(android.content.ActivityNotFoundException missing){reset();a.message("没有可用的文件管理器");}
  }
  boolean result(int request,int result,Intent data){
    if(request!=EXPORT&&request!=IMPORT)return false;
    String expected=actor;int mode=pending;reset();
    if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
    if(mode!=request||!allowed()||!a.repo.currentUser().id().equals(expected)||!a.route().kind().equals("admin-feedback")){
      a.message("账号或页面已变化，请重新选择表格");return true;
    }
    var uri=data.getData();
    if(request==EXPORT){
      a.request(null,null,()->{
        var file=a.repo.exportFeedback();
        try(var out=a.getContentResolver().openOutputStream(uri,"wt")){
          if(out==null)throw new IOException();out.write(file.csv().getBytes(StandardCharsets.UTF_8));
        }catch(IOException|SecurityException error){throw new IllegalStateException("CSV 保存失败，请重新导出；已包含的反馈保持处理中");}
      },()->{a.message("CSV 已保存，导出的待处理反馈已标为处理中");a.redraw();});
    }else{
      reading=true;AtomicReference<String> csv=new AtomicReference<>();TextView error=new TextView(a);
      error.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(s.length()>0)a.message(s.toString());}public void afterTextChanged(android.text.Editable s){}});
      a.read("feedback-csv:"+UUID.randomUUID(),null,error,()->csv.set(read(uri)),()->{
        if(!sameActor(expected)){a.message("账号或页面已变化，请重新选择表格");return;}
        confirmation=Ui.confirm(a,"批量导入回复与状态？","有效记录将更新，错误记录和已完成记录会跳过并列出。",()->{
          if(!sameActor(expected)){a.message("账号或页面已变化，请重新选择表格");return;}
          AtomicReference<ImportResult> imported=new AtomicReference<>();
          a.request(null,null,()->imported.set(a.repo.importFeedback(csv.get())),()->{a.redraw();showResult(imported.get());});
        });
        confirmation.setOnDismissListener(dialog->confirmation=null);
      },()->reading=false);
    }
    return true;
  }
  private String read(android.net.Uri uri){
    try(var in=a.getContentResolver().openInputStream(uri);var out=new ByteArrayOutputStream()){
      if(in==null)throw new IOException();byte[] buffer=new byte[8192];int count;
      while((count=in.read(buffer))!=-1){if(out.size()+count>8000000)throw new IllegalArgumentException("文件过大，请分批导入");out.write(buffer,0,count);}
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();
    }catch(CharacterCodingException error){throw new IllegalArgumentException("文件编码不正确，请另存为 CSV UTF-8");}
    catch(IOException|SecurityException error){throw new IllegalStateException("读取 CSV 失败，请重新选择文件");}
  }
  private void showResult(ImportResult result){
    StringBuilder text=new StringBuilder("已更新 "+result.updated()+" 条，跳过 "+result.skipped()+" 条。");
    for(var issue:result.issues())text.append("\n第 ").append(issue.row()).append(" 条记录：").append(issue.message());
    ScrollView scroll=new ScrollView(a);TextView copy=Ui.text(a,text.toString(),15,Design.INK);copy.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,12));copy.setTextIsSelectable(true);scroll.addView(copy);
    new AlertDialog.Builder(a).setTitle("导入结果").setView(scroll).setPositiveButton("确认",null).show();
  }
}
