package dev.mrz.panic;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Ends the survival run on death: announce the run, keep the best, reset the haven flag. */
public final class DeathListener implements Listener {

  private final PanicPlugin plugin;

  public DeathListener(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    Player p = e.getEntity();
    long start = plugin.data().getRunStart(p.getUniqueId());
    if (start < 0) {
      return;
    }
    long seconds = (org.bukkit.Bukkit.getCurrentTick() - start) / 20L;
    long best = Math.max(plugin.data().getBest(p.getUniqueId()), seconds);
    plugin.data().setBest(p.getUniqueId(), best);
    plugin.data().setRunStart(p.getUniqueId(), -1L);
    plugin.data().setEscaped(p.getUniqueId(), false);
    plugin.data().save();
    plugin.broadcast(
        ChatColor.WHITE
            + p.getName()
            + "§7's run ended at §f"
            + RunTimer.format(seconds)
            + "§7. Best: §f"
            + RunTimer.format(best)
            + "§7.");
  }
}
