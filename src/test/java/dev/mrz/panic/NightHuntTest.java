package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

final class NightHuntTest {

  private static Location at(double x, double z) {
    return new Location(null, x, 64, z);
  }

  @Test
  void picksNearestTarget() {
    List<Location> targets = List.of(at(100, 0), at(10, 0), at(50, 0));
    assertEquals(1, NightHunt.nearestWithinIndex(targets, at(0, 0), 200));
  }

  @Test
  void ignoresTargetsOutOfRange() {
    List<Location> targets = List.of(at(100, 0), at(1000, 0));
    assertEquals(0, NightHunt.nearestWithinIndex(targets, at(0, 0), 150));
    assertEquals(-1, NightHunt.nearestWithinIndex(targets, at(0, 0), 50));
  }

  @Test
  void boundaryIsInclusive() {
    List<Location> targets = List.of(at(150, 0));
    assertEquals(0, NightHunt.nearestWithinIndex(targets, at(0, 0), 150));
    assertEquals(-1, NightHunt.nearestWithinIndex(targets, at(0, 0), 149.9));
  }

  @Test
  void emptyListHasNoTarget() {
    assertEquals(-1, NightHunt.nearestWithinIndex(List.of(), at(0, 0), 1600));
  }

  @Test
  void diagonalDistanceCounts() {
    // 3-4-5 triangle scaled x2: (30, 40) is 50 away.
    List<Location> targets = List.of(at(30, 40));
    assertEquals(0, NightHunt.nearestWithinIndex(targets, at(0, 0), 50));
    assertEquals(-1, NightHunt.nearestWithinIndex(targets, at(0, 0), 49));
  }
}
