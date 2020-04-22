/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.test;

import org.bedework.calfacade.BwDateTime;

import net.fortuna.ical4j.model.property.DtStart;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * User: mike Date: 4/5/20 Time: 23:22
 */
public class BwDateTimeTest {


  @BeforeClass
  public static void oneTimeSetUp() {

  }

  @AfterClass
  public static void oneTimeTearDown() {

  }

  @Test
  public void test() {
    final BwDateTime bwd1 = BwDateTime.makeBwDateTime(true,
                                                      "20200315",
                                                      null);
    final DtStart dts1 = bwd1.makeDtStart();

    Assert.assertEquals("Start", "20200315", dts1.getValue());

    final BwDateTime bwd2 = bwd1.getNextDay();

    Assert.assertEquals("Start+1", "20200316", dts1.getValue());

    final DtStart dts2 = bwd2.makeDtStart();

    Assert.assertEquals("Start+1(dtstart)", "20200316", dts1.getValue());
  }
}
