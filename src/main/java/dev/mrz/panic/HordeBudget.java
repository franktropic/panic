package dev.mrz.panic;

/**
 * Load-bearing performance caps: how many alphas and horde mobs may be live at once. Pure counters
 * — the manager adds and removes as entities spawn and die.
 */
public final class HordeBudget {

  private final int maxAlphas;
  private final int maxHordeTotal;
  private int alphas;
  private int horde;

  public HordeBudget(int maxAlphas, int maxHordeTotal) {
    if (maxAlphas <= 0 || maxHordeTotal <= 0) {
      throw new IllegalArgumentException("caps must be positive");
    }
    this.maxAlphas = maxAlphas;
    this.maxHordeTotal = maxHordeTotal;
  }

  /**
   * @param populationCap one alpha per player that has ever joined (brief); the smaller of the
   *     config cap and this wins.
   */
  public boolean canSpawnAlpha(int populationCap) {
    return alphas < maxAlphas && alphas < populationCap;
  }

  public boolean canSpawnHorde() {
    return horde < maxHordeTotal;
  }

  public void addAlpha() {
    alphas++;
  }

  public void removeAlpha() {
    alphas = Math.max(0, alphas - 1);
  }

  public void addHorde() {
    horde++;
  }

  public void removeHorde() {
    horde = Math.max(0, horde - 1);
  }

  /** Releases up to n horde slots at once (an alpha's whole horde going quiet). */
  public void releaseHorde(int n) {
    horde = Math.max(0, horde - n);
  }

  public int alphas() {
    return alphas;
  }

  public int horde() {
    return horde;
  }
}
