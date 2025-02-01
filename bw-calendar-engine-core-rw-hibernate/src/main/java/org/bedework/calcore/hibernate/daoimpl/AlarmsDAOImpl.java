/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.calcore.rw.common.dao.AlarmsDAO;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.database.db.DbSession;

import java.util.Collection;

/**
 * User: mike
 * Date: 11/21/16
 * Time: 23:30
 */
public class AlarmsDAOImpl extends DAOBaseImpl
        implements AlarmsDAO {
  /**
   * @param sess             the session
   */
  public AlarmsDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return AlarmsDAOImpl.class.getName();
  }

  /* ==========================================================
   *                   Alarms
   * ========================================================== */

  private static final String getUnexpiredAlarmsQuery =
          "select al from BwAlarm al " +
                  "where al.expired = false";

  /* Return all unexpired alarms before the given time */
  private static final String getUnexpiredAlarmsTimeQuery =
          "select al from BwAlarm al " +
                  "where al.expired = false and " +
                  "al.triggerTime <= :tt";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwAlarm> getUnexpiredAlarms(
          final long triggerTime) {
    final var sess = getSess();

    if (triggerTime == 0) {
      sess.createQuery(getUnexpiredAlarmsQuery);
    } else {
      sess.createQuery(getUnexpiredAlarmsTimeQuery);
      sess.setString("tt", String.valueOf(triggerTime));
    }

    return (Collection<BwAlarm>)sess.getList();
  }

  private static final String eventByAlarmQuery =
          "select count(*) from BwEventObj ev " +
                  "where ev.tombstoned=false and :alarm in alarms";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm) {
    final var sess = getSess();

    sess.createQuery(eventByAlarmQuery);
    sess.setInt("alarmId", alarm.getId());

    return (Collection<BwEvent>)sess.getList();
  }
}
