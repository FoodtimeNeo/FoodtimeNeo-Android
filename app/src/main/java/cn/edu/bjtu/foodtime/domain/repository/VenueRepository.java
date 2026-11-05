package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.LikeState;
import cn.edu.bjtu.foodtime.domain.model.VenueModels.*;

public interface VenueRepository {
  default AuditDetail venueAuditDetail(String type, String id) {
    throw new UnsupportedOperationException();
  }

  default Target venueSummary(String type, String id) {
    return null;
  }

  default void loadVenueSummary(String type, String id) {
    throw new UnsupportedOperationException();
  }

  default Snapshot venue(String type, String id) {
    return null;
  }

  default void loadVenue(String type, String id, String rating, boolean more) {
    throw new UnsupportedOperationException();
  }

  default void refreshVenuePending(String type, String id) {
    loadVenue(type, id, "all", false);
  }

  default void refreshVenueRepliesPending(String id) {
    loadVenueReplies(id, false, false);
  }

  default Row submitVenue(String type, String id, int rating, String content, String request) {
    throw new UnsupportedOperationException();
  }

  default Page venueReplies(String review, boolean mine) {
    return Page.empty();
  }

  default void loadVenueReplies(String review, boolean more, boolean mineMore) {
    throw new UnsupportedOperationException();
  }

  default Row submitVenueReply(String review, String content, String target, String request) {
    throw new UnsupportedOperationException();
  }

  default LikeState likeVenue(String id, boolean liked, boolean reply) {
    throw new UnsupportedOperationException();
  }

  default void deleteVenue(String id, boolean reply, boolean admin, String reason) {
    throw new UnsupportedOperationException();
  }

  default Page myVenueReviews() {
    return Page.empty();
  }

  default void loadMyVenueReviews(String type, boolean replies, int page) {
    throw new UnsupportedOperationException();
  }
}
