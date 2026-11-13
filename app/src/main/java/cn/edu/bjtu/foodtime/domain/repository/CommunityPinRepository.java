package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.CommunityPins;
import java.util.List;

/** Public cached read and independent management operations; all network calls run on a worker. */
public interface CommunityPinRepository {
  default CommunityPins.Snapshot communityPins(){return CommunityPins.Snapshot.empty();}
  default CommunityPins.Snapshot managedPins(){throw new UnsupportedOperationException();}
  default CommunityPins.Snapshot pinPost(String id,long revision){throw new UnsupportedOperationException();}
  default CommunityPins.Snapshot unpinPost(String id,long revision){throw new UnsupportedOperationException();}
  default CommunityPins.Snapshot reorderPins(List<String> ids,long revision){throw new UnsupportedOperationException();}
}
