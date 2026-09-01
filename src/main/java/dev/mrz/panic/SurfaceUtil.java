package dev.mrz.panic;

import org.bukkit.World;
import org.bukkit.block.Block;

/** Small world helpers shared by spawning code. */
public final class SurfaceUtil {

  private SurfaceUtil() {}

  /**
   * Finds the highest y where a mob can stand at (x, z): an air block with a solid block below.
   * Returns -1 when no valid surface exists (e.g. over the void).
   */
  public static int findSurface(World world, int x, int z) {
    for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
      Block here = world.getBlockAt(x, y, z);
      if (here.getType().isAir()) {
        Block below = world.getBlockAt(x, y - 1, z);
        if (below.isSolid()) {
          return y;
        }
      }
    }
    return -1;
  }
}
