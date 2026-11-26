package cn.edu.bjtu.foodtime.domain.map;

import java.util.*;

/** Offline campus walking router using road-edge snapping + Dijkstra + turn-by-turn step merging. */
public final class CampusNavigator {
  public record Node(String id, String name, String kind, float x, float y,
                     double lat, double lng, int presetOrder) {}
  public record Edge(String from, String to, String roadName, double distanceMeters, String note) {}
  public record RouteStep(String instruction, String roadName, double distanceMeters, float x, float y) {}
  public record RoutePlan(List<float[]> polyline, List<RouteStep> steps, double totalMeters,
                          int walkMinutes, boolean snappedFromRoad, float startX, float startY,
                          String startLabel, String targetNodeId, String targetName) {}

  private record Link(String to, String roadName, double distanceMeters, String note) {}
  private record Segment(String roadName, double distanceMeters, String note,
                         float x1, float y1, float x2, float y2) {}

  private final double walkSpeedMetersPerMin;
  private final Map<String, Node> nodes = new LinkedHashMap<>();
  private final List<Edge> edges;
  private final Map<String, List<Link>> adjacency = new HashMap<>();

  public CampusNavigator(double walkSpeedMetersPerMin, List<Node> nodeList, List<Edge> edgeList) {
    this.walkSpeedMetersPerMin = walkSpeedMetersPerMin > 10 ? walkSpeedMetersPerMin : 75.0;
    for (Node n : nodeList) {
      nodes.put(n.id(), n);
      adjacency.put(n.id(), new ArrayList<>());
    }
    this.edges = List.copyOf(edgeList);
    for (Edge e : this.edges) {
      if (!nodes.containsKey(e.from()) || !nodes.containsKey(e.to())) continue;
      adjacency.get(e.from()).add(new Link(e.to(), e.roadName(), e.distanceMeters(), e.note()));
      adjacency.get(e.to()).add(new Link(e.from(), e.roadName(), e.distanceMeters(), e.note()));
    }
  }

  public List<Node> presetStartNodes() {
    List<Node> presets = new ArrayList<>();
    for (Node n : nodes.values()) if (n.presetOrder() > 0) presets.add(n);
    presets.sort(Comparator.comparingInt(Node::presetOrder));
    return presets;
  }

  public Node node(String id) { return nodes.get(id); }

  public RoutePlan routeFromNode(String startNodeId, String targetNodeId, String targetName) {
    Node start = nodes.get(startNodeId);
    Node target = nodes.get(targetNodeId);
    if (start == null || target == null) return null;
    if (startNodeId.equals(targetNodeId)) {
      return new RoutePlan(List.of(new float[]{start.x(), start.y()}),
          List.of(new RouteStep("您已在 " + targetName + " 附近", "", 0, start.x(), start.y())),
          0, 1, false, start.x(), start.y(), start.name(), targetNodeId, targetName);
    }
    List<String> path = dijkstra(startNodeId, targetNodeId, null, null);
    if (path.isEmpty()) return null;
    List<float[]> poly = new ArrayList<>();
    List<Segment> segments = new ArrayList<>();
    double total = 0;
    for (int i = 0; i < path.size(); i++) {
      Node cur = nodes.get(path.get(i));
      poly.add(new float[]{cur.x(), cur.y()});
      if (i + 1 < path.size()) {
        Node nxt = nodes.get(path.get(i + 1));
        Link link = findLink(cur.id(), nxt.id());
        if (link != null) {
          total += link.distanceMeters();
          segments.add(new Segment(link.roadName(), link.distanceMeters(), link.note(),
              cur.x(), cur.y(), nxt.x(), nxt.y()));
        }
      }
    }
    return buildPlan(poly, segments, total, false, start.x(), start.y(), start.name(), targetNodeId, targetName);
  }

  public RoutePlan routeFromPixel(float startX, float startY, String startLabel,
                                  String targetNodeId, String targetName) {
    Node target = nodes.get(targetNodeId);
    if (target == null || edges.isEmpty()) return null;

    // Snap arbitrary start pixel to the closest walkable road segment
    Edge bestEdge = null;
    double bestDist2 = Double.POSITIVE_INFINITY;
    float bestPx = startX, bestPy = startY;
    double bestT = 0;
    for (Edge e : edges) {
      Node a = nodes.get(e.from()), b = nodes.get(e.to());
      if (a == null || b == null) continue;
      double dx = b.x() - a.x(), dy = b.y() - a.y();
      double len2 = dx * dx + dy * dy;
      double t = len2 < 1e-6 ? 0.0 : ((startX - a.x()) * dx + (startY - a.y()) * dy) / len2;
      t = Math.max(0.0, Math.min(1.0, t));
      float px = (float) (a.x() + t * dx), py = (float) (a.y() + t * dy);
      double d2 = (startX - px) * (startX - px) + (startY - py) * (startY - py);
      if (d2 < bestDist2) {
        bestDist2 = d2;
        bestEdge = e;
        bestPx = px;
        bestPy = py;
        bestT = t;
      }
    }
    if (bestEdge == null) return null;

    // Estimate off-road connector length using edge's local meters-per-pixel scale
    Node ea = nodes.get(bestEdge.from()), eb = nodes.get(bestEdge.to());
    double edgePx = Math.hypot(eb.x() - ea.x(), eb.y() - ea.y());
    double metersPerPx = edgePx > 1 ? bestEdge.distanceMeters() / edgePx : 0.25;
    double connectorMeters = Math.sqrt(bestDist2) * metersPerPx;
    double toA = bestEdge.distanceMeters() * bestT;
    double toB = bestEdge.distanceMeters() * (1.0 - bestT);

    String virtualId = "__start__";
    List<Link> virtualLinks = List.of(
        new Link(bestEdge.from(), bestEdge.roadName(), toA, ""),
        new Link(bestEdge.to(), bestEdge.roadName(), toB, ""));

    List<String> path = dijkstra(virtualId, targetNodeId, virtualId, virtualLinks);
    if (path.isEmpty()) return null;

    List<float[]> poly = new ArrayList<>();
    List<Segment> segments = new ArrayList<>();
    double total = connectorMeters;
    boolean snapped = Math.sqrt(bestDist2) > 8.0;
    poly.add(new float[]{bestPx, bestPy});
    if (path.size() >= 2) {
      Node first = nodes.get(path.get(1));
      double segDist = path.get(1).equals(bestEdge.from()) ? toA : toB;
      if (segDist > 1.0) {
        total += segDist;
        segments.add(new Segment(bestEdge.roadName(), segDist, "", bestPx, bestPy, first.x(), first.y()));
      }
      poly.add(new float[]{first.x(), first.y()});
      for (int i = 1; i + 1 < path.size(); i++) {
        Node cur = nodes.get(path.get(i));
        Node nxt = nodes.get(path.get(i + 1));
        Link link = findLink(cur.id(), nxt.id());
        if (link != null) {
          total += link.distanceMeters();
          segments.add(new Segment(link.roadName(), link.distanceMeters(), link.note(),
              cur.x(), cur.y(), nxt.x(), nxt.y()));
        }
        poly.add(new float[]{nxt.x(), nxt.y()});
      }
    }
    return buildPlan(poly, segments, total, snapped, startX, startY, startLabel, targetNodeId, targetName);
  }

  public double remainingMeters(RoutePlan plan, float currentX, float currentY) {
    if (plan == null || plan.polyline().size() < 2) return 0;
    List<float[]> pts = plan.polyline();
    // Find closest polyline segment and sum remaining ratio
    int bestIdx = 0;
    double bestDist2 = Double.POSITIVE_INFINITY;
    double bestT = 0;
    double totalPx = 0;
    double[] segPx = new double[pts.size() - 1];
    for (int i = 0; i < pts.size() - 1; i++) {
      float[] a = pts.get(i), b = pts.get(i + 1);
      double dx = b[0] - a[0], dy = b[1] - a[1];
      double len2 = dx * dx + dy * dy;
      segPx[i] = Math.sqrt(len2);
      totalPx += segPx[i];
      double t = len2 < 1e-6 ? 0.0 : ((currentX - a[0]) * dx + (currentY - a[1]) * dy) / len2;
      t = Math.max(0.0, Math.min(1.0, t));
      double px = a[0] + t * dx, py = a[1] + t * dy;
      double d2 = (currentX - px) * (currentX - px) + (currentY - py) * (currentY - py);
      if (d2 < bestDist2) {
        bestDist2 = d2;
        bestIdx = i;
        bestT = t;
      }
    }
    double metersPerPx = totalPx > 1.0 ? plan.totalMeters() / totalPx : 0.35;
    double offRoadMeters = Math.sqrt(bestDist2) * metersPerPx;
    if (totalPx < 1) return Math.max(0, offRoadMeters);
    double remPx = segPx[bestIdx] * (1.0 - bestT);
    for (int i = bestIdx + 1; i < segPx.length; i++) remPx += segPx[i];
    return Math.max(0, plan.totalMeters() * (remPx / totalPx) + offRoadMeters);
  }

  public int activeStepIndex(RoutePlan plan, float currentX, float currentY) {
    if (plan == null || plan.steps().isEmpty()) return 0;
    int best = 0;
    double bestD2 = Double.POSITIVE_INFINITY;
    for (int i = 0; i < plan.steps().size(); i++) {
      RouteStep s = plan.steps().get(i);
      double d2 = (s.x() - currentX) * (s.x() - currentX) + (s.y() - currentY) * (s.y() - currentY);
      if (d2 < bestD2) {
        bestD2 = d2;
        best = i;
      }
    }
    return best;
  }

  private RoutePlan buildPlan(List<float[]> poly, List<Segment> segments, double totalMeters,
                              boolean snapped, float startX, float startY, String startLabel,
                              String targetNodeId, String targetName) {
    List<RouteStep> steps = new ArrayList<>();
    int i = 0;
    float prevDx = 0, prevDy = 0;
    boolean hasPrev = false;
    while (i < segments.size()) {
      Segment first = segments.get(i);
      String road = first.roadName();
      String note = first.note();
      double dist = first.distanceMeters();
      float sx = first.x1(), sy = first.y1();
      float ex = first.x2(), ey = first.y2();
      int j = i + 1;
      while (j < segments.size()
          && (segments.get(j).note() == null || segments.get(j).note().isEmpty())
          && (note == null || note.isEmpty())) {
        Segment nxt = segments.get(j);
        boolean sameRoad = Objects.equals(nxt.roadName(), road);
        boolean microStraight = nxt.distanceMeters() < 32.0
            && "直行".equals(turnWord(ex - sx, ey - sy, nxt.x2() - nxt.x1(), nxt.y2() - nxt.y1()));
        if (!sameRoad && !microStraight) break;
        dist += nxt.distanceMeters();
        ex = nxt.x2();
        ey = nxt.y2();
        j++;
      }
      float curDx = ex - sx, curDy = ey - sy;
      String action;
      if (note != null && !note.isBlank()) {
        action = note + "，沿 " + road + " 步行 " + CampusProjection.formatDistance(dist);
      } else if (!hasPrev) {
        action = "从 " + startLabel + " 出发，沿 " + road + " " + compassWord(curDx, curDy)
            + "步行 " + CampusProjection.formatDistance(dist);
      } else {
        String turn = turnWord(prevDx, prevDy, curDx, curDy);
        action = turn + "进入 " + road + "，步行 " + CampusProjection.formatDistance(dist);
      }
      steps.add(new RouteStep(action, road, dist, sx, sy));
      prevDx = curDx;
      prevDy = curDy;
      hasPrev = true;
      i = j;
    }
    float[] endPt = poly.get(poly.size() - 1);
    steps.add(new RouteStep("到达 " + targetName, "", 0, endPt[0], endPt[1]));
    int mins = CampusProjection.walkingMinutes(totalMeters, walkSpeedMetersPerMin);
    return new RoutePlan(poly, steps, totalMeters, mins, snapped, startX, startY, startLabel, targetNodeId, targetName);
  }

  private static String compassWord(float dx, float dy) {
    // Campus map north is tilted ~11 deg counter-clockwise; simple 4-way hint is intuitive
    if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? "向东" : "向西";
    return dy >= 0 ? "向南" : "向北";
  }

  private static String turnWord(float dx1, float dy1, float dx2, float dy2) {
    double l1 = Math.hypot(dx1, dy1), l2 = Math.hypot(dx2, dy2);
    if (l1 < 1e-4 || l2 < 1e-4) return "继续直行";
    double cross = (dx1 * dy2 - dy1 * dx2) / (l1 * l2);
    double dot = (dx1 * dx2 + dy1 * dy2) / (l1 * l2);
    if (dot > 0.85) return "直行";
    // Screen coordinates have Y pointing downward: positive cross = right turn, negative = left turn
    if (cross > 0.28) return "右转";
    if (cross < -0.28) return "左转";
    return "前行";
  }

  private Link findLink(String from, String to) {
    List<Link> links = adjacency.get(from);
    if (links == null) return null;
    for (Link l : links) if (l.to().equals(to)) return l;
    return null;
  }

  private List<String> dijkstra(String start, String target, String extraId, List<Link> extraLinks) {
    record State(String id, double dist) {}
    Map<String, Double> dist = new HashMap<>();
    Map<String, String> prev = new HashMap<>();
    PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingDouble(State::dist));
    dist.put(start, 0.0);
    pq.add(new State(start, 0.0));
    while (!pq.isEmpty()) {
      State cur = pq.poll();
      if (cur.dist() > dist.getOrDefault(cur.id(), Double.POSITIVE_INFINITY)) continue;
      if (cur.id().equals(target)) break;
      List<Link> nextLinks = cur.id().equals(extraId) ? extraLinks : adjacency.get(cur.id());
      if (nextLinks == null) continue;
      for (Link link : nextLinks) {
        double nd = cur.dist() + link.distanceMeters();
        if (nd < dist.getOrDefault(link.to(), Double.POSITIVE_INFINITY)) {
          dist.put(link.to(), nd);
          prev.put(link.to(), cur.id());
          pq.add(new State(link.to(), nd));
        }
      }
    }
    if (!dist.containsKey(target)) return List.of();
    LinkedList<String> path = new LinkedList<>();
    for (String at = target; at != null; at = prev.get(at)) path.addFirst(at);
    return path;
  }
}
