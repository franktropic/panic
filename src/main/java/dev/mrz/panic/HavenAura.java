package dev.mrz.panic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A ring of soft light along the haven border so the safe room reads as warded space from far away:
 * one area-effect cloud per perimeter step, each breathing glow particles just above the border
 * ring. Clouds persist in the chunk, so on startup any stale clouds near the haven are purged first
 * (idempotent across restarts); clouds also die with their chunk when nobody is around, and the
 * refresh task respawns them within a few seconds of the chunk coming back.
 */
final class HavenAura {

  /** Refresh cadence in ticks (5s). */
  private static final int REFRESH_TICKS = 100;

  /** Duration set on spawn, in ticks (120s); a cloud that expires is respawned by the refresh. */
  private static final int CLOUD_DURATION = 1200;

  private static final float CLOUD_RADIUS = 0.8f;

  /** Perimeter step in blocks between clouds. */
  private static final int PERIMETER_STEP = 2;

  private final JavaPlugin plugin;
  private final World world;
  private final HavenRegion haven;
  private final int floorY;
  private final List<int[]> spots;
  private final List<AreaEffectCloud> clouds;
  private int task;

  private HavenAura(JavaPlugin plugin, World world, HavenRegion haven, int floorY) {
    this.plugin = plugin;
    this.world = world;
    this.haven = haven;
    this.floorY = floorY;
    this.spots = perimeter(haven.centerX(), haven.centerZ(), haven.half(), PERIMETER_STEP);
    this.clouds = new ArrayList<>(Collections.nCopies(spots.size(), null));
    this.task = 0;
  }

  static HavenAura start(JavaPlugin plugin, World world, HavenRegion haven, int floorY) {
    HavenAura aura = new HavenAura(plugin, world, haven, floorY);
    aura.purgeStale();
    aura.respawnAll();
    aura.task =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimer(plugin, aura::refresh, REFRESH_TICKS, REFRESH_TICKS)
            .getTaskId();
    return aura;
  }

  /**
   * Perimeter positions of the square at ±half around (cx, cz), stepping every {@code step} blocks
   * along each side; each corner is emitted exactly once. Pure.
   */
  static List<int[]> perimeter(int cx, int cz, int half, int step) {
    List<int[]> out = new ArrayList<>();
    for (int x = cx - half; x <= cx + half; x += step) {
      out.add(new int[] {x, cz - half});
      out.add(new int[] {x, cz + half});
    }
    for (int z = cz - half + step; z < cz + half; z += step) {
      out.add(new int[] {cx - half, z});
      out.add(new int[] {cx + half, z});
    }
    return out;
  }

  /** Live cloud count for diagnostics. */
  int count() {
    int n = 0;
    for (AreaEffectCloud c : clouds) {
      if (c != null && c.isValid()) {
        n++;
      }
    }
    return n;
  }

  void stop() {
    plugin.getServer().getScheduler().cancelTask(task);
    for (AreaEffectCloud c : clouds) {
      if (c != null && c.isValid()) {
        c.remove();
      }
    }
    clouds.clear();
  }

  /** Splash-potion clouds persist in the chunk; clear any near the haven from a previous boot. */
  private void purgeStale() {
    int cx = haven.centerX();
    int cz = haven.centerZ();
    int reach = haven.half() + 4;
    for (AreaEffectCloud c : world.getEntitiesByClass(AreaEffectCloud.class)) {
      if (Math.abs(c.getLocation().getBlockX() - cx) <= reach
          && Math.abs(c.getLocation().getBlockZ() - cz) <= reach) {
        c.remove();
      }
    }
  }

  private void respawnAll() {
    for (int i = 0; i < spots.size(); i++) {
      int[] s = spots.get(i);
      if (world.isChunkLoaded(s[0] >> 4, s[1] >> 4)) {
        clouds.set(i, spawnCloud(s[0], s[1]));
      }
    }
  }

  /**
   * Re-arms expired clouds and respawns any lost to chunk unload (only where chunks are loaded).
   */
  private void refresh() {
    for (int i = 0; i < spots.size(); i++) {
      AreaEffectCloud c = clouds.get(i);
      if (c != null && c.isValid() && c.getDuration() >= CLOUD_DURATION / 2) {
        continue;
      }
      int[] s = spots.get(i);
      if (!world.isChunkLoaded(s[0] >> 4, s[1] >> 4)) {
        continue;
      }
      if (c != null && c.isValid()) {
        c.remove();
      }
      clouds.set(i, spawnCloud(s[0], s[1]));
    }
  }

  private AreaEffectCloud spawnCloud(int x, int z) {
    AreaEffectCloud c =
        world.spawn(new Location(world, x + 0.5, floorY + 1.0, z + 0.5), AreaEffectCloud.class);
    c.setParticle(Particle.GLOW);
    c.setRadius(CLOUD_RADIUS);
    c.setDuration(CLOUD_DURATION);
    c.setWaitTime(0);
    return c;
  }
}
