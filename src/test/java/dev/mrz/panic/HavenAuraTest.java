package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class HavenAuraTest {

  @Test
  void perimeterHasExpectedCount() {
    // half=12, step=2: 13 steps per horizontal side (26) + 11 vertical steps (22) = 48.
    assertEquals(48, HavenAura.perimeter(0, 0, 12, 2).size());
  }

  @Test
  void perimeterStaysOnTheEdge() {
    List<int[]> p = HavenAura.perimeter(5, -3, 12, 2);
    for (int[] s : p) {
      boolean onEdge = Math.abs(s[0] - 5) == 12 || Math.abs(s[1] + 3) == 12;
      assertTrue(onEdge, "off-edge " + s[0] + "," + s[1]);
    }
  }

  @Test
  void perimeterCornersExactlyOnce() {
    List<int[]> p = HavenAura.perimeter(0, 0, 12, 2);
    assertEquals(1, occurrences(p, -12, -12));
    assertEquals(1, occurrences(p, 12, -12));
    assertEquals(1, occurrences(p, -12, 12));
    assertEquals(1, occurrences(p, 12, 12));
  }

  @Test
  void perimeterNoDuplicates() {
    List<int[]> p = HavenAura.perimeter(5, -3, 12, 2);
    Set<String> keys = new HashSet<>();
    for (int[] s : p) {
      keys.add(s[0] + "," + s[1]);
    }
    assertEquals(p.size(), keys.size());
  }

  private static int occurrences(List<int[]> p, int x, int z) {
    int n = 0;
    for (int[] s : p) {
      if (s[0] == x && s[1] == z) {
        n++;
      }
    }
    return n;
  }
}
