package cn.edu.bjtu.foodtime.domain.model;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.Assert.*;
public class PriceHistoryTest {
  private PriceHistory.Entry row(int id,String price,String unit){return new PriceHistory.Entry(""+id,price==null?null:new BigDecimal(price),unit,id*1000L,"changed",1,false);}
  private PriceHistory.Snapshot data(PriceHistory.Entry... rows){return new PriceHistory.Snapshot("dish","菜品",null,"serving",1,0,10000,rows.length,false,List.of(rows));}
  @Test public void adjacentEqualPricesCollapseButARealReturnIsRetained(){var runs=PriceHistory.runs(data(row(1,"12","serving"),row(2,"12.00","serving"),row(3,"15","serving"),row(4,"12","serving")));assertEquals(1,runs.size());assertEquals(List.of("1","3","4"),runs.get(0).points().stream().map(p->p.entry().id()).collect(java.util.stream.Collectors.toList()));}
  @Test public void differentUnitsNeverJoinEvenForTheSameAmount(){var runs=PriceHistory.runs(data(row(1,"15","serving"),row(2,"15","jin"),row(3,"15","serving")));assertEquals(3,runs.size());assertEquals(2000,runs.get(0).end());assertEquals(2000,runs.get(1).start());}
  @Test public void unknownCreatesAGapAndZeroRemainsARealPoint(){var runs=PriceHistory.runs(data(row(1,"12","serving"),row(2,null,"serving"),row(3,"0","serving")));assertEquals(2,runs.size());assertEquals(BigDecimal.ZERO,runs.get(1).points().get(0).entry().price());assertEquals("价格未知",PriceHistory.price(null,"serving"));}
  @Test public void anchorUsesWindowStartWithoutChangingOriginalTime(){var entry=row(1,"12","serving");var value=new PriceHistory.Snapshot("dish","菜品",new BigDecimal("12"),"serving",1,5000,10000,1,false,List.of(entry));var run=PriceHistory.runs(value).get(0);assertEquals(5000,run.start());assertEquals(1000,run.points().get(0).entry().recordedAt());}
  @Test public void allUnknownAndEmptyHistoryProduceNoFakeLine(){assertTrue(PriceHistory.runs(data()).isEmpty());assertTrue(PriceHistory.runs(data(row(1,null,"kg"),row(2,null,"serving"))).isEmpty());}
  @Test public void unitLabelsDistinguishUnspecifiedFromWeightAndPortion(){assertTrue(PriceHistory.price(new BigDecimal("15"),"serving").endsWith("/份"));assertTrue(PriceHistory.price(new BigDecimal("15"),"kg").endsWith("/公斤"));assertTrue(PriceHistory.price(new BigDecimal("15"),"unspecified").contains("单位未注明"));}
}
