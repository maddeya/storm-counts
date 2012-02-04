package com.mapr.stats;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by IntelliJ IDEA. User: tdunning Date: 2/1/12 Time: 2:08 PM To change this template use
 * File | Settings | File Templates.
 */
public class BetaDistributionTest extends DistributionTest {
  @Test
  public void testPdf1() {
    pdfCheck(new double[]{0, .2, .5, .7, 1}, new double[]{1, 1, 1, 1, 1}, new BetaDistribution(1, 1));
  }

  @Test
  public void testPdf2() {
    pdfCheck(
      new double[]{0, 0.2, 0.5, 0.7, 1},
      new double[]{0, 1.536, 1.500, 0.756, 0}, new BetaDistribution(2, 3));
  }

  @Test
  public void testCdf1() {
    double[] x = {0, 0.1, 0.5, 1};
    cdfCheck(x, x, new BetaDistribution(1, 1));
  }

  @Test
  public void testCdf2() {
    cdfCheck(new double[]{0.0000, 0.1808, 0.6875, 0.9163, 1.0000}, new double[]{0.0000, 0.2, 0.5, 0.7, 1}, new BetaDistribution(2, 3));
  }

  @Test
  public void testMean() {
    assertEquals(0.5, new BetaDistribution(1, 1).mean(), 1e-10);
    assertEquals(0.4, new BetaDistribution(2, 3).mean(), 1e-10);
    assertEquals(0.6 / 10.6, new BetaDistribution(0.6, 10).mean(), 1e-10);
  }

  @Test
  public void testSampleDistribution1() {
    checkDistribution(new BetaDistribution(1, 1), String.format("alpha = %.1f, beta = %.1f", 1.0, 1.0), 1e-2);
    checkDistribution(new BetaDistribution(2, 3), String.format("alpha = %.1f, beta = %.1f", 2.0, 3.0), 1e-2);
    checkDistribution(new BetaDistribution(0.6, 20.0), String.format("alpha = %.1f, beta = %.1f", 0.6, 20.0), 1e-2);
  }
}