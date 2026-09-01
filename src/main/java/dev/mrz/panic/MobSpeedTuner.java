package dev.mrz.panic;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Paces creepers to a configurable base movement speed. Vanilla creepers run 0.25, 9% faster than
 * the 0.23 zombie baseline, which reads as "speed creepers"; the default locks them to the zombie
 * pace so every mob on the server moves at the same 1x baseline.
 */
public final class MobSpeedTuner implements Listener {

  private final PanicPlugin plugin;

  public MobSpeedTuner(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onSpawn(CreatureSpawnEvent e) {
    if (!(e.getEntity() instanceof Creeper c)) {
      return;
    }
    double speed = plugin.config().creeperSpeed;
    if (speed <= 0) {
      return; // 0 = leave vanilla as-is
    }
    AttributeInstance attr = c.getAttribute(Attribute.MOVEMENT_SPEED);
    if (attr != null) {
      attr.setBaseValue(speed);
    }
  }
}
