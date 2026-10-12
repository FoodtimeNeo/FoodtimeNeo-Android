package cn.edu.bjtu.foodtime;
import java.util.List;
import cn.edu.bjtu.foodtime.Models.*;
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
