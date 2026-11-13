package cn.edu.bjtu.foodtime.presentation;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Zero denotes an omitted range, never a target page. */
final class PaginationItems {
  static List<Integer> build(int page,int pages,int windowSize){
    int total=Math.max(1,pages),current=Math.max(1,Math.min(total,page)),count=Math.max(1,Math.min(total,windowSize));
    var result=new ArrayList<Integer>();
    if((long)total<=count+4L){for(int i=1;i<=total;i++)result.add(i);return result;}
    int start=Math.max(1,current-count/2),end=(int)Math.min(total,(long)start+count-1);
    if(start<=2){start=1;end=count+2;}else if(end>=total-1){start=total-count-1;end=total;}
    var numbers=new TreeSet<Integer>();numbers.add(1);numbers.add(total);for(int i=start;i<=end;i++)numbers.add(i);
    int previous=0;
    for(int number:numbers){if(previous>0&&number-previous>1)result.add(0);result.add(number);previous=number;}
    return result;
  }
  private PaginationItems(){}
}
