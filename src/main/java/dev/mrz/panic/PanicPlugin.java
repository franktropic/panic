package dev.mrz.panic;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Panic — alpha horde roguelike survival. Core loop (haven, high score, alphas with hordes,
 * shortened days) plus the dread pack (proximity warnings, screams, heartbeat, false alarms, fake
 * cracks). See PLAN.md for the full spec.
 */
public final class PanicPlugin extends JavaPlugin {

  private static PanicPlugin instance;

  public static PanicPlugin get() {
    return instance;
  }

  private PanicConfig config;
  private DataStore data;
  private World world;
  private HavenRegion haven;
  private DayNightClock clock;
  private AlphaManager alphaManager;
  private ScoreboardService scoreboard;
  private DreadService dread;
  private TunnelService tunnels;
  private HavenListener havenListener;

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();
    config = new PanicConfig(getConfig());
    data = new DataStore(new File(getDataFolder(), "data.yml"));
    data.load();

    if (getServer().getWorlds().isEmpty()) {
      getLogger().severe("No worlds loaded; disabling Panic.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    world = getServer().getWorlds().get(0);
    haven =
        new HavenRegion(
            world.getSpawnLocation().getBlockX(),
            world.getSpawnLocation().getBlockZ(),
            config.havenSize);

    scoreboard = new ScoreboardService();
    alphaManager = new AlphaManager(this);
    dread = new DreadService(this);
    tunnels = new TunnelService(this);
    clock =
        new DayNightClock(
            world,
            new TimeOfDay(config.dayLength, config.nightLength),
            alphaManager::killAllAtDawn);
    clock.addDawnListener(tunnels::dawnHeal);
    clock.start();

    havenListener = new HavenListener(this);
    getServer().getPluginManager().registerEvents(havenListener, this);
    getServer().getPluginManager().registerEvents(new DeathListener(this), this);
    getServer().getPluginManager().registerEvents(alphaManager, this);

    getServer().getScheduler().runTaskTimer(this, clock::tick, 1L, 1L);
    getServer().getScheduler().runTaskTimer(this, this::secondTick, 20L, 20L);

    PluginCommand top = getCommand("top");
    if (top != null) {
      TopCommand topCommand = new TopCommand(this);
      top.setExecutor(topCommand);
      top.setTabCompleter(topCommand);
    }
    PluginCommand panic = getCommand("panic");
    if (panic != null) {
      panic.setExecutor(new PanicCommand(this));
    }

    Logger log = getLogger();
    log.info(
        "Panic enabled: haven "
            + config.havenSize
            + "x"
            + config.havenSize
            + " (peace ring "
            + config.peaceRadius
            + "b), day "
            + config.dayLength
            + "t / night "
            + config.nightLength
            + "t, alpha every "
            + config.spawnIntervalMinutes
            + " min, horde "
            + config.hordeSize
            + " (cap "
            + config.hordeMaxTotal
            + "), tunneling "
            + (config.tunnelEnabled ? "on" : "off")
            + (config.tunnelEnabled ? " (heal grace " + config.tunnelHealGraceSeconds + "s)" : "")
            + ".");
  }

  private void secondTick() {
    alphaManager.tick();
    dread.tick();
    tunnels.tick();
    havenListener.peaceTick();
    for (Player p : getServer().getOnlinePlayers()) {
      UUID uuid = p.getUniqueId();
      scoreboard.update(p, data.runSeconds(uuid), data.getBest(uuid));
      if (haven.contains(p.getLocation()) && p.getFoodLevel() < 20) {
        p.setFoodLevel(20);
      }
    }
  }

  @Override
  public void onDisable() {
    if (alphaManager != null) {
      alphaManager.despawnAll();
    }
    if (scoreboard != null) {
      scoreboard.clearAll();
    }
    if (data != null) {
      data.save();
    }
    instance = null;
  }

  public PanicConfig config() {
    return config;
  }

  public DataStore data() {
    return data;
  }

  public World world() {
    return world;
  }

  public HavenRegion haven() {
    return haven;
  }

  /** The peace ring around spawn: while a player is home, the ring is off-limits to mobs. */
  public boolean inPeaceRing(Location loc) {
    HavenRegion h = haven;
    double r = config().peaceRadius;
    double dx = loc.getX() - (h.centerX() + 0.5);
    double dz = loc.getZ() - (h.centerZ() + 0.5);
    return dx * dx + dz * dz <= r * r;
  }

  public DayNightClock clock() {
    return clock;
  }

  public AlphaManager alphaManager() {
    return alphaManager;
  }

  public ScoreboardService scoreboard() {
    return scoreboard;
  }

  public DreadService dread() {
    return dread;
  }

  public TunnelService tunnels() {
    return tunnels;
  }

  public String prefix() {
    return "§8[§4Panic§8] §7";
  }

  public void broadcast(String legacyMessage) {
    getServer().broadcastMessage(prefix() + legacyMessage);
  }
}
