package cn.edu.bjtu.foodtime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    public LocalRepository repo;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); Design.init(this); repo=new LocalRepository(this);
        LinearLayout content=Ui.col(this); content.setPadding(Ui.dp(this,14),Ui.dp(this,32),Ui.dp(this,14),0);content.setBackgroundColor(Design.PAPER);
        content.addView(Ui.title(this,"北交干饭吧",36));Ui.gap(content,16);content.addView(Ui.note(this,"今天食堂不开盲盒"));setContentView(content);
    }
}
