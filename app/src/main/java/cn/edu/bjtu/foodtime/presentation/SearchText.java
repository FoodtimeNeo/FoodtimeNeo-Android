package cn.edu.bjtu.foodtime.presentation;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

/** Literal matching only: a keyword is never interpreted as markup or a pattern. */
final class SearchText {
  private SearchText() { }
  static CharSequence highlight(String text,String keyword) {
    SpannableString value=new SpannableString(text);String term=keyword.strip();if(term.isEmpty())return value;
    for(int index=0;index<=text.length()-term.length();){
      if(text.regionMatches(true,index,term,0,term.length())){int end=index+term.length();value.setSpan(new ForegroundColorSpan(Design.RED),index,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);index=end;}
      else index++;
    }
    return value;
  }
}
