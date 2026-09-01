package dev.mrz.panic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.util.Vector;

/**
 * The spawn haven: one-way exit, re-entry knockback, no mob damage or hunger inside, no monster
 * spawns, an unbreakable bedrock-to-sky column, basic kit for new players, safe respawn placement.
 */
public final class HavenListener implements Listener {

  /**
   * Re-entry gate message cooldown, ticks (5s) so it cannot spam while a player is held at the
   * edge.
   */
  private static final long GATE_MESSAGE_COOLDOWN = 100L;

  private final PanicPlugin plugin;
  private final Map<UUID, Long> lastGateMessage = new HashMap<>();

  public HavenListener(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    boolean isNew = plugin.data().markJoined(p.getUniqueId(), p.getName());
    plugin.data().save();
    plugin.scoreboard().start(p);
    if (!plugin.data().isEscaped(p.getUniqueId())) {
      if (isNew) {
        for (org.bukkit.inventory.ItemStack item : plugin.config().kit) {
          p.getInventory().addItem(item);
        }
      }
      p.teleport(havenCenter());
      p.sendMessage(plugin.prefix() + "You wake in the haven. The door behind you locks.");
    } else {
      p.teleport(rejoinPoint());
      p.sendMessage(plugin.prefix() + "The haven is behind you. It will not open again.");
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    plugin.scoreboard().stop(e.getPlayer());
    lastGateMessage.remove(e.getPlayer().getUniqueId());
  }

  /**
   * After death, a new run starts: respawn on the flat haven floor (never inside a block) and hand
   * out a fresh kit.
   */
  @EventHandler
  public void onRespawn(PlayerRespawnEvent e) {
    Player p = e.getPlayer();
    if (!plugin.data().isEscaped(p.getUniqueId())) {
      e.setRespawnLocation(havenCenter());
    }
    for (org.bukkit.inventory.ItemStack item : plugin.config().kit) {
      p.getInventory().addItem(item.clone());
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onMove(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    Location from = e.getFrom();
    Location to = e.getTo();
    if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
      return;
    }
    HavenRegion haven = plugin.haven();
    boolean fromIn = haven.containsBlock(from.getBlockX(), from.getBlockZ());
    boolean toIn = haven.containsBlock(to.getBlockX(), to.getBlockZ());
    if (fromIn == toIn) {
      return;
    }
    if (!fromIn && toIn) {
      if (plugin.data().isEscaped(p.getUniqueId())) {
        e.setCancelled(true);
        knockBackToCenter(p, from);
        long now = org.bukkit.Bukkit.getCurrentTick();
        Long last = lastGateMessage.get(p.getUniqueId());
        if (last == null || now - last >= GATE_MESSAGE_COOLDOWN) {
          lastGateMessage.put(p.getUniqueId(), now);
          p.sendMessage(plugin.prefix() + "The gate is shut. The haven is behind you.");
        }
      }
    } else if (!plugin.data().isEscaped(p.getUniqueId())) {
      plugin.data().setEscaped(p.getUniqueId(), true);
      plugin.data().setRunStart(p.getUniqueId(), org.bukkit.Bukkit.getCurrentTick());
      plugin.data().save();
      p.sendMessage(plugin.prefix() + "You step out into the dark. The clock starts.");
    }
  }

  @EventHandler
  public void onDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || !plugin.haven().contains(p.getLocation())) {
      return;
    }
    EntityDamageEvent.DamageCause cause = e.getCause();
    if (cause == EntityDamageEvent.DamageCause.STARVATION) {
      e.setCancelled(true);
      return;
    }
    org.bukkit.damage.DamageSource source = e.getDamageSource();
    if (source == null) {
      return;
    }
    if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
        || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
      if (source.getDirectEntity() instanceof LivingEntity) {
        e.setCancelled(true);
      }
      return;
    }
    if (cause == EntityDamageEvent.DamageCause.PROJECTILE
        && source.getCausingEntity() instanceof LivingEntity) {
      e.setCancelled(true);
    }
  }

  /** The haven column is unbreakable and unplaceable, from bedrock to the sky. */
  @EventHandler
  public void onBlockBreak(BlockBreakEvent e) {
    if (plugin.haven().containsBlock(e.getBlock().getX(), e.getBlock().getZ())) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent e) {
    if (plugin.haven().containsBlock(e.getBlock().getX(), e.getBlock().getZ())) {
      e.setCancelled(true);
    }
  }

  /** Explosions (creepers and friends) still damage entities but cannot break haven blocks. */
  @EventHandler
  public void onExplode(EntityExplodeEvent e) {
    HavenRegion haven = plugin.haven();
    e.blockList().removeIf(b -> haven.containsBlock(b.getX(), b.getZ()));
  }

  /** Endermen may not pick up or swap blocks inside the haven column. */
  @EventHandler
  public void onEntityChangeBlock(EntityChangeBlockEvent e) {
    if (plugin.haven().containsBlock(e.getBlock().getX(), e.getBlock().getZ())) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent e) {
    if (!(e.getEntity() instanceof Monster)) {
      return;
    }
    Location loc = e.getLocation();
    if (plugin.haven().contains(loc)) {
      e.setCancelled(true);
      return;
    }
    // While a player is home, no monsters spawn within the peace ring.
    if (playerInPeaceRing() && plugin.inPeaceRing(loc)) {
      e.setCancelled(true);
    }
  }

  /**
   * Runs every second: while a player is home, monsters inside the peace ring lose their target and
   * catch fire instead of camping the haven gate.
   */
  public void peaceTick() {
    if (!playerInPeaceRing()) {
      return;
    }
    HavenRegion haven = plugin.haven();
    World world = plugin.world();
    // Center the scan at the surface elevation so it covers ground-level mobs, not just a slab
    // around y=0 (the old fixed y=0 + 32 radius missed the entire surface layer).
    int surfaceY = SurfaceUtil.findSurface(world, haven.centerX(), haven.centerZ());
    if (surfaceY < 0) {
      surfaceY = 0;
    }
    Location center = new Location(world, haven.centerX() + 0.5, surfaceY, haven.centerZ() + 0.5);
    double r = plugin.config().peaceRadius;
    for (Entity e : world.getNearbyEntities(center, r, 96, r)) {
      if (e instanceof Monster m) {
        m.setTarget(null);
        // 2s of fire, renewed every second while it stays in the ring.
        if (m.getFireTicks() < 40) {
          m.setFireTicks(40);
        }
      }
    }
  }

  /** While a player is home, monsters in the peace ring may not (re-)target players. */
  @EventHandler
  public void onTarget(EntityTargetEvent e) {
    if (!(e.getEntity() instanceof Monster m) || !(e.getTarget() instanceof Player)) {
      return;
    }
    if (playerInPeaceRing() && plugin.inPeaceRing(m.getLocation())) {
      e.setTarget(null);
    }
  }

  private boolean playerInPeaceRing() {
    for (Player p : plugin.getServer().getOnlinePlayers()) {
      if (plugin.inPeaceRing(p.getLocation())) {
        return true;
      }
    }
    return false;
  }

  private void knockBackToCenter(Player p, Location from) {
    HavenRegion haven = plugin.haven();
    Vector dir =
        new Vector(from.getX() - (haven.centerX() + 0.5), 0, from.getZ() - (haven.centerZ() + 0.5));
    if (dir.lengthSquared() < 0.01) {
      dir = new Vector(1, 0, 0);
    }
    p.setVelocity(dir.normalize().multiply(1.6).setY(0.35));
    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
  }

  private Location havenCenter() {
    HavenRegion haven = plugin.haven();
    World world = plugin.world();
    int y = SurfaceUtil.findSurface(world, haven.centerX(), haven.centerZ());
    if (y < 0) {
      return world.getSpawnLocation();
    }
    return new Location(world, haven.centerX() + 0.5, y, haven.centerZ() + 0.5);
  }

  private Location rejoinPoint() {
    HavenRegion haven = plugin.haven();
    World world = plugin.world();
    int[][] offsets = {
      {haven.half() + 2, 0}, {-haven.half() - 2, 0}, {0, haven.half() + 2}, {0, -haven.half() - 2}
    };
    for (int[] o : offsets) {
      int bx = haven.centerX() + o[0];
      int bz = haven.centerZ() + o[1];
      int y = SurfaceUtil.findSurface(world, bx, bz);
      if (y >= 0) {
        return new Location(world, bx + 0.5, y, bz + 0.5);
      }
    }
    return havenCenter();
  }
}
