package cn.edu.bjtu.foodtime.presentation;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;

final class CommunityAdminScreen {
 static void queue(MainActivity a,LinearLayout page,MainActivity.Route route){
  AdminScreen.guard(a);CommunityAuditPage result=a.repo.communityAudit();
  a.heading(page,"",result.type().equals("posts")?"社区食记审核":"社区评论与回复审核","查看完整内容后处理；下架必须填写原因。");
  LinearLayout filters=Ui.row(a);String[] states={"pending","approved","rejected"},labels={"待审核","已通过","已驳回"};
  for(int i=0;i<states.length;i++){String state=states[i];filters.addView(Ui.button(a,labels[i],state.equals(result.status()),()->a.replaceRoute("admin-community",result.type()+"~"+state+"~1")));}page.addView(filters);
  page.addView(Ui.text(a,"共 "+result.total()+" 条",13,Design.MUTED));Ui.gap(page,12);
  if(result.items().isEmpty())Ui.empty(page,"暂无审核记录","");
  for(Submission item:result.items()){
   LinearLayout card=Ui.card(page);card.addView(Ui.title(a,item.name().isBlank()?"社区评论／回复":item.name(),20));Ui.gap(card,8);
   card.addView(Ui.text(a,"投稿者："+item.userDisplayName()+" · "+SubmissionScreen.date(item.createdAt()),12,Design.MUTED));Ui.gap(card,8);
   card.addView(Ui.text(a,item.content(),15,Design.INK));AiAssessmentView.show(a,card,item.ai());Ui.gap(card,10);
   Ui.action(card,"查看完整内容与配图",false,()->a.go("admin-community-detail",result.type()+"~"+item.id()));
  }
  LinearLayout pager=Ui.row(a);
  if(result.page()>1)pager.addView(Ui.button(a,"上一页",false,()->a.replaceRoute("admin-community",result.type()+"~"+result.status()+"~"+(result.page()-1))));
  if(result.page()*20L<result.total())pager.addView(Ui.button(a,"下一页",false,()->a.replaceRoute("admin-community",result.type()+"~"+result.status()+"~"+(result.page()+1))));page.addView(pager);
 }
 static void detail(MainActivity a,LinearLayout page,MainActivity.Route route){
  AdminScreen.guard(a);CommunityAuditDetail detail=a.repo.communityAuditDetail();if(detail==null)throw new IllegalArgumentException("审核内容不可用，请返回刷新");
  String[] parts=route.id().split("~",-1);String type=parts[0],id=parts[1];CommunityPost post=detail.post();CommunityComment comment=detail.comment();
  a.heading(page,"","完整社区审核",comment==null?"正文和全部配图均须核验。":"请结合根楼层、实际回复对象及原帖判断。");
  LinearLayout context=Ui.card(page);context.addView(Ui.title(a,post.title(),24));Ui.gap(context,8);context.addView(Ui.text(a,post.content(),15,Design.INK));Ui.gap(context,12);
  context.addView(new CommunityGallery(a,post.imageUrls()),new LinearLayout.LayoutParams(-1,-2));
  if(detail.rootComment()!=null){Ui.gap(context,12);context.addView(Ui.text(a,"一级评论："+content(detail.rootComment()),14,Design.MUTED));}
  if(detail.replyToComment()!=null){Ui.gap(context,8);context.addView(Ui.text(a,"实际回复对象："+detail.replyToComment().userDisplayName()+" · "+content(detail.replyToComment()),14,Design.MUTED));}
  if(comment!=null){Ui.gap(context,16);context.addView(Ui.title(a,comment.rootCommentId().isBlank()?"本次评论":"本次回复",20));Ui.gap(context,8);context.addView(Ui.text(a,comment.content(),16,Design.INK));}
  AiAssessmentView.show(a,context,detail.ai());
  String status=comment==null?post.status():comment.status();
  if(status.equals("rejected")){context.addView(Ui.text(a,"已驳回："+(comment==null?post.reviewReason():comment.reviewReason()),14,Design.RED));return;}
  LinearLayout form=Ui.card(page);EditText reason=Ui.field(form,status.equals("approved")?"下架原因":"审核说明",false,true);reason.setHint("驳回或下架时必填，最多500字");reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
  TextView error=Ui.text(a,"",13,Design.RED);form.addView(error);
  if(status.equals("pending")){
   Ui.submit(form,"通过审核",error,()->{String explanation=reason.getText().toString().trim();return ()->a.repo.moderateCommunity(type,id,"approve",explanation);},()->{a.clearDraft(route);a.message("审核已通过");a.back();});
   Ui.submit(form,"驳回内容",error,()->{String explanation=Validation.text(reason.getText().toString(),500,"驳回原因");return ()->a.repo.moderateCommunity(type,id,"reject",explanation);},()->{a.clearDraft(route);a.message("已驳回");a.back();});
  }else Ui.submit(form,"下架违规内容",error,()->{String explanation=Validation.text(reason.getText().toString(),500,"下架原因");return ()->a.repo.removeCommunityAsAdmin(type,id,explanation);},()->{a.clearDraft(route);a.message("内容已下架");a.back();});
 }
 private static String content(CommunityComment comment){return comment.deleted()?"已删除":comment.content();}
 private CommunityAdminScreen(){}
}
