package dev.mrz.panic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Per-player persistence: escaped flag, run start tick, best score. YAML file in
 * plugins/Panic/data.yml. The ever-joined set is derived from the file's player keys.
 */
public final class DataStore {

  public record Score(String name, long bestSeconds) {}

  private final File file;
  private YamlConfiguration data = new YamlConfiguration();

  public DataStore(File file) {
    this.file = file;
  }

  public void load() {
    if (file.exists()) {
      data = YamlConfiguration.loadConfiguration(file);
    }
  }

  public void save() {
    try {
      data.save(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private String path(UUID uuid) {
    return "players." + uuid;
  }

  /** Marks the player as joined; returns true the first time we see them. */
  public boolean markJoined(UUID uuid, String name) {
    String p = path(uuid);
    boolean isNew = data.get(p + ".name") == null;
    data.set(p + ".name", name);
    if (isNew) {
      data.set(p + ".escaped", false);
      data.set(p + ".run-start", -1L);
      data.set(p + ".best", 0L);
    }
    return isNew;
  }

  public boolean isEscaped(UUID uuid) {
    return data.getBoolean(path(uuid) + ".escaped", false);
  }

  public void setEscaped(UUID uuid, boolean escaped) {
    data.set(path(uuid) + ".escaped", escaped);
  }

  public long getRunStart(UUID uuid) {
    return data.getLong(path(uuid) + ".run-start", -1L);
  }

  public void setRunStart(UUID uuid, long tick) {
    data.set(path(uuid) + ".run-start", tick);
  }

  public long getBest(UUID uuid) {
    return data.getLong(path(uuid) + ".best", 0L);
  }

  public void setBest(UUID uuid, long bestSeconds) {
    data.set(path(uuid) + ".best", bestSeconds);
  }

  public String getName(UUID uuid) {
    return data.getString(path(uuid) + ".name", "???");
  }

  /** Seconds the player has been alive in the current run, or 0 when not running. */
  public long runSeconds(UUID uuid) {
    long start = getRunStart(uuid);
    if (start < 0) {
      return 0L;
    }
    return (Bukkit.getCurrentTick() - start) / 20L;
  }

  /** One alpha per player that has ever joined (brief population cap). */
  public int everJoinedCount() {
    ConfigurationSection players = data.getConfigurationSection("players");
    return players == null ? 0 : players.getKeys(false).size();
  }

  public List<Score> topScores(int limit) {
    ConfigurationSection players = data.getConfigurationSection("players");
    if (players == null) {
      return List.of();
    }
    List<Score> scores = new ArrayList<>();
    for (String key : players.getKeys(false)) {
      long best = players.getLong(key + ".best", 0L);
      if (best > 0) {
        scores.add(new Score(players.getString(key + ".name", "???"), best));
      }
    }
    scores.sort(Comparator.comparingLong(Score::bestSeconds).reversed());
    return scores.subList(0, Math.min(limit, scores.size()));
  }

  /**
   * @return name keys for tab completion.
   */
  public List<String> knownNames() {
    List<String> names = new ArrayList<>();
    ConfigurationSection players = data.getConfigurationSection("players");
    if (players == null) {
      return names;
    }
    for (String key : players.getKeys(false)) {
      names.add(players.getString(key + ".name", "???"));
    }
    return names;
  }
}
