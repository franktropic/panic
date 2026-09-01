package dev.mrz.panic;

import org.bukkit.Location;

/**
 * The square safe region around world spawn. Pure geometry: the caller supplies coordinates,
 * nothing here touches the server.
 */
public final class HavenRegion {

  private final int centerX;
  private final int centerZ;
  private final int half;

  public HavenRegion(int centerBlockX, int centerBlockZ, int sizeBlocks) {
    if (sizeBlocks < 4) {
      throw new IllegalArgumentException("haven size must be at least 4 blocks");
    }
    this.centerX = centerBlockX;
    this.centerZ = centerBlockZ;
    this.half = sizeBlocks / 2;
  }

  public boolean containsBlock(int x, int z) {
    return Math.abs(x - centerX) <= half && Math.abs(z - centerZ) <= half;
  }

  public boolean contains(Location location) {
    return containsBlock(location.getBlockX(), location.getBlockZ());
  }

  public int centerX() {
    return centerX;
  }

  public int centerZ() {
    return centerZ;
  }

  public int half() {
    return half;
  }

  public int size() {
    return half * 2;
  }

  /**
   * A point guaranteed to be just outside the region (used for escaped players who rejoin),
   * searching +x, -x, +z, -z beyond the border.
   */
  public int[] outsidePoint() {
    return new int[] {centerX + half + 2, centerZ};
  }
}
