package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** One active post draft per account on this device; images are app-private file paths. */
final class PostDraftStore {
  record Draft(String title, String content, String linkedDishId,
               List<String> images, long updatedAt, long revision) {}

  private static final String FILE = "draft.json";
  private PostDraftStore() {}

  static synchronized void save(MainActivity activity, String ownerId, String title,
      String content, String linkedDishId, List<String> images) {
    if (ownerId == null || ownerId.isBlank()) return;
    File directory = directory(activity, ownerId);
    if (title.isBlank() && content.isBlank() && images.isEmpty()) {
      delete(activity, ownerId);
      return;
    }
    if (!directory.exists() && !directory.mkdirs()) return;
    Draft existing = load(activity, ownerId);
    try {
      JSONObject json = new JSONObject();
      json.put("schemaVersion", 1);
      json.put("title", title);
      json.put("content", content);
      json.put("linkedDishId", linkedDishId == null ? "" : linkedDishId);
      JSONArray paths = new JSONArray();
      for (String image : images) if (image != null && !image.isBlank()) paths.put(image);
      json.put("images", paths);
      json.put("updatedAt", System.currentTimeMillis());
      json.put("revision", (existing == null ? 0 : existing.revision()) + 1);
      File temporary = new File(directory, FILE + ".tmp");
      try (FileOutputStream output = new FileOutputStream(temporary)) {
        output.write(json.toString().getBytes(StandardCharsets.UTF_8));
        output.getFD().sync();
      }
      File target = new File(directory, FILE);
      if (!temporary.renameTo(target)) {
        target.delete();
        temporary.renameTo(target);
      }
    } catch (IOException | org.json.JSONException ignored) {
      // The current editor content remains authoritative; the next change retries.
    }
  }

  static synchronized Draft load(Context context, String ownerId) {
    if (ownerId == null || ownerId.isBlank()) return null;
    File file = new File(directory(context, ownerId), FILE);
    if (!file.isFile()) return null;
    try {
      String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      JSONObject json = new JSONObject(text);
      List<String> images = new ArrayList<>();
      JSONArray paths = json.optJSONArray("images");
      if (paths != null) for (int i = 0; i < paths.length(); i++) {
        String path = paths.optString(i, "");
        if (!path.isBlank()) images.add(path);
      }
      return new Draft(json.optString("title", ""), json.optString("content", ""),
          json.optString("linkedDishId", ""), List.copyOf(images),
          json.optLong("updatedAt", 0), json.optLong("revision", 0));
    } catch (IOException | org.json.JSONException failure) {
      return null;
    }
  }

  static synchronized void delete(Context context, String ownerId) {
    if (ownerId == null || ownerId.isBlank()) return;
    new File(directory(context, ownerId), FILE).delete();
    new File(directory(context, ownerId), FILE + ".tmp").delete();
  }

  /** Account deletion: remove metadata and the private image copies owned by the draft directory. */
  static synchronized void purge(Context context, String ownerId) {
    if (ownerId == null || ownerId.isBlank()) return;
    deleteTree(directory(context, ownerId));
  }

  private static File directory(Context context, String ownerId) {
    String safe = ownerId.replaceAll("[^A-Za-z0-9_-]", "_");
    return new File(new File(context.getFilesDir(), "post-drafts"), safe);
  }

  private static void deleteTree(File file) {
    if (file == null || !file.exists()) return;
    File[] children = file.listFiles();
    if (children != null) for (File child : children) deleteTree(child);
    file.delete();
  }
}
