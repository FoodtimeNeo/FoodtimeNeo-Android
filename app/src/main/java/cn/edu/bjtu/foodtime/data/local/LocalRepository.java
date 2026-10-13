package cn.edu.bjtu.foodtime.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.math.BigDecimal;
import java.util.*;
import org.json.*;

/** Offline simulator. Only approved content reaches public catalog reads. */
public class LocalRepository implements FoodtimeRepository {
  protected final SharedPreferences prefs;
  protected JSONObject db;

  public LocalRepository(Context context) {
    prefs = context.getSharedPreferences("foodtime_mock_v1", Context.MODE_PRIVATE);
    try {
      db = new JSONObject(prefs.getString("data", "{}"));
    } catch (JSONException e) {
      throw new IllegalStateException("本地数据无法读取", e);
    }
    if (!db.has("halls")) seed();
    if (rows("users").length() == 0) {
      seedUser("20260001@bjtu.edu.cn", "干饭同学", "user");
      seedUser("20260002@bjtu.edu.cn", "食堂管理员", "admin");
      seedUser("20260003@bjtu.edu.cn", "平台管理员", "superadmin");
      save();
    }
  }

  protected JSONArray rows(String key) {
    return db.optJSONArray(key);
  }

  protected static JSONObject obj(Object... pairs) {
    JSONObject o = new JSONObject();
    try {
      for (int i = 0; i < pairs.length; i += 2) o.put((String) pairs[i], pairs[i + 1]);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    return o;
  }

  protected void put(String key, Object value) {
    try {
      db.put(key, value);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  protected void save() {
    if (!prefs.edit().putString("data", db.toString()).commit()) {
      try {
        db = new JSONObject(prefs.getString("data", "{}"));
      } catch (JSONException e) {
        throw new IllegalStateException("本地数据无法读取", e);
      }
      throw new IllegalStateException("保存失败，请重试");
    }
  }

  public AdminEntity entity(String table, String id) {
    requireAdmin();
    if (!List.of("halls", "stalls", "dishes").contains(table))
      throw new IllegalArgumentException("未知实体");
    return toAdminEntity(find(table, id));
  }

  private static AdminEntity toAdminEntity(JSONObject value) {
    if (value == null) return null;
    return new AdminEntity(
        value.optString("id"),
        value.optString("name"),
        value.optString("description"),
        value.optString("price"),
        value.optString("coverImageUrl"),
        value.optBoolean("active", true));
  }

  protected static String id() {
    return UUID.randomUUID().toString();
  }

  protected JSONObject find(String table, String id) {
    JSONArray a = rows(table);
    for (int i = 0; i < a.length(); i++)
      if (a.optJSONObject(i).optString("id").equals(id)) return a.optJSONObject(i);
    return null;
  }

  protected static boolean active(JSONObject o) {
    return o != null && o.optBoolean("active", true);
  }

  private void seed() {
    for (String name :
        List.of(
            "halls",
            "stalls",
            "dishes",
            "reviews",
            "photos",
            "submissions",
            "users",
            "audits",
            "favorites",
            "likes")) put(name, new JSONArray());
    String[] names = {
      "学一餐厅", "明湖餐厅", "东快餐厅", "学二餐厅", "学生三餐厅", "清真餐厅", "学四餐厅", "学四风味餐厅", "留园餐厅", "教工餐厅", "学苑大伙餐厅",
      "学苑清真餐厅", "学苑西餐厅", "东区餐厅", "东区清真餐厅", "益民餐厅"
    };
    for (int i = 0; i < names.length; i++) {
      String h =
          UUID.nameUUIDFromBytes(("hall" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8))
              .toString();
      rows("halls")
          .put(
              obj(
                  "id",
                  h,
                  "name",
                  names[i],
                  "coverImageUrl",
                  "asset:hall_" + (i % 6 + 1) + ".webp",
                  "active",
                  true));
      String[] stalls = {"西北面食", "费大叔湘菜", "家常小炒"};
      for (int j = 0; j < 3; j++) {
        String s =
            UUID.nameUUIDFromBytes((h + j).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        rows("stalls")
            .put(
                obj(
                    "id",
                    s,
                    "diningHallId",
                    h,
                    "name",
                    stalls[j],
                    "coverImageUrl",
                    "asset:stall_" + (j % 2 + 1) + ".webp",
                    "active",
                    true));
        String[][] dishes = {
          {"红烧牛肉面", "酸汤米线", "油泼扯面"}, {"黑椒鸡腿饭", "番茄肥牛饭", "小炒黄牛肉饭"}, {"牛肉煲仔饭", "香菇滑鸡饭", "宫保鸡丁盖饭"}
        };
        for (int k = 0; k < 3; k++) {
          String d =
              UUID.nameUUIDFromBytes((s + k).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                  .toString();
          rows("dishes")
              .put(
                  obj(
                      "id",
                      d,
                      "stallId",
                      s,
                      "name",
                      dishes[j][k],
                      "price",
                      12 + j * 3 + k * 2,
                      "description",
                      "",
                      "active",
                      true));
          if (k < 2)
            rows("photos")
                .put(
                    obj(
                        "id",
                        id(),
                        "dishId",
                        d,
                        "imageUrl",
                        "asset:dish_" + (j * 2 + k + 1) + ".webp",
                        "userId",
                        "sample-student"));
          if (k == 0)
            rows("reviews")
                .put(
                    obj(
                        "id",
                        id(),
                        "dishId",
                        d,
                        "userId",
                        "sample-student",
                        "rating",
                        5,
                        "content",
                        "味道很扎实，分量也足，午饭又多了一个选择。",
                        "createdAt",
                        System.currentTimeMillis() - 86400000));
        }
      }
    }
    save();
  }

  public List<Hall> halls() {
    List<Hall> list = new ArrayList<>();
    JSONArray a = rows("halls");
    for (int i = 0; i < a.length(); i++) {
      Hall h = hall(a.optJSONObject(i).optString("id"));
      if (h.active()) list.add(h);
    }
    return list;
  }

  public Hall hall(String id) {
    JSONObject o = find("halls", id);
    return o == null
        ? null
        : new Hall(id, o.optString("name"), o.optString("coverImageUrl"), active(o));
  }

  public Stall stall(String id) {
    JSONObject o = find("stalls", id);
    return o == null
        ? null
        : new Stall(
            id,
            o.optString("diningHallId"),
            o.optString("name"),
            o.optString("coverImageUrl"),
            active(o));
  }

  public Dish dish(String id) {
    JSONObject o = find("dishes", id);
    return o == null
        ? null
        : new Dish(
            id,
            o.optString("stallId"),
            o.optString("name"),
            new BigDecimal(o.optString("price", "0")),
            o.optString("description"),
            active(o));
  }

  public List<Stall> stalls(String hallId) {
    if (!active(find("halls", hallId))) throw new IllegalArgumentException("食堂不存在或已停用");
    List<Stall> list = new ArrayList<>();
    JSONArray a = rows("stalls");
    for (int i = 0; i < a.length(); i++) {
      Stall s = stall(a.optJSONObject(i).optString("id"));
      if (s.diningHallId().equals(hallId) && s.active()) list.add(s);
    }
    return list;
  }

  public List<Dish> dishes(String stallId) {
    Stall s = stall(stallId);
    if (s == null || !s.active() || !active(find("halls", s.diningHallId())))
      throw new IllegalArgumentException("档口不存在或已停用");
    List<Dish> list = new ArrayList<>();
    JSONArray a = rows("dishes");
    for (int i = 0; i < a.length(); i++) {
      Dish d = dish(a.optJSONObject(i).optString("id"));
      if (d.stallId().equals(stallId) && d.active()) list.add(d);
    }
    return list;
  }

  public List<Review> reviews(String dishId) {
    List<Review> list = new ArrayList<>();
    JSONArray a = rows("reviews");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (dishId == null || o.optString("dishId").equals(dishId))
        list.add(
            new Review(
                o.optString("id"),
                o.optString("dishId"),
                o.optString("userId"),
                o.optInt("rating"),
                o.optString("content"),
                o.optLong("createdAt")));
    }
    list.sort(Comparator.comparingLong(Review::createdAt).reversed());
    return list;
  }

  public List<Photo> photos(String dishId) {
    List<Photo> list = new ArrayList<>();
    JSONArray a = rows("photos");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o.optString("dishId").equals(dishId))
        list.add(
            new Photo(o.optString("id"), dishId, o.optString("imageUrl"), o.optString("userId")));
    }
    return list;
  }

  private void seedUser(String email, String name, String role) {
    rows("users")
        .put(
            obj(
                "id",
                id(),
                "email",
                email,
                "displayName",
                name,
                "role",
                role,
                "active",
                true,
                "passwordHash",
                Passwords.hash("Foodtime2026!")));
  }

  public User user(String id) {
    JSONObject o = find("users", id);
    return o == null
        ? null
        : new User(
            id, o.optString("email"), o.optString("displayName"), o.optString("role"), active(o));
  }

  public User currentUser() {
    User u = user(db.optString("sessionUser"));
    return u != null && u.active() && db.optLong("sessionExpires") > System.currentTimeMillis()
        ? u
        : null;
  }

  protected User requireUser() {
    User u = currentUser();
    if (u == null) throw new IllegalStateException("登录已失效，请重新登录");
    return u;
  }

  public User login(String account, String password) {
    String email = Validation.email(account);
    Validation.password(password, false);
    JSONObject match = null;
    JSONArray users = rows("users");
    for (int i = 0; i < users.length(); i++)
      if (users.optJSONObject(i).optString("email").equals(email)) match = users.optJSONObject(i);
    if (match == null
        || !active(match)
        || !Passwords.matches(password, match.optString("passwordHash")))
      throw new IllegalArgumentException("邮箱或密码错误");
    put("sessionUser", match.optString("id"));
    put("sessionExpires", System.currentTimeMillis() + 7L * 86400000);
    save();
    return currentUser();
  }

  public void logout() {
    put("sessionUser", "");
    put("sessionExpires", 0);
    save();
  }

  public String sendVerificationCode(String raw) {
    String email = Validation.email(raw);
    for (int i = 0; i < rows("users").length(); i++)
      if (rows("users").optJSONObject(i).optString("email").equals(email))
        throw new IllegalArgumentException("该邮箱已注册");
    long now = System.currentTimeMillis();
    if (now - db.optLong("codeSent") < 60000) throw new IllegalArgumentException("请等待60秒后再获取验证码");
    String code =
        String.format(Locale.ROOT, "%06d", new java.security.SecureRandom().nextInt(1000000));
    put("codeEmail", email);
    put("codeValue", code);
    put("codeExpires", now + 900000);
    put("codeSent", now);
    put("codeAttempts", 0);
    save();
    return code;
  }

  public void register(String raw, String password, String code) {
    String email = Validation.email(raw);
    Validation.password(password, true);
    JSONArray users = rows("users");
    for (int i = 0; i < users.length(); i++)
      if (users.optJSONObject(i).optString("email").equals(email))
        throw new IllegalArgumentException("该邮箱已注册");
    if (!email.equals(db.optString("codeEmail"))
        || db.optLong("codeExpires") < System.currentTimeMillis()
        || db.optInt("codeAttempts") >= 5) throw new IllegalArgumentException("验证码失效，请重新获取");
    if (!code.matches("[0-9]{6}") || !code.equals(db.optString("codeValue"))) {
      put("codeAttempts", db.optInt("codeAttempts") + 1);
      save();
      throw new IllegalArgumentException("验证码错误");
    }
    rows("users")
        .put(
            obj(
                "id",
                id(),
                "email",
                email,
                "displayName",
                "干饭人" + code,
                "role",
                "user",
                "active",
                true,
                "passwordHash",
                Passwords.hash(password)));
    put("codeValue", "");
    put("codeExpires", 0);
    save();
  }

  public void submitPhoto(String dishId, String imageUrl) {
    User u = requireUser();
    Dish d = dish(dishId);
    if (d == null || !d.active()) throw new IllegalArgumentException("菜品不存在或已下架");
    if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("请先选择菜品图片");
    rows("submissions")
        .put(
            obj(
                "id",
                id(),
                "type",
                "photo",
                "targetId",
                dishId,
                "userId",
                u.id(),
                "name",
                d.name(),
                "content",
                "",
                "imageUrl",
                imageUrl,
                "status",
                "pending",
                "createdAt",
                System.currentTimeMillis()));
    save();
  }

  public void submitReview(String dishId, int rating, String content) {
    User u = requireUser();
    Dish d = dish(dishId);
    if (d == null || !d.active()) throw new IllegalArgumentException("菜品不存在或已下架");
    if (rating < 1 || rating > 5) throw new IllegalArgumentException("请选择1–5星评分");
    String body = Validation.text(content, 500, "评价");
    for (int i = 0; i < rows("submissions").length(); i++) {
      JSONObject o = rows("submissions").optJSONObject(i);
      if (o.optString("type").equals("review")
          && o.optString("targetId").equals(dishId)
          && o.optString("userId").equals(u.id())
          && o.optString("status").equals("pending"))
        throw new IllegalArgumentException("这道菜已有一条待审核评价，请等待审核后再提交");
    }
    rows("submissions")
        .put(
            obj(
                "id",
                id(),
                "type",
                "review",
                "targetId",
                dishId,
                "userId",
                u.id(),
                "name",
                d.name(),
                "content",
                body,
                "rating",
                rating,
                "status",
                "pending",
                "createdAt",
                System.currentTimeMillis()));
    save();
  }

  public List<Submission> submissions(String userId) {
    User viewer = requireUser();
    if (userId == null && !viewer.isAdmin()
        || userId != null && !userId.equals(viewer.id()) && !viewer.isAdmin())
      throw new IllegalStateException("无权查看这些提交");
    List<Submission> list = new ArrayList<>();
    JSONArray a = rows("submissions");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (userId == null || o.optString("userId").equals(userId))
        list.add(
            new Submission(
                o.optString("id"),
                o.optString("type"),
                o.optString("targetId"),
                o.optString("userId"),
                o.optString("name"),
                o.optString("content"),
                new BigDecimal(o.optString("price", "0")),
                o.optString("imageUrl"),
                o.optInt("rating"),
                o.optString("status"),
                o.optString("reason"),
                o.optLong("createdAt")));
    }
    list.sort(Comparator.comparingLong(Submission::createdAt).reversed());
    return list;
  }

  public void submitDish(
      String stallId, String name, String description, BigDecimal price, String imageUrl) {
    User u = requireUser();
    dishes(stallId);
    String title = Validation.text(name, 120, "菜品名称");
    if (price == null) throw new IllegalArgumentException("请填写价格");
    Validation.price(price.toPlainString());
    if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("请添加菜品图片");
    if (description.length() > 1000) throw new IllegalArgumentException("描述最多1000字");
    rows("submissions")
        .put(
            obj(
                "id",
                id(),
                "type",
                "dish",
                "targetId",
                stallId,
                "userId",
                u.id(),
                "name",
                title,
                "content",
                description.trim(),
                "price",
                price,
                "imageUrl",
                imageUrl,
                "status",
                "pending",
                "createdAt",
                System.currentTimeMillis()));
    save();
  }

  public void submitStall(String hallId, String name, String description, String imageUrl) {
    User u = requireUser();
    stalls(hallId);
    String title = Validation.text(name, 100, "档口名称");
    if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("请添加档口封面");
    if (description.length() > 1000) throw new IllegalArgumentException("描述最多1000字");
    rows("submissions")
        .put(
            obj(
                "id",
                id(),
                "type",
                "stall",
                "targetId",
                hallId,
                "userId",
                u.id(),
                "name",
                title,
                "content",
                description.trim(),
                "imageUrl",
                imageUrl,
                "status",
                "pending",
                "createdAt",
                System.currentTimeMillis()));
    save();
  }

  public String context(String dishId) {
    Dish d = dish(dishId);
    if (d == null) return "菜品已删除";
    Stall s = stall(d.stallId());
    Hall h = s == null ? null : hall(s.diningHallId());
    return (h == null ? "未知食堂" : h.name())
        + " — "
        + (s == null ? "未知档口" : s.name())
        + " — "
        + d.name();
  }

  public List<Review> myReviews() {
    String userId = requireUser().id();
    List<Review> result = new ArrayList<>();
    for (Review r : reviews(null)) if (r.userId().equals(userId)) result.add(r);
    return result;
  }

  public void deleteReview(String reviewId) {
    User u = requireUser();
    JSONObject o = find("reviews", reviewId);
    if (o == null) throw new IllegalArgumentException("评价不存在");
    if (!o.optString("userId").equals(u.id())) throw new IllegalStateException("只能删除自己的评价");
    remove("reviews", reviewId);
    save();
  }

  protected void remove(String table, String id) {
    JSONArray a = rows(table);
    for (int i = a.length() - 1; i >= 0; i--)
      if (a.optJSONObject(i).optString("id").equals(id)) a.remove(i);
  }

  public void changePassword(String oldPassword, String newPassword) {
    User user = requireUser();
    Validation.password(oldPassword, false);
    Validation.password(newPassword, true);
    if (oldPassword.equals(newPassword)) throw new IllegalArgumentException("新密码不能与旧密码相同");
    JSONObject account = find("users", user.id());
    if (!Passwords.matches(oldPassword, account.optString("passwordHash")))
      throw new IllegalArgumentException("旧密码错误");
    try {
      account.put("passwordHash", Passwords.hash(newPassword));
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    put("sessionUser", "");
    put("sessionExpires", 0);
    save();
  }

  private User requireAdmin() {
    User u = requireUser();
    if (!u.isAdmin()) throw new IllegalStateException("需要管理员权限");
    return u;
  }

  public void moderate(String submissionId, String action, String reason) {
    User admin = requireAdmin();
    if (!List.of("approve", "reject").contains(action))
      throw new IllegalArgumentException("未知审核操作");
    JSONObject o = find("submissions", submissionId);
    if (o == null || !o.optString("status").equals("pending"))
      throw new IllegalArgumentException("记录已审核或不存在");
    String explanation =
        action.equals("reject") ? Validation.text(reason, 500, "驳回原因") : reason.trim();
    String type = o.optString("type"), target = o.optString("targetId");
    if (action.equals("approve")) {
      if (type.equals("dish")) dishes(target);
      else if (type.equals("stall")) stalls(target);
      else {
        Dish dish = dish(target);
        if (dish == null || !dish.active()) throw new IllegalArgumentException("目标菜品不存在或已下架");
        dishes(dish.stallId());
      }
      String approvedId = id();
      if (type.equals("review")) {
        JSONObject existing = null;
        for (int i = 0; i < rows("reviews").length(); i++) {
          JSONObject r = rows("reviews").optJSONObject(i);
          if (r.optString("dishId").equals(target)
              && r.optString("userId").equals(o.optString("userId"))) existing = r;
        }
        if (existing != null) {
          approvedId = existing.optString("id");
          remove("reviews", approvedId);
        }
        rows("reviews")
            .put(
                obj(
                    "id",
                    approvedId,
                    "dishId",
                    target,
                    "userId",
                    o.optString("userId"),
                    "rating",
                    o.optInt("rating"),
                    "content",
                    o.optString("content"),
                    "createdAt",
                    o.optLong("createdAt")));
      } else if (type.equals("photo"))
        rows("photos")
            .put(
                obj(
                    "id",
                    approvedId,
                    "dishId",
                    target,
                    "imageUrl",
                    o.optString("imageUrl"),
                    "userId",
                    o.optString("userId")));
      else if (type.equals("dish")) {
        rows("dishes")
            .put(
                obj(
                    "id",
                    approvedId,
                    "stallId",
                    target,
                    "name",
                    o.optString("name"),
                    "price",
                    o.opt("price"),
                    "description",
                    o.optString("content"),
                    "active",
                    true));
        rows("photos")
            .put(
                obj(
                    "id",
                    id(),
                    "dishId",
                    approvedId,
                    "imageUrl",
                    o.optString("imageUrl"),
                    "userId",
                    o.optString("userId")));
      } else if (type.equals("stall")) {
        for (int i = 0; i < rows("stalls").length(); i++) {
          JSONObject s = rows("stalls").optJSONObject(i);
          if (s.optString("diningHallId").equals(target)
              && s.optString("name").equals(o.optString("name")))
            throw new IllegalArgumentException("该食堂已存在同名档口");
        }
        rows("stalls")
            .put(
                obj(
                    "id",
                    approvedId,
                    "diningHallId",
                    target,
                    "name",
                    o.optString("name"),
                    "description",
                    o.optString("content"),
                    "coverImageUrl",
                    o.optString("imageUrl"),
                    "active",
                    true));
      } else throw new IllegalArgumentException("未知投稿类型");
      try {
        o.put("approvedId", approvedId);
      } catch (JSONException e) {
        throw new IllegalStateException(e);
      }
    }
    try {
      o.put("status", action.equals("approve") ? "approved" : "rejected");
      o.put("reason", explanation);
      o.put("reviewedBy", admin.id());
      o.put("reviewedAt", System.currentTimeMillis());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    rows("audits")
        .put(
            obj(
                "id",
                id(),
                "submissionId",
                submissionId,
                "moderatorId",
                admin.id(),
                "action",
                action,
                "reason",
                explanation,
                "createdAt",
                System.currentTimeMillis()));
    save();
  }

  public List<Audit> audits() {
    requireAdmin();
    List<Audit> list = new ArrayList<>();
    for (int i = 0; i < rows("audits").length(); i++) {
      JSONObject o = rows("audits").optJSONObject(i);
      list.add(
          new Audit(
              o.optString("id"),
              o.optString("submissionId"),
              o.optString("moderatorId"),
              o.optString("action"),
              o.optString("reason"),
              o.optLong("createdAt")));
    }
    Collections.reverse(list);
    return list;
  }

  public List<AdminEntity> entities(String table, String parentId) {
    requireAdmin();
    if (!List.of("halls", "stalls", "dishes").contains(table))
      throw new IllegalArgumentException("未知实体");
    List<AdminEntity> result = new ArrayList<>();
    for (int i = 0; i < rows(table).length(); i++) {
      JSONObject o = rows(table).optJSONObject(i);
      if (table.equals("halls")
          || (table.equals("stalls") ? o.optString("diningHallId") : o.optString("stallId"))
              .equals(parentId)) result.add(toAdminEntity(o));
    }
    return result;
  }

  public void editEntity(
      String table,
      String entityId,
      String parentId,
      String name,
      String description,
      String price,
      String image,
      boolean enabled) {
    requireAdmin();
    if (!List.of("halls", "stalls", "dishes").contains(table))
      throw new IllegalArgumentException("未知实体");
    String title = Validation.text(name, table.equals("dishes") ? 120 : 100, "名称");
    if (description.length() > 1000) throw new IllegalArgumentException("描述最多1000字");
    BigDecimal amount = table.equals("dishes") ? Validation.price(price) : null;
    JSONObject o = entityId.isEmpty() ? null : find(table, entityId);
    if (!entityId.isEmpty() && o == null) throw new IllegalArgumentException("记录不存在");
    String parent = table.equals("stalls") ? "diningHallId" : "stallId";
    if (o != null) parentId = o.optString(parent);
    if (!table.equals("halls")
        && find(table.equals("stalls") ? "halls" : "stalls", parentId) == null)
      throw new IllegalArgumentException("上级记录不存在");
    if (table.equals("stalls")) {
      for (int i = 0; i < rows("stalls").length(); i++) {
        JSONObject s = rows("stalls").optJSONObject(i);
        if (!s.optString("id").equals(entityId)
            && s.optString("diningHallId").equals(parentId)
            && s.optString("name").equals(title))
          throw new IllegalArgumentException("同一食堂内不能有同名档口");
      }
    }
    if (o == null) {
      o = obj("id", id(), parent, parentId);
      rows(table).put(o);
    }
    try {
      o.put("name", title);
      o.put("description", description.trim());
      o.put("active", enabled);
      if (amount != null) o.put("price", amount);
      if (!table.equals("dishes")) o.put("coverImageUrl", image);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    save();
  }

  public void removeReviewAsAdmin(String reviewId) {
    requireAdmin();
    if (find("reviews", reviewId) == null) throw new IllegalArgumentException("评价不存在");
    remove("reviews", reviewId);
    save();
  }

  public List<User> users() {
    User admin = requireAdmin();
    if (!admin.role().equals("superadmin")) throw new IllegalStateException("仅平台管理员可管理用户");
    List<User> users = new ArrayList<>();
    for (int i = 0; i < rows("users").length(); i++)
      users.add(user(rows("users").optJSONObject(i).optString("id")));
    return users;
  }

  public void updateUser(String userId, String role, boolean enabled) {
    User admin = requireAdmin();
    if (!admin.role().equals("superadmin")) throw new IllegalStateException("仅平台管理员可管理用户");
    if (userId.equals(admin.id())) throw new IllegalArgumentException("不能修改当前自己的权限或状态");
    if (!List.of("user", "admin", "superadmin").contains(role))
      throw new IllegalArgumentException("未知角色");
    JSONObject o = find("users", userId);
    if (o == null) throw new IllegalArgumentException("用户不存在");
    try {
      o.put("role", role);
      o.put("active", enabled);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    save();
  }
}
