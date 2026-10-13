package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.List;

/** Replace the local implementation with API calls, without changing screen rendering. */
public interface CatalogRepository {
  List<Hall> halls();

  List<Stall> stalls(String hallId);

  List<Dish> dishes(String stallId);

  Hall hall(String id);

  Stall stall(String id);

  Dish dish(String id);

  List<Review> reviews(String dishId);

  List<Photo> photos(String dishId);
}
