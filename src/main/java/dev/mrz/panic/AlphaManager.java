package dev.mrz.panic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Spawns and runs alphas and their hordes. Phase 1: night-only spawns on a 10 minute cadence,
 * population cap of one alpha per ever-joined player, 4 concurrent max, hordes capped at 40 total.
 * Alphas never idle: a 1 second task force-targets the nearest escaped player and nudges stuck
 * alphas forward.
 */
public final class AlphaManager implements Listener {

  public static final class Alpha {
    final UUID id = UUID.randomUUID();
    final Zombie zombie;
    final Set<UUID> horde = new HashSet<>();
    Location lastPos;
    int stuckSeconds;
    int offlineSeconds;
    UUID hunted;

    Alpha(Zombie zombie) {
      this.zombie = zombie;
      this.lastPos = zombie.getLocation().clone();
    }
  }

  public static final String META_ALPHA = "panic_alpha";
  public static final String META_HORDE = "panic_horde";

  private final PanicPlugin plugin;
  private final Map<UUID, Alpha> alphas = new HashMap<>();
  private final Map<UUID, Long> lastAlphaHit = new HashMap<>();
  private final HordeBudget budget;
  private final Random random = new Random();
  private long lastSpawnTick;

  public AlphaManager(PanicPlugin plugin) {
    this.plugin = plugin;
    this.budget =
        new HordeBudget(plugin.config().maxConcurrentAlphas, plugin.config().hordeMaxTotal);
    this.lastSpawnTick = -plugin.config().spawnIntervalMinutes * 1200L;
  }

  /** Called once per second by the plugin. */
  public void tick() {
    pruneDeadAlphas();
    for (Alpha alpha : new ArrayList<>(alphas.values())) {
      if (despawnIfHuntedOffline(alpha)) {
        continue;
      }
      Player target = currentTarget(alpha);
      if (target == null) {
        Player nearest = nearestHunt(alpha.zombie, plugin.config().detectRange);
        if (nearest != null) {
          alpha.zombie.setTarget(nearest);
        }
        target = currentTarget(alpha);
      }
      if (target != null) {
        alpha.hunted = target.getUniqueId();
      }
      if (!nudgeIfStuck(alpha, target)) {
        continue;
      }
      maintainHorde(alpha, target);
    }
    if (plugin.clock().isNight() && canSpawnNow()) {
      spawnAlpha(randomAnchor());
    }
  }

  /**
   * When an alpha's last hunt target logs off, the hunt ends: the alpha waits
   * alpha.offline-despawn-minutes, then burns away. Returns true when the alpha was despawned.
   */
  private boolean despawnIfHuntedOffline(Alpha alpha) {
    if (alpha.hunted == null) {
      return false;
    }
    Player hunted = Bukkit.getPlayer(alpha.hunted);
    if (hunted != null && hunted.isOnline()) {
      alpha.offlineSeconds = 0;
      return false;
    }
    alpha.offlineSeconds++;
    if (alpha.offlineSeconds >= plugin.config().alphaOfflineDespawnMinutes * 60) {
      killAlpha(alpha, null, "The hunt ends. Its prey is gone.");
      return true;
    }
    return false;
  }

  private boolean canSpawnNow() {
    long interval = plugin.config().spawnIntervalMinutes * 1200L;
    return Bukkit.getCurrentTick() - lastSpawnTick >= interval
        && budget.canSpawnAlpha(plugin.data().everJoinedCount());
  }

  /**
   * @return a valid hunt target, or null.
   */
  private Player currentTarget(Alpha alpha) {
    LivingEntity t = alpha.zombie.getTarget();
    if (t instanceof Player p && isHuntTarget(p)) {
      return p;
    }
    return null;
  }

  private boolean isHuntTarget(Player p) {
    return p.isOnline()
        && !p.isDead()
        && plugin.data().isEscaped(p.getUniqueId())
        && !plugin.haven().contains(p.getLocation())
        // Spawn is off-limits: an escaped player standing at the rejoin point is not fair game.
        && !plugin.inPeaceRing(p.getLocation());
  }

  private Player nearestHunt(Mob mob, double range) {
    Player best = null;
    double bestDist = range;
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (!isHuntTarget(p)) {
        continue;
      }
      double d = mob.getLocation().distance(p.getLocation());
      if (d <= bestDist) {
        bestDist = d;
        best = p;
      }
    }
    return best;
  }

  /**
   * Alphas never idle: if one barely moved while it has a target in range, nudge it forward. After
   * alpha.stuck-retarget-seconds of being stuck, force a retarget to re-roll the pathfinder; after
   * alpha.stuck-despawn-seconds, give up and despawn.
   *
   * @return false when the alpha was despawned for being permanently stuck
   */
  private boolean nudgeIfStuck(Alpha alpha, Player target) {
    Location here = alpha.zombie.getLocation();
    double dist = target == null ? Double.MAX_VALUE : here.distanceSquared(target.getLocation());
    boolean stuck = here.distanceSquared(alpha.lastPos) < 0.25;
    boolean inRange =
        dist > 1.0 && dist < plugin.config().detectRange * plugin.config().detectRange;
    alpha.lastPos = here.clone();
    if (target == null || !stuck || !inRange) {
      alpha.stuckSeconds = 0;
      return true;
    }
    PanicConfig cfg = plugin.config();
    alpha.stuckSeconds++;
    if (alpha.stuckSeconds >= cfg.alphaStuckDespawnSeconds) {
      killAlpha(alpha, null, "The alpha loses the trail.");
      return false;
    }
    if (alpha.stuckSeconds == cfg.alphaStuckRetargetSeconds) {
      alpha.zombie.setTarget(null);
      alpha.zombie.setTarget(target);
    }
    double power = alpha.stuckSeconds >= cfg.alphaStuckRetargetSeconds ? 1.0 : 0.5;
    Vector dir = target.getLocation().toVector().subtract(here.toVector());
    dir.setY(0);
    if (dir.lengthSquared() > 0.01) {
      dir.normalize().multiply(power);
      if (alpha.zombie.isOnGround()) {
        dir.setY(0.35);
      }
      alpha.zombie.setVelocity(dir);
    }
    return true;
  }

  private void maintainHorde(Alpha alpha, Player target) {
    pruneHorde(alpha);
    int alive = alpha.horde.size();
    if (alive < plugin.config().hordeSize && budget.canSpawnHorde()) {
      int toSpawn = Math.min(2, plugin.config().hordeSize - alive);
      for (int i = 0; i < toSpawn; i++) {
        spawnHordeMember(alpha, target);
      }
    }
    if (target != null) {
      for (UUID uuid : alpha.horde) {
        Entity e = Bukkit.getEntity(uuid);
        if (e instanceof Mob m) {
          m.setTarget(target);
        }
      }
    }
  }

  private void pruneHorde(Alpha alpha) {
    for (Iterator<UUID> it = alpha.horde.iterator(); it.hasNext(); ) {
      Entity e = Bukkit.getEntity(it.next());
      if (e == null || !e.isValid()) {
        it.remove();
        budget.removeHorde();
      }
    }
  }

  private void spawnHordeMember(Alpha alpha, Player target) {
    World world = alpha.zombie.getWorld();
    Location base = alpha.zombie.getLocation();
    for (int attempt = 0; attempt < 8; attempt++) {
      double ang = random.nextDouble() * 2 * Math.PI;
      double d = 4 + random.nextDouble() * 6;
      int bx = base.getBlockX() + (int) (Math.cos(ang) * d);
      int bz = base.getBlockZ() + (int) (Math.sin(ang) * d);
      int y = SurfaceUtil.findSurface(world, bx, bz);
      if (y < 0) {
        continue;
      }
      Location loc = new Location(world, bx + 0.5, y, bz + 0.5);
      if (plugin.haven().contains(loc)) {
        continue;
      }
      try {
        Zombie z = world.spawn(loc, Zombie.class);
        z.setCanPickupItems(false);
        z.setMetadata(META_HORDE, new FixedMetadataValue(plugin, alpha.id));
        if (target != null) {
          z.setTarget(target);
        }
        alpha.horde.add(z.getUniqueId());
        budget.addHorde();
      } catch (IllegalStateException e) {
        return;
      }
      return;
    }
  }

  /**
   * Spawns an alpha 40-80 blocks from the anchor player on valid surface, never in the haven.
   *
   * @return true when an alpha spawned
   */
  public boolean spawnAlpha(Player anchor) {
    if (anchor == null) {
      return false;
    }
    World world = anchor.getWorld();
    for (int attempt = 0; attempt < 12; attempt++) {
      double ang = random.nextDouble() * 2 * Math.PI;
      double d = 40 + random.nextDouble() * 40;
      int bx = anchor.getLocation().getBlockX() + (int) (Math.cos(ang) * d);
      int bz = anchor.getLocation().getBlockZ() + (int) (Math.sin(ang) * d);
      if (plugin.haven().containsBlock(bx, bz)) {
        continue;
      }
      int y = SurfaceUtil.findSurface(world, bx, bz);
      if (y < 0) {
        continue;
      }
      Location loc = new Location(world, bx + 0.5, y, bz + 0.5);
      if (plugin.haven().contains(loc)) {
        continue;
      }
      // An anchor near the ring edge can put a 40-80b spawn back inside the ring, where the peace
      // tick would immediately strip the alpha of its target and set it on fire.
      if (plugin.inPeaceRing(loc)) {
        continue;
      }
      doSpawnAlpha(loc);
      return true;
    }
    return false;
  }

  /** Spawns an alpha on the surface at (x, z). Admin/testing. @return true when spawned. */
  public boolean spawnAlphaAt(int x, int z) {
    World world = plugin.world();
    if (plugin.haven().containsBlock(x, z)) {
      return false;
    }
    int y = SurfaceUtil.findSurface(world, x, z);
    if (y < 0) {
      return false;
    }
    doSpawnAlpha(new Location(world, x + 0.5, y, z + 0.5));
    return true;
  }

  private void doSpawnAlpha(Location loc) {
    World world = loc.getWorld();
    PanicConfig cfg = plugin.config();
    Zombie z;
    try {
      z = world.spawn(loc, Zombie.class);
    } catch (IllegalStateException e) {
      return;
    }
    z.setPersistent(true);
    z.setRemoveWhenFarAway(false);
    z.setCanPickupItems(false);
    scale(z, Attribute.MAX_HEALTH, cfg.healthMult);
    scale(z, Attribute.ATTACK_DAMAGE, cfg.damageMult);
    scale(z, Attribute.MOVEMENT_SPEED, cfg.speedMult);
    setBase(z, Attribute.FOLLOW_RANGE, cfg.detectRange);
    setBase(z, Attribute.SCALE, cfg.scale);
    AttributeInstance health = z.getAttribute(Attribute.MAX_HEALTH);
    if (health != null) {
      z.setHealth(health.getValue());
    }
    if (cfg.alphaGlow) {
      z.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, -1, 0, false, false));
    }

    Alpha alpha = new Alpha(z);
    z.setMetadata(META_ALPHA, new FixedMetadataValue(plugin, alpha.id));
    alphas.put(alpha.id, alpha);
    budget.addAlpha();
    lastSpawnTick = Bukkit.getCurrentTick();

    world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
    plugin.broadcast("Something large moves in the dark.");
  }

  private void scale(LivingEntity e, Attribute a, double mult) {
    AttributeInstance ai = e.getAttribute(a);
    if (ai != null) {
      ai.setBaseValue(ai.getBaseValue() * mult);
    }
  }

  private void setBase(LivingEntity e, Attribute a, double value) {
    AttributeInstance ai = e.getAttribute(a);
    if (ai != null) {
      ai.setBaseValue(value);
    }
  }

  /** Nearest live alpha within range of the given location, or null. */
  public Alpha nearestAlphaTo(Location loc, double range) {
    Alpha best = null;
    double bestDist = range * range;
    for (Alpha alpha : alphas.values()) {
      double d = alpha.zombie.getLocation().distanceSquared(loc);
      if (d <= bestDist) {
        bestDist = d;
        best = alpha;
      }
    }
    return best;
  }

  @EventHandler
  public void onEntityDamage(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Zombie z
        && e.getEntity() instanceof Player p
        && z.hasMetadata(META_ALPHA)) {
      lastAlphaHit.put(p.getUniqueId(), (long) Bukkit.getCurrentTick());
    }
  }

  /** True once, when the player was last hit by an alpha within the last few seconds. */
  public boolean consumeAlphaHit(UUID player) {
    Long t = lastAlphaHit.remove(player);
    return t != null && Bukkit.getCurrentTick() - t <= 100L;
  }

  public Player randomAnchor() {
    List<Player> candidates = new ArrayList<>();
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (isHuntTarget(p)) {
        candidates.add(p);
      }
    }
    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.get(random.nextInt(candidates.size()));
  }

  public void killAllAtDawn() {
    for (Alpha alpha : new ArrayList<>(alphas.values())) {
      killAlpha(alpha, null, "Dawn burns the night away.");
    }
  }

  public void despawnAll() {
    for (Alpha alpha : new ArrayList<>(alphas.values())) {
      killAlpha(alpha, null, null);
    }
  }

  public int liveAlphas() {
    return alphas.size();
  }

  public int liveHorde() {
    return budget.horde();
  }

  private void pruneDeadAlphas() {
    for (Iterator<Map.Entry<UUID, Alpha>> it = alphas.entrySet().iterator(); it.hasNext(); ) {
      Alpha alpha = it.next().getValue();
      if (!alpha.zombie.isValid()) {
        it.remove();
        budget.removeAlpha();
        scatter(alpha);
      }
    }
  }

  /**
   * Breaks the link: hordes lose their target, scatter for a moment, then behave vanilla. Releases
   * the budget for every horde member still on the books.
   */
  private void scatter(Alpha alpha) {
    int remaining = alpha.horde.size();
    for (UUID uuid : alpha.horde) {
      Entity e = Bukkit.getEntity(uuid);
      if (e instanceof Mob m) {
        m.setTarget(null);
        Vector v = new Vector(random.nextGaussian(), 0.4, random.nextGaussian()).multiply(1.2);
        e.setVelocity(v);
      }
    }
    alpha.horde.clear();
    budget.releaseHorde(remaining);
  }

  private void killAlpha(Alpha alpha, Player killer, String dawnMessage) {
    alphas.remove(alpha.id);
    budget.removeAlpha();
    Location loc = alpha.zombie.getLocation();
    World world = loc.getWorld();
    world.spawnParticle(Particle.FLAME, loc, 40, 1.4, 1.8, 1.4, 0.05);
    world.spawnParticle(Particle.LARGE_SMOKE, loc, 20, 1.4, 1.8, 1.4, 0.0);
    world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.5f, 1.6f);
    if (dawnMessage != null) {
      plugin.broadcast(dawnMessage);
    } else if (killer != null) {
      plugin.broadcast(ChatColor.GREEN + killer.getName() + " slays the alpha.");
    }
    alpha.zombie.remove();
    scatter(alpha);
  }

  @EventHandler
  public void onEntityDeath(EntityDeathEvent e) {
    UUID id = e.getEntity().getUniqueId();
    for (Alpha alpha : alphas.values()) {
      if (alpha.zombie.getUniqueId().equals(id)) {
        killAlpha(alpha, e.getEntity().getKiller(), null);
        return;
      }
      if (alpha.horde.contains(id)) {
        alpha.horde.remove(id);
        budget.removeHorde();
      }
    }
  }
}
