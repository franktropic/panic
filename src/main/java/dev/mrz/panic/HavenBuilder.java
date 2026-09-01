package dev.mrz.panic;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Flattens the haven at startup so it reads as a clean, lit, safe room: a solid floor at one level,
 * a glowing border ring on the edge, a glowing center marker, the ceiling cleared, and tree
 * canopies stripped above it so nothing floats over the room. Runs on the main thread during
 * onEnable; the spawn chunks are loaded by then.
 */
final class HavenBuilder {

  /** Blocks cleared above the floor so the haven has open space under any overhang. */
  private static final int CEILING_BLOCKS = 7;

  /** How far above the cleared ceiling tree material is still stripped. */
  private static final int TREE_STRIP_BLOCKS = 16;

  /** Tree material stripped above the haven. 26.2 has no Material.isLog()/isLeaves() helper. */
  private static final Set<Material> TREE_MATERIALS =
      EnumSet.of(
          Material.OAK_LEAVES,
          Material.SPRUCE_LEAVES,
          Material.BIRCH_LEAVES,
          Material.JUNGLE_LEAVES,
          Material.ACACIA_LEAVES,
          Material.DARK_OAK_LEAVES,
          Material.MANGROVE_LEAVES,
          Material.CHERRY_LEAVES,
          Material.PALE_OAK_LEAVES,
          Material.OAK_LOG,
          Material.SPRUCE_LOG,
          Material.BIRCH_LOG,
          Material.JUNGLE_LOG,
          Material.ACACIA_LOG,
          Material.DARK_OAK_LOG,
          Material.MANGROVE_LOG,
          Material.CHERRY_LOG,
          Material.PALE_OAK_LOG,
          Material.MANGROVE_ROOTS,
          Material.OAK_SAPLING,
          Material.SPRUCE_SAPLING,
          Material.BIRCH_SAPLING,
          Material.JUNGLE_SAPLING,
          Material.ACACIA_SAPLING,
          Material.DARK_OAK_SAPLING,
          Material.MANGROVE_PROPAGULE,
          Material.CHERRY_SAPLING,
          Material.PALE_OAK_SAPLING,
          Material.AZALEA,
          Material.FLOWERING_AZALEA);

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
        // The ceiling clear can cut a tree mid-trunk and leave its canopy floating above the box;
        // strip tree material higher up so the room always reads clean from above.
        for (int y = floorY + CEILING_BLOCKS;
            y < floorY + CEILING_BLOCKS + TREE_STRIP_BLOCKS;
            y++) {
          Block b = world.getBlockAt(x, y, z);
          if (TREE_MATERIALS.contains(b.getType())) {
            b.setType(Material.AIR);
          }
        }
      }
    }
    return floorY;
  }

  /**
   * Like {@link SurfaceUtil#findSurface} but anchors on the natural ground even when a slab from an
   * earlier build sits on top of it. Two cases, top to bottom: (1) air above natural solid ground —
   * the pristine case; (2) a floor/border slab directly on natural solid ground — a previous build
   * at the same level, whose surface is one block above the slab. A slab with air under it is
   * floating and keeps being ignored (case 1 finds the ground below it instead).
   */
  private static int findSurfaceIgnoring(
      World world, int x, int z, Material floor, Material border) {
    for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
      Block here = world.getBlockAt(x, y, z);
      Material type = here.getType();
      if (type.isAir()) {
        Block below = world.getBlockAt(x, y - 1, z);
        Material belowType = below.getType();
        if (belowType != floor && belowType != border && below.isSolid()) {
          return y;
        }
        continue;
      }
      if (type == floor || type == border) {
        Block below = world.getBlockAt(x, y - 1, z);
        Material belowType = below.getType();
        if (belowType != floor && belowType != border && below.isSolid()) {
          return y + 1;
        }
      }
    }
    return -1;
  }
}
