package cn.edu.bjtu.foodtime.domain.model;

import java.math.BigDecimal;
import java.util.List;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;

public final class SearchModels {
  private SearchModels() {}
  public record Query(String q,List<String> halls,BigDecimal minPrice,BigDecimal maxPrice,
      BigDecimal minRating,String sort,String type,Double latitude,Double longitude,int previewSize) {
    public Query { halls=List.copyOf(halls); }
  }
  public record Dish(String id,String name,BigDecimal price,String priceUnit,String imageUrl,
      BigDecimal averageScore,long reviewCount) {}
  public record Stall(String id,String name,String hallId,String hallName,String imageUrl,
      BigDecimal averageScore,long reviewCount,Double distanceMeters,long matchCount,List<Dish> dishes) {
    public Stall { dishes=List.copyOf(dishes); }
  }
  public record Note(CommunityPost post,Double distanceMeters,String hallName) {}
  public record Page<T>(List<T> items,long total,int page,int size) {
    public Page { items=List.copyOf(items); }
  }
  public static int columns(int widthDp) { return Math.max(2,Math.min(6,(widthDp+8)/84)); }
  public static String distance(Double meters) {
    if(meters==null||!Double.isFinite(meters)||meters<0)return "";
    if(meters<100)return "直线 <100m";
    return meters<1000?"直线 "+Math.round(meters/10)*10+"m":String.format(java.util.Locale.ROOT,"直线 %.1fkm",meters/1000);
  }
}
