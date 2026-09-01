package dev.mrz.panic;

import org.bukkit.World;

/**
 * Owns the shortened day cycle. Advances one virtual tick per server tick and drives the world time
 * so the sky matches (5 min day / 10 min night by default). Fires a dawn callback.
 */
public final class DayNightClock {

  private final World world;
  private final TimeOfDay tod;
  private final Runnable dawnListener;
  private long vt;

  public DayNightClock(World world, TimeOfDay tod, Runnable dawnListener) {
    this.world = world;
    this.tod = tod;
    this.dawnListener = dawnListener;
  }

  /** Forces dawn and starts the cycle. Call once on enable. */
  public void start() {
    vt = 0;
    world.setTime(0L);
  }

  public void tick() {
    vt++;
    boolean dawn = vt % tod.cycleLength() == 0 && vt > 0;
    world.setTime(tod.vanillaTimeAt(vt));
    if (dawn) {
      dawnListener.run();
    }
  }

  public TimeOfDay.Phase phase() {
    return tod.phaseAt(vt);
  }

  public boolean isNight() {
    return tod.isNight(vt);
  }

  /** Jumps the cycle to the start of the given phase (testing / admin). */
  public void setPhase(TimeOfDay.Phase phase) {
    vt = phase == TimeOfDay.Phase.DAY ? 0 : tod.dayLength();
    world.setTime(tod.vanillaTimeAt(vt));
  }
}
