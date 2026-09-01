package dev.mrz.panic;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /panic: help for everyone, spawnalpha/despalphas/dawn/night/status for admins. */
public final class PanicCommand implements CommandExecutor {

  private final PanicPlugin plugin;

  public PanicCommand(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }
    String sub = args[0].toLowerCase();
    if (sub.equals("help")) {
      sendHelp(sender);
      return true;
    }
    if (!sender.hasPermission("panic.admin")) {
      sender.sendMessage(plugin.prefix() + "That part is for the keepers. Try /panic help.");
      return true;
    }
    switch (sub) {
      case "spawnalpha" -> {
        boolean ok;
        if (args.length >= 4 && args[1].equalsIgnoreCase("at")) {
          try {
            int x = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);
            ok = plugin.alphaManager().spawnAlphaAt(x, z);
          } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.prefix() + "Usage: /panic spawnalpha at <x> <z>");
            return true;
          }
        } else {
          Player anchor = sender instanceof Player p ? p : plugin.alphaManager().randomAnchor();
          ok = plugin.alphaManager().spawnAlpha(anchor);
        }
        sender.sendMessage(
            plugin.prefix() + (ok ? "An alpha stirs." : "No valid spawn point found."));
      }
      case "despalphas" -> {
        int n = plugin.alphaManager().liveAlphas();
        plugin.alphaManager().despawnAll();
        sender.sendMessage(plugin.prefix() + "Cleared " + n + " alpha(s).");
      }
      case "dawn" -> {
        plugin.clock().setPhase(TimeOfDay.Phase.DAY);
        sender.sendMessage(plugin.prefix() + "Dawn.");
      }
      case "night" -> {
        plugin.clock().setPhase(TimeOfDay.Phase.NIGHT);
        sender.sendMessage(plugin.prefix() + "Night falls.");
      }
      case "status" ->
          sender.sendMessage(
              plugin.prefix()
                  + "alphas="
                  + plugin.alphaManager().liveAlphas()
                  + " horde="
                  + plugin.alphaManager().liveHorde()
                  + " tunnels="
                  + plugin.tunnels().brokenCount()
                  + " phase="
                  + plugin.clock().phase());
      default -> sendUsage(sender);
    }
    return true;
  }

  private void sendUsage(CommandSender sender) {
    sender.sendMessage(
        plugin.prefix()
            + "Usage: /panic <help|spawnalpha|despalphas|dawn|night|status> (admin subcommands need panic.admin)");
  }

  /** How to play, for anyone. */
  private void sendHelp(CommandSender sender) {
    String p = plugin.prefix();
    sender.sendMessage(p + ChatColor.BOLD + "How to play");
    sender.sendMessage(
        p
            + "You wake in the "
            + ChatColor.AQUA
            + "haven"
            + ChatColor.GRAY
            + ". "
            + ChatColor.WHITE
            + "Leaving it starts your run"
            + ChatColor.GRAY
            + " — the gate shuts behind you and you get "
            + ChatColor.WHITE
            + "one life"
            + ChatColor.GRAY
            + ".");
    sender.sendMessage(
        p
            + "Your "
            + ChatColor.AQUA
            + "survival time"
            + ChatColor.GRAY
            + " is your score. Die and the run ends; your best is kept.");
    sender.sendMessage(
        p
            + "At "
            + ChatColor.WHITE
            + "night"
            + ChatColor.GRAY
            + ", a giant "
            + ChatColor.WHITE
            + "alpha zombie"
            + ChatColor.GRAY
            + " and its horde hunt you. They "
            + ChatColor.WHITE
            + "dig through walls"
            + ChatColor.GRAY
            + " toward you — dirt fast, stone slow, metal slowest.");
    sender.sendMessage(
        p
            + "Sounds are your map: "
            + ChatColor.WHITE
            + "screams"
            + ChatColor.GRAY
            + " mean something is hunting, the "
            + ChatColor.WHITE
            + "heartbeat"
            + ChatColor.GRAY
            + " means it is close, "
            + ChatColor.WHITE
            + "total silence"
            + ChatColor.GRAY
            + " means it is right on top of you.");
    sender.sendMessage(
        p
            + "Build, hide, and "
            + ChatColor.WHITE
            + "outlast the night"
            + ChatColor.GRAY
            + ". At "
            + ChatColor.AQUA
            + "dawn"
            + ChatColor.GRAY
            + " the walls heal and the alpha burns away.");
    sender.sendMessage(
        p + "Run " + ChatColor.AQUA + "/top" + ChatColor.GRAY + " for the leaderboard.");
  }
}
