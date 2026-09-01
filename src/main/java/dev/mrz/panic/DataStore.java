package dev.mrz.panic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Per-player persistence: escaped flag, run start tick, best score. YAML file in
 * plugins/Panic/data.yml. The ever-joined set is derived from the file's player keys.
 *
 * <p>Run clock pausing: while an escaped player is inside the haven the clock is paused. Cumulative
 * paused ticks per finished pause are persisted ({@code paused-ticks}); the in-flight pause start
 * is memory-only (a restart mid-pause just loses the current stretch, which is negligible next to
 * the boot re-anchor of the run itself).
 */
public final class DataStore {

  public record Score(String name, long bestSeconds) {}

  private final File file;
  private YamlConfiguration data = new YamlConfiguration();
  private final Map<UUID, Long> pausedSince = new HashMap<>();

  public DataStore(File file) {
    this.file = file;
  }

  public void load() {
    if (file.exists()) {
      data = YamlConfiguration.loadConfiguration(file);
    }
  }

  /**
   * Saves atomically: write to a sidecar file, then rename over the real one. A crash mid-save must
   * not leave a half-written data.yml that wipes the high scores.
   */
  public void save() {
    File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
    try {
      data.save(tmp);
      if (!tmp.renameTo(file)) {
        data.save(file);
      }
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
    return runSeconds(uuid, Bukkit.getCurrentTick());
  }

  /**
   * Run seconds excluding paused time (time spent inside the haven). Package-visible with an
   * explicit now for tests; the public overload uses the server tick.
   */
  long runSeconds(UUID uuid, long now) {
    long start = getRunStart(uuid);
    if (start < 0) {
      return 0L;
    }
    long paused = getPausedTicks(uuid);
    Long since = pausedSince.get(uuid);
    if (since != null) {
      paused += Math.max(0L, now - since);
    }
    // Clamped: a stale anchor from before a restart must never count backwards.
    return Math.max(0L, (now - start - paused) / 20L);
  }

  public long getPausedTicks(UUID uuid) {
    return data.getLong(path(uuid) + ".paused-ticks", 0L);
  }

  /** Cumulative paused ticks from finished pauses; death resets this along with the run. */
  public void setPausedTicks(UUID uuid, long ticks) {
    data.set(path(uuid) + ".paused-ticks", ticks);
  }

  /** True while the player is currently inside the haven with a live run. */
  public boolean isPaused(UUID uuid) {
    return pausedSince.containsKey(uuid);
  }

  /** Pauses the run clock when a player enters the haven. No-op without a live run. */
  public void pauseRun(UUID uuid) {
    pauseRun(uuid, Bukkit.getCurrentTick());
  }

  void pauseRun(UUID uuid, long now) {
    if (getRunStart(uuid) >= 0 && !pausedSince.containsKey(uuid)) {
      pausedSince.put(uuid, now);
    }
  }

  /**
   * Resumes the run clock when a player leaves the haven, folding the stretch into paused-ticks.
   */
  public void resumeRun(UUID uuid) {
    resumeRun(uuid, Bukkit.getCurrentTick());
  }

  void resumeRun(UUID uuid, long now) {
    Long since = pausedSince.remove(uuid);
    if (since == null) {
      return;
    }
    setPausedTicks(uuid, getPausedTicks(uuid) + Math.max(0L, now - since));
    save();
  }

  /** Drops an in-flight pause without counting it (death resets the run anyway). */
  public void discardPause(UUID uuid) {
    pausedSince.remove(uuid);
  }

  /**
   * Bukkit's tick counter resets to 0 on every restart, so a persisted run-start from the previous
   * boot would count negative. Re-anchor any in-progress run to this boot; the run itself (the
   * escaped flag) is kept, the clock just starts over.
   */
  public void resetStaleRunStarts() {
    ConfigurationSection players = data.getConfigurationSection("players");
    if (players == null) {
      return;
    }
    for (String key : players.getKeys(false)) {
      if (players.getLong(key + ".run-start", -1L) >= 0) {
        players.set(key + ".run-start", 0L);
      }
    }
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
