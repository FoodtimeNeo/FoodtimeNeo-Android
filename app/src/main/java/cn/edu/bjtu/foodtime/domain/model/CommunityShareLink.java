package cn.edu.bjtu.foodtime.domain.model;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CommunityShareLink {
  private static final String HOST = "bjtufoodtime.top";
  private static final String PREFIX = "/community/posts/";
  private static final Pattern PATH = Pattern.compile(PREFIX
      + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
      Pattern.CASE_INSENSITIVE);

  private CommunityShareLink() {}

  public static String postId(String url) {
    if (url == null || url.length() > 2048) return "";
    try {
      URI uri = URI.create(url);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())
          || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawPath() == null) return "";
      var match = PATH.matcher(uri.getRawPath());
      return match.matches() ? match.group(1).toLowerCase(Locale.ROOT) : "";
    } catch (IllegalArgumentException malformed) {
      return "";
    }
  }

  public static String postUrl(String id) {
    return "https://" + HOST + PREFIX + id;
  }
}
