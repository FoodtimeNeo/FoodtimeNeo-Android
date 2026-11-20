package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.map.CampusMapGeometry;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** Bundled map and independent catalog links. The map remains useful when catalog reads fail. */
final class CampusMapScreen {
  record Region(String id, String name, float[][] points, List<String> halls) {}
  static List<Region> regions(MainActivity activity) {
    try (var input = activity.getAssets().open("campus-map/regions.json")) {
      JSONObject data = new JSONObject(readUtf8(input));
      JSONArray rows = data.getJSONArray("regions");
      List<Region> result = new ArrayList<>();
      for (int i = 0; i < rows.length(); i++) {
        var row = rows.getJSONObject(i); var polygon = row.getJSONArray("polygon");
        float[][] points = new float[polygon.length()][2];
        for (int j = 0; j < polygon.length(); j++) {
          points[j][0] = (float) polygon.getJSONArray(j).getDouble(0);
          points[j][1] = (float) polygon.getJSONArray(j).getDouble(1);
        }
        List<String> halls = new ArrayList<>(); var ids = row.getJSONArray("diningHallIds");
        for (int j = 0; j < ids.length(); j++) halls.add(ids.getString(j));
        result.add(new Region(row.getString("id"), row.getString("name"), points, halls));
      }
      return result;
    } catch (Exception failure) { throw new IllegalStateException("地图资料暂时无法读取", failure); }
  }

  private static String readUtf8(java.io.InputStream input) throws java.io.IOException {
    var output = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    return output.toString(StandardCharsets.UTF_8.name());
  }

  static void show(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "", "食堂地图", "双指缩放、拖动地图，点选食堂查看详情。");
    List<Region> regions = regions(a);
    String key = a.draftKey("selected");
    LinearLayout details = Ui.col(a);
    MapView map = new MapView(a, regions);
    map.setTag("campus-map-canvas");
    page.addView(map, new LinearLayout.LayoutParams(-1, Ui.dp(a, 380)));
    LinearLayout controls = Ui.row(a);
    String[] labels = {"放大", "缩小", "全图"};
    Runnable[] actions = {() -> map.zoomBy(1.5f), () -> map.zoomBy(1 / 1.5f), map::reset};
    for (int i = 0; i < labels.length; i++) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      if (i > 0) lp.leftMargin = Ui.dp(a, 10);
      controls.addView(Ui.button(a, labels[i], false, actions[i]), lp);
    }
    Ui.gap(page, 10); page.addView(controls); Ui.gap(page, 14);
    TextView choose = Ui.text(a, "选择食堂，也可点击地图中的轮廓", 14, Design.MUTED);
    page.addView(choose);
    Map<String,TextView> buttons = new HashMap<>();
    Consumer<Region> selected = region -> {
      a.drafts.putString(key, region.id()); map.select(region.id());
      for (var entry : buttons.entrySet()) entry.getValue().setSelected(entry.getKey().equals(region.id()));
      details.removeAllViews(); details.addView(Ui.title(a, region.name(), 22)); Ui.gap(details, 8);
      int found = 0;
      for (var hall : a.repo.halls()) if (hall.active() && region.halls().contains(hall.id())) {
        found++; Ui.action(details, hall.name(), false, () -> a.go("stalls", hall.id())); Ui.gap(details, 10);
      }
      if (found == 0) {
        details.addView(Ui.text(a, a.isPageLoading() ? "正在读取食堂入口…" :
            a.pageFailed() ? "食堂入口读取失败，地图仍可使用。" : "此区域暂无可用的食堂详情入口。", 14, Design.MUTED));
        if (a.pageFailed()) Ui.action(details, "重新读取食堂", false, a::render);
      }
    };
    map.listener = selected;
    for (Region region : regions) {
      Ui.gap(page, 8);
      TextView button = Ui.button(a, region.name(), false, () -> selected.accept(region));
      button.setTag("map-region:" + region.id()); buttons.put(region.id(), button); page.addView(button);
    }
    Ui.gap(page, 16); details.setTag("campus-map-selection"); details.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); page.addView(details);
    for (Region region : regions) if (region.id().equals(a.drafts.getString(key, ""))) selected.accept(region);
    Ui.gap(page, 18);
    page.addView(Ui.text(a, "校园手绘示意图（2023 年更新）。暂未标注教工、学苑及东区清真；暂不提供定位和路线导航。", 12, Design.MUTED));
  }

  static final class MapView extends View {
    private static final float WIDTH = 5032, HEIGHT = 3579;
    private final List<Region> regions;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scales;
    private final int slop;
    private Bitmap bitmap;
    private float zoom = 1, panX, panY, lastX, lastY, downX, downY;
    private boolean moved, decoding, failed;
    private String selection = "";
    private final MainActivity owner;
    Consumer<Region> listener;
    MapView(MainActivity a, List<Region> regions) {
      super(a); this.owner = a; this.regions = regions;
      zoom = a.drafts.getFloat("campus-map::zoom",1);panX=a.drafts.getFloat("campus-map::x",0);panY=a.drafts.getFloat("campus-map::y",0); slop = ViewConfiguration.get(a).getScaledTouchSlop();
      setBackgroundColor(0xffb3d38a); setContentDescription("校园食堂地图，可双指缩放，或使用下方食堂名称选择");
      scales = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override public boolean onScale(ScaleGestureDetector detector) {
          moved = true; scale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY()); return true;
        }
      });
    }
    @Override protected void onAttachedToWindow() {
      super.onAttachedToWindow();
      if (bitmap != null || decoding) return;
      decoding = true;
      ((cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider)getContext().getApplicationContext()).backgroundExecutor().execute(() -> {
        Bitmap decoded = null;
        try (var input = getContext().getAssets().open("campus-map/campus-map.webp")) {
          var options = new BitmapFactory.Options(); options.inSampleSize = 2;
          decoded = BitmapFactory.decodeStream(input, null, options);
        } catch (java.io.IOException ignored) { /* The selector remains usable. */ }
        Bitmap result = decoded;
        post(() -> { decoding = false; if (!isAttachedToWindow()) { if(result != null) result.recycle(); return; }
          bitmap = result; failed = result == null; invalidate(); });
      });
    }
    @Override protected void onDetachedFromWindow() {
      owner.drafts.putFloat("campus-map::zoom",zoom);owner.drafts.putFloat("campus-map::x",panX);owner.drafts.putFloat("campus-map::y",panY);
      super.onDetachedFromWindow(); if (bitmap != null) { bitmap.recycle(); bitmap = null; }
    }
    void select(String id) { selection = id; invalidate(); }
    void reset() { zoom = 1; panX = panY = 0; invalidate(); }
    void zoomBy(float factor) { scale(factor, getWidth()/2f, getHeight()/2f); }
    float zoomLevel() { return zoom; }
    private float fit() { return Math.min(getWidth()/WIDTH, getHeight()/HEIGHT); }
    private float left() { return (getWidth()-WIDTH*fit())/2; }
    private float top() { return (getHeight()-HEIGHT*fit())/2; }
    private void constrain() {
      float w = WIDTH*fit(), h = HEIGHT*fit();
      panX = CampusMapGeometry.clamp(panX, getWidth()-left()-w*zoom, -left());
      panY = CampusMapGeometry.clamp(panY, getHeight()-top()-h*zoom, -top());
      if (w*zoom <= getWidth()) panX = (getWidth()-w*zoom)/2-left();
      if (h*zoom <= getHeight()) panY = (getHeight()-h*zoom)/2-top();
    }
    private void scale(float factor, float x, float y) {
      float next = CampusMapGeometry.clamp(zoom*factor, 1, 6), ratio = next/zoom;
      panX = (x-left())-(x-left()-panX)*ratio;
      panY = (y-top())-(y-top()-panY)*ratio; zoom = next; constrain(); invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { super.onSizeChanged(w,h,oldw,oldh); constrain(); }
    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas); canvas.save(); canvas.clipRect(0,0,getWidth(),getHeight());
      canvas.translate(left()+panX,top()+panY); canvas.scale(fit()*zoom,fit()*zoom);
      paint.setStyle(Paint.Style.FILL);
      if (bitmap != null) { paint.setColor(Color.WHITE); canvas.drawBitmap(bitmap,null,new RectF(0,0,WIDTH,HEIGHT),paint); }
      for (Region region : regions) {
        Path path = new Path(); for (int i=0;i<region.points().length;i++) {
          float[] p=region.points()[i]; if(i==0)path.moveTo(p[0],p[1]);else path.lineTo(p[0],p[1]);
        } path.close();
        paint.setStyle(Paint.Style.FILL); paint.setColor(region.id().equals(selection)?0x88c9341e:0x22c9341e); canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(Design.RED);paint.setStrokeWidth(Ui.dp(getContext(),2)/(fit()*zoom));canvas.drawPath(path,paint);
      }
      canvas.restore();
      if(failed){paint.setStyle(Paint.Style.FILL);paint.setColor(Design.INK);paint.setTextSize(Ui.dp(getContext(),14));canvas.drawText("地图图片读取失败，请使用下方名称选择",Ui.dp(getContext(),12),Ui.dp(getContext(),28),paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
      scales.onTouchEvent(event);
      switch(event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN -> { downX=lastX=event.getX();downY=lastY=event.getY();moved=false;getParent().requestDisallowInterceptTouchEvent(true);return true; }
        case MotionEvent.ACTION_POINTER_DOWN -> { moved=true;return true; }
        case MotionEvent.ACTION_MOVE -> { if(event.getPointerCount()==1&&!scales.isInProgress()) {
          if(Math.hypot(event.getX()-downX,event.getY()-downY)>slop)moved=true;
          if(moved){panX+=event.getX()-lastX;panY+=event.getY()-lastY;constrain();invalidate();}
        } lastX=event.getX();lastY=event.getY();return true; }
        case MotionEvent.ACTION_POINTER_UP -> { moved=true;int index=event.getActionIndex()==0?1:0;if(index<event.getPointerCount()){lastX=event.getX(index);lastY=event.getY(index);}return true; }
        case MotionEvent.ACTION_UP -> {
          if(!moved){float x=(event.getX()-left()-panX)/(fit()*zoom),y=(event.getY()-top()-panY)/(fit()*zoom);
            for(Region region:regions)if(CampusMapGeometry.contains(region.points(),x,y)){select(region.id());if(listener!=null)listener.accept(region);break;}performClick();}
          getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        case MotionEvent.ACTION_CANCEL -> { moved=true;getParent().requestDisallowInterceptTouchEvent(false);return true; }
        default -> { return true; }
      }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
  }
  private CampusMapScreen() {}
}
