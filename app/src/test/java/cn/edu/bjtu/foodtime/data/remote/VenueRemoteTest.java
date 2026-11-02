package cn.edu.bjtu.foodtime.data.remote;

import static org.junit.Assert.*;

import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.Test;

public class VenueRemoteTest {
  static final String HALL = "00000000-0000-4000-8000-000000000101",
      ROOT = "00000000-0000-4000-8000-000000000401",
      REPLY = "00000000-0000-4000-8000-000000000402";

  static JSONObject object(Object... fields) {
    try {
      JSONObject j = new JSONObject();
      for (int i = 0; i < fields.length; i += 2) j.put((String) fields[i], fields[i + 1]);
      return j;
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  static JSONObject row(String id, String status, String content) {
    return object(
        "id",
        id,
        "reviewId",
        ROOT,
        "submissionId",
        REPLY,
        "targetType",
        "hall",
        "targetId",
        HALL,
        "rating",
        4,
        "content",
        content,
        "status",
        status,
        "ownedByMe",
        true,
        "createdAt",
        "2026-11-02T00:00:00Z");
  }

  static JSONObject meta() {
    return object(
        "type",
        "hall",
        "targetId",
        HALL,
        "name",
        "学苑餐厅",
        "diningHallId",
        HALL,
        "averageScore",
        JSONObject.NULL,
        "reviewCount",
        0);
  }

  static JSONObject page(JSONObject... rows) {
    return object(
        "items", new JSONArray(Arrays.asList(rows)), "total", rows.length, "hasMore", false);
  }

  @Test
  public void pendingRestoresFromOwnerEndpointWithoutJoiningPublicCounts() {
    List<String> paths = new ArrayList<>();
    VenueRemote remote =
        new VenueRemote(
            (m, p, b) -> {
              paths.add(p);
              if (p.endsWith("/rating")) return meta();
              if (p.endsWith("/me"))
                return object(
                    "publicVersion",
                    JSONObject.NULL,
                    "privateSubmission",
                    row(ROOT, "pending", "本人草稿"));
              return page();
            },
            () -> "one");
    remote.load("hall", HALL, "all", false);
    var snapshot = remote.snapshot("hall", HALL);
    assertEquals(0, snapshot.published().total());
    assertNull(snapshot.target().averageScore());
    assertEquals("本人草稿", snapshot.privateSubmission().content());
    assertTrue(paths.contains("dining-halls/" + HALL + "/reviews/me"));
  }

  @Test
  public void sameOperationAndActualReplyTargetAreSentWithoutRegeneration() {
    List<JSONObject> bodies = new ArrayList<>();
    VenueRemote remote =
        new VenueRemote(
            (m, p, b) -> {
              bodies.add(b);
              return row(REPLY, "pending", "回复");
            },
            () -> "one");
    String op = UUID.randomUUID().toString();
    remote.submitReply(ROOT, "回复", REPLY, op);
    remote.submitReply(ROOT, "回复", REPLY, op);
    assertEquals(op, bodies.get(0).optString("clientRequestId"));
    assertEquals(op, bodies.get(1).optString("clientRequestId"));
    assertEquals(REPLY, bodies.get(0).optString("replyToId"));
    assertEquals(1, remote.replies(ROOT, true).items().size());
    assertEquals(1, remote.replies(ROOT, true).total());
  }

  @Test
  public void approvalReplacesPrivatePreviewAndRefreshesPublicOnlyOnTransition() {
    AtomicReference<String> state = new AtomicReference<>("pending");
    List<String> paths = new ArrayList<>();
    VenueRemote remote =
        new VenueRemote(
            (m, p, b) -> {
              paths.add(p);
              if (p.endsWith("/rating")) return meta();
              if (p.endsWith("/me"))
                return state.get().equals("pending")
                    ? object("privateSubmission", row(ROOT, "pending", "新正文"))
                    : object("publicVersion", row(ROOT, "approved", "新正文"));
              return state.get().equals("pending") ? page() : page(row(ROOT, "approved", "新正文"));
            },
            () -> "one");
    remote.load("hall", HALL, "all", false);
    paths.clear();
    remote.refreshPending("hall", HALL);
    assertEquals(1, paths.size());
    state.set("approved");
    remote.refreshPending("hall", HALL);
    assertNull(remote.snapshot("hall", HALL).privateSubmission());
    assertEquals(1, remote.snapshot("hall", HALL).published().items().size());
  }

  @Test
  public void failedRefreshPreservesLastSnapshotAndNeverInventsSampleData() {
    AtomicReference<Boolean> fail = new AtomicReference<>(false);
    VenueRemote remote =
        new VenueRemote(
            (m, p, b) -> {
              if (fail.get()) throw new RepositoryException(503, "OFFLINE", "离线", 0);
              return meta();
            },
            () -> "one");
    remote.loadSummary("hall", HALL);
    var old = remote.summary("hall", HALL);
    fail.set(true);
    assertThrows(RepositoryException.class, () -> remote.loadSummary("hall", HALL));
    assertSame(old, remote.summary("hall", HALL));
  }

  @Test
  public void delayedOldAccountCannotPublishPrivateDataIntoNewAccount() throws Exception {
    AtomicReference<String> owner = new AtomicReference<>("one");
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    VenueRemote remote =
        new VenueRemote(
            (m, p, b) -> {
              started.countDown();
              try {
                release.await(3, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return meta();
            },
            owner::get);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> result = pool.submit(() -> remote.loadSummary("hall", HALL));
      assertTrue(started.await(3, TimeUnit.SECONDS));
      owner.set("two");
      assertNull(remote.summary("hall", HALL));
      release.countDown();
      assertThrows(ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS));
      assertNull(remote.summary("hall", HALL));
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void invalidTypesCannotProduceDifferentApiPaths() {
    VenueRemote remote =
        new VenueRemote(
            (m, p, b) -> {
              throw new AssertionError("must not send");
            },
            () -> "one");
    assertThrows(IllegalArgumentException.class, () -> remote.load("dish", HALL, "all", false));
    assertThrows(IllegalArgumentException.class, () -> remote.load("hall", HALL, "6", false));
  }

  @Test
  public void malformedSummaryAndPageAreErrorsRatherThanInventedEmptyState() {
    VenueRemote remote = new VenueRemote((m, p, b) -> new JSONObject(), () -> "one");
    assertEquals(
        "VENUE_INVALID_RESPONSE",
        assertThrows(RepositoryException.class, () -> remote.loadSummary("hall", HALL)).code());
    VenueRemote badPage =
        new VenueRemote(
            (m, p, b) -> p.endsWith("/rating") ? meta() : new JSONObject(), () -> "one");
    assertThrows(RepositoryException.class, () -> badPage.load("hall", HALL, "all", false));
    assertNull(badPage.snapshot("hall", HALL));
  }
}
