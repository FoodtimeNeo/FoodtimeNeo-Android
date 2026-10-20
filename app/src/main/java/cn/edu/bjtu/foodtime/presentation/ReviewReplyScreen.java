package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.util.UUID;

final class ReviewReplyScreen {
  static void thread(MainActivity a,LinearLayout page,MainActivity.Route route) {
    ReplyPage replies=a.repo.reviewReplies(route.id());
    a.heading(page,"","评论回复",replies.total()+" 条已公开回复");
    Ui.action(page,"回复这条点评",true,() -> a.go("reply-create",route.id()+"~"));
    Ui.gap(page,12);
    if(replies.items().isEmpty()) Ui.empty(page,"还没有已公开的回复", "回复审核通过后公开，可在“我的回复”查看进度。");
    for(ReviewReply row:replies.items()) {
      LinearLayout card=card(a,page,row,false);
      ReviewActions.like(a,card,"回复",row.likes(),liked -> a.repo.setReplyLike(row.id(),liked));
      Ui.action(card,"回复这条",false,() -> a.go("reply-create",route.id()+"~"+row.id()));
      if(row.ownedByMe()) remove(a,card,row);
    }
    TextView error=Ui.text(a,"",14,Design.RED);page.addView(error);
    if(replies.hasMore()) Ui.submit(page,"加载更多回复",error,() -> () -> a.repo.loadMoreReplies(route.id()),a::redraw);
    Ui.action(page,"刷新回复",false,a::render);
  }
  static void create(MainActivity a,LinearLayout page,MainActivity.Route route) {
    String[] args=route.id().split("~",-1);String root=args[0],target=args.length>1?args[1]:"";
    a.heading(page,"","写回复","最多500字，审核通过后公开");
    if(!target.isBlank()) {
      ReviewReply selected=a.repo.reviewReplies(root).items().stream().filter(r -> r.id().equals(target)).findFirst().orElse(null);
      page.addView(Ui.text(a,selected==null?"回复已选中的回复":"回复 "+selected.userDisplayName(),14,Design.BLUE));
    }
    LinearLayout form=Ui.card(page);EditText content=Ui.field(form,"回复内容",false,true);
    content.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
    TextView error=Ui.text(a,"",14,Design.RED);form.addView(error);
    Ui.submit(form,"提交回复",error,() -> {
      String body=Validation.text(content.getText().toString(),500,"回复内容");
      String signatureKey=a.draftKey("reply-signature"),idKey=a.draftKey("reply-request-id");
      if(!body.equals(a.drafts.getString(signatureKey,""))) {
        a.drafts.putString(signatureKey,body);a.drafts.putString(idKey,UUID.randomUUID().toString());
      }
      String requestId=a.drafts.getString(idKey,UUID.randomUUID().toString());a.drafts.putString(idKey,requestId);
      return () -> a.repo.submitReply(root,target,body,requestId);
    },() -> {a.clearDraft(route);a.message("回复已提交，审核通过后公开");a.back();});
  }
  static void mine(MainActivity a,LinearLayout page,MainActivity.Route route) {
    int number=route.id().isBlank()?1:Integer.parseInt(route.id());ReplyPage replies=a.repo.myReviewReplies();
    a.heading(page,"","我的回复","共 "+replies.total()+" 条 · 第 "+number+" 页");
    if(replies.items().isEmpty()) Ui.empty(page,"暂无回复记录", "在菜品点评下参与讨论后，可在这里查看审核状态。");
    for(ReviewReply row:replies.items()) {
      LinearLayout card=card(a,page,row,true);remove(a,card,row);
      if(!row.reviewDeleted()) Ui.action(card,"查看讨论",false,() -> a.go("review-replies",row.reviewId()));
    }
    if(number>1) Ui.action(page,"上一页",false,() -> a.go("mine-review-replies",String.valueOf(number-1)));
    if(replies.hasMore()) Ui.action(page,"下一页",false,() -> a.go("mine-review-replies",String.valueOf(number+1)));
  }
  private static LinearLayout card(MainActivity a,LinearLayout page,ReviewReply row,boolean own) {
    LinearLayout card=Ui.card(page);
    card.addView(Ui.text(a,row.userDisplayName(),14,Design.BLUE));
    if(own) {
      card.addView(Ui.title(a,row.dishName(),24));
      card.addView(Ui.stamp(a,SubmissionScreen.status(row.status()),row.status().equals("approved")?Design.GREEN:Design.BLUE));
      if(!row.reviewReason().isBlank()) card.addView(Ui.text(a,"审核说明："+row.reviewReason(),14,Design.RED));
      if(row.reviewDeleted()) card.addView(Ui.text(a,"原点评已删除，整楼不再公开",14,Design.MUTED));
    }
    if(!row.replyToId().isBlank()) card.addView(Ui.text(a,"回复 "+(row.replyToDeleted()?"已删除的回复":row.replyToUserDisplayName()),14,Design.MUTED));
    card.addView(Ui.text(a,row.content(),16,Design.INK));
    card.addView(Ui.text(a,SubmissionScreen.date(row.createdAt()),12,Design.MUTED));Ui.gap(card,12);
    return card;
  }
  private static void remove(MainActivity a,LinearLayout card,ReviewReply row) {
    Ui.action(card,"删除回复",false,() -> Ui.confirm(a,"删除这条回复？","正文不再公开，后续回复保留。",
        () -> a.request(null,null,() -> a.repo.deleteReply(row.id()),() -> {a.message("回复已删除");a.render();})));
  }
  static void removeAsAdmin(MainActivity a,String id) {
    LinearLayout form=Ui.col(a);form.setPadding(Ui.dp(a,20),Ui.dp(a,16),Ui.dp(a,20),Ui.dp(a,8));
    EditText reason=Ui.field(form,"删除原因",false,true);TextView error=Ui.text(a,"",14,Design.RED);form.addView(error);
    AlertDialog dialog=new AlertDialog.Builder(a).setTitle("删除违规回复？").setView(form).setNegativeButton("取消",null).setPositiveButton("删除",null).create();
    dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
      String value=reason.getText().toString().strip();if(value.isBlank()) {error.setText("请填写删除原因");return;}
      a.request(w,error,() -> a.repo.removeReplyAsAdmin(id,value),() -> {dialog.dismiss();a.render();});
    }));dialog.show();
  }
}
