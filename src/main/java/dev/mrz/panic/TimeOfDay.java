package dev.mrz.panic;

/**
 * Classifies game ticks into day/night phases for the shortened day cycle.
 *
 * <p>A full cycle is {@code dayLength + nightLength} ticks and starts at dawn (tick 0). The day
 * half runs first; alphas only spawn during the night half.
 */
public final class TimeOfDay {

  public enum Phase {
    DAY,
    NIGHT
  }

  private final int dayLength;
  private final int nightLength;

  public TimeOfDay(int dayLength, int nightLength) {
    if (dayLength <= 0 || nightLength <= 0) {
      throw new IllegalArgumentException("day and night lengths must be positive");
    }
    this.dayLength = dayLength;
    this.nightLength = nightLength;
  }

  public int dayLength() {
    return dayLength;
  }

  public int nightLength() {
    return nightLength;
  }

  public int cycleLength() {
    return dayLength + nightLength;
  }

  /**
   * Phase at an absolute game tick. Negative ticks are normalized into the cycle (the server world
   * time never goes negative, but callers may pass offsets).
   */
  public Phase phaseAt(long tick) {
    long t = Math.floorMod(tick, cycleLength());
    return t < dayLength ? Phase.DAY : Phase.NIGHT;
  }

  /** True while the given tick is in the night half of the cycle. */
  public boolean isNight(long tick) {
    return phaseAt(tick) == Phase.NIGHT;
  }

  /**
   * Maps a cycle tick to a vanilla world time (0-23999) so the sky matches the shortened cycle. The
   * day half is stretched over the vanilla day window [0,12000) and the night half over
   * [12000,24000), so a 6000/12000 config reads as 5 minutes of sun and 10 of night in real time.
   */
  public long vanillaTimeAt(long tick) {
    long t = Math.floorMod(tick, cycleLength());
    if (t < dayLength) {
      return t * 12000L / dayLength;
    }
    return 12000L + (t - dayLength) * 12000L / nightLength;
  }
}
