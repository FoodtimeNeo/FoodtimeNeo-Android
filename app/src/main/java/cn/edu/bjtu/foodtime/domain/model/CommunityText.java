package cn.edu.bjtu.foodtime.domain.model;

/** Pure presentation-friendly text rules shared by community screens and tests. */
public final class CommunityText {
  private static final java.time.format.DateTimeFormatter PUBLISHED_AT = java.time.format.DateTimeFormatter
      .ofPattern("MM月dd日 HH:mm",java.util.Locale.CHINA).withZone(java.time.ZoneId.of("Asia/Shanghai"));
  public static String publishedAt(long epochMillis) {
    return epochMillis<=0?"":PUBLISHED_AT.format(java.time.Instant.ofEpochMilli(epochMillis));
  }
  private static final float[] COVER_RATIOS = {0.82f, 1.0f, 1.18f, 1.36f};

  public static String headline(String content) {
    if (content == null || content.isBlank()) return "一页校园食记";
    String value = content.strip().replaceAll("\\s+", " ");
    int sentence = firstBreak(value);
    if (sentence > 0 && sentence <= 42) value = value.substring(0, sentence);
    if (value.length() <= 36) return value;
    return value.substring(0, 35) + "…";
  }

  /** Stable fallback while the real image dimensions are decoded off the UI thread. */
  public static float coverHeightRatio(String postId) {
    int hash = postId == null ? 0 : postId.hashCode();
    return COVER_RATIOS[Math.floorMod(hash, COVER_RATIOS.length)];
  }

  private static int firstBreak(String value) {
    int result = -1;
    for (char marker : new char[] {'。', '！', '？', '!', '?'}) {
      int at = value.indexOf(marker);
      if (at >= 0 && (result < 0 || at < result)) result = at + 1;
    }
    return result;
  }

  private CommunityText() {}
}
