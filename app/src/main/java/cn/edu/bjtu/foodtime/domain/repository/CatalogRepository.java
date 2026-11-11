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
  default boolean dishFavorite(String dishId) { throw new UnsupportedOperationException(); }
  default boolean setDishFavorite(String dishId, boolean saved) { throw new UnsupportedOperationException(); }

  Hall hall(String id);

  Stall stall(String id);

  Dish dish(String id);

  List<Review> reviews(String dishId);

  List<Photo> photos(String dishId);
}
