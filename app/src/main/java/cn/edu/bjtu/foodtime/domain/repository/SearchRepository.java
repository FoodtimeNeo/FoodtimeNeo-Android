package cn.edu.bjtu.foodtime.domain.repository;
import cn.edu.bjtu.foodtime.domain.model.SearchModels.*;
public interface SearchRepository {
  default boolean searchDistanceAvailable() { return false; }
  default Page<Stall> searchStalls(Query query,int page) { throw new UnsupportedOperationException(); }
  default Page<Dish> searchStallDishes(Query query,String stallId,int page) { throw new UnsupportedOperationException(); }
  default Page<Note> searchNotes(Query query,int page) { throw new UnsupportedOperationException(); }
}
