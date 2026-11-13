package cn.edu.bjtu.foodtime.domain.model;

import java.util.*;

public final class CommunityPins {
  public record Entry(String id,String title,String userDisplayName,String status,int position) {
    public Entry { if(id==null||id.isBlank()||title==null||!Set.of("approved","pending","rejected").contains(status)||position<1||position>3)throw new IllegalArgumentException("置顶记录异常"); }
  }
  public record Snapshot(long revision,List<Entry> items,boolean canManage) {
    public Snapshot {
      items=List.copyOf(items);
      if(revision<0||items.size()>3||items.stream().map(Entry::id).distinct().count()!=items.size()||items.stream().map(Entry::position).distinct().count()!=items.size())throw new IllegalArgumentException("置顶列表异常");
    }
    public static Snapshot empty(){return new Snapshot(0,List.of(),false);}
  }
  private CommunityPins(){}
}
