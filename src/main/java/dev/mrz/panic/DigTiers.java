package dev.mrz.panic;

import org.bukkit.Material;

/**
 * Block resistance for zombie digging (brief: tunneling). Chewing a block takes a fixed amount of
 * shared work no matter how many zombies gnaw on it at once. Obsidian and bedrock are immune.
 *
 * <p>Tier targets from the brief: dirt and sand ~1s, wood ~3s, cobblestone and stone ~6s, iron and
 * metal ~15s. 26.2 paper-api has no material groups, so classification is by material name.
 */
public final class DigTiers {

  /** One second of chewing, in ticks (brief: dirt and sand ~1s). */
  public static final int FAST = 20;

  /** Brief: wood ~3s. */
  public static final int WOOD = 60;

  /** Brief: cobblestone and stone ~6s. Also the default for any other diggable solid. */
  public static final int STONE = 120;

  /** Brief: iron and metal blocks ~15s. */
  public static final int METAL = 300;

  /** Immune materials: never chewed through. */
  public static final int IMMUNE = 0;

  private DigTiers() {}

  /**
   * Ticks of shared chewing needed to break this block. 0 means the block is not chewable (air,
   * fluid, or immune). Pure name logic so it can be unit tested without a live server; the runtime
   * additionally skips non-solid blocks (torches, plants) via {@code Material.isSolid()}.
   */
  public static int chewTicks(Material material) {
    if (material == null) {
      return 0;
    }
    String n = material.name();
    if (isAirOrFluid(n)) {
      return 0;
    }
    if (n.contains("OBSIDIAN") || n.equals("BEDROCK")) {
      return IMMUNE;
    }
    if (isDirtLike(n)) {
      return FAST;
    }
    if (isWood(n)) {
      return WOOD;
    }
    if (isMetal(n)) {
      return METAL;
    }
    return STONE;
  }

  static boolean isAirOrFluid(String n) {
    return n.equals("AIR")
        || n.equals("CAVE_AIR")
        || n.equals("VOID_AIR")
        || n.contains("WATER")
        || n.contains("LAVA");
  }

  static boolean isDirtLike(String n) {
    return n.contains("DIRT")
        || n.contains("SAND")
        || n.contains("GRASS")
        || n.equals("CLAY")
        || n.equals("GRAVEL")
        || n.equals("MUD");
  }

  static boolean isWood(String n) {
    return n.contains("LOG")
        || n.contains("PLANKS")
        || n.contains("BAMBOO")
        || n.endsWith("_WOOD")
        || (n.contains("_DOOR") && !n.startsWith("IRON"));
  }

  static boolean isMetal(String n) {
    return n.startsWith("IRON")
        || n.startsWith("GOLD")
        || n.startsWith("COPPER")
        || n.startsWith("SILVER")
        || n.startsWith("NETHERITE")
        || n.startsWith("ANVIL")
        || n.equals("CHAIN");
  }
}
