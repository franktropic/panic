package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

final class DeathListenerTest {

  @Test
  void fullChanceKeepsEverything() {
    assertEquals(40, DeathListener.countKept(new Random(1), 40, 1.0));
    assertEquals(0, DeathListener.countKept(new Random(1), 0, 1.0));
  }

  @Test
  void zeroChanceKeepsNothing() {
    assertEquals(0, DeathListener.countKept(new Random(1), 40, 0.0));
  }

  @Test
  void partialChanceKeepsSome() {
    int kept = DeathListener.countKept(new Random(42), 400, 0.25);
    assertTrue(kept > 0 && kept < 400, "kept=" + kept);
  }

  @Test
  void partialChanceIsApproximatelyTheRate() {
    int kept = DeathListener.countKept(new Random(7), 10_000, 0.25);
    assertTrue(kept > 2000 && kept < 3000, "kept=" + kept);
  }
}
