/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.rw.common;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.AlarmsDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreAlarmsI;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.util.AccessChecker;

import java.util.Collection;

/**
 * User: mike Date: 2/1/20 Time: 14:49
 */
public class CoreAlarms extends CalintfHelper
        implements CoreAlarmsI {
  private final AlarmsDAO dao;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CoreAlarms(final AlarmsDAO dao,
                    final Calintf intf,
                    final AccessChecker ac,
                    final boolean sessionless) {
    this.dao = dao;
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    dao.rollback();
    throw be;
  }

  @Override
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime) {
    return dao.getUnexpiredAlarms(triggerTime);
  }

  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm) {
    return dao.getEventsByAlarm(alarm);
  }
}
