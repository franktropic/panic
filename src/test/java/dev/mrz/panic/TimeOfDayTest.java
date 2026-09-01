package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TimeOfDayTest {

  /** Default brief values: 5 min day, 10 min night (20 ticks per game-second = 1 min). */
  private final TimeOfDay tod = new TimeOfDay(6000, 12000);

  @Test
  void dayPhaseCoversStartOfCycle() {
    assertEquals(TimeOfDay.Phase.DAY, tod.phaseAt(0));
    assertEquals(TimeOfDay.Phase.DAY, tod.phaseAt(5999));
  }

  @Test
  void nightPhaseCoversSecondHalf() {
    assertEquals(TimeOfDay.Phase.NIGHT, tod.phaseAt(6000));
    assertEquals(TimeOfDay.Phase.NIGHT, tod.phaseAt(17999));
  }

  @Test
  void wrapsAroundCycle() {
    assertEquals(TimeOfDay.Phase.DAY, tod.phaseAt(18000));
    assertEquals(TimeOfDay.Phase.NIGHT, tod.phaseAt(18000 + 6000));
  }

  @Test
  void normalizesNegativeTicks() {
    assertEquals(TimeOfDay.Phase.NIGHT, tod.phaseAt(-1));
    assertEquals(TimeOfDay.Phase.NIGHT, tod.phaseAt(-12000));
    assertEquals(TimeOfDay.Phase.DAY, tod.phaseAt(-12001));
  }

  @Test
  void rejectsNonPositiveLengths() {
    assertThrows(IllegalArgumentException.class, () -> new TimeOfDay(0, 12000));
    assertThrows(IllegalArgumentException.class, () -> new TimeOfDay(6000, -1));
  }

  @Test
  void isNightMatchesPhase() {
    assertTrue(tod.isNight(9000));
    assertFalse(tod.isNight(3000));
  }
}
