package dev.mrz.panic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStoreTest {

  @TempDir File tmp;

  private DataStore store() {
    DataStore ds = new DataStore(new File(tmp, "data.yml"));
    ds.load();
    return ds;
  }

  @Test
  void staleRunStartsReanchorToBoot() {
    DataStore ds = store();
    UUID u = UUID.randomUUID();
    ds.markJoined(u, "Anita");
    ds.setEscaped(u, true);
    ds.setRunStart(u, 99_999L);
    ds.resetStaleRunStarts();
    assertEquals(0L, ds.getRunStart(u), "run clock re-anchors to this boot");
    assertTrue(ds.isEscaped(u), "the run itself survives the restart");
  }

  @Test
  void finishedRunsAreNotTouched() {
    DataStore ds = store();
    UUID u = UUID.randomUUID();
    ds.markJoined(u, "Jash");
    assertEquals(-1L, ds.getRunStart(u));
    ds.resetStaleRunStarts();
    assertEquals(-1L, ds.getRunStart(u), "no in-progress run: nothing to re-anchor");
  }

  @Test
  void saveOverwritesCleanly() throws Exception {
    DataStore ds = store();
    UUID u = UUID.randomUUID();
    ds.markJoined(u, "Anita");
    ds.save();
    File f = new File(tmp, "data.yml");
    assertTrue(f.exists());
    ds.setBest(u, 123L);
    ds.save();
    assertEquals(123L, ds.getBest(u));
    assertFalse(new File(tmp, "data.yml.tmp").exists(), "sidecar file is renamed away");
  }
}
