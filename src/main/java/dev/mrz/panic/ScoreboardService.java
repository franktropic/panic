package dev.mrz.panic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Per-player sidebar scoreboard showing the current run and the all-time best. Uses team suffixes
 * so the lines can be rewritten in place every second without rebuilding scores.
 */
public final class ScoreboardService {

  private static final String TITLE = ChatColor.DARK_RED + "P A N I C";

  private final Map<UUID, Objective> bars = new HashMap<>();

  public void start(Player player) {
    if (bars.containsKey(player.getUniqueId())) {
      return;
    }
    Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
    Objective objective = sb.registerNewObjective("panic", "dummy", TITLE);
    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    sb.registerNewTeam("panic_run");
    sb.registerNewTeam("panic_best");
    player.setScoreboard(sb);
    bars.put(player.getUniqueId(), objective);
    update(player, 0L, 0L);
  }

  public void update(Player player, long runSeconds, long bestSeconds) {
    Objective objective = bars.get(player.getUniqueId());
    if (objective == null) {
      return;
    }
    Scoreboard sb = objective.getScoreboard();
    objective.getScore("panic_run").setScore(2);
    objective.getScore("panic_best").setScore(1);
    team(sb, "panic_run").setSuffix(ChatColor.YELLOW + RunTimer.format(runSeconds));
    team(sb, "panic_best").setSuffix(ChatColor.GRAY + RunTimer.format(bestSeconds));
  }

  private Team team(Scoreboard sb, String name) {
    Team t = sb.getTeam(name);
    return t == null ? sb.registerNewTeam(name) : t;
  }

  public void stop(Player player) {
    bars.remove(player.getUniqueId());
    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
  }

  public void clearAll() {
    for (Player p : Bukkit.getOnlinePlayers()) {
      stop(p);
    }
  }
}
