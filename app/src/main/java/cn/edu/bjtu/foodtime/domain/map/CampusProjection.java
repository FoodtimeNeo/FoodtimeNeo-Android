package cn.edu.bjtu.foodtime.domain.map;

import java.util.*;

/** Pure-Java WGS84 <-> illustrated campus map projection using global affine + TPS residuals. */
public final class CampusProjection {
  public record Anchor(String id, String name, double lat, double lng, double x, double y) {}
  public record PixelPoint(float x, float y) {}
  public record Bounds(double minLat, double maxLat, double minLng, double maxLng) {}

  private final float width, height;
  private final double centerLat, centerLng, campusRadiusMeters;
  private final Bounds bounds;
  private final List<Anchor> anchors;
  private final double[] srcU, srcV, weightsX, weightsY;
  private final double minLat, latSpan, minLng, lngSpan;

  public CampusProjection(float width, float height, double centerLat, double centerLng,
      double campusRadiusMeters, Bounds bounds, List<Anchor> anchors) {
    if (anchors == null || anchors.size() < 3) {
      throw new IllegalArgumentException("At least 3 calibration anchors are required");
    }
    this.width = width;
    this.height = height;
    this.centerLat = centerLat;
    this.centerLng = centerLng;
    this.campusRadiusMeters = campusRadiusMeters;
    this.bounds = bounds;
    this.anchors = List.copyOf(anchors);

    double loLat = Double.POSITIVE_INFINITY, hiLat = Double.NEGATIVE_INFINITY;
    double loLng = Double.POSITIVE_INFINITY, hiLng = Double.NEGATIVE_INFINITY;
    for (Anchor a : this.anchors) {
      loLat = Math.min(loLat, a.lat());
      hiLat = Math.max(hiLat, a.lat());
      loLng = Math.min(loLng, a.lng());
      hiLng = Math.max(hiLng, a.lng());
    }
    this.minLat = loLat;
    this.latSpan = Math.max(1e-6, hiLat - loLat);
    this.minLng = loLng;
    this.lngSpan = Math.max(1e-6, hiLng - loLng);

    int n = this.anchors.size();
    this.srcU = new double[n];
    this.srcV = new double[n];
    double[] dstX = new double[n];
    double[] dstY = new double[n];
    for (int i = 0; i < n; i++) {
      Anchor a = this.anchors.get(i);
      srcU[i] = (a.lng() - minLng) / lngSpan;
      srcV[i] = (a.lat() - minLat) / latSpan;
      dstX[i] = a.x();
      dstY[i] = a.y();
    }
    this.weightsX = fitTps(srcU, srcV, dstX, 1e-6);
    this.weightsY = fitTps(srcU, srcV, dstY, 1e-6);
  }

  public PixelPoint toPixel(double lat, double lng) {
    double u = (lng - minLng) / lngSpan;
    double v = (lat - minLat) / latSpan;
    double px = evalTps(srcU, srcV, weightsX, u, v);
    double py = evalTps(srcU, srcV, weightsY, u, v);
    return new PixelPoint(
        CampusMapGeometry.clamp((float) px, 0f, width),
        CampusMapGeometry.clamp((float) py, 0f, height));
  }

  public boolean isOnCampus(double lat, double lng) {
    if (!Double.isFinite(lat) || !Double.isFinite(lng)) return false;
    if (bounds != null && (lat < bounds.minLat() || lat > bounds.maxLat()
        || lng < bounds.minLng() || lng > bounds.maxLng())) {
      return false;
    }
    return distanceMeters(lat, lng, centerLat, centerLng) <= campusRadiusMeters;
  }

  public double distanceToCampusCenter(double lat, double lng) {
    return distanceMeters(lat, lng, centerLat, centerLng);
  }

  public List<Anchor> anchors() { return anchors; }

  public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.pow(Math.sin(dLat / 2.0), 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.pow(Math.sin(dLng / 2.0), 2);
    return 6371008.8 * 2.0 * Math.asin(Math.sqrt(Math.min(1.0, Math.max(0.0, a))));
  }

  public static int walkingMinutes(double meters, double speedMetersPerMin) {
    double speed = speedMetersPerMin > 10 ? speedMetersPerMin : 75.0;
    return Math.max(1, (int) Math.ceil(Math.max(0.0, meters) / speed));
  }

  public static String formatDistance(double meters) {
    double safe = Math.max(0.0, meters);
    if (safe < 1000.0) return Math.round(safe) + "m";
    return String.format(Locale.ROOT, "%.1fkm", safe / 1000.0);
  }

  public static String formatWalkSummary(double meters) {
    return formatDistance(meters) + " · 约" + walkingMinutes(meters, 75.0) + "分钟";
  }

  private static double tpsBasis(double r2) {
    return r2 <= 1e-18 ? 0.0 : 0.5 * r2 * Math.log(r2);
  }

  private static double[] fitTps(double[] u, double[] v, double[] dst, double reg) {
    int n = u.length;
    int size = n + 3;
    double[][] mat = new double[size][size];
    double[] rhs = new double[size];
    for (int i = 0; i < n; i++) {
      rhs[i] = dst[i];
      for (int j = 0; j < n; j++) {
        double du = u[i] - u[j], dv = v[i] - v[j];
        mat[i][j] = tpsBasis(du * du + dv * dv) + (i == j ? reg : 0.0);
      }
      mat[i][n] = 1.0;
      mat[i][n + 1] = u[i];
      mat[i][n + 2] = v[i];
      mat[n][i] = 1.0;
      mat[n + 1][i] = u[i];
      mat[n + 2][i] = v[i];
    }
    return solveLinear(mat, rhs);
  }

  private static double evalTps(double[] u, double[] v, double[] w, double pu, double pv) {
    int n = u.length;
    double val = w[n] + w[n + 1] * pu + w[n + 2] * pv;
    for (int i = 0; i < n; i++) {
      double du = pu - u[i], dv = pv - v[i];
      val += w[i] * tpsBasis(du * du + dv * dv);
    }
    return val;
  }

  private static double[] solveLinear(double[][] a, double[] b) {
    int n = b.length;
    double[][] m = new double[n][n + 1];
    for (int i = 0; i < n; i++) {
      System.arraycopy(a[i], 0, m[i], 0, n);
      m[i][n] = b[i];
    }
    for (int col = 0; col < n; col++) {
      int pivot = col;
      for (int r = col + 1; r < n; r++) {
        if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
      }
      double[] tmp = m[col];
      m[col] = m[pivot];
      m[pivot] = tmp;
      double div = m[col][col];
      if (Math.abs(div) < 1e-15) continue;
      for (int j = col; j <= n; j++) m[col][j] /= div;
      for (int r = 0; r < n; r++) {
        if (r == col) continue;
        double f = m[r][col];
        for (int j = col; j <= n; j++) m[r][j] -= f * m[col][j];
      }
    }
    double[] x = new double[n];
    for (int i = 0; i < n; i++) x[i] = m[i][n];
    return x;
  }
}
