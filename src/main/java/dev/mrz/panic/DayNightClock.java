package dev.mrz.panic;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;

/**
 * Owns the shortened day cycle. Advances one virtual tick per server tick and drives the world time
 * so the sky matches (5 min day / 10 min night by default). Fires dawn callbacks.
 */
public final class DayNightClock {

  private final World world;
  private final TimeOfDay tod;
  private final List<Runnable> dawnListeners = new ArrayList<>();
  private long vt;

  public DayNightClock(World world, TimeOfDay tod, Runnable dawnListener) {
    this.world = world;
    this.tod = tod;
    this.dawnListeners.add(dawnListener);
  }

  public void addDawnListener(Runnable listener) {
    dawnListeners.add(listener);
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
      fireDawn();
    }
  }

  private void fireDawn() {
    for (Runnable listener : dawnListeners) {
      listener.run();
    }
  }

  public TimeOfDay.Phase phase() {
    return tod.phaseAt(vt);
  }

  public boolean isNight() {
    return tod.isNight(vt);
  }

  /**
   * Jumps the cycle to the start of the given phase (testing / admin). Jumping to DAY fires the
   * dawn listeners, so admin dawn behaves like natural dawn (alphas burn, tunnels heal).
   */
  public void setPhase(TimeOfDay.Phase phase) {
    vt = phase == TimeOfDay.Phase.DAY ? 0 : tod.dayLength();
    world.setTime(tod.vanillaTimeAt(vt));
    if (phase == TimeOfDay.Phase.DAY) {
      fireDawn();
    }
  }
}
