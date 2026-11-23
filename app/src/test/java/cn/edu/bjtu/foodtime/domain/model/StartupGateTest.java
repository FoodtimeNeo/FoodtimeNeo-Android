package cn.edu.bjtu.foodtime.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class StartupGateTest {
  @Test public void minimumStartsAtFirstPaintAndIsNotResetByRotation() {
    var gate = new StartupGate(100, 1500, 15000);
    gate.shown(600);
    assertEquals(1500, gate.remaining(600, true));
    gate.shown(1000);
    assertEquals(1100, gate.remaining(1000, true));
    assertEquals(0, gate.remaining(2100, true));
  }
  @Test public void fastContentStillWaitsForMinimum() {
    var gate = new StartupGate(100, 1500, 15000);
    assertEquals(1400, gate.remaining(200, true));
    assertEquals(1, gate.remaining(1599, true));
    assertEquals(0, gate.remaining(1600, true));
  }
  @Test public void slowContentWaitsForReadinessWithoutAddingAnotherDelay() {
    var gate = new StartupGate(100, 1500, 15000);
    assertEquals(Long.MAX_VALUE, gate.remaining(5000, false));
    assertEquals(0, gate.remaining(5000, true));
  }
  @Test public void timeoutDoesNotOverrideReadyContent() {
    var gate = new StartupGate(100, 1500, 15000);
    assertFalse(gate.timedOut(15099, false));
    assertTrue(gate.timedOut(15100, false));
    assertFalse(gate.timedOut(15100, true));
  }
  @Test public void reattachmentUsesOriginalDeadlineAndWarmStartNeverReplays() {
    var process = new StartupGate(100, 1500, 15000);
    assertEquals(500, process.remaining(1100, true));
    assertEquals(14000, process.timeoutRemaining(1100));
    process.dismiss();
    assertFalse(process.showing());
    assertEquals(0, process.remaining(200, false));
    assertFalse(process.timedOut(99999, false));
  }
}
