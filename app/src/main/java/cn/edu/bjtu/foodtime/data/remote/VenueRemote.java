package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.Models.LikeState;
import cn.edu.bjtu.foodtime.domain.model.VenueModels.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.json.*;

/**
 * Worker-only network access, account-scoped immutable snapshots; stale reads cannot publish into
 * another account.
 */
final class VenueRemote {
  interface Exchange {
    JSONObject call(String method, String path, JSONObject body);
  }

  private final Exchange exchange;
  private final Supplier<String> identity;
  private String owner = "";
  private long epoch, sequence;
  private final Map<String, Long> requests = new HashMap<>();
  private final Map<String, Target> summaries = new HashMap<>();
  private final Map<String, Snapshot> pages = new HashMap<>();
  private final Map<String, Page> replies = new HashMap<>(), privateReplies = new HashMap<>();
  private Page personal = Page.empty();

  VenueRemote(Exchange exchange, Supplier<String> identity) {
    this.exchange = exchange;
    this.identity = identity;
  }

  private synchronized void scope() {
    String next = identity.get();
    if (!owner.equals(next)) {
      clear();
      owner = next;
    }
  }

  synchronized void clear() {
    epoch++;
    requests.clear();
    summaries.clear();
    pages.clear();
    replies.clear();
    privateReplies.clear();
    personal = Page.empty();
    owner = "";
  }

  private record Token(String owner, long epoch, String key, long request) {}

  private synchronized Token begin(String key) {
    scope();
    long request = ++sequence;
    requests.put(key, request);
    return new Token(owner, epoch, key, request);
  }

  private synchronized void valid(Token token) {
    scope();
    if (Thread.currentThread().isInterrupted()
        || !owner.equals(token.owner())
        || epoch != token.epoch()
        || !Objects.equals(requests.get(token.key()), token.request()))
      throw new RepositoryException(0, "STALE_RESPONSE", "请求已失效，请重试", 0);
  }

  private static String key(String type, String id) {
    if (!Set.of("hall", "stall").contains(type)) throw new IllegalArgumentException("评价类型不合法");
    UUID.fromString(id);
    return type + "~" + id;
  }

  private static String path(String type, String id) {
    key(type, id);
    return (type.equals("hall") ? "dining-halls/" : "stalls/") + id;
  }

  private static String encode(String value) {
    try {
      return java.net.URLEncoder.encode(value, "UTF-8");
    } catch (java.io.UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static String text(JSONObject j, String field) {
    return j == null || j.isNull(field) ? "" : j.optString(field, "");
  }

  private static long time(JSONObject j, String field) {
    try {
      return Instant.parse(text(j, field)).toEpochMilli();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private static Target target(JSONObject j) {
    if (j == null
        || !Set.of("hall", "stall").contains(text(j, "type"))
        || text(j, "targetId").isBlank()
        || text(j, "name").isBlank()
        || !j.has("averageScore")) throw invalid();
    long count = nonNegative(j, "reviewCount");
    java.math.BigDecimal average =
        j.isNull("averageScore")
            ? null
            : new java.math.BigDecimal(j.opt("averageScore").toString());
    if ((count == 0) != (average == null)
        || average != null
            && (average.compareTo(java.math.BigDecimal.ONE) < 0
                || average.compareTo(java.math.BigDecimal.valueOf(5)) > 0)) throw invalid();
    return new Target(
        text(j, "type"),
        text(j, "targetId"),
        text(j, "name"),
        text(j, "diningHallId"),
        text(j, "stallId"),
        average,
        count);
  }

  private static RepositoryException invalid() {
    return new RepositoryException(0, "VENUE_INVALID_RESPONSE", "评价响应不符合接口约定，请重试", 0);
  }

  private static long nonNegative(JSONObject j, String key) {
    Object value = j.opt(key);
    if (!(value instanceof Number)) throw invalid();
    try {
      long n = new java.math.BigDecimal(value.toString()).longValueExact();
      if (n < 0) throw invalid();
      return n;
    } catch (ArithmeticException | NumberFormatException e) {
      throw invalid();
    }
  }

  static Row row(JSONObject j) {
    if (j == null) return null;
    if (text(j, "id").isBlank()
        || text(j, "reviewId").isBlank()
        || text(j, "content").isBlank()
        || !Set.of("pending", "approved", "rejected", "invalidated").contains(text(j, "status")))
      throw invalid();
    return new Row(
        text(j, "id"),
        text(j, "reviewId"),
        text(j, "submissionId"),
        text(j, "userId"),
        text(j, "userDisplayName"),
        text(j, "targetType"),
        text(j, "targetId"),
        text(j, "name"),
        j.optInt("rating"),
        text(j, "content"),
        text(j, "status"),
        text(j, "reviewReason"),
        time(j, "createdAt"),
        time(j, "updatedAt"),
        j.optBoolean("ownedByMe"),
        new LikeState(
            j.optBoolean("likedByMe"), j.optLong("likeCount"), j.optLong("counterVersion")),
        j.optLong("replyCount"),
        text(j, "replyToId"),
        text(j, "replyToUserDisplayName"),
        j.optBoolean("replyToDeleted"));
  }

  private static Page page(JSONObject j) {
    List<Row> items = new ArrayList<>();
    JSONArray array = j.optJSONArray("items");
    if (array == null) throw invalid();
    for (int i = 0; i < array.length(); i++) {
      JSONObject item = array.optJSONObject(i);
      if (item == null) throw invalid();
      items.add(row(item));
    }
    return new Page(items, nonNegative(j, "total"), j.optBoolean("hasMore"), text(j, "nextCursor"));
  }

  private static Page merged(Page old, Page next, boolean more) {
    if (!more) return next;
    Map<String, Row> all = new LinkedHashMap<>();
    old.items().forEach(r -> all.put(r.id(), r));
    next.items().forEach(r -> all.put(r.id(), r));
    return new Page(List.copyOf(all.values()), next.total(), next.hasMore(), next.nextCursor());
  }

  private static JSONObject body(Object... pairs) {
    try {
      JSONObject j = new JSONObject();
      for (int i = 0; i < pairs.length; i += 2)
        j.put((String) pairs[i], pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
      return j;
    } catch (JSONException e) {
      throw new IllegalArgumentException("提交内容不合法");
    }
  }

  synchronized Target summary(String type, String id) {
    scope();
    return summaries.get(key(type, id));
  }

  synchronized Snapshot snapshot(String type, String id) {
    scope();
    return pages.get(key(type, id));
  }

  synchronized Page replies(String id, boolean mine) {
    scope();
    return (mine ? privateReplies : replies).getOrDefault(id, Page.empty());
  }

  synchronized Page personal() {
    scope();
    return personal;
  }

  AuditDetail auditDetail(String type, String id) {
    if (!Set.of("hall-review", "stall-review", "hall-review-reply", "stall-review-reply")
        .contains(type)) throw new IllegalArgumentException("审核类型不合法");
    UUID.fromString(id);
    Token token = begin("audit:" + id);
    JSONObject data = exchange.call("GET", "admin/venue-moderation/" + type + "/" + id, null);
    synchronized (this) {
      valid(token);
      return new AuditDetail(
          row(data.optJSONObject("publicVersion")), row(data.optJSONObject("submission")));
    }
  }

  void loadSummary(String type, String id) {
    Token token = begin("summary:" + key(type, id));
    Target next = target(exchange.call("GET", path(type, id) + "/rating", null));
    synchronized (this) {
      valid(token);
      summaries.put(key(type, id), next);
    }
  }

  void load(String type, String id, String rating, boolean more) {
    if (!Set.of("all", "5", "4", "1-3").contains(rating))
      throw new IllegalArgumentException("评分筛选不合法");
    String key = key(type, id);
    Token token = begin("reviews:" + key);
    Snapshot old = snapshot(type, id);
    boolean append = more && old != null && old.rating().equals(rating);
    String cursor = append ? old.published().nextCursor() : "";
    Target meta = target(exchange.call("GET", path(type, id) + "/rating", null));
    Page pub =
        page(
            exchange.call(
                "GET",
                path(type, id)
                    + "/reviews?size=20&rating="
                    + rating
                    + (cursor.isBlank() ? "" : "&cursor=" + encode(cursor)),
                null));
    JSONObject mine = exchange.call("GET", path(type, id) + "/reviews/me", null);
    synchronized (this) {
      valid(token);
      summaries.put(key, meta);
      pages.put(
          key,
          new Snapshot(
              meta,
              merged(append ? old.published() : Page.empty(), pub, append),
              row(mine.optJSONObject("publicVersion")),
              row(mine.optJSONObject("privateSubmission")),
              rating));
    }
  }

  void refreshPending(String type, String id) {
    String key = key(type, id);
    Token token = begin("reviews:" + key);
    Snapshot old = snapshot(type, id);
    if (old == null) {
      load(type, id, "all", false);
      return;
    }
    JSONObject mine = exchange.call("GET", path(type, id) + "/reviews/me", null);
    Row pub = row(mine.optJSONObject("publicVersion")),
        own = row(mine.optJSONObject("privateSubmission"));
    boolean changed =
        !Objects.equals(
                pub == null ? null : pub.submissionId(),
                old.publicVersion() == null ? null : old.publicVersion().submissionId())
            || !Objects.equals(
                own == null ? null : own.status(),
                old.privateSubmission() == null ? null : old.privateSubmission().status());
    synchronized (this) {
      valid(token);
      pages.put(key, new Snapshot(old.target(), old.published(), pub, own, old.rating()));
    }
    if (changed) load(type, id, old.rating(), false);
  }

  void refreshReplies(String id) {
    Token token = begin("replies:" + id);
    Page old = replies(id, true);
    Page own = page(exchange.call("GET", "venue-reviews/" + id + "/replies/me?size=20", null));
    boolean changed =
        !old.items().stream()
            .map(r -> r.id() + ":" + r.status())
            .collect(java.util.stream.Collectors.toList())
            .equals(
                own.items().stream()
                    .map(r -> r.id() + ":" + r.status())
                    .collect(java.util.stream.Collectors.toList()));
    synchronized (this) {
      valid(token);
      privateReplies.put(id, own);
    }
    if (changed) loadReplies(id, false, false);
  }

  Row submit(String type, String id, int rating, String content, String request) {
    Token token = begin("submit:" + key(type, id));
    Row created =
        row(
            exchange.call(
                "POST",
                path(type, id) + "/reviews",
                body("rating", rating, "content", content, "clientRequestId", request)));
    synchronized (this) {
      valid(token);
      Snapshot old = pages.get(key(type, id));
      if (old != null)
        pages.put(
            key(type, id),
            new Snapshot(
                old.target(), old.published(), old.publicVersion(), created, old.rating()));
    }
    return created;
  }

  void loadReplies(String id, boolean more, boolean mineMore) {
    UUID.fromString(id);
    Token token = begin("replies:" + id);
    Page old = replies(id, false), oldMine = replies(id, true);
    String cursor = more ? old.nextCursor() : "", ownCursor = mineMore ? oldMine.nextCursor() : "";
    Page pub =
        page(
            exchange.call(
                "GET",
                "venue-reviews/"
                    + id
                    + "/replies?size=20"
                    + (cursor.isBlank() ? "" : "&cursor=" + encode(cursor)),
                null));
    Page mine =
        page(
            exchange.call(
                "GET",
                "venue-reviews/"
                    + id
                    + "/replies/me?size=20"
                    + (ownCursor.isBlank() ? "" : "&cursor=" + encode(ownCursor)),
                null));
    synchronized (this) {
      valid(token);
      replies.put(id, merged(old, pub, more));
      privateReplies.put(id, merged(oldMine, mine, mineMore));
    }
  }

  Row submitReply(String id, String content, String target, String request) {
    Token token = begin("reply-submit:" + id);
    Row created =
        row(
            exchange.call(
                "POST",
                "venue-reviews/" + id + "/replies",
                body(
                    "content",
                    content,
                    "replyToId",
                    target == null || target.isBlank() ? null : target,
                    "clientRequestId",
                    request)));
    synchronized (this) {
      valid(token);
      Page old = privateReplies.getOrDefault(id, Page.empty());
      if (created.status().equals("pending")) {
        List<Row> items = new ArrayList<>(old.items());
        boolean existed = items.removeIf(r -> r.id().equals(created.id()));
        items.add(created);
        privateReplies.put(
            id, new Page(items, old.total() + (existed ? 0 : 1), old.hasMore(), old.nextCursor()));
      }
    }
    return created;
  }

  LikeState like(String id, boolean liked, boolean reply) {
    UUID.fromString(id);
    Token token = begin("like:" + id);
    JSONObject result =
        exchange.call(
            liked ? "PUT" : "DELETE",
            (reply ? "venue-review-replies/" : "venue-reviews/") + id + "/like",
            null);
    valid(token);
    return new LikeState(
        result.optBoolean("likedByMe"),
        result.optLong("likeCount"),
        result.optLong("counterVersion"));
  }

  void delete(String id, boolean reply, boolean admin, String reason) {
    UUID.fromString(id);
    Token token = begin("delete:" + id);
    exchange.call(
        "DELETE",
        (admin ? "admin/" : "")
            + (reply
                ? "venue-review-replies/"
                : admin ? "venue-reviews/" : "users/me/venue-reviews/")
            + id,
        admin ? body("reason", reason) : null);
    valid(token);
  }

  void loadPersonal(String type, boolean reply, int number) {
    if (!type.isBlank() && !Set.of("hall", "stall").contains(type) || number < 1)
      throw new IllegalArgumentException("分页或类型不合法");
    Token token = begin("personal");
    JSONObject data =
        exchange.call(
            "GET",
            "users/me/venue-"
                + (reply ? "review-replies" : "reviews")
                + "?page="
                + number
                + "&size=20"
                + (type.isBlank() ? "" : "&type=" + type),
            null);
    Page page = page(data);
    synchronized (this) {
      valid(token);
      personal =
          new Page(
              page.items(),
              page.total(),
              number * 20L < page.total(),
              Integer.toString(number + 1));
    }
  }
}
