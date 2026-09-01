package dev.mrz.panic;

import java.util.List;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/** Ends the survival run on death: announce the run, keep the best, reset the haven flag. */
public final class DeathListener implements Listener {

  private final PanicPlugin plugin;
  private final Random rng = new Random();

  public DeathListener(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    Player p = e.getEntity();
    applyDeathKeep(e);
    if (plugin.alphaManager().consumeAlphaHit(p.getUniqueId())) {
      p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
      plugin.broadcast(ChatColor.DARK_RED + "The alpha took " + p.getName() + " down.");
    }
    long start = plugin.data().getRunStart(p.getUniqueId());
    if (start < 0) {
      return;
    }
    long seconds = (org.bukkit.Bukkit.getCurrentTick() - start) / 20L;
    long best = Math.max(plugin.data().getBest(p.getUniqueId()), seconds);
    plugin.data().setBest(p.getUniqueId(), best);
    plugin.data().setRunStart(p.getUniqueId(), -1L);
    plugin.data().setEscaped(p.getUniqueId(), false);
    plugin.data().discardPause(p.getUniqueId());
    plugin.data().setPausedTicks(p.getUniqueId(), 0L);
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

  /**
   * Softens the death wipe: keeps {@code highscore.death-keeps-percent}% of the dropped items and
   * XP in the player's inventory (each item unit is an independent coin flip); the rest drops at
   * the grave as usual. 0 = full vanilla wipe.
   */
  private void applyDeathKeep(PlayerDeathEvent e) {
    int percent = plugin.config().deathKeepsPercent;
    if (percent <= 0) {
      return;
    }
    double chance = Math.min(100, percent) / 100.0;
    List<ItemStack> drops = e.getDrops();
    for (int i = drops.size() - 1; i >= 0; i--) {
      ItemStack stack = drops.get(i);
      int amt = stack.getAmount();
      int keep = countKept(rng, amt, chance);
      if (keep == 0) {
        continue;
      }
      if (keep >= amt) {
        e.getItemsToKeep().add(stack);
        drops.remove(i);
        continue;
      }
      ItemStack kept = stack.clone();
      kept.setAmount(keep);
      stack.setAmount(amt - keep);
      e.getItemsToKeep().add(kept);
    }
    e.setDroppedExp((int) Math.round(e.getDroppedExp() * chance));
  }

  /** Pure: how many of {@code amt} item units survive the death, each an independent coin flip. */
  static int countKept(Random rng, int amt, double chance) {
    if (chance >= 1.0) {
      return amt;
    }
    if (chance <= 0.0) {
      return 0;
    }
    int kept = 0;
    for (int i = 0; i < amt; i++) {
      if (rng.nextDouble() < chance) {
        kept++;
      }
    }
    return kept;
  }
}
