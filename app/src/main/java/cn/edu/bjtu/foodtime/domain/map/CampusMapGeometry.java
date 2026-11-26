package cn.edu.bjtu.foodtime.domain.map;

/** Image coordinates only. No GPS, platform views or catalog permissions. */
public final class CampusMapGeometry {
  public static boolean contains(float[][] polygon, float x, float y) {
    boolean inside = false;
    for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      float[] a = polygon[i], b = polygon[j];
      if ((a[1] > y) != (b[1] > y)
          && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) inside = !inside;
    }
    return inside;
  }

  public static float clamp(float value, float minimum, float maximum) {
    if (!Float.isFinite(value)) return minimum;
    return Math.max(minimum, Math.min(maximum, value));
  }

  private CampusMapGeometry() {}
}
