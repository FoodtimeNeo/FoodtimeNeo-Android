package cn.edu.bjtu.foodtime;
import android.widget.*;
import android.view.*;
import cn.edu.bjtu.foodtime.Models.*;

final class DiningScreen {
    static void halls(MainActivity a,LinearLayout page,MainActivity.Route route) {
        a.heading(page,"01","食堂手账","今天食堂不开盲盒");
        if(a.repo.halls().isEmpty()){Ui.empty(page,"还没有收录食堂","食堂信息上新后，会出现在这里。");return;}
        for(Hall hall:a.repo.halls())cover(a,page,hall.name(),hall.coverImageUrl(),()->a.go("stalls",hall.id()));
    }
    static void cover(MainActivity a,LinearLayout page,String name,String image,Runnable action){
        LinearLayout card=Ui.card(page);card.setPadding(0,0,0,Ui.dp(a,4));
        card.addView(Ui.image(a,image,"暂未上传封面",true));
        LinearLayout label=Ui.row(a);label.setPadding(Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,12));
        label.addView(Ui.title(a,name,24),new LinearLayout.LayoutParams(0,-2,1));label.addView(Ui.text(a,"↗",24,Design.RED));card.addView(label);
        card.setFocusable(true);card.setClickable(true);card.setContentDescription(name+"，查看");card.setOnClickListener(v->action.run());
    }
}
