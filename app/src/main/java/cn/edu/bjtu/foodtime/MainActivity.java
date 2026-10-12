package cn.edu.bjtu.foodtime;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    public LocalRepository repo;
    public record Route(String kind,String id) {}
    public interface Screen { void show(MainActivity activity,LinearLayout page,Route route); }
    final Map<String,Screen> screens=new HashMap<>();
    final Map<String,ArrayList<Route>> stacks=new HashMap<>();
    String tab="dining";
    LinearLayout root,content,header;
    ScrollView scroll;
    final Bundle drafts=new Bundle();
    String pickingKey;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);Design.init(this);repo=new LocalRepository(this);registerScreens();
        for(String key:List.of("dining","profile","admin"))stacks.put(key,new ArrayList<>(List.of(new Route(key,""))));
        if(state!=null){tab=state.getString("tab","dining");for(String key:stacks.keySet()){ArrayList<String> saved=state.getStringArrayList(key);if(saved!=null&&!saved.isEmpty()){ArrayList<Route> routes=new ArrayList<>();for(String value:saved){String[] p=value.split("\\|",2);routes.add(new Route(p[0],p.length>1?p[1]:""));}stacks.put(key,routes);}}}
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::back);
        if(state!=null){Bundle saved=state.getBundle("drafts");if(saved!=null)drafts.putAll(saved);pickingKey=state.getString("pickingKey");}
        if(repo.currentUser()==null)showAuth(false);else openApp();
    }
    void registerScreens() {screens.put("dining",DiningScreen::halls);screens.put("stalls",DiningScreen::stalls);screens.put("dishes",DishScreen::dishes);screens.put("gallery",DishScreen::gallery);screens.put("upload-photo",UploadScreen::photo);screens.put("submit-dish",UploadScreen::dish);screens.put("submit-stall",UploadScreen::stall);screens.put("mine-submissions",SubmissionScreen::mine);screens.put("profile",ProfileScreen::profile);screens.put("mine-reviews",ProfileScreen::reviews);screens.put("detail",DishScreen::detail);screens.put("review",ReviewScreen::create);}
    public void showAuth(boolean register) {shell(false);header.addView(Ui.stamp(this,"FOODTIME / 校园食堂手账",Design.RED));AuthScreen.show(this,content,register);}
    public void openApp() {if(repo.currentUser()==null){showAuth(false);return;}if(tab.equals("admin")&&!repo.currentUser().isAdmin())tab="dining";render();}
    public void go(String kind,String id){stacks.get(tab).add(new Route(kind,id));render();}
    public Route route(){ArrayList<Route> s=stacks.get(tab);return s.get(s.size()-1);}
    public void home(){tab="dining";stacks.get(tab).clear();stacks.get(tab).add(new Route("dining",""));render();}
    public void selectTab(String next){if(tab.equals(next))return;tab=next;render();}
    public void back(){if(repo.currentUser()==null){finish();return;}ArrayList<Route> s=stacks.get(tab);if(s.size()>1){s.remove(s.size()-1);render();}else if(!tab.equals("dining")){tab="dining";render();}else finish();}
    @Override public void onBackPressed(){back();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBundle("drafts",drafts);state.putString("pickingKey",pickingKey);state.putString("tab",tab);for(String key:stacks.keySet()){ArrayList<String> saved=new ArrayList<>();for(Route route:stacks.get(key))saved.add(route.kind()+"|"+route.id());state.putStringArrayList(key,saved);}}
    public void render(){
        if(repo.currentUser()==null){showAuth(false);return;}shell(true);
        LinearLayout bar=Ui.row(this);header.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        TextView back=Ui.button(this,"←",false,this::back);back.setContentDescription("返回上一级");if(stacks.get(tab).size()>1)bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView brand=Ui.title(this,"北交干饭吧",22);brand.setGravity(Gravity.CENTER_VERTICAL);bar.addView(brand,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView home=Ui.button(this,"⌂",false,this::home);home.setContentDescription("回到食堂首页");bar.addView(home,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        try{Screen s=screens.get(route().kind());if(s==null){content.addView(Ui.title(this,tab.equals("profile")?"干饭档案":tab.equals("admin")?"管理手账":"食堂手账",36));}else s.show(this,content,route());}
        catch(RuntimeException e){Ui.empty(content,"这一页暂时没打开",e.getMessage()==null?"请稍后重试":e.getMessage());Ui.action(content,"重新加载",true,this::render);}
    }
    private void shell(boolean navigation) {
        root=Ui.col(this);root.setBackgroundColor(Design.BAR);
        root.setOnApplyWindowInsetsListener((v,insets)->{int left,top,right,bottom;if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets sys=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());left=sys.left;top=sys.top;right=sys.right;bottom=sys.bottom;}else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}root.setPadding(left,top,right,bottom);return insets;});
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().getDecorView().getWindowInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        header=Ui.col(this);header.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        View rule=new View(this);rule.setBackgroundColor(Design.BORDER);root.addView(rule,new LinearLayout.LayoutParams(-1,Ui.dp(this,1)));
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=Ui.col(this);content.setBackgroundColor(Design.PAPER);content.setPadding(Ui.dp(this,14),Ui.dp(this,24),Ui.dp(this,14),Ui.dp(this,24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(navigation){LinearLayout nav=Ui.row(this);nav.setBackground(Ui.shape(this,Design.PAPER,Design.BORDER,0));addTab(nav,"01","食堂","dining");if(repo.currentUser().isAdmin())addTab(nav,"02","管理","admin");addTab(nav,repo.currentUser().isAdmin()?"03":"02","我的","profile");root.addView(nav,new LinearLayout.LayoutParams(-1,-2));}
        setContentView(root);root.requestApplyInsets();
    }
    private void addTab(LinearLayout nav,String index,String label,String key){TextView b=Ui.button(this,index+"\n"+label,false,()->selectTab(key));b.setTextColor(tab.equals(key)?Design.RED:Design.MUTED);b.setBackground(Ui.ripple(this,tab.equals(key)?Design.ALT:Design.PAPER,0,0));b.setContentDescription(label+(tab.equals(key)?"，已选中":""));nav.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,72),1));}
    public void heading(LinearLayout page,String index,String title,String note){page.addView(Ui.stamp(this,index+" / FOODTIME",Design.RED));Ui.gap(page,16);page.addView(Ui.title(this,title,36));Ui.gap(page,8);if(!note.isBlank())page.addView(Ui.note(this,note));Ui.gap(page,24);}
    public void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    public String draftKey(String label){return route().kind()+":"+route().id()+":"+label;}
    public void pickImage(String key){pickingKey=key;android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);startActivityForResult(intent,101);}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request==101&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pickingKey!=null){safely(()->{try(java.io.InputStream input=getContentResolver().openInputStream(data.getData())){java.io.File file=new java.io.File(getFilesDir(),"photo-"+java.util.UUID.randomUUID()+".jpg");try(java.io.FileOutputStream output=new java.io.FileOutputStream(file)){byte[] buffer=new byte[8192];int count;long total=0;while((count=input.read(buffer))!=-1){total+=count;if(total>20*1024*1024){output.close();file.delete();throw new IllegalArgumentException("图片不能超过20MB");}output.write(buffer,0,count);}}android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(options.outWidth<=0){file.delete();throw new IllegalArgumentException("请选择可读取的图片");}drafts.putString(pickingKey,android.net.Uri.fromFile(file).toString());render();}catch(java.io.IOException e){throw new IllegalStateException("图片读取失败，请重新选择",e);}});}}
    public void safely(Runnable action){try{action.run();}catch(RuntimeException e){message(e.getMessage()==null?"操作失败，请重试":e.getMessage());}}
    public void logout(){repo.logout();for(String key:stacks.keySet()){stacks.get(key).clear();stacks.get(key).add(new Route(key,""));}tab="dining";showAuth(false);}
}
