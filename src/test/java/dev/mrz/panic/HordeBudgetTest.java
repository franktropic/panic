package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HordeBudgetTest {

  @Test
  void alphaCapRespectsConfigAndPopulation() {
    HordeBudget b = new HordeBudget(4, 40);
    assertTrue(b.canSpawnAlpha(10));
    b.addAlpha();
    b.addAlpha();
    b.addAlpha();
    b.addAlpha();
    assertFalse(b.canSpawnAlpha(10), "config cap of 4 hit");
    assertFalse(b.canSpawnAlpha(0), "population cap of 0 blocks all spawns");

    b.removeAlpha();
    assertTrue(b.canSpawnAlpha(4), "population cap now matches 4");
    assertFalse(b.canSpawnAlpha(3), "population cap of 3 blocks the 4th");
  }

  @Test
  void hordeCapIsIndependent() {
    HordeBudget b = new HordeBudget(4, 40);
    for (int i = 0; i < 40; i++) {
      assertTrue(b.canSpawnHorde());
      b.addHorde();
    }
    assertFalse(b.canSpawnHorde());
    b.removeHorde();
    assertTrue(b.canSpawnHorde());
  }

  @Test
  void releaseHordeFreesASlotBatch() {
    HordeBudget b = new HordeBudget(4, 40);
    b.addHorde();
    b.addHorde();
    b.addHorde();
    b.releaseHorde(2);
    assertEquals(1, b.horde());
    b.releaseHorde(9);
    assertEquals(0, b.horde(), "cannot go negative");
  }

  @Test
  void countersNeverGoNegative() {
    HordeBudget b = new HordeBudget(4, 40);
    b.removeAlpha();
    b.removeHorde();
    assertEquals(0, b.alphas());
    assertEquals(0, b.horde());
  }

  @Test
  void rejectsNonPositiveCaps() {
    assertThrows(IllegalArgumentException.class, () -> new HordeBudget(0, 40));
    assertThrows(IllegalArgumentException.class, () -> new HordeBudget(4, 0));
  }
}
