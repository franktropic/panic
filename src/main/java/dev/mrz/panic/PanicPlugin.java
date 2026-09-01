package dev.mrz.panic;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Panic — alpha horde roguelike survival.
 *
 * <p>Skeleton plugin. See PLAN.md for the full spec and build order.
 */
public final class PanicPlugin extends JavaPlugin {

  private static PanicPlugin instance;

  public static PanicPlugin get() {
    return instance;
  }

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();
    getLogger().info("Panic enabled (skeleton). Build order in PLAN.md, phase 1: core loop.");
  }

  @Override
  public void onDisable() {
    instance = null;
  }
}
