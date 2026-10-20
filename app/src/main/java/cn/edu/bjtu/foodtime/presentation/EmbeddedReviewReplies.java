package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.util.UUID;

/** A single, lazily loaded reply floor and shared draft editor inside its original review. */
final class EmbeddedReviewReplies {
  private final MainActivity a;
  private final String root,key;
  private final LinearLayout panel;
  private final TextView toggle,error,progress;
  private long total;
  private boolean needsReload;

  static void attach(MainActivity a,LinearLayout card,Review review) {new EmbeddedReviewReplies(a,card,review.id(),review.replyCount(),review);}
  static void thread(MainActivity a,LinearLayout page,String root) {
    a.drafts.putBoolean("embedded-replies:"+root+":expanded",true);
    new EmbeddedReviewReplies(a,page,root,a.repo.reviewReplies(root).total(),null);
  }
  private EmbeddedReviewReplies(MainActivity activity,LinearLayout card,String id,long count,Review review) {
    a=activity;root=id;key="embedded-replies:"+root+":";total=count;
    needsReload=review!=null&&a.repo.hasLoadedReviewReplies(root)&&a.repo.reviewReplies(root).total()!=count;
    if(review!=null) card.addView(Ui.text(a,SubmissionScreen.date(review.createdAt()),14,Design.MUTED));
    ReviewActionRow actions=new ReviewActionRow(a);card.addView(actions);
    if(review!=null) ReviewActions.like(a,actions,"评论",new LikeState(review.likedByMe(),review.likeCount(),review.counterVersion()),
        liked -> a.repo.setReviewLike(root,liked));
    toggle=ReviewActions.button(a,"",this::toggle);actions.addView(toggle);
    error=Ui.text(a,"",14,Design.RED);error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);
    Ui.observeError(error);
    progress=Ui.text(a,"正在加载回复…",14,Design.MUTED);progress.setVisibility(View.GONE);
    progress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(progress);
    error.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
      public void onTextChanged(CharSequence s,int st,int before,int count) {if(s.length()>0) progress.setVisibility(View.GONE);}
      public void afterTextChanged(android.text.Editable s) {}
    });
    panel=Ui.col(a);panel.setBackgroundColor(Design.ALT);
    panel.setPadding(Ui.dp(a,12),0,Ui.dp(a,8),Ui.dp(a,12));
    // Quiet left rule separates a nested floor from the primary paper card.
    android.graphics.drawable.LayerDrawable background=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
        Ui.shape(a,Design.BORDER,0,0),Ui.shape(a,Design.ALT,0,0)});
    background.setLayerInset(1,Ui.dp(a,2),0,0,0);panel.setBackground(background);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=Ui.dp(a,8);lp.topMargin=Ui.dp(a,8);card.addView(panel,lp);
    paint();
    if(expanded()&&(!a.repo.hasLoadedReviewReplies(root)||needsReload)) panel.post(() -> load(toggle,false));
  }
  private boolean expanded() {return a.drafts.getBoolean(key+"expanded",false);}
  private void toggle() {
    if(a.operationInFlight()) return;
    boolean next=!expanded();a.drafts.putBoolean(key+"expanded",next);paint();
    if(next&&(!a.repo.hasLoadedReviewReplies(root)||needsReload)) load(toggle,false);
  }
  private void load(View trigger,boolean more) {
    if(a.operationInFlight()) return;
    progress.setVisibility(View.VISIBLE);
    a.request(trigger,error,() -> {if(more) a.repo.loadMoreReplies(root);else a.repo.loadReviewReplies(root);},() -> {needsReload=false;paint();});
  }
  private void paint() {
    progress.setVisibility(View.GONE);
    if(a.repo.hasLoadedReviewReplies(root)&&!needsReload) total=a.repo.reviewReplies(root).total();
    toggle.setText((expanded()?"收起回复":"回复")+" · "+total);
    toggle.setContentDescription((expanded()?"收起":"展开")+total+"条回复");
    panel.setVisibility(expanded()?View.VISIBLE:View.GONE);
    if(!expanded()) {hideKeyboard();return;}
    panel.removeAllViews();
    LinearLayout heading=Ui.row(a);panel.addView(heading);
    TextView title=Ui.text(a,"回复 "+total,14,Design.INK);title.setTypeface(Design.body(),android.graphics.Typeface.BOLD);
    heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    TextView refresh=ReviewActions.button(a,"刷新",() -> {});heading.addView(refresh);
    refresh.setContentDescription("刷新回复");refresh.setOnClickListener(v -> load(v,false));
    ReplyPage replies=a.repo.reviewReplies(root);
    if(replies.items().isEmpty()) {
      panel.addView(Ui.text(a,a.repo.hasLoadedReviewReplies(root)?"还没有已公开的回复，来聊聊这道菜吧。":"尚未加载回复，可点击刷新重试。",14,Design.MUTED));
      Ui.gap(panel,8);
    }
    for(ReviewReply row:replies.items()) {
      View rule=new View(a);rule.setBackgroundColor(Design.BORDER);panel.addView(rule,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));
      LinearLayout item=Ui.col(a);item.setPadding(0,Ui.dp(a,12),0,Ui.dp(a,8));panel.addView(item);
      TextView author=Ui.text(a,row.userDisplayName(),14,Design.INK);author.setTypeface(Design.body(),android.graphics.Typeface.BOLD);item.addView(author);
      if(!row.replyToId().isBlank()) item.addView(Ui.text(a,"回复 "+(row.replyToDeleted()?"已删除的回复":row.replyToUserDisplayName()),14,Design.MUTED));
      Ui.gap(item,4);item.addView(Ui.text(a,row.content(),16,Design.INK));Ui.gap(item,4);
      item.addView(Ui.text(a,SubmissionScreen.date(row.createdAt()),14,Design.MUTED));
      ReviewActionRow actions=new ReviewActionRow(a);item.addView(actions);
      ReviewActions.like(a,actions,"回复",row.likes(),liked -> a.repo.setReplyLike(row.id(),liked));
      actions.addView(ReviewActions.button(a,"回复这条",() -> edit(row.id(),row.userDisplayName())));
      if(row.ownedByMe()) {
        TextView more=ReviewActions.button(a,"更多",() -> {});more.setContentDescription("我的回复，更多操作");actions.addView(more);
        more.setOnClickListener(v -> {
          if(a.operationInFlight()) return;
          PopupMenu menu=new PopupMenu(a,more);menu.getMenu().add("删除回复");
          menu.setOnMenuItemClickListener(entry -> {Ui.confirm(a,"删除这条回复？","正文不再公开，后续回复保留。",
              () -> a.request(more,error,() -> {a.repo.deleteReply(row.id());a.repo.loadReviewReplies(root);},() -> {
                if(a.drafts.getString(key+"target","").equals(row.id())) {a.drafts.remove(key+"target");a.drafts.remove(key+"target-name");}
                a.message("回复已删除");paint();}));return true;});menu.show();
        });
      }
    }
    if(replies.hasMore()) {
      TextView more=ReviewActions.button(a,"加载更多回复",() -> {});panel.addView(more);more.setOnClickListener(v -> load(v,true));
    }
    composer();
  }
  private void edit(String target,String name) {
    if(a.operationInFlight()) return;
    a.drafts.putString(key+"target",target);a.drafts.putString(key+"target-name",name);a.drafts.putBoolean(key+"editing",true);
    paint();
    EditText field=panel.findViewWithTag(key+"input");
    if(field!=null) {field.requestFocus();field.post(() -> ((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT));}
  }
  private void composer() {
    if(!a.drafts.getBoolean(key+"editing",false)) {
      TextView prompt=ReviewActions.button(a,a.drafts.getString(key+"body","").isBlank()?"写下你的回复…":"继续编辑回复…",
          () -> edit(a.drafts.getString(key+"target",""),a.drafts.getString(key+"target-name","")));
      prompt.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
      prompt.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,0));panel.addView(prompt,new LinearLayout.LayoutParams(-1,-2));return;
    }
    LinearLayout form=Ui.col(a);panel.addView(form);Ui.gap(form,8);
    String target=a.drafts.getString(key+"target",""),name=a.drafts.getString(key+"target-name","");
    TextView label=Ui.text(a,target.isBlank()?"回复这条点评":"回复 "+name,14,Design.INK);form.addView(label);
    if(!target.isBlank()) form.addView(ReviewActions.button(a,"取消回复对象",() -> edit("","")));
    EditText input=new EditText(a);input.setTag(key+"input");input.setId(View.generateViewId());label.setLabelFor(input.getId());
    input.setTextSize(16);input.setTextColor(Design.INK);input.setTypeface(Design.body());input.setGravity(android.view.Gravity.TOP);
    input.setInputType(131073);input.setMinLines(3);input.setPadding(Ui.dp(a,12),Ui.dp(a,8),Ui.dp(a,12),Ui.dp(a,8));
    input.setBackground(Ui.shape(a,Design.PAPER,Design.MUTED,0));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
    input.setText(a.drafts.getString(key+"body",""));form.addView(input,new LinearLayout.LayoutParams(-1,-2));
    TextView hint=Ui.text(a,input.length()+"/500 · 审核通过后公开",12,Design.MUTED);form.addView(hint);
    Ui.observe(input,() -> {a.drafts.putString(key+"body",input.getText().toString());hint.setText(input.length()+"/500 · 审核通过后公开");});
    TextView failure=Ui.text(a,"",14,Design.RED);failure.setVisibility(View.GONE);Ui.observeError(failure);
    failure.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);form.addView(failure);
    ReviewActionRow actions=new ReviewActionRow(a);form.addView(actions);
    actions.addView(ReviewActions.button(a,"收起编辑",() -> {if(!a.operationInFlight()) {a.drafts.putBoolean(key+"editing",false);hideKeyboard();paint();}}));
    TextView submit=Ui.button(a,"发布回复",true,() -> {});actions.addView(submit);
    submit.setOnClickListener(v -> {
      if(a.operationInFlight()) return;
      try {
        String body=Validation.text(input.getText().toString(),500,"回复内容"),signature=target+"\n"+body;
        if(!signature.equals(a.drafts.getString(key+"signature",""))) {
          a.drafts.putString(key+"signature",signature);a.drafts.putString(key+"request-id",UUID.randomUUID().toString());
        }
        String requestId=a.drafts.getString(key+"request-id","");
        a.drafts.putString("embedded-reply-operation",root);
        input.setEnabled(false);
        a.request(v,failure,() -> a.repo.submitReply(root,target,body,requestId),() -> {
          completeSubmission(a);
          a.message("回复已提交，审核通过后公开，可在我的回复查看");hideKeyboard();paint();
        });
      } catch(RuntimeException e) {failure.setText(e.getMessage());input.requestFocus();}
    });
    failure.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
      public void onTextChanged(CharSequence s,int st,int before,int count) {if(s.length()>0) input.setEnabled(true);}
      public void afterTextChanged(android.text.Editable s) {}
    });
  }
  private void hideKeyboard() {
    if(panel.getWindowToken()!=null) ((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(panel.getWindowToken(),0);
    panel.clearFocus();
  }
  static void completeSubmission(MainActivity a) {
    String root=a.drafts.getString("embedded-reply-operation","");
    if(root.isBlank()) return;
    for(String suffix:new String[]{"body","target","target-name","signature","request-id","editing"}) a.drafts.remove("embedded-replies:"+root+":"+suffix);
    a.drafts.remove("embedded-reply-operation");
  }
}
