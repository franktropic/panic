package dev.mrz.panic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Dread systems: proximity warnings, distant screams, warden heartbeat with pre-ambush silence,
 * fake footsteps, per-player fake block cracks, and false alarms. Pure server-side effects (sounds,
 * chat, potion effects, particles) — vanilla-client compatible. Runs every second from the plugin's
 * secondTick, alongside alpha targeting.
 */
public final class DreadService {

  private static final String[] WARNINGS = {
    "Something has your scent.", "The ground trembles.", "It is here."
  };

  private final PanicPlugin plugin;
  private final Random random = new Random();
  private final Map<UUID, Dread> dread = new HashMap<>();

  /** Per-player state. Warning tiers de-escalate one step per second to avoid spam. */
  private static final class Dread {
    int tier;
    long nextScream;
    long nextHeartbeat;
    long nextFakeStep;
    long nextCrack;
    long nextFalseAlarm;
  }

  public DreadService(PanicPlugin plugin) {
    this.plugin = plugin;
  }

  public void tick() {
    long now = Bukkit.getCurrentTick();
    for (Player p : Bukkit.getOnlinePlayers()) {
      UUID id = p.getUniqueId();
      if (!plugin.data().isEscaped(id) || plugin.haven().contains(p.getLocation())) {
        dread.remove(id);
        continue;
      }
      Dread d = dread.get(id);
      if (d == null) {
        d = new Dread();
        d.nextScream = now + ticks(5) + random.nextInt(ticks(15));
        d.nextFakeStep = now + ticks(15) + random.nextInt(ticks(45));
        d.nextCrack = now + ticks(3) + random.nextInt(ticks(9));
        d.nextFalseAlarm = nextFalseAlarmAt(now);
        dread.put(id, d);
      }
      AlphaManager.Alpha alpha =
          plugin.alphaManager().nearestAlphaTo(p.getLocation(), plugin.config().detectRange);
      if (alpha != null) {
        hunt(p, d, now, alpha);
      } else {
        d.tier = Math.max(0, d.tier - 1);
        fakeSteps(p, d, now);
        falseAlarms(p, d, now);
      }
    }
    for (UUID id : new ArrayList<>(dread.keySet())) {
      if (Bukkit.getPlayer(id) == null) {
        dread.remove(id);
      }
    }
  }

  private void hunt(Player p, Dread d, long now, AlphaManager.Alpha alpha) {
    double dist = p.getLocation().distance(alpha.zombie.getLocation());
    PanicConfig cfg = plugin.config();

    int tier = dist <= cfg.nearRange ? 3 : dist <= cfg.midRange ? 2 : dist <= cfg.farRange ? 1 : 0;
    if (tier > d.tier) {
      d.tier = tier;
      warn(p, tier);
    } else if (tier < d.tier) {
      d.tier = Math.max(tier, d.tier - 1);
    }

    if (now >= d.nextScream) {
      d.nextScream =
          now
              + ticks(cfg.screamMinSec)
              + random.nextInt(ticks(Math.max(1, cfg.screamMaxSec - cfg.screamMinSec + 1)));
      p.playSound(
          offset(p, 15 + random.nextDouble() * 35),
          Sound.ENTITY_ZOMBIE_AMBIENT,
          0.9f,
          0.3f + random.nextFloat() * 0.3f);
    }

    // Warden heartbeat speeds up as the alpha closes; total silence right before the ambush.
    if (dist <= cfg.heartbeatRange && dist > cfg.silenceRange && now >= d.nextHeartbeat) {
      d.nextHeartbeat = now + Math.max(20L, (long) (20 + (dist - cfg.silenceRange) * 4L));
      p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.0f);
      p.addPotionEffect(
          new PotionEffect(PotionEffectType.DARKNESS, 40, dist < 12 ? 1 : 0, false, false, false));
    }

    fakeCracks(p, d, now);
    fakeSteps(p, d, now);
  }

  /** Crack particles on a block in the player's view, visible to them only. */
  private void fakeCracks(Player p, Dread d, long now) {
    if (now < d.nextCrack) {
      return;
    }
    d.nextCrack = now + ticks(3) + random.nextInt(ticks(7));
    Location eye = p.getEyeLocation();
    for (int i = 0; i < 6; i++) {
      Vector dir = eye.getDirection();
      dir.add(
          new Vector(
              random.nextGaussian() * 0.5,
              random.nextGaussian() * 0.5,
              random.nextGaussian() * 0.5));
      dir.normalize();
      Location probe = eye.clone().add(dir.multiply(2 + random.nextInt(8)));
      Block b = probe.getBlock();
      if (b.getType().isSolid()) {
        p.spawnParticle(
            Particle.BLOCK,
            b.getLocation().add(0.5, 0.5, 0.5),
            5,
            0.35,
            0.35,
            0.35,
            b.getBlockData());
        return;
      }
    }
  }

  /** A soft footstep behind the player when nothing is there. */
  private void fakeSteps(Player p, Dread d, long now) {
    if (now < d.nextFakeStep) {
      return;
    }
    d.nextFakeStep = now + ticks(30) + random.nextInt(ticks(60));
    Vector dir = p.getLocation().getDirection();
    dir.setY(0);
    if (dir.lengthSquared() < 0.01) {
      double ang = random.nextDouble() * 2 * Math.PI;
      dir = new Vector(Math.cos(ang), 0, Math.sin(ang));
    }
    Location behind = p.getLocation().add(dir.multiply(-(4 + random.nextDouble() * 6)));
    Sound s = random.nextBoolean() ? Sound.BLOCK_GRASS_BREAK : Sound.BLOCK_STONE_BREAK;
    p.playSound(behind, s, 0.4f, 0.5f + random.nextFloat() * 0.5f);
  }

  /** Occasional approach warning with no alpha behind it, for players with nothing near. */
  private void falseAlarms(Player p, Dread d, long now) {
    if (now < d.nextFalseAlarm) {
      return;
    }
    d.nextFalseAlarm = nextFalseAlarmAt(now);
    if (random.nextInt(100) < plugin.config().falseAlarmPercent) {
      warn(p, 1 + random.nextInt(3));
    }
  }

  private long nextFalseAlarmAt(long now) {
    PanicConfig cfg = plugin.config();
    return now
        + ticks(cfg.falseAlarmMinSec)
        + random.nextInt(ticks(Math.max(1, cfg.falseAlarmMaxSec - cfg.falseAlarmMinSec)));
  }

  private void warn(Player p, int tier) {
    p.sendMessage(plugin.prefix() + ChatColor.DARK_RED + WARNINGS[tier - 1]);
  }

  private Location offset(Player p, double dist) {
    double ang = random.nextDouble() * 2 * Math.PI;
    return p.getLocation().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
  }

  private static int ticks(int seconds) {
    return seconds * 20;
  }
}
