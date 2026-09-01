package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RunTimerTest {

  @Test
  void startsStopped() {
    RunTimer t = new RunTimer();
    assertFalse(t.running());
    assertEquals(0L, t.currentSeconds(1000));
  }

  @Test
  void measuresSurvivalSeconds() {
    RunTimer t = new RunTimer();
    t.start(1000);
    assertTrue(t.running());
    assertEquals(0L, t.currentSeconds(1019));
    assertEquals(1L, t.currentSeconds(1020));
    assertEquals(300L, t.currentSeconds(7000));
  }

  @Test
  void endUpdatesBestAndStops() {
    RunTimer t = new RunTimer();
    t.start(0);
    assertEquals(60L, t.end(1200));
    assertFalse(t.running());
    assertEquals(60L, t.bestSeconds());

    t.start(2000);
    assertEquals(10L, t.end(2200));
    assertEquals(60L, t.bestSeconds(), "best must keep the longer run");
  }

  @Test
  void endWithNoRunReturnsZero() {
    RunTimer t = new RunTimer();
    assertEquals(0L, t.end(500));
    assertEquals(0L, t.bestSeconds());
  }

  @Test
  void formatHumanReads() {
    assertEquals("45s", RunTimer.format(45));
    assertEquals("12m 05s", RunTimer.format(725));
    assertEquals("1h 04m 09s", RunTimer.format(3849));
    assertEquals("0s", RunTimer.format(0));
  }
}
