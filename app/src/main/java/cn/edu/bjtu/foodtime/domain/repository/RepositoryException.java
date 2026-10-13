package cn.edu.bjtu.foodtime.domain.repository;

/** Safe user-facing API failure. Credentials, SQL and transport internals are not exposed. */
public final class RepositoryException extends RuntimeException {
  private final int status;
  private final String code;
  private final long retryAfterSeconds;

  public RepositoryException(int status, String code, String message, long retryAfterSeconds) {
    super(message);
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int status() { return status; }
  public String code() { return code; }
  public long retryAfterSeconds() { return retryAfterSeconds; }
}
