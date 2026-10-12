package cn.edu.bjtu.foodtime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.graphics.Color;
import java.util.*;
import cn.edu.bjtu.foodtime.Models.*;

public class MainActivity extends Activity {
    public LocalRepository repo;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); Design.init(this); repo=new LocalRepository(this);
        if(repo.currentUser()==null)showAuth(false);else openApp();
    }
    LinearLayout page() { ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=Ui.col(this);page.setPadding(Ui.dp(this,14),Ui.dp(this,24),Ui.dp(this,14),Ui.dp(this,24));page.setBackgroundColor(Design.PAPER);scroll.addView(page);setContentView(scroll);return page; }
    public void showAuth(boolean register){AuthScreen.show(this,page(),register);}
    public void openApp(){LinearLayout p=page();p.addView(Ui.title(this,"食堂手账",36));Ui.link(p,"退出登录",()->{repo.logout();showAuth(false);});}
    public void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
