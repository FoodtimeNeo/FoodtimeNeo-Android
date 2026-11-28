package cn.edu.bjtu.foodtime.domain.model;

import java.math.BigDecimal;
import java.util.*;

/** Canonical 4-dimension dish tag taxonomy & smart recommendation models (pure domain layer). */
public final class DishTagModels {
  private DishTagModels() {}

  public record DishTag(String code, String label, String dimension, String dimensionLabel, int sortOrder) {}

  public record SmartRecommendQuery(
      List<String> diningHallIds,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      boolean portionOnly,
      BigDecimal minRating,
      List<String> includeTags,
      List<String> excludeTags,
      List<String> excludeDishIds,
      String sort,
      Double latitude,
      Double longitude,
      int limit,
      long seed) {
    public SmartRecommendQuery {
      diningHallIds = diningHallIds == null ? List.of() : List.copyOf(diningHallIds);
      includeTags = includeTags == null ? List.of() : List.copyOf(includeTags);
      excludeTags = excludeTags == null ? List.of() : List.copyOf(excludeTags);
      excludeDishIds = excludeDishIds == null ? List.of() : List.copyOf(excludeDishIds);
      sort = sort == null || sort.isBlank() ? "match" : sort;
      limit = limit < 1 ? 12 : Math.min(30, limit);
    }
  }

  public record RecommendedDish(
      String id,
      String name,
      BigDecimal price,
      String priceUnit,
      String imageUrl,
      String stallId,
      String stallName,
      String diningHallId,
      String diningHallName,
      BigDecimal averageScore,
      long reviewCount,
      Double distanceMeters,
      List<String> tags,
      List<String> matchedTags,
      List<String> missedRequirements,
      List<String> reasons,
      double matchScore) {
    public RecommendedDish {
      tags = tags == null ? List.of() : List.copyOf(tags);
      matchedTags = matchedTags == null ? List.of() : List.copyOf(matchedTags);
      missedRequirements = missedRequirements == null ? List.of() : List.copyOf(missedRequirements);
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
  }

  public record SmartRecommendResult(
      boolean exactMode,
      long exactTotal,
      int returnedCount,
      List<String> relaxedHints,
      RecommendedDish picked,
      List<RecommendedDish> items) {
    public SmartRecommendResult {
      relaxedHints = relaxedHints == null ? List.of() : List.copyOf(relaxedHints);
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public static final List<DishTag> DEFAULTS = List.of(
      new DishTag("rice", "米饭/盖饭", "category", "主食/品类", 10),
      new DishTag("noodle", "面食/粉面", "category", "主食/品类", 20),
      new DishTag("western", "西餐/轻食", "category", "主食/品类", 30),
      new DishTag("breakfast", "粥饼/早点", "category", "主食/品类", 40),
      new DishTag("home_dish", "小炒/家常菜", "category", "主食/品类", 50),
      new DishTag("pork", "猪肉", "ingredient", "肉类/食材", 110),
      new DishTag("poultry", "鸡鸭禽类", "ingredient", "肉类/食材", 120),
      new DishTag("beef_lamb", "牛羊肉", "ingredient", "肉类/食材", 130),
      new DishTag("seafood", "鱼虾海鲜", "ingredient", "肉类/食材", 140),
      new DishTag("vegetarian", "素菜/蛋豆", "ingredient", "肉类/食材", 150),
      new DishTag("steamed", "蒸", "method", "做法", 210),
      new DishTag("stewed", "炖/煮/汤", "method", "做法", 220),
      new DishTag("stir_fried", "炒/煎", "method", "做法", 230),
      new DishTag("roasted", "烤", "method", "做法", 240),
      new DishTag("fried", "炸/酥脆", "method", "做法", 250),
      new DishTag("cold_braised", "凉拌/卤", "method", "做法", 260),
      new DishTag("healthy", "健康清淡", "style", "饮食偏向", 310),
      new DishTag("spicy", "香辣/麻辣", "style", "饮食偏向", 320),
      new DishTag("savory", "下饭重口", "style", "饮食偏向", 330)
  );

  public static final List<String> DIMENSIONS = List.of("category", "ingredient", "method", "style");

  public static String dimensionLabel(String dimension) {
    return switch (dimension == null ? "" : dimension) {
      case "category" -> "主食/品类";
      case "ingredient" -> "肉类/食材";
      case "method" -> "做法";
      case "style" -> "饮食偏向";
      default -> dimension == null ? "" : dimension;
    };
  }

  public static String labelOf(String code, List<DishTag> catalog) {
    if (code == null || code.isBlank()) return "";
    if (catalog != null) {
      for (DishTag tag : catalog) {
        if (code.equals(tag.code())) return tag.label();
      }
    }
    for (DishTag tag : DEFAULTS) {
      if (code.equals(tag.code())) return tag.label();
    }
    return code;
  }

  public static String joinLabels(List<String> codes, List<DishTag> catalog, int limit) {
    if (codes == null || codes.isEmpty()) return "";
    List<String> labels = new ArrayList<>();
    for (String code : codes) {
      if (labels.size() >= limit) break;
      String label = labelOf(code, catalog);
      if (!label.isBlank() && !labels.contains(label)) labels.add(label);
    }
    return String.join(" · ", labels);
  }
}
