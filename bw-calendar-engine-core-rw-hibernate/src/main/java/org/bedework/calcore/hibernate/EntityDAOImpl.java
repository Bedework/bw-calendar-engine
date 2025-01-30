/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate;

import org.bedework.calcore.rw.common.dao.EntityDAO;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.database.db.DbSession;

import java.util.Collection;

/**
 * User: mike
 * Date: 11/21/16
 * Time: 23:30
 */
public class EntityDAOImpl extends DAOBaseImpl
        implements EntityDAO {
  /**
   * @param sess             the session
   */
  public EntityDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return EntityDAOImpl.class.getName();
  }

  /* ==========================================================
   *                       calendar suites
   * ========================================================== */

  private static final String getCalSuiteByGroupQuery =
          "from org.bedework.calfacade.svc.BwCalSuite cal " +
                  "where cal.group=:group";

  @Override
  public BwCalSuite get(final BwAdminGroup group) {
    final var sess = getSess();

    sess.createQuery(getCalSuiteByGroupQuery);

    sess.setEntity("group", group);

    final BwCalSuite cs = (BwCalSuite)sess.getUnique();

    if (cs != null){
      sess.evict(cs);
    }

    return cs;
  }

  private static final String getCalSuiteQuery =
          "from org.bedework.calfacade.svc.BwCalSuite cal " +
                  "where cal.name=:name";

  @Override
  public BwCalSuite getCalSuite(final String name) {
    final var sess = getSess();

    sess.createQuery(getCalSuiteQuery);

    sess.setString("name", name);

    return (BwCalSuite)sess.getUnique();
  }

  private static final String getAllCalSuitesQuery =
          "from " + BwCalSuite.class.getName();

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwCalSuite> getAllCalSuites() {
    final var sess = getSess();

    sess.createQuery(getAllCalSuitesQuery);

    return (Collection<BwCalSuite>)sess.getList();
  }

  /* ==========================================================
   *                   Alarms
   * ========================================================== */

  private static final String getUnexpiredAlarmsQuery =
          "from " + BwAlarm.class.getName() + " as al " +
                  "where al.expired = false";

  /* Return all unexpired alarms before the given time */
  private static final String getUnexpiredAlarmsTimeQuery =
          "from " + BwAlarm.class.getName() + " as al " +
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
          "select count(*) from " + BwEventObj.class.getName() + " as ev " +
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
