package cn.edu.bjtu.foodtime.presentation;

import android.view.Gravity;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.CatalogImagePage;

final class CatalogQuickActions {
  static boolean allowed(MainActivity a) {
    return a.repo.currentUser()!=null && "superadmin".equals(a.repo.currentUser().role());
  }
  static void show(MainActivity a,LinearLayout page,String table,String id) {
    if (!allowed(a)) return;
    String label=table.equals("halls")?"食堂":table.equals("stalls")?"档口":"菜品";
    Ui.action(page,"管理"+label+" · 编辑 / 图片",false,() -> a.go("admin-edit",table+"~"+id));
    Ui.gap(page,12);
  }
  static void images(MainActivity a,LinearLayout page,String table,String id) {
    if (!allowed(a)) return;
    LinearLayout card=Ui.card(page);
    card.addView(Ui.title(a,table.equals("halls")?"食堂封面":"图片管理",22));
    String key=a.draftKey("managed:image"),image=a.drafts.getString(key,"");
    LinearLayout contents=Ui.col(a); card.addView(contents);
    TextView error=Ui.text(a,"",14,Design.RED); card.addView(error);
    int[] currentPage={a.drafts.getInt(a.draftKey("image-page"),1)};
    Runnable[] load={null};
    load[0]=() -> {
      contents.removeAllViews(); contents.addView(Ui.text(a,"正在读取图片…",14,Design.MUTED));
      CatalogImagePage[] result={null};
      a.read("catalog-images:"+table+":"+id,null,error,
          () -> result[0]=a.repo.managedImages(table,id,currentPage[0]),() -> {
            contents.removeAllViews();
            if(result[0].items().isEmpty()) contents.addView(Ui.text(a,"暂未添加图片",14,Design.MUTED));
            ScrollView viewport=new ScrollView(a); LinearLayout rows=Ui.col(a); viewport.addView(rows);
            for(var photo:result[0].items()) {
              LinearLayout row=Ui.row(a); row.setGravity(Gravity.CENTER_VERTICAL);
              row.addView(Ui.image(a,Ui.thumb(photo.imageUrl()),"目录图片",false),new LinearLayout.LayoutParams(Ui.dp(a,88),Ui.dp(a,72)));
              TextView remove=Ui.button(a,"移除图片",false,() -> Ui.confirm(a,"移除这张图片？","此图片将从当前目录移除。",
                  () -> a.read("catalog-image-remove:"+photo.id(),null,error,
                      () -> a.repo.removeManagedImage(table,id,photo.id()),() -> {
                        if(result[0].items().size()==1&&currentPage[0]>1) currentPage[0]--;
                        a.drafts.putInt(a.draftKey("image-page"),currentPage[0]); a.render();
                      },() -> {})));
              row.addView(remove,new LinearLayout.LayoutParams(0,-2,1)); rows.addView(row); Ui.gap(rows,8);
            }
            if(!result[0].items().isEmpty()) contents.addView(viewport,new LinearLayout.LayoutParams(-1,Ui.dp(a,Math.min(240,result[0].items().size()*80))));
            LinearLayout navigation=Ui.row(a);
            if(currentPage[0]>1) navigation.addView(Ui.button(a,"上一页",false,() -> {currentPage[0]--;load[0].run();}));
            if(result[0].hasMore()) navigation.addView(Ui.button(a,"下一页",false,() -> {currentPage[0]++;load[0].run();}));
            contents.addView(navigation);
          },() -> {
            contents.removeAllViews(); Ui.action(contents,"重新读取图片",false,load[0]);
          });
    };
    load[0].run();
    if(!image.isBlank()) card.addView(Ui.image(a,image,"待添加图片",true),new LinearLayout.LayoutParams(-1,Ui.dp(a,150)));
    Ui.action(card,image.isBlank()?"选择图片":"更换待添加图片",false,() -> a.pickImage(key));
    if(!image.isBlank()) Ui.submit(card,table.equals("halls")?"保存封面":"添加图片",error,() -> () -> a.repo.addManagedImage(table,id,image),() -> {
      a.drafts.remove(key); PhotoImport.discard(a,image); a.message("图片已保存"); a.render();
    });
  }
}
