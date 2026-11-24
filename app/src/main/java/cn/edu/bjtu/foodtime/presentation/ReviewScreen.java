package cn.edu.bjtu.foodtime.presentation;

import android.content.res.ColorStateList;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.concurrent.atomic.AtomicReference;

/** One review composer shared by dish details and the dining-home quick entry. */
final class ReviewScreen {
  private record Target(String id, String name, String hallName, String stallName) {}

  static View createButton(MainActivity a) {
    ImageButton button=CommunityScreen.iconButton(a,android.R.drawable.ic_menu_edit,"一键点评",
        () -> a.go("quick-review",""));
    button.setImageTintList(ColorStateList.valueOf(Design.ON_RED));
    button.setBackground(Ui.ripple(a,Design.RED,0,28));
    button.setElevation(Ui.dp(a,5));
    button.setTag("quick-review-entry");
    return button;
  }

  static void create(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish dish=a.repo.dish(route.id());
    if(dish==null)throw new IllegalArgumentException("菜品不存在");
    Stall stall=a.repo.stall(dish.stallId());
    Hall hall=stall==null?null:a.repo.hall(stall.diningHallId());
    a.heading(page,"","写点评",dish.name());
    composer(a,page,route,new Target(dish.id(),dish.name(),hall==null?"":hall.name(),stall==null?"":stall.name()));
  }

  static void quick(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page,"","一键点评","选好这道菜，说说你的真实感受。");

    LinearLayout picker=Ui.card(page);
    picker.addView(Ui.title(a,"按食堂查找",20));
    Ui.gap(picker,8);
    DishPickerScreen.showRequired(a,picker,()->{resetSubmissionState(a);a.redraw();});
    Ui.gap(page,14);

    LinearLayout searchCard=Ui.card(page);
    searchCard.addView(Ui.title(a,"直接搜索菜品",20));
    Ui.gap(searchCard,8);
    EditText query=Ui.field(searchCard,"菜品名称",false,false);
    String queryKey=a.draftKey("quickSearch");
    query.setText(a.drafts.getString(queryKey,""));
    query.setHint("输入菜名，例如：麻辣烫");
    query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
    Ui.observe(query,()->a.drafts.putString(queryKey,query.getText().toString()));
    LinearLayout results=Ui.col(a);
    TextView status=Ui.text(a,"",13,Design.MUTED);
    TextView error=Ui.text(a,"",13,Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    TextView search=Ui.button(a,"搜索菜品",false,()->{});
    search.setTag("quick-review-search");
    search.setOnClickListener(v->{
      String keyword=query.getText().toString().strip();
      if(keyword.isBlank()){error.setText("请输入菜品名称");return;}
      AtomicReference<DishSearchPage> response=new AtomicReference<>();
      status.setText("正在查找菜品…");results.removeAllViews();
      a.read("quick-review-search:"+keyword,v,error,
          ()->response.set(a.repo.searchDishes(keyword,1)),
          ()->{
            DishSearchPage data=response.get();status.setText(data.items().isEmpty()?"没有匹配的菜品":"请选择要点评的菜品");
            for(DishSearchRow dish:data.items())DishSearchRows.appendRow(a,results,dish,()->{
              storeTarget(a,dish.id(),dish.name(),dish.diningHallName(),dish.stallName());a.redraw();
            });
          },()->{if(response.get()==null)status.setText("");});
    });
    searchCard.addView(search);searchCard.addView(error);searchCard.addView(status);searchCard.addView(results);

    Target target=selectedTarget(a);
    if(target==null){Ui.gap(page,18);Ui.empty(page,"先选一道菜","可以逐级选择，也可以直接搜索。");return;}
    Ui.gap(page,18);
    LinearLayout chosen=Ui.card(page);
    chosen.addView(Ui.text(a,"正在点评",13,Design.MUTED));
    chosen.addView(Ui.title(a,target.name(),22));
    String location=location(target);
    if(!location.isBlank()){Ui.gap(chosen,5);chosen.addView(Ui.text(a,location,14,Design.MUTED));}
    Ui.gap(page,14);
    composer(a,page,route,target);
  }

  private static Target selectedTarget(MainActivity a) {
    String base=a.draftKey("")+"linkedDish";
    String id=a.drafts.getString(base+"Id","");
    String name=a.drafts.getString(base+"Name","");
    String hall=a.drafts.getString(base+"HallName","");
    String stall=a.drafts.getString(base+"StallName","");
    return id.isBlank()?null:new Target(id,name.isBlank()?"已选菜品":name,hall,stall);
  }

  private static void storeTarget(MainActivity a,String id,String name,String hall,String stall) {
    String base=a.draftKey("")+"linkedDish";
    if(!id.equals(a.drafts.getString(base+"Id","")))resetSubmissionState(a);
    a.drafts.putString(base+"Id",id);a.drafts.putString(base+"Name",name);
    a.drafts.putString(base+"HallName",hall);a.drafts.putString(base+"StallName",stall);
  }

  private static String location(Target target) {
    if(target.hallName().isBlank())return target.stallName();
    if(target.stallName().isBlank())return target.hallName();
    return target.hallName()+" · "+target.stallName();
  }

  private static void resetSubmissionState(MainActivity a) {
    a.drafts.remove(a.draftKey("reviewSubmitted"));
    a.drafts.remove(a.draftKey("photoSubmitted"));
    a.drafts.remove(a.draftKey("reviewSubmissionId"));
    a.drafts.remove(a.draftKey("reviewUploadedPhoto"));
    a.drafts.remove(a.draftKey("reviewUploadedSource"));
  }

  private static void composer(MainActivity a,LinearLayout page,MainActivity.Route route,Target target) {
    LinearLayout card=Ui.card(page);
    card.setTag("review-composer");
    card.addView(Ui.title(a,"给菜品打分",24));
    Ui.gap(card,16);
    String ratingKey=a.draftKey("rating");
    int[] rating={a.drafts.getInt(ratingKey,0)};
    LinearLayout stars=Ui.row(a);card.addView(stars);
    TextView[] buttons=new TextView[5];
    Runnable paint=()->{
      for(int i=0;i<5;i++){
        buttons[i].setText(i<rating[0]?"★":"☆");
        buttons[i].setTextColor(i<rating[0]?Design.RED:Design.MUTED);
        buttons[i].setContentDescription((i+1)+"星"+(rating[0]==i+1?"，已选中":""));
      }
    };
    for(int i=0;i<5;i++){
      int n=i+1;
      buttons[i]=Ui.button(a,"☆",false,()->{rating[0]=n;a.drafts.putInt(ratingKey,n);paint.run();});
      buttons[i].setTextSize(28);buttons[i].setPadding(0,0,0,0);
      buttons[i].setBackground(Ui.ripple(a,Design.PAPER,0,0));
      stars.addView(buttons[i],new LinearLayout.LayoutParams(0,Ui.dp(a,56),1));
    }
    paint.run();Ui.gap(card,20);

    String contentKey=a.draftKey("content");
    EditText text=Ui.field(card,"评价内容",false,true);
    text.setText(a.drafts.getString(contentKey,""));
    text.setHint("最多500字");
    text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
    Ui.observe(text,()->a.drafts.putString(contentKey,text.getText().toString()));
    Ui.gap(card,18);

    card.addView(Ui.text(a,"添加菜品实拍（可选）",14,Design.MUTED));
    String imageKey=a.draftKey("image");
    String image=a.drafts.getString(imageKey,"");
    card.addView(Ui.image(a,image,image.isBlank()?"可选上传一张菜品实拍":"待审核的菜品实拍",true));
    Ui.gap(card,10);
    Ui.action(card,image.isBlank()?"从相册选择图片":"更换图片",false,()->a.pickImage(imageKey));
    if(!image.isBlank()){
      Ui.gap(card,8);
      Ui.action(card,"移除图片",false,()->{a.discardPhoto(image);a.drafts.remove(imageKey);a.redraw();});
    }
    String syncKey=a.draftKey("syncCommunity");
    CheckBox sync=new CheckBox(a);sync.setText("提交后前往社区发帖");
    sync.setChecked(a.drafts.getBoolean(syncKey,false));
    sync.setOnCheckedChangeListener((button,checked)->a.drafts.putBoolean(syncKey,checked));
    card.addView(sync);
    Ui.gap(card,14);
    TextView error=Ui.text(a,"",14,Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);Ui.gap(card,8);
    String reviewDone=a.draftKey("reviewSubmitted"),photoDone=a.draftKey("photoSubmitted");
    String submissionId=a.draftKey("reviewSubmissionId"),uploadedKey=a.draftKey("reviewUploadedPhoto");
    Ui.submit(card,"提交点评",error,()->{
      int score=rating[0];String content=text.getText().toString().strip();
      String selectedImage=a.drafts.getString(imageKey,"");
      if(score<1||content.isBlank())throw new IllegalArgumentException("请选择评分并填写评价");
      if(a.drafts.getBoolean(reviewDone,false) && (!content.equals(a.drafts.getString(a.draftKey("submittedContent"),"")) || score!=a.drafts.getInt(a.draftKey("submittedRating"),0)))
        throw new IllegalArgumentException("点评已提交，请恢复原评分和文字后重试图片");
      return ()->{
        if(!a.drafts.getBoolean(reviewDone,false)){
          String id=a.repo.submitReviewTracked(target.id(),score,content);
          a.drafts.putString(a.draftKey("submittedContent"),content);a.drafts.putInt(a.draftKey("submittedRating"),score);
          a.drafts.putString(submissionId,id);a.drafts.putBoolean(reviewDone,true);
        }
        if(!selectedImage.isBlank()&&!a.drafts.getBoolean(photoDone,false)){
          try{
            String uploaded=a.drafts.getString(uploadedKey,"");
            String sourceKey=a.draftKey("reviewUploadedSource");
            if(!selectedImage.equals(a.drafts.getString(sourceKey,"")))uploaded="";
            if(uploaded.isBlank()){uploaded=a.repo.uploadReviewPhoto(selectedImage);a.drafts.putString(uploadedKey,uploaded);a.drafts.putString(sourceKey,selectedImage);}
            a.repo.submitReviewPhoto(target.id(),uploaded,a.drafts.getString(submissionId,""));a.drafts.putBoolean(photoDone,true);
          }
          catch(RuntimeException failure){throw new IllegalStateException("点评已提交；图片暂未提交成功，请重试",failure);}
        }
      };
    },()->{
      boolean withPhoto=!a.drafts.getString(imageKey,"").isBlank();
      if(sync.isChecked()) {
        String body=text.getText().toString().strip(), photo=a.drafts.getString(imageKey,"");
        openCommunity(a,route,target,body,photo,imageKey);
      } else {a.clearDraft(route);a.message(withPhoto?"点评和图片已提交审核":"点评已提交审核");a.back();}
    });
  }

  private static void openCommunity(MainActivity a,MainActivity.Route route,Target target,String content,String photo,String imageKey) {
    Runnable proceed=()->{
      // Transfer ownership of the selected local file to the community draft.
      if(!photo.isBlank())a.drafts.remove(imageKey);
      a.clearDraft(route);
      a.clearDraft(new MainActivity.Route("community-create",""));
      String base="community-create::";
      String title=target.name()+"品鉴心得";
      String safeTitle=title.length()>50?title.substring(0,50):title;
      a.drafts.putString(base+"食记标题",safeTitle);
      a.drafts.putString(base+"动态正文",content);
      a.drafts.putString(base+"linkedDishId",target.id());
      a.drafts.putString(base+"linkedDishName",target.name());
      a.drafts.putString(base+"linkedDishHallName",target.hallName());
      a.drafts.putString(base+"linkedDishStallName",target.stallName());
      if(!photo.isBlank())a.drafts.putString(base+"photo:0:image",photo);
      if(a.repo.currentUser()!=null)PostDraftStore.save(a,a.repo.currentUser().id(),safeTitle,content,target.id(),photo.isBlank()?java.util.List.of():java.util.List.of(photo));
      a.go("community-create","");
    };
    String owner=a.repo.currentUser()==null?"":a.repo.currentUser().id();
    if(PostDraftStore.load(a,owner)==null){proceed.run();return;}
    new android.app.AlertDialog.Builder(a).setMessage("已有社区草稿，是否替换？")
        .setPositiveButton("替换并继续",(dialog,which)->{PostDraftStore.delete(a,owner);proceed.run();})
        .setNegativeButton("保留草稿",(dialog,which)->{a.clearDraft(route);a.back();a.message("点评已提交，社区草稿已保留");}).show();
  }
}
