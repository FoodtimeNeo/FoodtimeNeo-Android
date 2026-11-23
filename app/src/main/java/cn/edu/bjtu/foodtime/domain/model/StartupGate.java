package cn.edu.bjtu.foodtime.domain.model;

/** Monotonic startup timing, separate from network work and Android views. */
public final class StartupGate {
  private final long startedAt, minimumMillis, timeoutMillis;
  private long visibleAt;
  private boolean painted;
  private boolean dismissed;
  public StartupGate(long startedAt, long minimumMillis, long timeoutMillis) {
    if (minimumMillis < 0 || timeoutMillis < minimumMillis) throw new IllegalArgumentException("Invalid startup timing");
    this.startedAt = startedAt;
    visibleAt = startedAt;
    this.minimumMillis = minimumMillis;
    this.timeoutMillis = timeoutMillis;
  }
  public boolean showing() { return !dismissed; }
  public void shown(long now) { if (!painted) { visibleAt = Math.max(startedAt, now); painted = true; } }
  public long remaining(long now, boolean contentReady) {
    if (dismissed) return 0;
    if (!contentReady) return Long.MAX_VALUE;
    return Math.max(0, minimumMillis - Math.max(0, now - visibleAt));
  }
  public boolean timedOut(long now, boolean contentReady) {
    return !dismissed && !contentReady && now - startedAt >= timeoutMillis;
  }
  public long timeoutRemaining(long now) { return Math.max(0, timeoutMillis - Math.max(0, now - startedAt)); }
  public void dismiss() { dismissed = true; }
}
