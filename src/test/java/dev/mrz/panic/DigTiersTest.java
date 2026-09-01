package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DigTiersTest {

  @Test
  void dirtAndSandTierIsOneSecond() {
    assertEquals(DigTiers.FAST, DigTiers.chewTicks(Material.DIRT));
    assertEquals(DigTiers.FAST, DigTiers.chewTicks(Material.SAND));
    assertEquals(DigTiers.FAST, DigTiers.chewTicks(Material.GRASS_BLOCK));
    assertEquals(DigTiers.FAST, DigTiers.chewTicks(Material.CLAY));
    assertEquals(DigTiers.FAST, DigTiers.chewTicks(Material.GRAVEL));
  }

  @Test
  void woodTierIsThreeSeconds() {
    assertEquals(DigTiers.WOOD, DigTiers.chewTicks(Material.OAK_LOG));
    assertEquals(DigTiers.WOOD, DigTiers.chewTicks(Material.SPRUCE_PLANKS));
    assertEquals(DigTiers.WOOD, DigTiers.chewTicks(Material.BAMBOO));
  }

  @Test
  void stoneTierIsSixSecondsAndIsTheDefault() {
    assertEquals(DigTiers.STONE, DigTiers.chewTicks(Material.STONE));
    assertEquals(DigTiers.STONE, DigTiers.chewTicks(Material.COBBLESTONE));
    assertEquals(DigTiers.STONE, DigTiers.chewTicks(Material.MOSSY_COBBLESTONE));
    assertEquals(DigTiers.STONE, DigTiers.chewTicks(Material.STONE_BRICKS));
    assertEquals(DigTiers.STONE, DigTiers.chewTicks(Material.GLASS));
  }

  @Test
  void metalTierIsFifteenSeconds() {
    assertEquals(DigTiers.METAL, DigTiers.chewTicks(Material.IRON_BLOCK));
    assertEquals(DigTiers.METAL, DigTiers.chewTicks(Material.GOLD_BLOCK));
    assertEquals(DigTiers.METAL, DigTiers.chewTicks(Material.COPPER_BLOCK));
    assertEquals(DigTiers.METAL, DigTiers.chewTicks(Material.NETHERITE_BLOCK));
    assertEquals(DigTiers.METAL, DigTiers.chewTicks(Material.ANVIL));
  }

  @Test
  void obsidianAndBedrockAreImmune() {
    assertEquals(DigTiers.IMMUNE, DigTiers.chewTicks(Material.OBSIDIAN));
    assertEquals(DigTiers.IMMUNE, DigTiers.chewTicks(Material.CRYING_OBSIDIAN));
    assertEquals(DigTiers.IMMUNE, DigTiers.chewTicks(Material.BEDROCK));
  }

  @Test
  void nonSolidsAreNotChewable() {
    assertEquals(0, DigTiers.chewTicks(Material.AIR));
    assertEquals(0, DigTiers.chewTicks(Material.WATER));
  }

  @Test
  void ironDoorIsNotWood() {
    // An iron door must not fall into the wood tier just because it ends in _DOOR.
    assertNotEquals(DigTiers.WOOD, DigTiers.chewTicks(Material.IRON_DOOR));
  }
}
