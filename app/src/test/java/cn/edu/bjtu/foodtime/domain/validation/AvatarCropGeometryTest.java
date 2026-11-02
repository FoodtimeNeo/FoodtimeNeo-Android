package cn.edu.bjtu.foodtime.domain.validation;
import org.junit.Test;
import static org.junit.Assert.*;
public class AvatarCropGeometryTest {
  @Test public void allRotationsAndDragsKeepImageCoveringTheSquare(){
    for(int[] dimensions:new int[][]{{1600,900},{900,1600},{512,512}})
      for(int rotation:new int[]{0,90,180,270})for(float zoom:new float[]{1,2,4})for(float pan:new float[]{-1000,1000}){
        var g=AvatarCropGeometry.calculate(dimensions[0],dimensions[1],350,320,rotation,zoom,pan,-pan);
        float width=rotation%180==0?dimensions[0]:dimensions[1],height=rotation%180==0?dimensions[1]:dimensions[0];
        assertTrue(175+g.panX()-width*g.scale()/2<=g.left()+.001f);
        assertTrue(175+g.panX()+width*g.scale()/2>=g.left()+g.side()-.001f);
        assertTrue(160+g.panY()-height*g.scale()/2<=g.top()+.001f);
        assertTrue(160+g.panY()+height*g.scale()/2>=g.top()+g.side()-.001f);
      }
  }
  @Test public void resizingViewportPreservesRelativePanAndSquareSelection(){
    var small=AvatarCropGeometry.calculate(1200,600,350,320,0,2,.2f,.2f);
    var large=AvatarCropGeometry.calculate(1200,600,700,640,0,2,.2f,.2f);
    assertEquals(small.panX()/small.side(),large.panX()/large.side(),.0001);
    assertEquals(small.panY()/small.side(),large.panY()/large.side(),.0001);
  }
  @Test(expected=IllegalArgumentException.class) public void cannotExportBeforeTheViewportIsLaidOut(){AvatarCropGeometry.calculate(512,512,0,0,0,1,0,0);}
}
