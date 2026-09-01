package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DataStorePauseTest {

  @TempDir File tmp;

  private DataStore store() {
    DataStore ds = new DataStore(new File(tmp, "data.yml"));
    ds.load();
    return ds;
  }

  private UUID player(DataStore ds, String name) {
    UUID u = UUID.randomUUID();
    ds.markJoined(u, name);
    ds.setEscaped(u, true);
    ds.setRunStart(u, 0L);
    return u;
  }

  @Test
  void clockStopsWhileInHaven() {
    DataStore ds = store();
    UUID u = player(ds, "Anita");
    ds.pauseRun(u, 1000L);
    assertTrue(ds.isPaused(u));
    assertEquals(50L, ds.runSeconds(u, 1000L), "time before the pause is retained");
    assertEquals(50L, ds.runSeconds(u, 2000L), "paused: no new time counts");
  }

  @Test
  void clockResumesAfterExit() {
    DataStore ds = store();
    UUID u = player(ds, "Jash");
    ds.pauseRun(u, 1000L);
    ds.resumeRun(u, 1600L); // 600t breather
    assertFalse(ds.isPaused(u));
    assertEquals(600L, ds.getPausedTicks(u));
    assertEquals(100L, ds.runSeconds(u, 2600L), "(2600 - 600 paused) / 20");
  }

  @Test
  void multiplePausesAccumulate() {
    DataStore ds = store();
    UUID u = player(ds, "Pete");
    ds.pauseRun(u, 1000L);
    ds.resumeRun(u, 1400L); // 400t
    ds.pauseRun(u, 2000L);
    ds.resumeRun(u, 2500L); // 500t
    assertEquals(900L, ds.getPausedTicks(u));
    assertEquals(105L, ds.runSeconds(u, 3000L), "(3000 - 900) / 20");
  }

  @Test
  void pauseBeforeRunIsIgnored() {
    DataStore ds = store();
    UUID u = UUID.randomUUID();
    ds.markJoined(u, "Andy");
    assertEquals(-1L, ds.getRunStart(u));
    ds.pauseRun(u, 100L);
    assertFalse(ds.isPaused(u), "no live run, nothing to pause");
    assertEquals(0L, ds.runSeconds(u, 999L));
  }

  @Test
  void deathResetsPauseForTheNextRun() {
    DataStore ds = store();
    UUID u = player(ds, "Cain");
    ds.pauseRun(u, 1000L);
    ds.resumeRun(u, 1600L); // 600t of breather in the dead run
    // death:
    ds.setRunStart(u, -1L);
    ds.setEscaped(u, false);
    ds.discardPause(u);
    ds.setPausedTicks(u, 0L);
    // fresh run:
    ds.setEscaped(u, true);
    ds.setRunStart(u, 3000L);
    assertEquals(5L, ds.runSeconds(u, 3100L), "stale pause must not eat into the new run");
  }

  @Test
  void runSecondsClampsAtZero() {
    DataStore ds = store();
    UUID u = player(ds, "Fungus");
    ds.setPausedTicks(u, 999_999L);
    assertEquals(0L, ds.runSeconds(u, 100L), "never negative");
  }
}
