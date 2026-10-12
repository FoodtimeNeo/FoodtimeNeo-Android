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
}
