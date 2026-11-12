package cn.edu.bjtu.foodtime.domain.validation;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PhotoZoomGeometryTest {
  @Test public void scaleIsClampedToTheExistingOneToFiveRange() {
    assertEquals(1f, PhotoZoomGeometry.clampScale(0.2f), 0f);
    assertEquals(1f, PhotoZoomGeometry.clampScale(1f), 0f);
    assertEquals(5f, PhotoZoomGeometry.clampScale(9f), 0f);
    assertEquals(2.5f, PhotoZoomGeometry.clampScale(2.5f), 0f);
  }

  @Test public void easeOutIsMonotonicAndEndsExactly() {
    assertEquals(0f, PhotoZoomGeometry.easeOutCubic(0f), 0f);
    assertEquals(1f, PhotoZoomGeometry.easeOutCubic(1f), 0f);
    float previous = 0f;
    for (int step = 1; step <= 10; step++) {
      float value = PhotoZoomGeometry.easeOutCubic(step / 10f);
      assertEquals(true, value >= previous);
      previous = value;
    }
    assertEquals(true, PhotoZoomGeometry.easeOutCubic(0.5f) > 0.5f);
  }

  @Test public void scaleInterpolatesFromAndToTheEndpoints() {
    assertEquals(1f, PhotoZoomGeometry.interpolateScale(1f, 2.5f, 0f), 0f);
    assertEquals(2.5f, PhotoZoomGeometry.interpolateScale(1f, 2.5f, 1f), 0f);
    assertEquals(1.75f, PhotoZoomGeometry.interpolateScale(1f, 2.5f, 0.5f), 0f);
  }

  @Test public void anchoredShiftKeepsTheAnchorWhenScaleIsUnchanged() {
    assertEquals(12f, PhotoZoomGeometry.anchoredShift(100f, 200f, 12f, 1f, 1f), 0f);
    assertEquals(12f, PhotoZoomGeometry.anchoredShift(100f, 200f, 12f, 2f, 2f), 0f);
  }

  @Test public void anchoredShiftMatchesTheTargetTransformFormula() {
    float anchor = 100f, center = 200f, baseShift = 10f, baseScale = 1f, scale = 2f;
    assertEquals(anchor - center - (anchor - center - baseShift) * (scale / baseScale),
        PhotoZoomGeometry.anchoredShift(anchor, center, baseShift, baseScale, scale), 0f);
  }
}
