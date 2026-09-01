package dev.mrz.panic;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /top — the longest survival runs. */
public final class TopCommand implements CommandExecutor, TabCompleter {

  private final PanicPlugin plugin;

  public TopCommand(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    List<DataStore.Score> top = plugin.data().topScores(5);
    if (top.isEmpty()) {
      sender.sendMessage(plugin.prefix() + "No runs yet. Step out of the haven.");
      return true;
    }
    sender.sendMessage(plugin.prefix() + "Longest survival runs:");
    int rank = 1;
    for (DataStore.Score s : top) {
      sender.sendMessage(
          "  "
              + ChatColor.YELLOW
              + rank++
              + ". "
              + ChatColor.WHITE
              + s.name()
              + " "
              + ChatColor.GRAY
              + "— "
              + ChatColor.WHITE
              + RunTimer.format(s.bestSeconds()));
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    return List.of();
  }
}
