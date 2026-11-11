package cn.edu.bjtu.foodtime.domain.model;

import java.time.*;
import java.time.format.*;
import java.util.Locale;

public final class NoticeTimes {
  public static final ZoneOffset ZONE=ZoneOffset.ofHours(8);
  private static final DateTimeFormatter INPUT=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
  public static String input(String instant){return instant==null||instant.isBlank()?"":Instant.parse(instant).atOffset(ZONE).format(INPUT);}
  public static String display(String instant){return instant==null||instant.isBlank()?"未设置":input(instant)+"（UTC+8）";}
  public static String instant(String input){
    if(input==null||input.isBlank())return "";
    try{return LocalDateTime.parse(input.strip().replace('T',' '),INPUT).toInstant(ZONE).toString();}
    catch(DateTimeException error){throw new IllegalArgumentException("请按 年月日 时:分 填写有效时间，例如 2026-11-11 15:30");}
  }
  public static String preserve(String input,String original){String entered=input==null?"":input;return original!=null&&!original.isBlank()&&entered.equals(input(original))?original:instant(entered);}
  public static NoticeSchedule resolve(String publication,String starts,String ends,String date,String mode,
      NoticeSchedule original,boolean update,boolean published,boolean enabled){
    String pub=preserve(publication,original.publishedAt()),start=preserve(starts,original.displayStartsAt());
    String end=mode.equals("minute")?preserve(ends,original.displayEndsAt()):"",expires="";
    if(mode.equals("minute")&&end.isBlank())throw new IllegalArgumentException("请填写截止日期和时分");
    if(mode.equals("date")){
      try{expires=LocalDate.parse(date).toString();}catch(DateTimeException error){throw new IllegalArgumentException("请填写有效截止日期，例如 2026-11-15");}
    }
    if(!pub.isBlank()&&!start.isBlank()&&Instant.parse(start).isBefore(Instant.parse(pub)))throw new IllegalArgumentException("开始显示时间不能早于发布时间");
    String first=start.isBlank()?pub:start;
    if(!end.isBlank()&&!first.isBlank()&&!Instant.parse(end).isAfter(Instant.parse(first)))throw new IllegalArgumentException("截止时间必须晚于开始显示时间");
    return new NoticeSchedule(pub,start,end,expires,update,published,published&&enabled);
  }
  public static String status(String value){return switch(value){case "draft"->"草稿";case "scheduled"->"等待发布";case "waiting"->"等待弹窗开始";case "active"->"弹窗生效中";case "expired"->"已截止，信箱留档";default->"弹窗已停用";};}
  private NoticeTimes(){}
}
