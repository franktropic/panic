package dev.mrz.panic;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Flattens the haven at startup so it reads as a clean, lit, safe room: a solid floor at one level,
 * a glowing border ring on the edge, a glowing center marker, and the ceiling cleared. Runs on the
 * main thread during onEnable; the spawn chunks are loaded by then.
 */
final class HavenBuilder {

  /** Blocks cleared above the floor so the haven has open space under any overhang. */
  private static final int CEILING_BLOCKS = 7;

  private HavenBuilder() {}

  /**
   * Flattens the haven region in place.
   *
   * @return the floor level (player feet y), or -1 when no usable surface was found
   */
  static int build(World world, HavenRegion haven, Material floor, Material border) {
    int half = haven.half();
    int cx = haven.centerX();
    int cz = haven.centerZ();
    // Anchor the floor to the surface of the spawn column so the haven sits at the level players
    // actually spawn on. Using the box maximum instead floats the floor over any dip in the box.
    // The scan ignores the floor/border materials so a slab left by a previous build cannot mask
    // the natural ground (the build stays idempotent even with a stale floating slab).
    int floorY = findSurfaceIgnoring(world, cx, cz, floor, border);
    if (floorY <= 0) {
      return -1;
    }
    for (int x = cx - half; x <= cx + half; x++) {
      for (int z = cz - half; z <= cz + half; z++) {
        boolean edge = x == cx - half || x == cx + half || z == cz - half || z == cz + half;
        boolean center = x == cx && z == cz;
        Material mat = (edge || center) ? border : floor;
        Block floorBlock = world.getBlockAt(x, floorY - 1, z);
        if (floorBlock.getType() != mat) {
          floorBlock.setType(mat);
        }
        for (int y = floorY; y < floorY + CEILING_BLOCKS; y++) {
          Block b = world.getBlockAt(x, y, z);
          if (b.getType().isAir() || b.isLiquid()) {
            continue;
          }
          b.setType(Material.AIR);
        }
      }
    }
    return floorY;
  }

  /**
   * Like {@link SurfaceUtil#findSurface} but treats the floor/border materials as transparent, so a
   * slab left by an earlier build does not read as the surface.
   */
  private static int findSurfaceIgnoring(
      World world, int x, int z, Material floor, Material border) {
    for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
      Block here = world.getBlockAt(x, y, z);
      if (here.getType().isAir()) {
        Block below = world.getBlockAt(x, y - 1, z);
        Material type = below.getType();
        if (type != floor && type != border && below.isSolid()) {
          return y;
        }
      }
    }
    return -1;
  }
}
