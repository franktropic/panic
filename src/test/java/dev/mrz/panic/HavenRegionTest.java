package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HavenRegionTest {

  private final HavenRegion haven = new HavenRegion(100, 200, 24);

  @Test
  void centerIsInside() {
    assertTrue(haven.containsBlock(100, 200));
  }

  @Test
  void bordersAreInside() {
    assertTrue(haven.containsBlock(88, 200));
    assertTrue(haven.containsBlock(112, 200));
    assertTrue(haven.containsBlock(100, 188));
    assertTrue(haven.containsBlock(100, 212));
  }

  @Test
  void justOutsideIsOutside() {
    assertFalse(haven.containsBlock(87, 200));
    assertFalse(haven.containsBlock(113, 200));
    assertFalse(haven.containsBlock(100, 187));
    assertFalse(haven.containsBlock(100, 213));
    assertFalse(haven.containsBlock(113, 213));
  }

  @Test
  void isASquareNotACircle() {
    // Corner of the 24x24 square is inside; the same distance on the diagonal is outside.
    assertTrue(haven.containsBlock(112, 212));
    assertFalse(haven.containsBlock(116, 216));
  }

  @Test
  void outsidePointLiesBeyondBorder() {
    int[] p = haven.outsidePoint();
    assertFalse(haven.containsBlock(p[0], p[1]));
  }

  @Test
  void rejectsTinyHavens() {
    assertThrows(IllegalArgumentException.class, () -> new HavenRegion(0, 0, 2));
  }
}
