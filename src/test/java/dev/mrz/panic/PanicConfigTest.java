package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PanicConfigTest {

  private static PanicConfig config(String extra) {
    // No kit here: ItemStack creation needs a live Registry (server). Kit parsing is tested
    // separately via parseKitEntries.
    String base =
        """
        spawn-haven:
          size: 24
        day-night:
          day-length-ticks: 6000
          night-length-ticks: 12000
        alpha:
          type: ZOMBIE
        horde:
          size: 8
          max-total: 40
        """;
    YamlConfiguration c = new YamlConfiguration();
    try {
      c.loadFromString(base + extra);
    } catch (InvalidConfigurationException e) {
      throw new AssertionError(e);
    }
    return new PanicConfig(c);
  }

  @Test
  void dreadDefaults() {
    PanicConfig c = config("");
    assertEquals(80, c.farRange);
    assertEquals(40, c.midRange);
    assertEquals(15, c.nearRange);
    assertEquals(20, c.heartbeatRange);
    assertEquals(8, c.silenceRange);
    assertEquals(20, c.screamMinSec);
    assertEquals(40, c.screamMaxSec);
    assertEquals(25, c.falseAlarmPercent);
    assertEquals(60, c.falseAlarmMinSec);
    assertEquals(180, c.falseAlarmMaxSec);
  }

  @Test
  void dreadRangesStayMonotonic() {
    PanicConfig c =
        config(
            """
            dread:
              far-range: 100
              mid-range: 150
              near-range: 200
              heartbeat-range: 999
              silence-range: 500
            """);
    assertEquals(100, c.farRange);
    assertEquals(100, c.midRange, "mid clamps to far");
    assertEquals(100, c.nearRange, "near clamps to mid");
    assertEquals(100, c.heartbeatRange, "heartbeat clamps to far");
    assertEquals(100, c.silenceRange, "silence clamps to heartbeat");
  }

  @Test
  void windowsStaySane() {
    PanicConfig c =
        config(
            """
            dread:
              scream-min-seconds: 30
              scream-max-seconds: 10
              false-alarm-percent: 150
              false-alarm-min-seconds: 120
              false-alarm-max-seconds: 30
            """);
    assertEquals(30, c.screamMaxSec, "max bumps up to min");
    assertEquals(100, c.falseAlarmPercent, "percent caps at 100");
    assertEquals(120, c.falseAlarmMaxSec, "max bumps up to min");
  }

  @Test
  void kitParsingSkipsBadEntries() {
    List<PanicConfig.KitEntry> entries =
        PanicConfig.parseKitEntries(
            List.of(
                "IRON_SWORD", "COBBLESTONE:16", "bread:8", "NOT_A_BLOCK", "GOLD:0", "TORCH:abc"));
    assertEquals(3, entries.size());
    assertEquals(new PanicConfig.KitEntry(Material.IRON_SWORD, 1), entries.get(0));
    assertEquals(new PanicConfig.KitEntry(Material.COBBLESTONE, 16), entries.get(1));
    assertEquals(new PanicConfig.KitEntry(Material.BREAD, 8), entries.get(2));
  }
}
