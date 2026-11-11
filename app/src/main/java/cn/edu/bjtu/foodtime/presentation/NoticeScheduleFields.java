package cn.edu.bjtu.foodtime.presentation;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.NoticeSchedule;
import cn.edu.bjtu.foodtime.domain.model.NoticeTimes;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class NoticeScheduleFields {
  private final MainActivity a;
  private final NoticeSchedule original;
  private final EditText publication,starts,date,end;
  private final CheckBox update,enabled;
  private final String prefix;
  private final List<View> controls=new ArrayList<>();
  private String mode;
  NoticeScheduleFields(MainActivity a,LinearLayout page,NoticeSchedule value){
    this.a=a;prefix=a.draftKey("schedule:");
    if(!a.drafts.getBoolean(prefix+"ready",false)){
      a.drafts.putString(prefix+"original-publication",value.publishedAt());a.drafts.putString(prefix+"original-start",value.displayStartsAt());a.drafts.putString(prefix+"original-end",value.displayEndsAt());
      initial("发布时间（UTC+8）",NoticeTimes.input(value.publishedAt()));initial("开始显示时间（UTC+8）",NoticeTimes.input(value.displayStartsAt()));
      initial("截止日期（包含当天）",value.expiresOn());initial("截止日期和时分（UTC+8）",NoticeTimes.input(value.displayEndsAt()));
      a.drafts.putBoolean(prefix+"update",value.isUpdate());a.drafts.putBoolean(prefix+"enabled",!value.published()||value.enabled());
      a.drafts.putString(prefix+"mode",!value.expiresOn().isBlank()?"date":!value.displayEndsAt().isBlank()?"minute":"none");a.drafts.putBoolean(prefix+"ready",true);
    }
    original=new NoticeSchedule(a.drafts.getString(prefix+"original-publication",""),a.drafts.getString(prefix+"original-start",""),a.drafts.getString(prefix+"original-end",""),"",value.isUpdate(),value.published(),value.enabled());
    page.addView(Ui.text(a,"所有时间均为 UTC+8。到发布时间进入用户信箱，弹窗按开始与截止时间显示。",14,Design.MUTED));
    update=check(page,"这是更新公告",prefix+"update");
    publication=timeField(page,"发布时间（UTC+8）",false);page.addView(Ui.text(a,"留空立即发布。",12,Design.MUTED));
    starts=timeField(page,"开始显示时间（UTC+8）",false);page.addView(Ui.text(a,"留空沿用发布时间。",12,Design.MUTED));
    mode=a.drafts.getString(prefix+"mode","none");RadioGroup options=new RadioGroup(a);Map<Integer,String> modes=new HashMap<>();
    for(String name:List.of("none","date","minute")){
      RadioButton option=new RadioButton(a);option.setId(View.generateViewId());option.setText(switch(name){case "date"->"截止到指定日期结束";case "minute"->"截止到指定日期和时分";default->"不设截止时间";});option.setTextColor(Design.INK);option.setMinHeight(Ui.dp(a,48));options.addView(option);modes.put(option.getId(),name);controls.add(option);if(mode.equals(name))options.check(option.getId());
    }
    page.addView(options);LinearLayout dayGroup=Ui.col(a),minuteGroup=Ui.col(a);page.addView(dayGroup);page.addView(minuteGroup);
    date=timeField(dayGroup,"截止日期（包含当天）",true);dayGroup.addView(Ui.text(a,"次日 00:00 停止弹窗，已发布公告仍在信箱留档。",12,Design.MUTED));
    end=timeField(minuteGroup,"截止日期和时分（UTC+8）",false);
    Runnable show=()->{dayGroup.setVisibility(mode.equals("date")?View.VISIBLE:View.GONE);minuteGroup.setVisibility(mode.equals("minute")?View.VISIBLE:View.GONE);};show.run();
    options.setOnCheckedChangeListener((group,id)->{mode=modes.get(id);a.drafts.putString(prefix+"mode",mode);show.run();});
    enabled=check(page,"发布后启用弹窗（遵循上面的时间）",prefix+"enabled");
  }
  private void initial(String label,String value){a.drafts.putString(a.draftKey(label),value);}
  private CheckBox check(LinearLayout page,String label,String key){CheckBox view=new CheckBox(a);view.setText(label);view.setTextColor(Design.INK);view.setMinHeight(Ui.dp(a,48));view.setChecked(a.drafts.getBoolean(key,false));view.setOnCheckedChangeListener((button,checked)->a.drafts.putBoolean(key,checked));page.addView(view);controls.add(view);return view;}
  private EditText timeField(LinearLayout page,String label,boolean dayOnly){
    EditText input=Ui.field(page,label,false,false);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(dayOnly?10:16)});input.setHint(dayOnly?"2026-11-15":"2026-11-11 15:30");controls.add(input);
    LinearLayout actions=Ui.row(a);TextView choose=Ui.button(a,dayOnly?"选择日期":"选择日期和时间",false,()->pick(input,dayOnly));actions.addView(choose,new LinearLayout.LayoutParams(0,-2,1));controls.add(choose);
    TextView clear=Ui.button(a,"清空",false,()->input.setText(""));actions.addView(clear);controls.add(clear);page.addView(actions);Ui.gap(page,12);return input;
  }
  private void pick(EditText input,boolean dayOnly){
    LocalDateTime base=OffsetDateTime.now(NoticeTimes.ZONE).toLocalDateTime();
    try{if(!input.getText().toString().isBlank())base=dayOnly?LocalDate.parse(input.getText().toString()).atStartOfDay():Instant.parse(NoticeTimes.instant(input.getText().toString())).atOffset(NoticeTimes.ZONE).toLocalDateTime();}catch(RuntimeException ignored){}
    LocalDateTime initial=base;
    new DatePickerDialog(a,(picker,year,month,day)->{
      LocalDate selected=LocalDate.of(year,month+1,day);
      if(dayOnly)input.setText(selected.toString());
      else new TimePickerDialog(a,(time,hour,minute)->input.setText(selected.atTime(hour,minute).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.ROOT))),initial.getHour(),initial.getMinute(),true).show();
    },initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
  }
  NoticeSchedule value(boolean published){return NoticeTimes.resolve(publication.getText().toString(),starts.getText().toString(),end.getText().toString(),date.getText().toString(),mode,original,update.isChecked(),published,enabled.isChecked());}
  void setEnabled(boolean value){for(View view:controls)view.setEnabled(value);}
}
