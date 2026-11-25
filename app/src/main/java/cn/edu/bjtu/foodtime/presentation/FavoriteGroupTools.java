package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** The default group is the existing unfiled view, not an extra server-side membership. */
final class FavoriteGroupTools {
  private FavoriteGroupTools() {}

  static void create(MainActivity a, String kind, Runnable done) {
    LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,20),0,Ui.dp(a,20),0);
    EditText input=Ui.field(body,"分组名称（1–20 个字符）",false,false);
    TextView error=Ui.text(a,"",14,Design.RED);body.addView(error);
    AlertDialog dialog=new AlertDialog.Builder(a).setTitle(kind.equals("post")?"新建帖子分组":"新建菜品分组")
        .setView(body).setNegativeButton("取消",null).setPositiveButton("新建",null).create();
    dialog.setOnShowListener(ignored->{
      styleDialog(a,dialog);
      dialog.getButton(-1).setOnClickListener(button->{
        String name=input.getText().toString().strip();
        if(name.isEmpty()||name.codePointCount(0,name.length())>20){error.setText("分组名称须为 1–20 个字符");return;}
        a.request(button,error,()->a.repo.createFavoriteGroup(kind,name),()->{dialog.dismiss();done.run();});
      });
    });dialog.show();
  }

  static void choose(MainActivity a,String kind,String targetId,Runnable done) {
    AtomicReference<List<FavoriteGroup>> groups=new AtomicReference<>();
    AtomicReference<List<String>> joined=new AtomicReference<>();
    a.read("favorite-membership:"+UUID.randomUUID(),null,null,()->{
      groups.set(a.repo.favoriteGroups(kind));joined.set(a.repo.favoriteGroupMembership(kind,targetId));
    },()->{
      List<FavoriteGroup> snapshot=List.copyOf(groups.get());
      String[] labels=new String[snapshot.size()+1];boolean[] original=new boolean[labels.length];
      labels[0]="默认分组（未加入自建分组）";original[0]=joined.get().isEmpty();
      for(int i=0;i<snapshot.size();i++){labels[i+1]=snapshot.get(i).name();original[i+1]=joined.get().contains(snapshot.get(i).id());}
      boolean[] desired=original.clone();
      LinearLayout body=Ui.col(a);TextView error=Ui.text(a,"",14,Design.RED);body.addView(error);
      AlertDialog dialog=new AlertDialog.Builder(a).setTitle("设置收藏分组")
          .setMultiChoiceItems(labels,desired,(choice,index,value)->{
            var list=((AlertDialog)choice).getListView();
            if(index==0&&value){for(int i=1;i<desired.length;i++){desired[i]=false;list.setItemChecked(i,false);}}
            boolean any=false;for(int i=1;i<desired.length;i++)any|=desired[i];
            desired[0]=!any;list.setItemChecked(0,desired[0]);
          }).setView(body).setNegativeButton("取消",null).setNeutralButton("+ 新建分组",(choice,which)->
              create(a,kind,()->choose(a,kind,targetId,done)))
          .setPositiveButton("保存",null).create();
      dialog.setOnShowListener(ignored->{
        styleDialog(a,dialog);
        dialog.getButton(-1).setOnClickListener(button->a.request(button,error,()->{
          // Android mutates the array passed to setMultiChoiceItems. Keep the original selection separate.
          for(int i=0;i<snapshot.size();i++){
            if(original[i+1]==desired[i+1])continue;
            if(desired[i+1])a.repo.addFavoriteGroupMember(snapshot.get(i).id(),targetId);
            else a.repo.removeFavoriteGroupMember(snapshot.get(i).id(),targetId);
          }
        },()->{dialog.dismiss();done.run();}));
      });dialog.show();
    },()->{});
  }

  private static void styleDialog(MainActivity a,AlertDialog dialog){
    if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(Ui.shape(a,Design.PAPER,Design.BORDER,20));
    var positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    if(positive!=null)positive.setTextColor(Design.RED);
    var negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if(negative!=null)negative.setTextColor(Design.MUTED);
    var neutral=dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
    if(neutral!=null)neutral.setTextColor(Design.INK);
  }
}
