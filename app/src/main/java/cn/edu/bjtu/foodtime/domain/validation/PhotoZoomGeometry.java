package cn.edu.bjtu.foodtime.domain.validation;

/** Pure zoom math shared by the full-screen photo gesture coordinator. */
public final class PhotoZoomGeometry {
  public static final float MIN_SCALE = 1f;
  public static final float MAX_SCALE = 5f;

  private PhotoZoomGeometry() {}

  public static float clampScale(float value) {
    return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
  }

  /** Ease-out curve; the animator itself stays linear so progress is predictable. */
  public static float easeOutCubic(float progress) {
    float t = Math.max(0f, Math.min(1f, progress));
    float inverse = 1f - t;
    return 1f - inverse * inverse * inverse;
  }

  public static float interpolateScale(float from, float to, float progress) {
    float t = Math.max(0f, Math.min(1f, progress));
    return from + (to - from) * t;
  }

  /** Keeps the content under the anchor fixed while scale changes from baseScale. */
  public static float anchoredShift(float anchor, float center, float baseShift,
      float baseScale, float scale) {
    return anchor - center - (anchor - center - baseShift) * (scale / baseScale);
  }
}
