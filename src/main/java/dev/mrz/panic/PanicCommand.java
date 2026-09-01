package dev.mrz.panic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /panic admin: spawnalpha, despalphas, dawn, night, status. */
public final class PanicCommand implements CommandExecutor {

  private final PanicPlugin plugin;

  public PanicCommand(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sendUsage(sender);
      return true;
    }
    switch (args[0].toLowerCase()) {
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
                  + " phase="
                  + plugin.clock().phase());
      default -> sendUsage(sender);
    }
    return true;
  }

  private void sendUsage(CommandSender sender) {
    sender.sendMessage(plugin.prefix() + "Usage: /panic <spawnalpha|despalphas|dawn|night|status>");
  }
}
