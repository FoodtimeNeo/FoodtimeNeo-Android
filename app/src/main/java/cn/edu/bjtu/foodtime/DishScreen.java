package cn.edu.bjtu.foodtime;
import android.widget.*;
import java.util.*;
import cn.edu.bjtu.foodtime.Models.*;

final class DishScreen {
    static void dishes(MainActivity a,LinearLayout page,MainActivity.Route route) {
        Stall stall=a.repo.stall(route.id());if(stall==null||!stall.active())throw new IllegalArgumentException("档口不存在或已停用");
        a.heading(page,"01.2",stall.name(),"只看名称与价格，先把选择变简单");
        EditText search=new EditText(a);search.setHint("搜索这家档口的菜品");search.setTextSize(16);search.setSingleLine(true);search.setPadding(Ui.dp(a,12),0,Ui.dp(a,12),0);search.setBackground(Ui.shape(a,Design.PAPER,Design.INK,0));page.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(a,48)));Ui.gap(page,16);
        LinearLayout list=Ui.col(a);page.addView(list);List<Dish> all=a.repo.dishes(stall.id());Runnable filter=()->{list.removeAllViews();String q=search.getText().toString().trim();for(Dish dish:all)if(q.isEmpty()||dish.name().contains(q)){LinearLayout row=Ui.card(list);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,12));TextView n=Ui.title(a,dish.name(),21);row.addView(n,new LinearLayout.LayoutParams(0,-2,1));TextView price=Ui.text(a,"¥"+dish.price().setScale(2),16,Design.RED);((LinearLayout)row).addView(price);row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->a.go("detail",dish.id()));}};Ui.observe(search,filter);filter.run();if(all.isEmpty())Ui.empty(page,"还没有菜品","档口菜单更新后，会出现在这里。");
    }
    static void detail(MainActivity a,LinearLayout page,MainActivity.Route route) {
        Dish d=a.repo.dish(route.id());if(d==null||!d.active())throw new IllegalArgumentException("菜品不存在或已下架");Stall s=a.repo.stall(d.stallId());Hall h=a.repo.hall(s.diningHallId());
        a.heading(page,"01.3",d.name(),h.name()+" · "+s.name());
        LinearLayout hero=Ui.card(page);hero.setPadding(0,0,0,Ui.dp(a,4));hero.addView(Ui.image(a,a.repo.photos(d.id()).isEmpty()?"":a.repo.photos(d.id()).get(0).imageUrl(),"暂未上传菜品图片",true));
        LinearLayout heroText=Ui.row(a);heroText.setPadding(Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,8));heroText.addView(Ui.title(a,"¥"+d.price().setScale(2),25),new LinearLayout.LayoutParams(0,-2,1));heroText.addView(Ui.stamp(a,a.repo.photos(d.id()).isEmpty()?"暂无图片":"图册",Design.BLUE));hero.setOnClickListener(v->a.go("gallery",d.id()));hero.addView(heroText);
        LinearLayout score=Ui.card(page);score.addView(Ui.title(a,"评分",22));Ui.gap(score,8);List<Review> reviews=a.repo.reviews(d.id());int sum=0;for(Review r:reviews)sum+=r.rating();float avg=reviews.isEmpty()?0:sum/(float)reviews.size();score.addView(Ui.text(a,reviews.isEmpty()?"暂无评分":"★".repeat(Math.round(avg))+"  "+String.format(Locale.ROOT,"%.1f / 5",avg),22,reviews.isEmpty()?Design.MUTED:Design.RED));score.addView(Ui.text(a,reviews.isEmpty()?"等第一位同学来写评价":"来自"+reviews.size()+"条已通过评价",14,Design.MUTED));
        LinearLayout action=Ui.row(a);action.setGravity(android.view.Gravity.CENTER);action.addView(Ui.button(a,"写点评",true,()->a.go("review",d.id())),new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));action.addView(Ui.button(a,"图册",false,()->a.go("gallery",d.id())),new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));page.addView(action);Ui.gap(page,20);
        page.addView(Ui.title(a,"同学怎么说",25));Ui.gap(page,12);if(reviews.isEmpty())Ui.empty(page,"评论区还很安静","吃完饭回来写两句，给下一位同学一个参考。");for(Review r:reviews){LinearLayout c=Ui.card(page);c.addView(Ui.text(a,r.userId(),12,Design.BLUE));c.addView(Ui.text(a,"★".repeat(r.rating()),16,Design.RED));Ui.gap(c,5);c.addView(Ui.text(a,r.content(),16,Design.INK));}
    }
}
