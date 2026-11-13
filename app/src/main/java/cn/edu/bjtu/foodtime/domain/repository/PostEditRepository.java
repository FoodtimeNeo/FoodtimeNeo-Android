package cn.edu.bjtu.foodtime.domain.repository;

import java.util.List;

/** Independent author-edit port; moderation and pin management keep their own contracts. */
public interface PostEditRepository {
  record Edit(String title, String content, List<String> images, String dishId,
      long expectedRevision, String clientRequestId) {
    public Edit { images = List.copyOf(images); }
  }
  default void editCommunityPost(String id, Edit edit) { throw new UnsupportedOperationException(); }
}
