package dev.mrz.panic;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

/**
 * Night hunt: at night, any monster that is loaded within {@code hunting.night-range} blocks of a
 * hunt target immediately takes them (vanilla detection is ~16 blocks; this makes the whole loaded
 * map hostile at dusk). Alphas are skipped — they run their own detect logic. Monsters inside the
 * haven or the peace ring are left alone (those zones keep their rules), and monsters that already
 * have a player target are not re-targeted. A cap on assignments per scan keeps the 2s scan cheap
 * no matter how many monsters happen to be loaded.
 */
final class NightHunt {

  /** Scan cadence in ticks (2s). */
  private static final int SCAN_TICKS = 40;

  /** Max new long-range targets per scan (rounds out over several scans). */
  private static final int SCAN_CAP = 32;

  private final PanicPlugin plugin;
  private final int task;
  private boolean announcedThisNight;

  private NightHunt(PanicPlugin plugin) {
    this.plugin = plugin;
    this.task =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimer(plugin, this::tick, SCAN_TICKS, SCAN_TICKS)
            .getTaskId();
  }

  static NightHunt start(PanicPlugin plugin) {
    return new NightHunt(plugin);
  }

  void stop() {
    plugin.getServer().getScheduler().cancelTask(task);
  }

  private void tick() {
    double range = plugin.config().huntNightRange;
    if (range <= 0 || !plugin.clock().isNight()) {
      announcedThisNight = false;
      return;
    }
    if (!announcedThisNight) {
      announcedThisNight = true;
      plugin.broadcast("The dark is listening. Whatever can see you is already walking.");
    }
    List<Player> targets = new ArrayList<>();
    for (Player p : plugin.getServer().getOnlinePlayers()) {
      if (isHuntTarget(p)) {
        targets.add(p);
      }
    }
    if (targets.isEmpty()) {
      return;
    }
    int assigned = 0;
    for (Monster m : plugin.world().getEntitiesByClass(Monster.class)) {
      if (assigned >= SCAN_CAP) {
        break;
      }
      if (plugin.alphaManager().isAlpha(m) || m.getTarget() instanceof Player) {
        continue;
      }
      Location here = m.getLocation();
      if (plugin.haven().contains(here) || plugin.inPeaceRing(here)) {
        continue;
      }
      int idx = nearestWithinIndex(targetLocations(targets), here, range);
      if (idx >= 0) {
        m.setTarget(targets.get(idx));
        assigned++;
      }
    }
  }

  private static List<Location> targetLocations(List<Player> targets) {
    List<Location> out = new ArrayList<>(targets.size());
    for (Player p : targets) {
      out.add(p.getLocation());
    }
    return out;
  }

  /** Same rules the alpha uses: a live, escaped player outside the haven and the peace ring. */
  private boolean isHuntTarget(Player p) {
    return p.isOnline()
        && !p.isDead()
        && plugin.data().isEscaped(p.getUniqueId())
        && !plugin.haven().contains(p.getLocation())
        && !plugin.inPeaceRing(p.getLocation());
  }

  /**
   * Index of the closest point within {@code range} (Euclidean, 3D), or -1. Pure, package-visible
   * for tests. Boundary-inclusive: a target exactly at the range still counts. Plain coordinate
   * math: 26.2's {@code Location.distance} demands a non-null world.
   */
  static int nearestWithinIndex(List<Location> points, Location from, double range) {
    int best = -1;
    double bestDistSq = range * range;
    for (int i = 0; i < points.size(); i++) {
      double d = squaredDistance(from, points.get(i));
      if (d <= bestDistSq) {
        bestDistSq = d;
        best = i;
      }
    }
    return best;
  }

  private static double squaredDistance(Location a, Location b) {
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    double dz = a.getZ() - b.getZ();
    return dx * dx + dy * dy + dz * dz;
  }
}
