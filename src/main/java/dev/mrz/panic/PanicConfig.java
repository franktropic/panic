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
  public final int farRange;
  public final int midRange;
  public final int nearRange;
  public final int heartbeatRange;
  public final int silenceRange;
  public final int screamMinSec;
  public final int screamMaxSec;
  public final int falseAlarmPercent;
  public final int falseAlarmMinSec;
  public final int falseAlarmMaxSec;
  public final boolean tunnelEnabled;
  public final double tunnelHordeDigSpeed;
  public final double tunnelVanillaDigSpeed;
  public final int tunnelHealGraceSeconds;
  public final int peaceRadius;

  public PanicConfig(FileConfiguration c) {
    this.havenSize = c.getInt("spawn-haven.size", 24);
    this.peaceRadius = Math.max(8, Math.min(128, c.getInt("spawn-haven.peace-radius", 32)));
    this.kit = new ArrayList<>();
    for (KitEntry e : parseKitEntries(c.getStringList("spawn-haven.kit"))) {
      this.kit.add(new ItemStack(e.material(), e.amount()));
    }
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
    this.farRange = Math.max(1, c.getInt("dread.far-range", 80));
    this.midRange = Math.max(1, Math.min(c.getInt("dread.mid-range", 40), this.farRange));
    this.nearRange = Math.max(1, Math.min(c.getInt("dread.near-range", 15), this.midRange));
    this.heartbeatRange =
        Math.max(1, Math.min(c.getInt("dread.heartbeat-range", 20), this.farRange));
    this.silenceRange =
        Math.max(1, Math.min(c.getInt("dread.silence-range", 8), this.heartbeatRange));
    this.screamMinSec = Math.max(1, c.getInt("dread.scream-min-seconds", 20));
    this.screamMaxSec = Math.max(this.screamMinSec, c.getInt("dread.scream-max-seconds", 40));
    this.falseAlarmPercent = Math.min(100, Math.max(0, c.getInt("dread.false-alarm-percent", 25)));
    this.falseAlarmMinSec = Math.max(1, c.getInt("dread.false-alarm-min-seconds", 60));
    this.falseAlarmMaxSec =
        Math.max(this.falseAlarmMinSec, c.getInt("dread.false-alarm-max-seconds", 180));
    this.tunnelEnabled = c.getBoolean("tunnel.enabled", true);
    this.tunnelHordeDigSpeed = Math.max(0.1, c.getDouble("tunnel.horde-dig-speed", 1.0));
    this.tunnelVanillaDigSpeed =
        Math.max(0.1, Math.min(1.0, c.getDouble("tunnel.vanilla-dig-speed", 0.5)));
    this.tunnelHealGraceSeconds = Math.max(0, c.getInt("tunnel.dawn-heal-grace-seconds", 30));
  }

  /** A kit entry before Bukkit ItemStack creation (testable without a server). */
  public record KitEntry(Material material, int amount) {}

  /** Parses "ITEM" or "ITEM:qty" strings; bad entries are skipped. */
  static List<KitEntry> parseKitEntries(List<String> raw) {
    List<KitEntry> entries = new ArrayList<>();
    for (String entry : raw) {
      String[] parts = entry.split(":");
      Material material;
      try {
        material = Material.valueOf(parts[0].trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        continue;
      }
      int amount;
      try {
        amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
      } catch (NumberFormatException e) {
        continue;
      }
      if (amount > 0) {
        entries.add(new KitEntry(material, amount));
      }
    }
    return entries;
  }
}
