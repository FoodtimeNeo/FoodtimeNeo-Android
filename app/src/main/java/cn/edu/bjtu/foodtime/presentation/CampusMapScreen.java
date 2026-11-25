package cn.edu.bjtu.foodtime.presentation;

import android.content.Intent;
import android.graphics.*;
import android.hardware.*;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.map.CampusMapGeometry;
import cn.edu.bjtu.foodtime.domain.map.CampusNavigator;
import cn.edu.bjtu.foodtime.domain.map.CampusProjection;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.json.*;

/** High-res tiled illustrated campus map with fixed-scale pins, live GPS projection, and offline walking navigation. */
final class CampusMapScreen {
  record Region(
      String id,
      String name,
      String shortName,
      String subtitle,
      float pinX,
      float pinY,
      String entryNodeId,
      double lat,
      double lng,
      float[][] points,
      List<String> halls) {}

  private static volatile List<Region> cachedRegions;
  private static volatile CampusProjection cachedProjection;
  private static volatile CampusNavigator cachedNavigator;

  static List<Region> regions(MainActivity activity) {
    List<Region> existing = cachedRegions;
    if (existing != null) return existing;
    try (var input = activity.getAssets().open("campus-map/regions.json")) {
      JSONObject data = new JSONObject(readUtf8(input));
      JSONArray rows = data.getJSONArray("regions");
      List<Region> result = new ArrayList<>();
      for (int i = 0; i < rows.length(); i++) {
        var row = rows.getJSONObject(i);
        var polygon = row.getJSONArray("polygon");
        float[][] points = new float[polygon.length()][2];
        float sumX = 0, sumY = 0;
        for (int j = 0; j < polygon.length(); j++) {
          points[j][0] = (float) polygon.getJSONArray(j).getDouble(0);
          points[j][1] = (float) polygon.getJSONArray(j).getDouble(1);
          sumX += points[j][0];
          sumY += points[j][1];
        }
        JSONArray pin = row.optJSONArray("pin");
        float pinX = pin != null && pin.length() >= 2 ? (float) pin.getDouble(0) : sumX / Math.max(1, points.length);
        float pinY = pin != null && pin.length() >= 2 ? (float) pin.getDouble(1) : sumY / Math.max(1, points.length);
        List<String> halls = new ArrayList<>();
        var ids = row.getJSONArray("diningHallIds");
        for (int j = 0; j < ids.length(); j++) halls.add(ids.getString(j));
        String name = row.getString("name");
        result.add(new Region(
            row.getString("id"),
            name,
            row.optString("shortName", name),
            row.optString("subtitle", ""),
            pinX,
            pinY,
            row.optString("entryNodeId", ""),
            row.optDouble("lat", Double.NaN),
            row.optDouble("lng", Double.NaN),
            points,
            halls));
      }
      cachedRegions = Collections.unmodifiableList(result);
      return cachedRegions;
    } catch (Exception failure) {
      throw new IllegalStateException("地图资料暂时无法读取", failure);
    }
  }

  static CampusProjection projection(MainActivity activity) {
    CampusProjection existing = cachedProjection;
    if (existing != null) return existing;
    try (var input = activity.getAssets().open("campus-map/calibration.json")) {
      JSONObject data = new JSONObject(readUtf8(input));
      float w = (float) data.optDouble("width", 5032);
      float h = (float) data.optDouble("height", 3579);
      JSONObject center = data.getJSONObject("campusCenter");
      double radius = data.optDouble("campusRadiusMeters", 1400);
      JSONObject b = data.optJSONObject("bounds");
      CampusProjection.Bounds bounds = b == null ? null : new CampusProjection.Bounds(
          b.getDouble("minLat"), b.getDouble("maxLat"), b.getDouble("minLng"), b.getDouble("maxLng"));
      JSONArray arr = data.getJSONArray("anchors");
      List<CampusProjection.Anchor> anchors = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
        JSONObject row = arr.getJSONObject(i);
        anchors.add(new CampusProjection.Anchor(
            row.getString("id"), row.optString("name", ""),
            row.getDouble("lat"), row.getDouble("lng"),
            row.getDouble("x"), row.getDouble("y")));
      }
      cachedProjection = new CampusProjection(
          w, h, center.getDouble("lat"), center.getDouble("lng"), radius, bounds, anchors);
      return cachedProjection;
    } catch (Exception failure) {
      throw new IllegalStateException("地图坐标校准资料暂时无法读取", failure);
    }
  }

  static CampusNavigator navigator(MainActivity activity) {
    CampusNavigator existing = cachedNavigator;
    if (existing != null) return existing;
    try (var input = activity.getAssets().open("campus-map/campus-graph.json")) {
      JSONObject data = new JSONObject(readUtf8(input));
      double speed = data.optDouble("walkSpeedMetersPerMin", 75.0);
      JSONArray nArr = data.getJSONArray("nodes");
      List<CampusNavigator.Node> nodes = new ArrayList<>();
      for (int i = 0; i < nArr.length(); i++) {
        JSONObject r = nArr.getJSONObject(i);
        nodes.add(new CampusNavigator.Node(
            r.getString("id"), r.optString("name", ""), r.optString("kind", "road"),
            (float) r.getDouble("x"), (float) r.getDouble("y"),
            r.optDouble("lat", 0), r.optDouble("lng", 0), r.optInt("presetOrder", 0)));
      }
      JSONArray eArr = data.getJSONArray("edges");
      List<CampusNavigator.Edge> edges = new ArrayList<>();
      for (int i = 0; i < eArr.length(); i++) {
        JSONObject r = eArr.getJSONObject(i);
        edges.add(new CampusNavigator.Edge(
            r.getString("from"), r.getString("to"),
            r.optString("roadName", "校内道路"), r.getDouble("distanceMeters"), r.optString("note", "")));
      }
      cachedNavigator = new CampusNavigator(speed, nodes, edges);
      return cachedNavigator;
    } catch (Exception failure) {
      throw new IllegalStateException("校内步行路网资料暂时无法读取", failure);
    }
  }

  private static String readUtf8(InputStream input) throws IOException {
    var output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    return output.toString(StandardCharsets.UTF_8.name());
  }

  static void show(MainActivity a, LinearLayout page, MainActivity.Route route) {
    page.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 24));
    List<Region> regions = regions(a);
    CampusProjection projection = projection(a);
    CampusNavigator navigator = navigator(a);
    List<CampusNavigator.Node> presets = navigator.presetStartNodes();

    TextView hint = Ui.text(a, "双指缩放清晰查看 · 点选地标或下方标签规划校内步行路线", 12, Design.MUTED);
    page.addView(hint);
    Ui.gap(page, 8);

    // Optional banner when user is tapping a custom start point on the map
    LinearLayout pickBanner = Ui.row(a);
    pickBanner.setGravity(Gravity.CENTER_VERTICAL);
    pickBanner.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
    pickBanner.setBackground(Ui.shape(a, Design.RED_SOFT, Design.RED, 12));
    pickBanner.setVisibility(View.GONE);
    pickBanner.addView(Ui.text(a, "请在地图上点击任意出发位置", 13, Design.RED), new LinearLayout.LayoutParams(0, -2, 1));
    TextView cancelPick = Ui.text(a, "取消", 12, Design.RED);
    cancelPick.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
    cancelPick.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
    cancelPick.setClickable(true);
    cancelPick.setFocusable(true);
    pickBanner.addView(cancelPick);
    LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(-1, -2);
    bannerLp.bottomMargin = Ui.dp(a, 8);
    page.addView(pickBanner, bannerLp);

    String key = a.draftKey("selected");
    LinearLayout details = Ui.col(a);

    FrameLayout mapCard = new FrameLayout(a);
    mapCard.setBackground(Ui.shape(a, 0xffb3d38a, Design.BORDER, 16));
    mapCard.setClipToOutline(true);
    mapCard.setElevation(Ui.dp(a, 2));

    MapView map = new MapView(a, regions);
    map.setTag("campus-map-canvas");
    mapCard.addView(map, new FrameLayout.LayoutParams(-1, Ui.dp(a, 420)));

    // State holder for interactive map & walking navigation
    final class NavState {
      Region selected;
      Location userFix = a.searchLocation.cachedFix();
      boolean userOnCampus;
      float userX, userY, userAccuracyMeters;
      boolean navigating;
      boolean stepsExpanded;
      boolean pickStartMode;
      String startMode = "auto"; // "auto" | "gps" | "preset:<id>" | "custom"
      String presetNodeId = presets.isEmpty() ? "gate-south" : presets.get(0).id();
      float customStartX, customStartY;
      CampusNavigator.RoutePlan plan;
      boolean locating;
      TextView locateBtn;
      Map<String, TextView> chips = new HashMap<>();

      void syncUserProjection() {
        if (userFix != null && projection.isOnCampus(userFix.getLatitude(), userFix.getLongitude())) {
          var pt = projection.toPixel(userFix.getLatitude(), userFix.getLongitude());
          userOnCampus = true;
          userX = pt.x();
          userY = pt.y();
          userAccuracyMeters = userFix.hasAccuracy() ? userFix.getAccuracy() : 18f;
          map.setUserLocation(true, userX, userY, userAccuracyMeters);
        } else {
          userOnCampus = false;
          map.setUserLocation(false, 0, 0, 0);
        }
        if (locateBtn != null) {
          locateBtn.setTextColor(userOnCampus ? 0xff1a73e8 : (locating ? Design.RED : Design.INK));
        }
      }

      void recomputeRoute(boolean focusMap) {
        if (!navigating || selected == null || selected.entryNodeId().isEmpty()) {
          plan = null;
          map.setRoute(null);
          return;
        }
        String effectiveMode = startMode;
        if ("auto".equals(effectiveMode)) {
          effectiveMode = userOnCampus ? "gps" : ("preset:" + presetNodeId);
        }
        if ("gps".equals(effectiveMode) && userOnCampus) {
          plan = navigator.routeFromPixel(userX, userY, "我的位置", selected.entryNodeId(), selected.name());
        } else if ("custom".equals(effectiveMode)) {
          plan = navigator.routeFromPixel(customStartX, customStartY, "地图选点", selected.entryNodeId(), selected.name());
        } else {
          String nodeId = effectiveMode.startsWith("preset:") ? effectiveMode.substring("preset:".length()) : presetNodeId;
          plan = navigator.routeFromNode(nodeId, selected.entryNodeId(), selected.name());
        }
        map.setRoute(plan);
        if (focusMap && plan != null) {
          focusRoute(map, plan, selected);
        }
      }
    }
    NavState state = new NavState();
    Runnable[] renderDetails = {null};

    // Floating right-bottom controls: +, -, 全图, 定位
    LinearLayout controls = Ui.col(a);
    controls.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
    controls.setBackground(Ui.shape(a, Design.PAPER, Design.BORDER, 14));
    controls.setElevation(Ui.dp(a, 4));
    String[] symbols = {"＋", "−", "全图", "定位"};
    String[] descriptions = {"放大", "缩小", "全图", "定位到我的位置"};
    Runnable locateAction = () -> {
      if (state.locating) return;
      state.locating = true;
      state.syncUserProjection();
      a.searchLocation.request(a, loc -> {
        state.locating = false;
        state.userFix = loc;
        state.syncUserProjection();
        a.searchLocation.startTracking(a, updated -> {
          if (!map.isAttachedToWindow()) return;
          state.userFix = updated;
          state.syncUserProjection();
          if (state.navigating && ("auto".equals(state.startMode) || "gps".equals(state.startMode))) {
            state.recomputeRoute(false);
          }
          renderDetails[0].run();
        });
        if (state.userOnCampus) {
          map.focusPoint(state.userX, state.userY, Math.max(map.zoomLevel(), 2.2f));
          if (state.navigating && "auto".equals(state.startMode)) {
            state.startMode = "gps";
            state.recomputeRoute(true);
          }
        } else {
          double km = projection.distanceToCampusCenter(loc.getLatitude(), loc.getLongitude()) / 1000.0;
          a.message(String.format(Locale.ROOT, "当前距学校约 %.1fkm，可选择校门起点预览校内路线", km));
        }
        renderDetails[0].run();
      }, msg -> {
        state.locating = false;
        state.syncUserProjection();
        a.message(msg == null || msg.isBlank() ? "定位失败，请重试" : msg);
      });
    };
    Runnable[] actions = {() -> map.zoomBy(1.5f), () -> map.zoomBy(1 / 1.5f), map::reset, locateAction};
    for (int i = 0; i < symbols.length; i++) {
      if (i > 0) {
        View sep = new View(a);
        sep.setBackgroundColor(Design.BORDER);
        controls.addView(sep, new LinearLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 1)));
      }
      TextView btn = Ui.text(a, symbols[i], i >= 2 ? 11 : 18, Design.INK);
      btn.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
      btn.setGravity(Gravity.CENTER);
      btn.setLineSpacing(0, 1);
      btn.setContentDescription(descriptions[i]);
      btn.setFocusable(true);
      btn.setClickable(true);
      btn.setBackground(Ui.ripple(a, Color.TRANSPARENT, 0, 10));
      Runnable action = actions[i];
      btn.setOnClickListener(v -> action.run());
      if (i == 3) state.locateBtn = btn;
      controls.addView(btn, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 38)));
    }
    FrameLayout.LayoutParams ctrlLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
    ctrlLp.setMargins(0, 0, Ui.dp(a, 10), Ui.dp(a, 10));
    mapCard.addView(controls, ctrlLp);
    page.addView(mapCard, new LinearLayout.LayoutParams(-1, -2));

    Ui.gap(page, 12);
    HorizontalScrollView chipScroll = new HorizontalScrollView(a);
    chipScroll.setHorizontalScrollBarEnabled(false);
    chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    LinearLayout chipRow = Ui.row(a);
    chipScroll.addView(chipRow);
    page.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));

    cancelPick.setOnClickListener(v -> {
      state.pickStartMode = false;
      map.setPickStartMode(false);
      pickBanner.setVisibility(View.GONE);
      renderDetails[0].run();
    });
    map.startPickListener = pt -> {
      state.pickStartMode = false;
      map.setPickStartMode(false);
      pickBanner.setVisibility(View.GONE);
      state.startMode = "custom";
      state.customStartX = pt[0];
      state.customStartY = pt[1];
      state.navigating = true;
      state.recomputeRoute(true);
      renderDetails[0].run();
    };

    renderDetails[0] = () -> {
      Region region = state.selected;
      for (var entry : state.chips.entrySet()) {
        boolean active = region != null && entry.getKey().equals(region.id());
        TextView chip = entry.getValue();
        chip.setSelected(active);
        chip.setTextColor(active ? Design.ON_RED : Design.INK);
        chip.setTypeface(active ? Typeface.create(Design.body(), Typeface.BOLD) : Design.body());
        chip.setBackground(Ui.ripple(a, active ? Design.RED : Design.ALT, active ? 0 : Design.BORDER, 18));
      }
      if (region == null) {
        details.removeAllViews();
        details.setVisibility(View.GONE);
        return;
      }
      details.removeAllViews();
      details.setVisibility(View.VISIBLE);
      details.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
      details.setBackground(Ui.shape(a, Design.ALT, Design.BORDER, 16));

      // Header row: region name + subtitle + live distance badge
      LinearLayout headRow = Ui.row(a);
      headRow.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout titleCol = Ui.col(a);
      titleCol.addView(Ui.title(a, region.name(), 18));
      if (!region.subtitle().isBlank()) {
        Ui.gap(titleCol, 2);
        titleCol.addView(Ui.text(a, region.subtitle(), 12, Design.MUTED));
      }
      headRow.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));
      if (state.userFix != null && Double.isFinite(region.lat()) && Double.isFinite(region.lng())) {
        double meters = CampusProjection.distanceMeters(
            state.userFix.getLatitude(), state.userFix.getLongitude(), region.lat(), region.lng());
        TextView badge = Ui.text(a, CampusProjection.formatWalkSummary(meters), 11, Design.RED);
        badge.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
        badge.setGravity(Gravity.CENTER);
        badge.setLineSpacing(0, 1);
        badge.setPadding(Ui.dp(a, 9), Ui.dp(a, 4), Ui.dp(a, 9), Ui.dp(a, 4));
        badge.setBackground(Ui.shape(a, Design.RED_SOFT, 0, 12));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.leftMargin = Ui.dp(a, 8);
        headRow.addView(badge, blp);
      }
      details.addView(headRow, new LinearLayout.LayoutParams(-1, -2));
      Ui.gap(details, 10);

      // Navigation action buttons
      LinearLayout navBtnRow = Ui.row(a);
      navBtnRow.setGravity(Gravity.CENTER_VERTICAL);
      String navLabel = state.navigating ? "重新聚焦路线" : "校内步行导航";
      TextView navBtn = Ui.button(a, navLabel, true, () -> {
        state.navigating = true;
        state.recomputeRoute(true);
        renderDetails[0].run();
      });
      navBtn.setTag("campus-map-start-nav");
      navBtnRow.addView(navBtn, new LinearLayout.LayoutParams(0, Ui.dp(a, 42), 1));
      if (state.userFix != null && !state.userOnCampus && Double.isFinite(region.lat()) && Double.isFinite(region.lng())) {
        TextView extBtn = Ui.button(a, "外部地图", false, () -> openExternalMap(a, region));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-2, Ui.dp(a, 42));
        elp.leftMargin = Ui.dp(a, 8);
        navBtnRow.addView(extBtn, elp);
      }
      details.addView(navBtnRow, new LinearLayout.LayoutParams(-1, -2));

      // Active in-app walking route guidance card
      if (state.navigating && state.plan != null) {
        Ui.gap(details, 10);
        LinearLayout navCard = Ui.col(a);
        navCard.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        navCard.setBackground(Ui.shape(a, Design.PAPER, Design.BORDER, 14));
        navCard.setTag("campus-map-nav-card");

        boolean usingGps = state.userOnCampus
            && ("gps".equals(state.startMode) || "auto".equals(state.startMode));
        double remaining = usingGps
            ? navigator.remainingMeters(state.plan, state.userX, state.userY)
            : state.plan.totalMeters();
        boolean arrived = usingGps && remaining <= 25.0;

        if (arrived) {
          TextView arrival = Ui.text(a, "已到达 " + region.name() + " 附近", 14, Design.GREEN);
          arrival.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
          navCard.addView(arrival);
          Ui.gap(navCard, 6);
        }

        LinearLayout summaryRow = Ui.row(a);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        String routeTitle = "步行 " + CampusProjection.formatDistance(state.plan.totalMeters())
            + " · 约" + state.plan.walkMinutes() + "分钟";
        if (usingGps && !arrived) {
          routeTitle += "（剩余 " + CampusProjection.formatDistance(remaining) + "）";
        }
        TextView sumView = Ui.text(a, routeTitle, 14, Design.INK);
        sumView.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
        summaryRow.addView(sumView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView stopNav = Ui.text(a, "结束导航", 12, Design.MUTED);
        stopNav.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        stopNav.setClickable(true);
        stopNav.setFocusable(true);
        stopNav.setOnClickListener(v -> {
          state.navigating = false;
          state.pickStartMode = false;
          map.setPickStartMode(false);
          pickBanner.setVisibility(View.GONE);
          state.recomputeRoute(false);
          renderDetails[0].run();
        });
        summaryRow.addView(stopNav);
        navCard.addView(summaryRow, new LinearLayout.LayoutParams(-1, -2));

        if (!state.userOnCampus) {
          Ui.gap(navCard, 4);
          String fallbackHint = state.userFix == null
              ? "未开启定位，已从校门规划路线；点击“定位”或下方起点可切换"
              : "当前不在校园内，已切换为校内起点预览（也可使用右上“外部地图”到校）";
          navCard.addView(Ui.text(a, fallbackHint, 11, Design.MUTED));
        }

        // Start-point switcher chips
        Ui.gap(navCard, 8);
        HorizontalScrollView startScroll = new HorizontalScrollView(a);
        startScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout startRow = Ui.row(a);
        String activeMode = "auto".equals(state.startMode)
            ? (state.userOnCampus ? "gps" : ("preset:" + state.presetNodeId))
            : state.startMode;
        if (state.userOnCampus) {
          addStartChip(a, startRow, "我的位置", "gps".equals(activeMode), () -> {
            state.startMode = "gps";
            state.recomputeRoute(true);
            renderDetails[0].run();
          });
        }
        for (CampusNavigator.Node preset : presets) {
          String modeKey = "preset:" + preset.id();
          addStartChip(a, startRow, preset.name(), modeKey.equals(activeMode), () -> {
            state.startMode = modeKey;
            state.presetNodeId = preset.id();
            state.recomputeRoute(true);
            renderDetails[0].run();
          });
        }
        addStartChip(a, startRow, "图上选起点", "custom".equals(activeMode) || state.pickStartMode, () -> {
          state.pickStartMode = true;
          map.setPickStartMode(true);
          pickBanner.setVisibility(View.VISIBLE);
          a.message("请在上方地图上点击任意位置作为起点");
        });
        startScroll.addView(startRow);
        navCard.addView(startScroll, new LinearLayout.LayoutParams(-1, -2));

        // Turn-by-turn steps
        List<CampusNavigator.RouteStep> steps = state.plan.steps();
        if (!steps.isEmpty()) {
          Ui.gap(navCard, 8);
          int activeStep = usingGps ? navigator.activeStepIndex(state.plan, state.userX, state.userY) : 0;
          CampusNavigator.RouteStep current = steps.get(Math.min(activeStep, steps.size() - 1));
          TextView curStep = Ui.text(a, "▶ " + current.instruction(), 13, Design.RED);
          curStep.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
          curStep.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
          curStep.setBackground(Ui.shape(a, Design.RED_SOFT, 0, 10));
          navCard.addView(curStep, new LinearLayout.LayoutParams(-1, -2));

          if (steps.size() > 1) {
            Ui.gap(navCard, 6);
            TextView toggleSteps = Ui.text(a,
                state.stepsExpanded ? "收起完整路线步骤 ⌃" : ("展开全部 " + steps.size() + " 步指引 ⌄"),
                12, Design.MUTED);
            toggleSteps.setPadding(Ui.dp(a, 2), Ui.dp(a, 4), Ui.dp(a, 2), Ui.dp(a, 4));
            toggleSteps.setClickable(true);
            toggleSteps.setFocusable(true);
            toggleSteps.setOnClickListener(v -> {
              state.stepsExpanded = !state.stepsExpanded;
              renderDetails[0].run();
            });
            navCard.addView(toggleSteps);
            if (state.stepsExpanded) {
              for (int i = 0; i < steps.size(); i++) {
                Ui.gap(navCard, 4);
                boolean isCur = i == activeStep;
                TextView stepRow = Ui.text(a, (i + 1) + ". " + steps.get(i).instruction(),
                    12, isCur ? Design.INK : Design.MUTED);
                if (isCur) stepRow.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
                navCard.addView(stepRow);
              }
            }
          }
        }
        details.addView(navCard, new LinearLayout.LayoutParams(-1, -2));
      }

      Ui.gap(details, 10);
      int found = 0;
      for (var hall : a.repo.halls()) {
        if (hall.active() && region.halls().contains(hall.id())) {
          found++;
          String label = hall.name() + (hall.averageScore() != null
              ? " · ★ " + hall.averageScore().stripTrailingZeros().toPlainString() : "");
          TextView enter = Ui.button(a, label + "  →", false, () -> a.go("stalls", hall.id()));
          LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2);
          if (found > 1) elp.topMargin = Ui.dp(a, 8);
          details.addView(enter, elp);
        }
      }
      if (found == 0) {
        details.addView(Ui.text(a, a.isPageLoading() ? "正在读取食堂入口…" :
            a.pageFailed() ? "食堂入口读取失败，地图与步行导航仍可使用。" : "此餐厅暂未录入档口菜单，后续将陆续补充。", 13, Design.MUTED));
        if (a.pageFailed()) {
          Ui.gap(details, 8);
          Ui.action(details, "重新读取食堂", false, a::render);
        }
      }
    };

    Consumer<Region> onSelectRegion = region -> {
      boolean changed = state.selected == null || !state.selected.id().equals(region.id());
      state.selected = region;
      a.drafts.putString(key, region.id());
      map.select(region.id());
      if (state.navigating && changed) {
        state.recomputeRoute(true);
      }
      renderDetails[0].run();
    };
    map.listener = onSelectRegion;

    for (int i = 0; i < regions.size(); i++) {
      Region region = regions.get(i);
      TextView chip = Ui.text(a, region.shortName(), 13, Design.INK);
      chip.setGravity(Gravity.CENTER);
      chip.setLineSpacing(0, 1);
      chip.setMinHeight(Ui.dp(a, 36));
      chip.setPadding(Ui.dp(a, 14), Ui.dp(a, 7), Ui.dp(a, 14), Ui.dp(a, 7));
      chip.setBackground(Ui.ripple(a, Design.ALT, Design.BORDER, 18));
      chip.setFocusable(true);
      chip.setClickable(true);
      chip.setTag("map-region:" + region.id());
      chip.setOnClickListener(v -> {
        onSelectRegion.accept(region);
        if (!state.navigating) map.focusPoint(region.pinX(), region.pinY(), Math.max(map.zoomLevel(), 1.9f));
      });
      state.chips.put(region.id(), chip);
      LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
      if (i > 0) clp.leftMargin = Ui.dp(a, 8);
      chipRow.addView(chip, clp);
    }

    Ui.gap(page, 12);
    details.setTag("campus-map-selection");
    details.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    details.setVisibility(View.GONE);
    page.addView(details, new LinearLayout.LayoutParams(-1, -2));

    // Sync initial GPS & live tracking if already permitted
    state.syncUserProjection();
    map.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View v) {
        if (a.searchLocation.hasPermission(a)) {
          a.searchLocation.startTracking(a, updated -> {
            if (!map.isAttachedToWindow()) return;
            state.userFix = updated;
            state.syncUserProjection();
            if (state.navigating && ("auto".equals(state.startMode) || "gps".equals(state.startMode))) {
              state.recomputeRoute(false);
            }
            renderDetails[0].run();
          });
        }
      }
      @Override public void onViewDetachedFromWindow(View v) {
        a.searchLocation.stopTracking();
      }
    });

    // Resolve deep-link route target ("nav:<hallId>" or "<hallId>" or "<regionId>") or saved draft
    String routeTarget = route == null || route.id() == null ? "" : route.id().strip();
    boolean autoNavigate = false;
    if (routeTarget.startsWith("nav:")) {
      autoNavigate = true;
      routeTarget = routeTarget.substring(4);
    }
    Region initial = findRegion(regions, routeTarget);
    if (initial == null && routeTarget.isEmpty()) {
      initial = findRegion(regions, a.drafts.getString(key, ""));
    }
    if (initial != null) {
      state.selected = initial;
      a.drafts.putString(key, initial.id());
      map.select(initial.id());
      if (autoNavigate) {
        state.navigating = true;
        state.recomputeRoute(false);
        final CampusNavigator.RoutePlan initialPlan = state.plan;
        final Region targetRegion = initial;
        map.post(() -> {
          if (initialPlan != null) focusRoute(map, initialPlan, targetRegion);
          else map.focusPoint(targetRegion.pinX(), targetRegion.pinY(), 2.2f);
        });
      }
      renderDetails[0].run();
    }

    Ui.gap(page, 14);
    page.addView(Ui.text(a, "北京交通大学手绘校园地图 · 高清分块渲染 · 校内离线步行导航", 12, Design.MUTED));
  }

  private static void addStartChip(MainActivity a, LinearLayout row, String label, boolean active, Runnable onClick) {
    TextView chip = Ui.text(a, label, 11, active ? Design.ON_RED : Design.INK);
    chip.setTypeface(active ? Typeface.create(Design.body(), Typeface.BOLD) : Design.body());
    chip.setGravity(Gravity.CENTER);
    chip.setLineSpacing(0, 1);
    chip.setMinHeight(Ui.dp(a, 28));
    chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
    chip.setBackground(Ui.ripple(a, active ? Design.RED : Design.ALT, active ? 0 : Design.BORDER, 14));
    chip.setClickable(true);
    chip.setFocusable(true);
    chip.setOnClickListener(v -> onClick.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    if (row.getChildCount() > 0) lp.leftMargin = Ui.dp(a, 6);
    row.addView(chip, lp);
  }

  private static Region findRegion(List<Region> regions, String idOrHallId) {
    if (idOrHallId == null || idOrHallId.isBlank()) return null;
    for (Region r : regions) {
      if (r.id().equals(idOrHallId) || r.halls().contains(idOrHallId)) return r;
    }
    return null;
  }

  private static void focusRoute(MapView map, CampusNavigator.RoutePlan plan, Region target) {
    float minX = target.pinX(), maxX = target.pinX(), minY = target.pinY(), maxY = target.pinY();
    minX = Math.min(minX, plan.startX());
    maxX = Math.max(maxX, plan.startX());
    minY = Math.min(minY, plan.startY());
    maxY = Math.max(maxY, plan.startY());
    for (float[] pt : plan.polyline()) {
      minX = Math.min(minX, pt[0]);
      maxX = Math.max(maxX, pt[0]);
      minY = Math.min(minY, pt[1]);
      maxY = Math.max(maxY, pt[1]);
    }
    map.focusBounds(minX, minY, maxX, maxY);
  }

  private static void openExternalMap(MainActivity a, Region region) {
    try {
      String encoded = Uri.encode("北京交通大学" + region.name());
      Uri uri = Uri.parse(String.format(Locale.ROOT, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
          region.lat(), region.lng(), region.lat(), region.lng(), encoded));
      a.startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (Exception e) {
      a.message("未找到可用的外部地图应用");
    }
  }

  static final class MapView extends View implements SensorEventListener {
    private static final float WIDTH = 5032f, HEIGHT = 3579f;
    private final List<Region> regions;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scales;
    private final int slop;
    private final MainActivity owner;
    private final Executor bg;
    private final Object tileLock = new Object();
    private Bitmap overviewBitmap;
    private BitmapRegionDecoder regionDecoder;
    private Bitmap tileBitmap;
    private Rect tileRect;
    private int tileGeneration;
    private final Runnable tileDecodeTrigger = this::decodeHighResTile;
    private float zoom = 1f, panX, panY, lastX, lastY, downX, downY;
    private boolean moved, decoding, failed;
    private String selection = "";
    private boolean hasUser;
    private float userX, userY, userAccuracyMeters;
    private boolean hasAzimuth;
    private float azimuthDeg;
    private CampusNavigator.RoutePlan routePlan;
    private boolean pickStartMode;
    private final Map<String, RectF> pinHitRects = new LinkedHashMap<>();
    private SensorManager sensorManager;
    Consumer<Region> listener;
    Consumer<float[]> startPickListener;

    MapView(MainActivity a, List<Region> regions) {
      super(a);
      this.owner = a;
      this.regions = regions;
      this.bg = ((RepositoryProvider) a.getApplicationContext()).backgroundExecutor();
      zoom = a.drafts.getFloat("campus-map::zoom", 1f);
      panX = a.drafts.getFloat("campus-map::x", 0f);
      panY = a.drafts.getFloat("campus-map::y", 0f);
      slop = ViewConfiguration.get(a).getScaledTouchSlop();
      setBackgroundColor(0xffb3d38a);
      setContentDescription("校园食堂地图，可双指缩放，或使用下方食堂名称选择");
      textPaint.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
      textPaint.setTextAlign(Paint.Align.LEFT);
      scales = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override public boolean onScale(ScaleGestureDetector detector) {
          moved = true;
          scale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
          return true;
        }
      });
    }

    void setUserLocation(boolean visible, float x, float y, float accuracyMeters) {
      this.hasUser = visible;
      this.userX = x;
      this.userY = y;
      this.userAccuracyMeters = accuracyMeters;
      invalidate();
    }

    void setRoute(CampusNavigator.RoutePlan plan) {
      this.routePlan = plan;
      invalidate();
    }

    void setPickStartMode(boolean enabled) {
      this.pickStartMode = enabled;
      invalidate();
    }

    @Override protected void onAttachedToWindow() {
      super.onAttachedToWindow();
      registerCompass();
      if (overviewBitmap != null || decoding) return;
      decoding = true;
      bg.execute(() -> {
        Bitmap decoded = null;
        BitmapRegionDecoder decoder = null;
        try (var input = getContext().getAssets().open("campus-map/campus-map.webp")) {
          var options = new BitmapFactory.Options();
          options.inSampleSize = 2;
          options.inPreferredConfig = Bitmap.Config.RGB_565;
          decoded = BitmapFactory.decodeStream(input, null, options);
        } catch (IOException ignored) {}
        try {
          var stream = getContext().getAssets().open("campus-map/campus-map.webp");
          decoder = openRegionDecoder(stream);
        } catch (IOException ignored) {}
        Bitmap result = decoded;
        BitmapRegionDecoder readyDecoder = decoder;
        post(() -> {
          decoding = false;
          if (!isAttachedToWindow()) {
            if (result != null) result.recycle();
            recycleDecoderAsync(readyDecoder);
            return;
          }
          overviewBitmap = result;
          synchronized (tileLock) { regionDecoder = readyDecoder; }
          failed = result == null;
          invalidate();
          scheduleTileDecode();
        });
      });
    }

    @SuppressWarnings("deprecation")
    private static BitmapRegionDecoder openRegionDecoder(InputStream stream) throws IOException {
      if (Build.VERSION.SDK_INT >= 31) return BitmapRegionDecoder.newInstance(stream);
      return BitmapRegionDecoder.newInstance(stream, false);
    }

    @Override protected void onDetachedFromWindow() {
      owner.drafts.putFloat("campus-map::zoom", zoom);
      owner.drafts.putFloat("campus-map::x", panX);
      owner.drafts.putFloat("campus-map::y", panY);
      removeCallbacks(tileDecodeTrigger);
      unregisterCompass();
      super.onDetachedFromWindow();
      if (overviewBitmap != null) { overviewBitmap.recycle(); overviewBitmap = null; }
      if (tileBitmap != null) { tileBitmap.recycle(); tileBitmap = null; tileRect = null; }
      BitmapRegionDecoder toClose;
      synchronized (tileLock) { toClose = regionDecoder; regionDecoder = null; }
      recycleDecoderAsync(toClose);
    }

    private void recycleDecoderAsync(BitmapRegionDecoder decoder) {
      if (decoder == null) return;
      bg.execute(() -> {
        synchronized (tileLock) {
          try { decoder.recycle(); } catch (Exception ignored) {}
        }
      });
    }

    private void registerCompass() {
      try {
        sensorManager = getContext().getSystemService(SensorManager.class);
        if (sensorManager != null) {
          Sensor rot = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
          if (rot != null) sensorManager.registerListener(this, rot, SensorManager.SENSOR_DELAY_UI);
        }
      } catch (Exception ignored) {}
    }

    private void unregisterCompass() {
      if (sensorManager != null) {
        try { sensorManager.unregisterListener(this); } catch (Exception ignored) {}
        sensorManager = null;
      }
    }

    @Override public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
      float[] rot = new float[9], orient = new float[3];
      SensorManager.getRotationMatrixFromVector(rot, event.values);
      SensorManager.getOrientation(rot, orient);
      float deg = (float) Math.toDegrees(orient[0]);
      if (!hasAzimuth || Math.abs(deg - azimuthDeg) > 4f) {
        azimuthDeg = deg;
        hasAzimuth = true;
        if (hasUser) invalidate();
      }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    void select(String id) { selection = id; invalidate(); }

    void reset() {
      zoom = 1f;
      panX = panY = 0f;
      constrain();
      clearHighResTile();
      invalidate();
    }

    void zoomBy(float factor) { scale(factor, getWidth() / 2f, getHeight() / 2f); }

    float zoomLevel() { return zoom; }

    void focusPoint(float mapX, float mapY, float targetZoom) {
      if (getWidth() <= 0 || getHeight() <= 0) return;
      zoom = CampusMapGeometry.clamp(targetZoom, 1f, 6f);
      float s = fit() * zoom;
      panX = getWidth() / 2f - left() - mapX * s;
      panY = getHeight() / 2f - top() - mapY * s;
      constrain();
      invalidate();
      scheduleTileDecode();
    }

    void focusBounds(float minX, float minY, float maxX, float maxY) {
      if (getWidth() <= 0 || getHeight() <= 0) return;
      float spanX = Math.max(420f, (maxX - minX) * 1.45f);
      float spanY = Math.max(360f, (maxY - minY) * 1.45f);
      float targetScale = Math.min(getWidth() / spanX, getHeight() / spanY);
      float targetZoom = CampusMapGeometry.clamp(targetScale / Math.max(1e-5f, fit()), 1f, 4.2f);
      focusPoint((minX + maxX) / 2f, (minY + maxY) / 2f, targetZoom);
    }

    private float fit() { return Math.min(getWidth() / WIDTH, getHeight() / HEIGHT); }
    private float left() { return (getWidth() - WIDTH * fit()) / 2f; }
    private float top() { return (getHeight() - HEIGHT * fit()) / 2f; }
    private float toScreenX(float mapX) { return left() + panX + mapX * fit() * zoom; }
    private float toScreenY(float mapY) { return top() + panY + mapY * fit() * zoom; }

    private void constrain() {
      if (getWidth() <= 0 || getHeight() <= 0) return;
      float w = WIDTH * fit(), h = HEIGHT * fit();
      panX = CampusMapGeometry.clamp(panX, getWidth() - left() - w * zoom, -left());
      panY = CampusMapGeometry.clamp(panY, getHeight() - top() - h * zoom, -top());
      if (w * zoom <= getWidth()) panX = (getWidth() - w * zoom) / 2f - left();
      if (h * zoom <= getHeight()) panY = (getHeight() - h * zoom) / 2f - top();
    }

    private void scale(float factor, float x, float y) {
      float next = CampusMapGeometry.clamp(zoom * factor, 1f, 6f), ratio = next / zoom;
      panX = (x - left()) - (x - left() - panX) * ratio;
      panY = (y - top()) - (y - top() - panY) * ratio;
      zoom = next;
      constrain();
      invalidate();
      scheduleTileDecode();
    }

    private void clearHighResTile() {
      removeCallbacks(tileDecodeTrigger);
      tileGeneration++;
      if (tileBitmap != null) {
        tileBitmap.recycle();
        tileBitmap = null;
        tileRect = null;
      }
    }

    private void scheduleTileDecode() {
      if (getWidth() <= 0 || getHeight() <= 0) return;
      float screenPxPerMapPx = fit() * zoom;
      if (screenPxPerMapPx <= 0.42f) {
        clearHighResTile();
        return;
      }
      removeCallbacks(tileDecodeTrigger);
      postDelayed(tileDecodeTrigger, 100);
    }

    private void decodeHighResTile() {
      if (!isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0) return;
      float s = fit() * zoom;
      if (s <= 0.42f) return;
      float visLeft = (0 - left() - panX) / s;
      float visTop = (0 - top() - panY) / s;
      float visRight = (getWidth() - left() - panX) / s;
      float visBottom = (getHeight() - top() - panY) / s;
      float padX = (visRight - visLeft) * 0.15f;
      float padY = (visBottom - visTop) * 0.15f;
      int rl = Math.max(0, (int) Math.floor(visLeft - padX));
      int rt = Math.max(0, (int) Math.floor(visTop - padY));
      int rr = Math.min((int) WIDTH, (int) Math.ceil(visRight + padX));
      int rb = Math.min((int) HEIGHT, (int) Math.ceil(visBottom + padY));
      if (rr - rl < 64 || rb - rt < 64) return;
      // Avoid unnecessary decode if current tile already covers the viewport
      if (tileRect != null && tileBitmap != null
          && tileRect.left <= visLeft && tileRect.top <= visTop
          && tileRect.right >= visRight && tileRect.bottom >= visBottom) {
        return;
      }
      Rect req = new Rect(rl, rt, rr, rb);
      long area = (long) req.width() * req.height();
      if (area > 2800L * 2800L) return;
      final int gen = ++tileGeneration;
      bg.execute(() -> {
        Bitmap decoded = null;
        synchronized (tileLock) {
          if (regionDecoder == null || regionDecoder.isRecycled()) return;
          try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            decoded = regionDecoder.decodeRegion(req, opts);
          } catch (Exception ignored) {}
        }
        Bitmap ready = decoded;
        post(() -> {
          if (!isAttachedToWindow() || gen != tileGeneration || ready == null) {
            if (ready != null) ready.recycle();
            return;
          }
          if (tileBitmap != null) tileBitmap.recycle();
          tileBitmap = ready;
          tileRect = req;
          invalidate();
        });
      });
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      constrain();
      scheduleTileDecode();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float s = fit() * zoom;

      // 1. Base map + high-res viewport tile + subtle selected building outline (in map coordinates)
      canvas.save();
      canvas.clipRect(0, 0, getWidth(), getHeight());
      canvas.translate(left() + panX, top() + panY);
      canvas.scale(s, s);
      paint.setStyle(Paint.Style.FILL);
      if (overviewBitmap != null) {
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(overviewBitmap, null, new RectF(0, 0, WIDTH, HEIGHT), paint);
      }
      if (tileBitmap != null && tileRect != null) {
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(tileBitmap, null, new RectF(tileRect), paint);
      }
      for (Region region : regions) {
        if (!region.id().equals(selection)) continue;
        Path path = new Path();
        for (int i = 0; i < region.points().length; i++) {
          float[] p = region.points()[i];
          if (i == 0) path.moveTo(p[0], p[1]);
          else path.lineTo(p[0], p[1]);
        }
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x1ac9341e);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xb8c9341e);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.8f) / Math.max(1e-4f, s));
        canvas.drawPath(path, paint);
      }
      canvas.restore();

      // 2. Walking route polyline (drawn in screen coordinates for crisp constant stroke width)
      if (routePlan != null && !routePlan.polyline().isEmpty()) {
        drawRoute(canvas, routePlan);
      }

      // 3. Live GPS blue dot + accuracy ring + compass cone
      if (hasUser) {
        drawUserDot(canvas, s);
      }

      // 4. Fixed screen-space pin pills (never obscured by red blobs, crisp at every zoom level)
      drawPins(canvas);

      if (failed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Design.INK);
        paint.setTextSize(Ui.dp(getContext(), 14));
        canvas.drawText("地图图片读取失败，请使用下方名称选择", Ui.dp(getContext(), 12), Ui.dp(getContext(), 28), paint);
      }
    }

    private void drawRoute(Canvas canvas, CampusNavigator.RoutePlan plan) {
      List<float[]> pts = plan.polyline();
      float startSx = toScreenX(plan.startX()), startSy = toScreenY(plan.startY());
      float firstSx = toScreenX(pts.get(0)[0]), firstSy = toScreenY(pts.get(0)[1]);

      if (plan.snappedFromRoad()) {
        Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        dash.setStyle(Paint.Style.STROKE);
        dash.setColor(0xff1a73e8);
        dash.setStrokeWidth(Ui.dp(getContext(), 2.5f));
        dash.setPathEffect(new DashPathEffect(new float[]{Ui.dp(getContext(), 5), Ui.dp(getContext(), 4)}, 0));
        canvas.drawLine(startSx, startSy, firstSx, firstSy, dash);
      }

      Path routePath = new Path();
      for (int i = 0; i < pts.size(); i++) {
        float sx = toScreenX(pts.get(i)[0]), sy = toScreenY(pts.get(i)[1]);
        if (i == 0) routePath.moveTo(sx, sy);
        else routePath.lineTo(sx, sy);
      }

      Paint casing = new Paint(Paint.ANTI_ALIAS_FLAG);
      casing.setStyle(Paint.Style.STROKE);
      casing.setStrokeCap(Paint.Cap.ROUND);
      casing.setStrokeJoin(Paint.Join.ROUND);
      casing.setColor(0xf2ffffff);
      casing.setStrokeWidth(Ui.dp(getContext(), 7.5f));
      canvas.drawPath(routePath, casing);

      Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
      core.setStyle(Paint.Style.STROKE);
      core.setStrokeCap(Paint.Cap.ROUND);
      core.setStrokeJoin(Paint.Join.ROUND);
      core.setColor(0xffd93f27);
      core.setStrokeWidth(Ui.dp(getContext(), 4.5f));
      canvas.drawPath(routePath, core);

      // Directional chevrons spaced along the route
      PathMeasure pm = new PathMeasure(routePath, false);
      float length = pm.getLength();
      float step = Ui.dp(getContext(), 46);
      float[] pos = new float[2], tan = new float[2];
      Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
      arrow.setStyle(Paint.Style.STROKE);
      arrow.setStrokeCap(Paint.Cap.ROUND);
      arrow.setStrokeJoin(Paint.Join.ROUND);
      arrow.setColor(Color.WHITE);
      arrow.setStrokeWidth(Ui.dp(getContext(), 1.6f));
      float sz = Ui.dp(getContext(), 3.2f);
      for (float d = step * 0.6f; d < length - step * 0.35f; d += step) {
        if (!pm.getPosTan(d, pos, tan)) continue;
        float angle = (float) Math.toDegrees(Math.atan2(tan[1], tan[0]));
        canvas.save();
        canvas.translate(pos[0], pos[1]);
        canvas.rotate(angle);
        Path chev = new Path();
        chev.moveTo(-sz, -sz);
        chev.lineTo(sz * 0.6f, 0);
        chev.lineTo(-sz, sz);
        canvas.drawPath(chev, arrow);
        canvas.restore();
      }

      // Green start marker ("起")
      float r = Ui.dp(getContext(), 9.5f);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.WHITE);
      canvas.drawCircle(startSx, startSy, r + Ui.dp(getContext(), 1.8f), paint);
      paint.setColor(0xff1e8e3e);
      canvas.drawCircle(startSx, startSy, r, paint);
      textPaint.setTextSize(Ui.dp(getContext(), 9.5f));
      textPaint.setColor(Color.WHITE);
      textPaint.setTextAlign(Paint.Align.CENTER);
      Paint.FontMetrics fm = textPaint.getFontMetrics();
      canvas.drawText("起", startSx, startSy - (fm.ascent + fm.descent) / 2f, textPaint);
      textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawUserDot(Canvas canvas, float scale) {
      float sx = toScreenX(userX), sy = toScreenY(userY);
      // Approximate pixels-per-meter (~3.8 map px/m on BJTU hand-drawn map)
      float accRadius = CampusMapGeometry.clamp(userAccuracyMeters * 3.8f * scale, Ui.dp(getContext(), 14), Ui.dp(getContext(), 54));
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(0x261a73e8);
      canvas.drawCircle(sx, sy, accRadius, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(0x551a73e8);
      paint.setStrokeWidth(Ui.dp(getContext(), 1f));
      canvas.drawCircle(sx, sy, accRadius, paint);

      if (hasAzimuth) {
        canvas.save();
        canvas.translate(sx, sy);
        // Map north is tilted ~11 deg counter-clockwise; convert azimuth (0=N clockwise) to canvas angle
        float coneAngle = azimuthDeg - 101f;
        float coneR = Ui.dp(getContext(), 24);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x451a73e8);
        canvas.drawArc(new RectF(-coneR, -coneR, coneR, coneR), coneAngle - 26f, 52f, true, paint);
        canvas.restore();
      }

      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.WHITE);
      canvas.drawCircle(sx, sy, Ui.dp(getContext(), 7.8f), paint);
      paint.setColor(0xff1a73e8);
      canvas.drawCircle(sx, sy, Ui.dp(getContext(), 5.2f), paint);
    }

    private void drawPins(Canvas canvas) {
      pinHitRects.clear();
      // Draw unselected pins first, then selected pin on top so it's never occluded
      for (Region region : regions) {
        if (!region.id().equals(selection)) drawSinglePin(canvas, region, false);
      }
      for (Region region : regions) {
        if (region.id().equals(selection)) drawSinglePin(canvas, region, true);
      }
    }

    private void drawSinglePin(Canvas canvas, Region region, boolean selected) {
      float anchorX = toScreenX(region.pinX());
      float anchorY = toScreenY(region.pinY());
      // Smoothly nudge close neighbors (minghu/yimin, liuyuan/xuesi) at low overview zoom to avoid overlap
      float spread = CampusMapGeometry.clamp((2.2f - zoom) / 1.2f, 0f, 1f);
      float nudgeX = 0f, nudgeY = 0f;
      if (spread > 0.01f) {
        switch (region.id()) {
          case "minghu" -> { nudgeX = -Ui.dp(getContext(), 16) * spread; nudgeY = -Ui.dp(getContext(), 8) * spread; }
          case "yimin" -> { nudgeX = Ui.dp(getContext(), 22) * spread; nudgeY = Ui.dp(getContext(), 6) * spread; }
          case "liuyuan" -> { nudgeX = -Ui.dp(getContext(), 14) * spread; nudgeY = -Ui.dp(getContext(), 10) * spread; }
          case "xuesi" -> { nudgeX = Ui.dp(getContext(), 16) * spread; nudgeY = Ui.dp(getContext(), 12) * spread; }
          default -> {}
        }
      }

      float pillH = Ui.dp(getContext(), selected ? 26f : 22f);
      float pointerH = Ui.dp(getContext(), selected ? 5f : 3.5f);
      float dotR = Ui.dp(getContext(), selected ? 3.5f : 2.6f);
      float padH = Ui.dp(getContext(), selected ? 9f : 7f);
      textPaint.setTextSize(Ui.dp(getContext(), selected ? 11.5f : 10.5f));
      String label = region.shortName();
      float tw = textPaint.measureText(label);
      float pillW = padH + dotR * 2f + Ui.dp(getContext(), 4.5f) + tw + padH;
      float cx = anchorX + nudgeX;
      float bottomY = anchorY + nudgeY - pointerH - Ui.dp(getContext(), 2f);
      RectF pill = new RectF(cx - pillW / 2f, bottomY - pillH, cx + pillW / 2f, bottomY);

      // Subtle anchor dot at exact building position + thin leader when nudged
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.WHITE);
      canvas.drawCircle(anchorX, anchorY, Ui.dp(getContext(), selected ? 4.4f : 3.4f), paint);
      paint.setColor(selected ? Design.RED : 0xffb8321c);
      canvas.drawCircle(anchorX, anchorY, Ui.dp(getContext(), selected ? 2.8f : 2.1f), paint);
      if (Math.hypot(nudgeX, nudgeY) > Ui.dp(getContext(), 4)) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(selected ? 0xccb8321c : 0x886b584d);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.1f));
        canvas.drawLine(anchorX, anchorY, cx, bottomY, paint);
      }

      // Drop shadow
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(selected ? 0x44000000 : 0x26000000);
      RectF shadow = new RectF(pill);
      shadow.offset(0, Ui.dp(getContext(), selected ? 2f : 1.2f));
      canvas.drawRoundRect(shadow, pillH / 2f, pillH / 2f, paint);

      // Pill body + pointer triangle
      paint.setColor(selected ? Design.RED : 0xf7fffdf9);
      canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint);
      if (Math.hypot(nudgeX, nudgeY) <= Ui.dp(getContext(), 4)) {
        Path tri = new Path();
        tri.moveTo(cx - pointerH, bottomY - 0.5f);
        tri.lineTo(cx + pointerH, bottomY - 0.5f);
        tri.lineTo(cx, bottomY + pointerH);
        tri.close();
        canvas.drawPath(tri, paint);
      }
      if (!selected) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xffd8c7be);
        paint.setStrokeWidth(Ui.dp(getContext(), 1f));
        canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint);
      }

      // Accent dot + crisp label text
      float dotCx = pill.left + padH + dotR;
      float midY = pill.centerY();
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(selected ? Color.WHITE : Design.RED);
      canvas.drawCircle(dotCx, midY, dotR, paint);

      textPaint.setColor(selected ? Design.ON_RED : Design.INK);
      Paint.FontMetrics fm = textPaint.getFontMetrics();
      float textY = midY - (fm.ascent + fm.descent) / 2f;
      canvas.drawText(label, dotCx + dotR + Ui.dp(getContext(), 4.5f), textY, textPaint);

      float hitPad = Ui.dp(getContext(), 6f);
      pinHitRects.put(region.id(), new RectF(pill.left - hitPad, pill.top - hitPad, pill.right + hitPad, pill.bottom + hitPad));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      scales.onTouchEvent(event);
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN -> {
          downX = lastX = event.getX();
          downY = lastY = event.getY();
          moved = false;
          getParent().requestDisallowInterceptTouchEvent(true);
          return true;
        }
        case MotionEvent.ACTION_POINTER_DOWN -> {
          moved = true;
          return true;
        }
        case MotionEvent.ACTION_MOVE -> {
          if (event.getPointerCount() == 1 && !scales.isInProgress()) {
            if (Math.hypot(event.getX() - downX, event.getY() - downY) > slop) moved = true;
            if (moved) {
              panX += event.getX() - lastX;
              panY += event.getY() - lastY;
              constrain();
              invalidate();
              scheduleTileDecode();
            }
          }
          lastX = event.getX();
          lastY = event.getY();
          return true;
        }
        case MotionEvent.ACTION_POINTER_UP -> {
          moved = true;
          int index = event.getActionIndex() == 0 ? 1 : 0;
          if (index < event.getPointerCount()) {
            lastX = event.getX(index);
            lastY = event.getY(index);
          }
          return true;
        }
        case MotionEvent.ACTION_UP -> {
          if (!moved) {
            float sx = event.getX(), sy = event.getY();
            float mapX = (sx - left() - panX) / (fit() * zoom);
            float mapY = (sy - top() - panY) / (fit() * zoom);
            if (pickStartMode && startPickListener != null) {
              startPickListener.accept(new float[]{
                  CampusMapGeometry.clamp(mapX, 0f, WIDTH),
                  CampusMapGeometry.clamp(mapY, 0f, HEIGHT)});
            } else {
              Region hit = hitTest(sx, sy, mapX, mapY);
              if (hit != null) {
                select(hit.id());
                if (listener != null) listener.accept(hit);
              }
            }
            performClick();
          }
          getParent().requestDisallowInterceptTouchEvent(false);
          return true;
        }
        case MotionEvent.ACTION_CANCEL -> {
          moved = true;
          getParent().requestDisallowInterceptTouchEvent(false);
          return true;
        }
        default -> { return true; }
      }
    }

    private Region hitTest(float sx, float sy, float mapX, float mapY) {
      // 1. Selected pin on top
      if (!selection.isEmpty()) {
        RectF selRect = pinHitRects.get(selection);
        if (selRect != null && selRect.contains(sx, sy)) {
          for (Region r : regions) if (r.id().equals(selection)) return r;
        }
      }
      // 2. Exact building polygon (preserves direct building taps like minghu at 2547,820)
      for (Region region : regions) {
        if (CampusMapGeometry.contains(region.points(), mapX, mapY)) return region;
      }
      // 3. Any fixed-scale pin pill
      for (Region region : regions) {
        RectF r = pinHitRects.get(region.id());
        if (r != null && r.contains(sx, sy)) return region;
      }
      return null;
    }

    @Override public boolean performClick() {
      super.performClick();
      return true;
    }
  }

  private CampusMapScreen() {}
}
