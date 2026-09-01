package dev.mrz.panic;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/** Typed view over config.yml. Every number in the brief lives here. */
public final class PanicConfig {

  public final int havenSize;
  public final List<ItemStack> kit;
  public final int dayLength;
  public final int nightLength;
  public final EntityType alphaType;
  public final double healthMult;
  public final double damageMult;
  public final double speedMult;
  public final double scale;
  public final double detectRange;
  public final int spawnIntervalMinutes;
  public final int maxConcurrentAlphas;
  public final int hordeSize;
  public final int hordeMaxTotal;
  public final int deathKeepsPercent;

  public PanicConfig(FileConfiguration c) {
    this.havenSize = c.getInt("spawn-haven.size", 24);
    this.kit = parseKit(c.getStringList("spawn-haven.kit"));
    this.dayLength = c.getInt("day-night.day-length-ticks", 6000);
    this.nightLength = c.getInt("day-night.night-length-ticks", 12000);
    this.alphaType = EntityType.valueOf(c.getString("alpha.type", "ZOMBIE").trim().toUpperCase());
    this.healthMult = c.getDouble("alpha.health-multiplier", 5.0);
    this.damageMult = c.getDouble("alpha.damage-multiplier", 3.0);
    this.speedMult = c.getDouble("alpha.speed-multiplier", 0.5);
    this.scale = c.getDouble("alpha.scale", 2.5);
    this.detectRange = c.getDouble("alpha.detect-range", 100.0);
    this.spawnIntervalMinutes = c.getInt("alpha.spawn-interval-minutes", 10);
    this.maxConcurrentAlphas = c.getInt("alpha.max-concurrent", 4);
    this.hordeSize = c.getInt("horde.size", 8);
    this.hordeMaxTotal = c.getInt("horde.max-total", 40);
    this.deathKeepsPercent = c.getInt("highscore.death-keeps-percent", 25);
  }

  /** Parses "ITEM" or "ITEM:qty" strings into stack items; bad entries are skipped. */
  static List<ItemStack> parseKit(List<String> raw) {
    List<ItemStack> kit = new ArrayList<>();
    for (String entry : raw) {
      String[] parts = entry.split(":");
      Material material;
      try {
        material = Material.valueOf(parts[0].trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        continue;
      }
      int amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
      if (amount > 0) {
        kit.add(new ItemStack(material, amount));
      }
    }
    return kit;
  }
}
