package cn.edu.bjtu.foodtime.domain.map;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public final class CampusNavigationTest {
  private static JSONObject asset(String name) throws Exception {
    Path path = Paths.get("src/main/assets/campus-map", name);
    return new JSONObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
  }

  @Test
  public void calibrationMapsEveryAnchorAccuratelyAndDetectsCampusBounds() throws Exception {
    JSONObject cal = asset("calibration.json");
    JSONObject center = cal.getJSONObject("campusCenter");
    JSONObject b = cal.getJSONObject("bounds");
    JSONArray arr = cal.getJSONArray("anchors");
    List<CampusProjection.Anchor> anchors = new ArrayList<>();
    for (int i = 0; i < arr.length(); i++) {
      JSONObject a = arr.getJSONObject(i);
      anchors.add(new CampusProjection.Anchor(a.getString("id"), a.getString("name"),
          a.getDouble("lat"), a.getDouble("lng"), a.getDouble("x"), a.getDouble("y")));
    }
    CampusProjection proj = new CampusProjection(
        (float) cal.getDouble("width"), (float) cal.getDouble("height"),
        center.getDouble("lat"), center.getDouble("lng"),
        cal.getDouble("campusRadiusMeters"),
        new CampusProjection.Bounds(b.getDouble("minLat"), b.getDouble("maxLat"),
            b.getDouble("minLng"), b.getDouble("maxLng")),
        anchors);

    for (CampusProjection.Anchor a : anchors) {
      CampusProjection.PixelPoint pt = proj.toPixel(a.lat(), a.lng());
      assertEquals("Anchor X error for " + a.id(), a.x(), pt.x(), 2.0);
      assertEquals("Anchor Y error for " + a.id(), a.y(), pt.y(), 2.0);
      assertTrue("Anchor must be on campus: " + a.id(), proj.isOnCampus(a.lat(), a.lng()));
    }
    assertFalse("Tiananmen is off campus", proj.isOnCampus(39.9087, 116.3975));
    assertEquals("280m · 约4分钟", CampusProjection.formatWalkSummary(280));
    assertEquals("1.2km · 约16分钟", CampusProjection.formatWalkSummary(1200));
  }

  @Test
  public void graphConnectsAllSevenHallsFromEveryPresetGateAndSnapsArbitraryPoints() throws Exception {
    JSONObject regions = asset("regions.json");
    JSONObject graph = asset("campus-graph.json");
    List<CampusNavigator.Node> nodes = new ArrayList<>();
    JSONArray na = graph.getJSONArray("nodes");
    for (int i = 0; i < na.length(); i++) {
      JSONObject n = na.getJSONObject(i);
      nodes.add(new CampusNavigator.Node(n.getString("id"), n.getString("name"), n.getString("kind"),
          (float) n.getDouble("x"), (float) n.getDouble("y"),
          n.getDouble("lat"), n.getDouble("lng"), n.optInt("presetOrder", 0)));
    }
    List<CampusNavigator.Edge> edges = new ArrayList<>();
    JSONArray ea = graph.getJSONArray("edges");
    for (int i = 0; i < ea.length(); i++) {
      JSONObject e = ea.getJSONObject(i);
      edges.add(new CampusNavigator.Edge(e.getString("from"), e.getString("to"),
          e.getString("roadName"), e.getDouble("distanceMeters"), e.optString("note", "")));
    }
    CampusNavigator nav = new CampusNavigator(graph.getDouble("walkSpeedMetersPerMin"), nodes, edges);
    assertEquals(7, regions.getJSONArray("regions").length());
    assertTrue(nav.presetStartNodes().size() >= 4);

    JSONArray regArr = regions.getJSONArray("regions");
    for (CampusNavigator.Node gate : nav.presetStartNodes()) {
      for (int i = 0; i < regArr.length(); i++) {
        JSONObject reg = regArr.getJSONObject(i);
        CampusNavigator.RoutePlan plan = nav.routeFromNode(gate.id(), reg.getString("entryNodeId"), reg.getString("name"));
        assertNotNull("Missing route from " + gate.id() + " to " + reg.getString("id"), plan);
        assertTrue(plan.polyline().size() >= 2);
        assertTrue(plan.steps().size() >= 2);
        assertTrue(plan.totalMeters() > 30 && plan.totalMeters() < 2500);
      }
    }

    CampusNavigator.RoutePlan snapped = nav.routeFromPixel(1935f, 1620f, "我的位置", "hall-minghu", "明湖餐厅");
    assertNotNull(snapped);
    assertTrue(snapped.totalMeters() > 150 && snapped.totalMeters() < 700);
    assertTrue(nav.remainingMeters(snapped, 1935f, 1620f) > 100);
  }
}
