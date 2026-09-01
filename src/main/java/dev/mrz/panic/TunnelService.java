package dev.mrz.panic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Zombie tunneling (brief: tunneling). A zombie that is blocked while hunting applies shared chew
 * work to the solid block ahead of it once per second, with crack particles for anyone nearby, and
 * breaks the block without drops when the accumulated work meets the material's tier. Alphas do not
 * dig (Snake's split): they loom outside while the horde chews through walls. Vanilla zombies dig
 * too, at half the horde dig speed.
 *
 * <p>Broken blocks are recorded with their break tick and heal at dawn: blocks broken before the
 * grace window are restored with a particle puff, blocks broken within it stay broken so escapes
 * are not walled off (they heal next dawn instead).
 *
 * <p>Known prototype risks (brief): stuck loops and self-drowning. Guards in place: digging only
 * starts after two stationary seconds, work is shared per block so packs dig together, and a block
 * covered by water is never chewed (keeps pools from flooding the digger).
 */
public final class TunnelService {

  /** How far ahead of the digger's eye a blocking block may be, blocks. */
  private static final double RAY_RANGE = 2.5;

  /** Squared distance: moved less than ~0.5 blocks in a second counts as stationary. */
  private static final double STUCK_SQ = 0.25;

  /** Consecutive stationary seconds before a blocked zombie starts chewing. */
  private static final int STUCK_SECONDS_TO_DIG = 2;

  /** Break particles are only worth sending when someone can see them. */
  private static final int PARTICLE_VIEW_DISTANCE = 16;

  /** Broken block coordinate key. */
  private record Key(int x, int y, int z) {}

  /** A dig-broken block awaiting dawn heal. */
  private record BreakRecord(BlockData data, long breakTick) {}

  private final PanicPlugin plugin;
  private final Map<Key, BreakRecord> brokenBlocks = new HashMap<>();
  private final Map<Key, Double> chew = new HashMap<>();
  private final Map<UUID, Location> lastPos = new HashMap<>();
  private final Map<UUID, Integer> stuckSeconds = new HashMap<>();

  public TunnelService(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  /** Called once per second by the plugin. */
  public void tick() {
    if (!plugin.config().tunnelEnabled) {
      return;
    }
    World world = plugin.world();
    for (Zombie z : world.getEntitiesByClass(Zombie.class)) {
      if (z.hasMetadata(AlphaManager.META_ALPHA)) {
        forget(z.getUniqueId());
        continue; // alphas loom, they do not dig
      }
      LivingEntity target = z.getTarget();
      Location here = z.getLocation();
      if (target == null || !target.isValid()) {
        lastPos.put(z.getUniqueId(), here.clone());
        stuckSeconds.remove(z.getUniqueId());
        continue;
      }
      Location prev = lastPos.put(z.getUniqueId(), here.clone());
      if (prev == null) {
        continue;
      }
      if (here.distanceSquared(prev) < STUCK_SQ) {
        stuckSeconds.merge(z.getUniqueId(), 1, Integer::sum);
      } else {
        stuckSeconds.remove(z.getUniqueId());
      }
      if (stuckSeconds.getOrDefault(z.getUniqueId(), 0) < STUCK_SECONDS_TO_DIG) {
        continue;
      }
      if (plugin.haven().contains(here)) {
        continue;
      }
      chewBlock(z, target);
    }
    pruneTracked();
    pruneStaleChew(world);
  }

  /** Finds the blocking block ahead of the digger and adds one second of chew work to it. */
  private void chewBlock(Zombie z, LivingEntity target) {
    World world = z.getWorld();
    Location eye = z.getEyeLocation();
    Vector dir = target.getLocation().toVector().subtract(eye.toVector());
    dir.setY(0);
    if (dir.lengthSquared() < 0.01) {
      return;
    }
    dir.normalize();
    RayTraceResult hit = world.rayTraceBlocks(eye, dir, RAY_RANGE, FluidCollisionMode.NEVER);
    if (hit == null || hit.getHitBlock() == null) {
      return;
    }
    Block block = hit.getHitBlock();
    Key key = new Key(block.getX(), block.getY(), block.getZ());
    Material material = block.getType();
    if (!material.isSolid()) {
      chew.remove(key); // plants, torches, buttons: not a real wall; also clears stale work
      return; // let it re-path
    }
    int needed = DigTiers.chewTicks(material);
    if (needed <= 0) {
      chew.remove(key);
      return;
    }
    if (plugin.haven().containsBlock(block.getX(), block.getZ())) {
      return; // the haven floor is not fair game
    }
    if (block.getRelative(0, 1, 0).getType() == Material.WATER) {
      return; // do not pour the pool onto ourselves
    }
    double work =
        (z.hasMetadata(AlphaManager.META_HORDE)
                ? plugin.config().tunnelHordeDigSpeed
                : plugin.config().tunnelVanillaDigSpeed)
            * 20.0;
    double total = chew.getOrDefault(key, 0.0) + work;
    if (total >= needed) {
      chew.remove(key);
      breakBlock(block, material);
    } else {
      chew.put(key, total);
      crack(block);
    }
  }

  /** Breaks the block with no drops, records it for dawn heal, and tells nearby players. */
  private void breakBlock(Block block, Material material) {
    Key key = new Key(block.getX(), block.getY(), block.getZ());
    brokenBlocks.put(key, new BreakRecord(block.getBlockData(), Bukkit.getCurrentTick()));
    Location loc = block.getLocation();
    // A chewed-through chest or hopper would otherwise keep its contents in a void block.
    if (block.getState() instanceof Container container) {
      for (ItemStack item : container.getInventory().getContents()) {
        if (item != null) {
          loc.getWorld().dropItemNaturally(loc, item);
        }
      }
    }
    if (nearPlayers(loc)) {
      loc.getWorld().playSound(loc, breakSound(material), 1.0f, 1.0f);
      loc.getWorld()
          .spawnParticle(Particle.BLOCK, loc, 12, 0.4, 0.4, 0.4, 0.05, block.getBlockData());
    }
    block.setType(Material.AIR);
  }

  private void crack(Block block) {
    Location loc = block.getLocation();
    if (nearPlayers(loc)) {
      loc.getWorld()
          .spawnParticle(Particle.BLOCK, loc, 6, 0.35, 0.35, 0.35, 0.05, block.getBlockData());
    }
  }

  /** Break particles and sounds are only worth sending when someone is close enough to see them. */
  private boolean nearPlayers(Location loc) {
    return !loc.getWorld().getNearbyPlayers(loc, PARTICLE_VIEW_DISTANCE).isEmpty();
  }

  private static Sound breakSound(Material material) {
    int tier = DigTiers.chewTicks(material);
    if (tier == DigTiers.FAST) {
      return Sound.BLOCK_GRASS_BREAK;
    }
    if (tier == DigTiers.WOOD) {
      return Sound.BLOCK_WOOD_BREAK;
    }
    if (tier == DigTiers.METAL) {
      return Sound.BLOCK_IRON_BREAK;
    }
    return Sound.BLOCK_STONE_BREAK;
  }

  /**
   * True when a block broken at {@code breakTick} is old enough to heal at {@code dawnTick}. Blocks
   * broken inside the grace window (the final seconds before dawn) stay broken.
   */
  static boolean healEligible(long breakTick, long dawnTick, long graceTicks) {
    return breakTick < dawnTick - graceTicks;
  }

  /** Restores dig-broken blocks that are past the grace window. @return blocks restored. */
  public int dawnHeal() {
    long now = Bukkit.getCurrentTick();
    long grace = plugin.config().tunnelHealGraceSeconds * 20L;
    World world = plugin.world();
    int healed = 0;
    for (Map.Entry<Key, BreakRecord> entry : new ArrayList<>(brokenBlocks.entrySet())) {
      Key key = entry.getKey();
      if (!healEligible(entry.getValue().breakTick(), now, grace)) {
        continue;
      }
      Block block = world.getBlockAt(key.x(), key.y(), key.z());
      if (!block.getType().isAir()) {
        brokenBlocks.remove(key); // something else occupies it now; leave it be
        continue;
      }
      block.setBlockData(entry.getValue().data());
      Location loc = block.getLocation();
      if (nearPlayers(loc)) {
        loc.getWorld()
            .spawnParticle(Particle.BLOCK, loc, 10, 0.4, 0.4, 0.4, 0.05, block.getBlockData());
        loc.getWorld().playSound(loc, Sound.BLOCK_GRASS_BREAK, 0.25f, 1.6f);
      }
      brokenBlocks.remove(key);
      healed++;
    }
    return healed;
  }

  /** Blocks broken by diggers and still awaiting dawn heal. */
  public int brokenCount() {
    return brokenBlocks.size();
  }

  private void forget(UUID id) {
    lastPos.remove(id);
    stuckSeconds.remove(id);
  }

  /**
   * Chew work on a block that is no longer solid (player-mined, exploded, or whose digger left and
   * the chunk unloaded) would leak forever; sweep it each second.
   */
  private void pruneStaleChew(World world) {
    for (Iterator<Map.Entry<Key, Double>> it = chew.entrySet().iterator(); it.hasNext(); ) {
      Key k = it.next().getKey();
      if (!world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) {
        it.remove();
      } else if (!world.getBlockAt(k.x(), k.y(), k.z()).getType().isSolid()) {
        it.remove();
      }
    }
  }

  /** Dropped diggers leak map entries otherwise; sweep only when the cache grows. */
  private void pruneTracked() {
    if (lastPos.size() < 256) {
      return;
    }
    for (Iterator<UUID> it = lastPos.keySet().iterator(); it.hasNext(); ) {
      UUID id = it.next();
      if (Bukkit.getEntity(id) == null) {
        it.remove();
        stuckSeconds.remove(id);
      }
    }
  }
}
