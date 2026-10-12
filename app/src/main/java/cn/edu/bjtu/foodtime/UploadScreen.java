package cn.edu.bjtu.foodtime;
import android.widget.*;
import cn.edu.bjtu.foodtime.Models.*;
final class UploadScreen {
    static void photo(MainActivity a,LinearLayout page,MainActivity.Route route){Dish dish=a.repo.dish(route.id());a.heading(page,"01.4","贴一张菜品照片",dish.name());LinearLayout card=Ui.card(page);String key=a.draftKey("image");String image=a.drafts.getString(key,"");card.addView(Ui.image(a,image,"请选择菜品实拍图片",true));Ui.gap(card,16);Ui.action(card,image.isBlank()?"从相册选择":"更换图片",false,()->a.pickImage(key));Ui.gap(card,16);card.addView(Ui.text(a,"提交后进入待审核状态，审核通过后展示在图册。",14,Design.MUTED));Ui.gap(card,16);Ui.action(card,"提交图片",true,()->a.safely(()->{a.repo.submitPhoto(dish.id(),image);a.drafts.remove(key);a.message("图片已提交，等待审核");a.back();}));}
}
