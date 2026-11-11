package cn.edu.bjtu.foodtime.domain.model;

import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoticeTimesTest {
  @Test public void dateAndTimeConversionAlwaysUsesUtcPlusEight(){
    TimeZone previous=TimeZone.getDefault();
    try{for(String zone:new String[]{"UTC","America/New_York","Asia/Tokyo"}){
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
      assertEquals("2026-11-11T07:30:00Z",NoticeTimes.instant("2026-11-11 15:30"));
      assertEquals("2026-11-11 15:30",NoticeTimes.input("2026-11-11T07:30:00Z"));
    }}finally{TimeZone.setDefault(previous);}
  }
  @Test public void unchangedInputsPreserveSecondsAndMicroseconds(){
    String original="2026-11-11T07:30:28.123456Z";
    assertEquals(original,NoticeTimes.preserve("2026-11-11 15:30",original));
    assertEquals("2026-11-11T07:31:00Z",NoticeTimes.preserve("2026-11-11 15:31",original));
  }
  @Test public void dateOnlyDeadlineIsSentAsCalendarDateWithoutGuessingTheDeviceZone(){
    NoticeSchedule original=new NoticeSchedule("","","","",false,false,true);
    var result=NoticeTimes.resolve("2026-11-11 15:30","","","2026-11-15","date",original,true,true,true);
    assertEquals("2026-11-11T07:30:00Z",result.publishedAt());assertEquals("",result.displayStartsAt());
    assertEquals("",result.displayEndsAt());assertEquals("2026-11-15",result.expiresOn());assertTrue(result.isUpdate());
  }
  @Test public void invalidDatesAndReversedIntervalsCannotBeSubmitted(){
    assertThrows(IllegalArgumentException.class,()->NoticeTimes.instant("2026-02-30 12:30"));
    NoticeSchedule original=new NoticeSchedule("","","","",false,false,true);
    assertThrows(IllegalArgumentException.class,()->NoticeTimes.resolve("2026-11-11 15:30","2026-11-11 15:29","","","none",original,false,true,true));
    assertThrows(IllegalArgumentException.class,()->NoticeTimes.resolve("2026-11-11 15:30","","2026-11-11 15:30","","minute",original,false,true,true));
  }
  @Test public void draftsRemainUnpublishedAndCannotEnablePopup(){
    var original=new NoticeSchedule("","","","",false,false,true);
    var result=NoticeTimes.resolve("","","","","none",original,false,false,true);
    assertFalse(result.published());assertFalse(result.enabled());
  }
}
