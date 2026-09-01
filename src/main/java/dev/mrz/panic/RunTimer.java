package dev.mrz.panic;

/**
 * Tick-based survival run timer. Pure: the caller feeds it server tick counts and persists the
 * results.
 */
public final class RunTimer {

  private long startTick = -1;
  private long bestSeconds;

  public void start(long serverTick) {
    this.startTick = serverTick;
  }

  public void reset() {
    this.startTick = -1;
  }

  public boolean running() {
    return startTick >= 0;
  }

  public long currentSeconds(long serverTick) {
    return running() ? (serverTick - startTick) / 20L : 0L;
  }

  /** Ends the current run, updates the best, and returns seconds survived. */
  public long end(long serverTick) {
    long s = currentSeconds(serverTick);
    reset();
    bestSeconds = Math.max(bestSeconds, s);
    return s;
  }

  public long bestSeconds() {
    return bestSeconds;
  }

  /** Formats seconds as 1h 04m 09s, 12m 33s, or 45s. */
  public static String format(long seconds) {
    long h = seconds / 3600L;
    long m = (seconds % 3600L) / 60L;
    long s = seconds % 60L;
    if (h > 0) {
      return String.format("%dh %02dm %02ds", h, m, s);
    }
    if (m > 0) {
      return String.format("%dm %02ds", m, s);
    }
    return String.format("%ds", s);
  }
}
