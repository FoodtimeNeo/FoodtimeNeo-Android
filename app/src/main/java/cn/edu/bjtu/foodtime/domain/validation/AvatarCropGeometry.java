package cn.edu.bjtu.foodtime.domain.validation;

/** Coordinates are independent of Android; pan is expressed as a fraction of the square side. */
public final class AvatarCropGeometry {
  public static final long MAX_BYTES=10L*1024*1024;
  public record Layout(float side,float left,float top,float scale,float panX,float panY){}
  public static Layout calculate(int imageWidth,int imageHeight,float viewportWidth,float viewportHeight,int rotation,float zoom,float panX,float panY){
    if(imageWidth<1||imageHeight<1||viewportWidth<=32||viewportHeight<=32)throw new IllegalArgumentException("请等待图片显示后再试");
    float side=Math.min(viewportWidth,viewportHeight)-32;
    float width=rotation%180==0?imageWidth:imageHeight,height=rotation%180==0?imageHeight:imageWidth;
    float scale=Math.max(side/width,side/height)*Math.max(1,Math.min(4,zoom));
    float maxX=Math.max(0,(width*scale-side)/2),maxY=Math.max(0,(height*scale-side)/2);
    return new Layout(side,(viewportWidth-side)/2,(viewportHeight-side)/2,scale,
        Math.max(-maxX,Math.min(maxX,panX*side)),Math.max(-maxY,Math.min(maxY,panY*side)));
  }
  private AvatarCropGeometry(){}
}
