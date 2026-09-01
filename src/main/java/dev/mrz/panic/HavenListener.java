package dev.mrz.panic;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/**
 * The spawn haven: one-way exit, re-entry knockback, no mob damage or hunger inside, no monster
 * spawns, basic kit for new players.
 */
public final class HavenListener implements Listener {

  private final PanicPlugin plugin;

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
        p.sendMessage(plugin.prefix() + "The gate is shut. The haven is behind you.");
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

  @EventHandler
  public void onCreatureSpawn(CreatureSpawnEvent e) {
    if (e.getEntity() instanceof Monster && plugin.haven().contains(e.getLocation())) {
      e.setCancelled(true);
    }
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
