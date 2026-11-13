package cn.edu.bjtu.foodtime.domain.model;

import java.math.BigDecimal;
import java.util.*;

/** Protocol snapshots and renderer-independent, gap-aware step runs. */
public final class PriceHistory {
  private PriceHistory() {}
  public static final List<String> UNITS=List.of("unspecified","serving","piece","bowl","jin","100g","kg");
  public static final List<String> LABELS=List.of("未注明","份","个","碗","斤","100克","公斤");
  public static String unit(String value){int index=UNITS.indexOf(value);return index<0?"未注明":LABELS.get(index);}
  public static String price(BigDecimal value,String unit){return value==null?"价格未知":"¥"+value.setScale(2).toPlainString()+(unit==null||unit.isBlank()||unit.equals("unspecified")?" · 单位未注明":"/"+unit(unit));}
  public record Entry(String id,BigDecimal price,String priceUnit,long recordedAt,String origin,long revision,boolean carriedForward){}
  public record Snapshot(String dishId,String dishName,BigDecimal currentPrice,String priceUnit,long revision,
      long windowStart,long windowEnd,long totalRecords,boolean canManage,List<Entry> records){public Snapshot{records=List.copyOf(records);}}
  public record Point(Entry entry,long time){}
  public record Run(String unit,long start,long end,List<Point> points){public Run{points=List.copyOf(points);}}
  public static List<Run> runs(Snapshot data){
    var result=new ArrayList<Run>();var points=new ArrayList<Point>();String unit=null;long start=0;
    for(Entry row:data.records()){
      long time=Math.max(data.windowStart(),row.recordedAt());
      if(!points.isEmpty()&&(row.price()==null||!Objects.equals(unit,row.priceUnit()))){result.add(new Run(unit,start,time,points));points=new ArrayList<>();}
      if(row.price()==null)continue;
      if(points.isEmpty()){unit=row.priceUnit();start=time;}
      if(points.isEmpty()||points.get(points.size()-1).entry().price().compareTo(row.price())!=0)points.add(new Point(row,time));
    }
    if(!points.isEmpty())result.add(new Run(unit,start,data.windowEnd(),points));
    return List.copyOf(result);
  }
}
