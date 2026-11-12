package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.List;

/** Replace the local implementation with API calls, without changing screen rendering. */
public interface CatalogRepository {
  List<Hall> halls();

  List<Stall> stalls(String hallId);

  List<Dish> dishes(String stallId);

  /** Load just one directory level on a worker. */
  default List<Hall> loadHalls() { return halls(); }
  default List<Stall> loadStalls(String hallId) { return stalls(hallId); }
  default List<Dish> loadDishes(String stallId) { return dishes(stallId); }

  /** Searches active dishes across all dining halls on a worker. */
  default DishSearchPage searchDishes(String query, int page) {
    throw new UnsupportedOperationException();
  }

  default DishSearchPage searchDishes(String query, int page, cn.edu.bjtu.foodtime.domain.model.Models.DishSearchFilters filters) {
    return searchDishes(query, page);
  }
  default DishSearchPage favoriteDishes(int page) { throw new UnsupportedOperationException(); }

  default DishSearchPage favoriteDishes(int page, String groupId, boolean unfiled) {
    return favoriteDishes(page);
  }

  default java.util.List<cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup> favoriteGroups(String kind) {
    throw new UnsupportedOperationException();
  }

  default cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup createFavoriteGroup(String kind, String name) {
    throw new UnsupportedOperationException();
  }

  default cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup updateFavoriteGroup(
      String id, String name, Integer sortOrder, Long expectedRevision) {
    throw new UnsupportedOperationException();
  }

  default void deleteFavoriteGroup(String id) { throw new UnsupportedOperationException(); }

  default java.util.List<String> favoriteGroupMembership(String kind, String targetId) {
    throw new UnsupportedOperationException();
  }

  default java.util.List<String> addFavoriteGroupMember(String groupId, String targetId) {
    throw new UnsupportedOperationException();
  }

  default java.util.List<String> removeFavoriteGroupMember(String groupId, String targetId) {
    throw new UnsupportedOperationException();
  }
  default boolean dishFavorite(String dishId) { throw new UnsupportedOperationException(); }
  default boolean setDishFavorite(String dishId, boolean saved) { throw new UnsupportedOperationException(); }

  Hall hall(String id);

  Stall stall(String id);

  Dish dish(String id);

  List<Review> reviews(String dishId);

  List<Photo> photos(String dishId);
}
