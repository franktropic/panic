package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TunnelServiceTest {

  private static final long GRACE = 30L * 20L; // default 30s grace, in ticks

  @Test
  void oldBlocksHealAtDawn() {
    assertTrue(TunnelService.healEligible(100L, 1000L, GRACE));
    // Broken well before the grace window opens.
    assertTrue(TunnelService.healEligible(0L, GRACE + 1L, GRACE));
  }

  @Test
  void blocksBrokenInsideTheGraceWindowStayBroken() {
    // Broken one tick before dawn: inside the 30s window, stays broken this dawn.
    assertFalse(TunnelService.healEligible(1000L, 1001L, GRACE));
    // Broken exactly at the window edge (dawn - grace): not old enough yet.
    assertFalse(TunnelService.healEligible(1000L, 1000L + GRACE, GRACE));
    // Broken one tick before the edge: now old enough.
    assertTrue(TunnelService.healEligible(1000L - 1L, 1000L + GRACE, GRACE));
  }

  @Test
  void zeroGraceMeansEverythingHeals() {
    assertTrue(TunnelService.healEligible(99L, 100L, 0L));
    // Broken on the dawn tick itself does not heal even with zero grace.
    assertFalse(TunnelService.healEligible(100L, 100L, 0L));
  }
}
