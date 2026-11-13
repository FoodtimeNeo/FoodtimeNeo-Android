package cn.edu.bjtu.foodtime.domain.repository;
import cn.edu.bjtu.foodtime.domain.model.PriceHistory;
import java.math.BigDecimal;
/** Price observations and their management stay independent of catalog editing and chart widgets. */
public interface PriceHistoryRepository {
  default PriceHistory.Snapshot priceHistory(String dish,int days){throw new UnsupportedOperationException();}
  default void correctPriceHistory(String dish,String entry,BigDecimal price,String unit,long revision){throw new UnsupportedOperationException();}
  default void deletePriceHistory(String dish,String entry,long revision){throw new UnsupportedOperationException();}
}
