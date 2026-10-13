package cn.edu.bjtu.foodtime.domain.repository;

/** Business queries independent of Activity routes and concrete data sources. */
public record ContentRequest(Query query,String id) {
  public enum Query {
    HALLS, STALLS, DISHES, DISH_DETAIL, MY_REVIEWS, MY_SUBMISSIONS, MODERATION,
    CATALOG, CATALOG_ITEM, ADMIN_REVIEWS, ADMIN_USERS, AUDITS, PROFILE
  }
}
