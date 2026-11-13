package cn.edu.bjtu.foodtime.presentation;
import android.content.Context;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.PriceHistory;
final class PriceUnits {
  private PriceUnits(){}
  static Spinner field(Context context,LinearLayout parent,String current){
    parent.addView(Ui.text(context,"计价单位",14,Design.MUTED));Spinner spinner=new Spinner(context);
    spinner.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,PriceHistory.LABELS));
    spinner.setSelection(Math.max(0,PriceHistory.UNITS.indexOf(current)));spinner.setMinimumHeight(Ui.dp(context,48));
    spinner.setContentDescription("计价单位");parent.addView(spinner,new LinearLayout.LayoutParams(-1,-2));return spinner;
  }
  static String value(Spinner spinner){return PriceHistory.UNITS.get(spinner.getSelectedItemPosition());}
}
