package cn.edu.bjtu.foodtime.presentation;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PaginationItemsTest {
  @Test public void matchesRequestedBeginningMiddleAndEnd(){
    assertEquals(List.of(1,2,3,4,5,6,7,0,34),PaginationItems.build(1,34,5));
    assertEquals(List.of(1,0,4,5,6,7,8,0,34),PaginationItems.build(6,34,5));
    assertEquals(List.of(1,0,28,29,30,31,32,33,34),PaginationItems.build(34,34,5));
  }
  @Test public void omittedRangesAlwaysKeepEndpointsAndCurrentAcrossWidths(){
    for(int total:new int[]{1,5,6,9,34,100,1000})for(int size:new int[]{1,2,3,5,9,20})for(int page=1;page<=total;page++){
      var items=PaginationItems.build(page,total,size);assertEquals(Integer.valueOf(1),items.get(0));assertEquals(Integer.valueOf(total),items.get(items.size()-1));assertTrue(items.contains(page));
      var seen=new HashSet<Integer>();for(int i=0;i<items.size();i++){int value=items.get(i);if(value==0){assertTrue(i>0&&i<items.size()-1&&items.get(i+1)-items.get(i-1)>1);}else {assertTrue(seen.add(value));if(i>0&&items.get(i-1)!=0)assertEquals(1,value-items.get(i-1));}}
    }
  }
}
