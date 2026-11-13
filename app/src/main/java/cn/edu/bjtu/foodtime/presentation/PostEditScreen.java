package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import cn.edu.bjtu.foodtime.domain.repository.PostEditRepository;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.util.*;

/** Edits an existing identity; the single local creation draft is never read or overwritten. */
final class PostEditScreen {
  static boolean allowed(MainActivity a, CommunityPost post) {
    var user=a.repo.currentUser();
    return user!=null&&post!=null&&!a.repo.sessionMode().equals("appeal_only")&&user.id().equals(post.userId())
        &&Set.of("user","admin","superadmin").contains(user.role());
  }
  private static void hydrate(MainActivity a,CommunityPost post) {
    a.drafts.putLong(a.draftKey("editRevision"),post.contentRevision());
    a.drafts.putString(a.draftKey("食记标题"),post.title());
    a.drafts.putString(a.draftKey("动态正文"),post.content());
    a.drafts.putString(a.draftKey("linkedDishId"),post.linkedDish()==null?"":post.linkedDish().id());
    for(int i=0;i<post.imageUrls().size();i++)a.drafts.putString(CommunityScreen.photoKey(a,i),post.imageUrls().get(i));
  }
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route) {
    CommunityPost post=a.repo.communityPost(route.id());
    if(!allowed(a,post))throw new IllegalArgumentException("只能修改自己的未删除食记");
    if(a.drafts.getLong(a.draftKey("editRevision"))==0)hydrate(a,post);
    a.heading(page,"","修改食记","提交后整帖暂时隐藏，重新审核通过后公开。点赞、评论、收藏、浏览量和原发布时间保留；转发来源不变。");
    LinearLayout editor=Ui.col(a);page.addView(editor);
    CommunityScreen.photoEditor(a,editor,!post.repostOfId().isBlank());
    LinearLayout form=Ui.card(editor);
    EditText title=Ui.field(form,"食记标题",false,false);title.setTag("post-edit-title");
    title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(50)});
    EditText content=Ui.field(form,"动态正文",false,true);content.setTag("post-edit-content");
    content.setMinLines(5);content.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
    DishPickerScreen.show(a,form);
    TextView error=Ui.text(a,"",14,Design.RED);error.setTag("post-edit-error");form.addView(error);
    error.addTextChangedListener(new android.text.TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){if(s.length()>0)enable(editor,true);}
      public void afterTextChanged(android.text.Editable s){}
    });
    TextView submit=Ui.button(a,"提交修改并重新审核",true,()->{});submit.setTag("post-edit-submit");form.addView(submit);
    submit.setOnClickListener(v->{
      if(a.operationInFlight())return;
      try {
        String heading=Validation.text(title.getText().toString(),50,"标题");
        String body=Validation.text(content.getText().toString(),2000,"正文");
        var photos=CommunityScreen.selectedPhotos(a);
        if(photos.isEmpty()&&post.repostOfId().isBlank())throw new IllegalArgumentException("请保留至少1张图片");
        String dish=a.drafts.getString(a.draftKey("linkedDishId"),"");
        long revision=a.drafts.getLong(a.draftKey("editRevision"));
        String fingerprint=heading+"\n"+body+"\n"+String.join("|",photos)+"\n"+dish+"\n"+revision;
        if(!fingerprint.equals(a.drafts.getString(a.draftKey("editPayload"),""))){
          a.drafts.putString(a.draftKey("editPayload"),fingerprint);
          a.drafts.putString(a.draftKey("editRequest"),UUID.randomUUID().toString());
        }
        var input=new PostEditRepository.Edit(heading,body,photos,dish,revision,a.drafts.getString(a.draftKey("editRequest"),""));
        enable(editor,false);
        a.request(submit,error,()->a.repo.editCommunityPost(post.id(),input),()->{
          a.clearDraft(route);a.requireFreshContent("community-detail",post.id());a.requireFreshContent("community","");
          a.drafts.putBoolean(a.draftKey("editExit"),true);a.back();a.message("提交成功，可在我的食记查看当前审核状态");
        });
      }catch(RuntimeException failure){error.setText(failure.getMessage());enable(editor,true);}
    });
    Ui.gap(page,12);
    TextView reload=Ui.button(a,"读取最新帖子（替换本次输入）",false,()->{});reload.setTag("post-edit-reload");page.addView(reload);
    reload.setOnClickListener(v->{if(a.operationInFlight())return;
      new AlertDialog.Builder(a).setTitle("读取最新帖子？").setMessage("重新读取会替换本次未提交的内容，请先复制需要保留的文字。")
          .setNegativeButton("继续编辑",null).setPositiveButton("读取",(d,w)->
            a.read("post-edit-reload:"+post.id(),reload,error,()->a.repo.refresh(new ContentRequest(ContentRequest.Query.COMMUNITY_POST,post.id())),()->{
              var latest=a.repo.communityPost(post.id());if(!allowed(a,latest)){error.setText("帖子已不可修改");return;}
              a.clearDraft(route);hydrate(a,latest);a.redraw();
            },()->{})).show();});
  }
  static boolean confirmExit(MainActivity a){
    String key=a.draftKey("editExit");if(a.drafts.getBoolean(key)){a.drafts.remove(key);return false;}
    new AlertDialog.Builder(a).setTitle("放弃本次修改？").setMessage("未提交的修改将丢弃，原帖不会改变。")
        .setNegativeButton("继续编辑",null).setPositiveButton("放弃修改",(d,w)->{
          a.clearDraft(a.route());a.drafts.putBoolean(key,true);a.back();
        }).show();return true;
  }
  private static void enable(View view,boolean value){view.setEnabled(value);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)enable(group.getChildAt(i),value);}
  private PostEditScreen(){}
}
