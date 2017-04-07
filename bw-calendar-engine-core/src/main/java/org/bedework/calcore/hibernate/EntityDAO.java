/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate;

import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;

import java.util.Collection;
import java.util.List;

/**
 * User: mike
 * Date: 11/21/16
 * Time: 23:30
 */
public class EntityDAO extends DAOBase {
  /**
   * @param sess             the session
   */
  public EntityDAO(final HibSession sess) {
    super(sess);
  }

  /* ====================================================================
   *                       calendar suites
   * ==================================================================== */

  private static final String getCalSuiteByGroupQuery =
          "from org.bedework.calfacade.svc.BwCalSuite cal " +
                  "where cal.group=:group";

  public BwCalSuite get(final BwAdminGroup group) throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getCalSuiteByGroupQuery);

    sess.setEntity("group", group);
    sess.cacheableQuery();

    final BwCalSuite cs = (BwCalSuite)sess.getUnique();

    if (cs != null){
      sess.evict(cs);
    }

    return cs;
  }

  private static final String getCalSuiteQuery =
          "from org.bedework.calfacade.svc.BwCalSuite cal " +
                  "where cal.name=:name";

  public BwCalSuite getCalSuite(final String name) throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getCalSuiteQuery);

    sess.setString("name", name);
    sess.cacheableQuery();

    return (BwCalSuite)sess.getUnique();
  }

  private static final String getAllCalSuitesQuery =
          "from " + BwCalSuite.class.getName();

  @SuppressWarnings("unchecked")
  public Collection<BwCalSuite> getAllCalSuites() throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getAllCalSuitesQuery);

    sess.cacheableQuery();

    return sess.getList();
  }

  /* ====================================================================
   *                   Alarms
   * ==================================================================== */

  private static final String getUnexpiredAlarmsQuery =
          "from " + BwAlarm.class.getName() + " as al " +
                  "where al.expired = false";

  /* Return all unexpired alarms before the given time */
  private static final String getUnexpiredAlarmsTimeQuery =
          "from " + BwAlarm.class.getName() + " as al " +
                  "where al.expired = false and " +
                  "al.triggerTime <= :tt";

  @SuppressWarnings("unchecked")
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime)
          throws CalFacadeException {
    final HibSession sess = getSess();

    if (triggerTime == 0) {
      sess.createQuery(getUnexpiredAlarmsQuery);
    } else {
      sess.createQuery(getUnexpiredAlarmsTimeQuery);
      sess.setString("tt", String.valueOf(triggerTime));
    }

    return sess.getList();
  }

  private static final String eventByAlarmQuery =
          "select count(*) from " + BwEventObj.class.getName() + " as ev " +
                  "where ev.tombstoned=false and :alarm in alarms";

  @SuppressWarnings("unchecked")
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm)
          throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(eventByAlarmQuery);
    sess.setInt("alarmId", alarm.getId());

    return sess.getList();
  }

  /* ====================================================================
   *                       resources
   * ==================================================================== */

  private static final String getResourceQuery =
          "from " + BwResource.class.getName() +
                  " where name=:name and colPath=:path" +
                  " and (encoding is null or encoding <> :tsenc)";

  public BwResource getResource(final String name,
                                final BwCalendar coll,
                                final int desiredAccess) throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getResourceQuery);

    sess.setString("name", name);
    sess.setString("path", coll.getPath());
    sess.setString("tsenc", BwResource.tombstoned);
    sess.cacheableQuery();

    return (BwResource)sess.getUnique();
  }

  private static final String getResourceContentQuery =
          "from " + BwResourceContent.class.getName() +
                  " as rc where rc.colPath=:path and rc.name=:name";

  public void getResourceContent(final BwResource val) throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getResourceContentQuery);
    sess.setString("path", val.getColPath());
    sess.setString("name", val.getName());
    sess.cacheableQuery();

    final BwResourceContent rc = (BwResourceContent)sess.getUnique();
    if (rc == null) {
      throw new CalFacadeException(CalFacadeException.missingResourceContent);
    }

    val.setContent(rc);
  }

  private static final String getAllResourcesQuery =
          "from " + BwResource.class.getName() +
                  " as r where r.colPath=:path" +
                  // No deleted collections for null sync-token or not sync
                  " and (r.encoding is null or r.encoding <> :tsenc)";

  private static final String getAllResourcesSynchQuery =
          "from " + BwResource.class.getName() +
                  " as r where r.colPath=:path" +
                  // Include deleted resources after the token.
                  " and (r.lastmod>:lastmod" +
                  " or (r.lastmod=:lastmod and r.sequence>:seq))";

  @SuppressWarnings("unchecked")
  public List<BwResource> getAllResources(final String path,
                                          final boolean forSynch,
                                          final String token) throws CalFacadeException {
    final HibSession sess = getSess();

    if (forSynch && (token != null)) {
      sess.createQuery(getAllResourcesSynchQuery);
    } else {
      sess.createQuery(getAllResourcesQuery);
    }

    sess.setString("path", path);

    if (forSynch && (token != null)) {
      sess.setString("lastmod", token.substring(0, 16));
      sess.setInt("seq", Integer.parseInt(token.substring(17), 16));
    } else {
      sess.setString("tsenc", BwResource.tombstoned);
    }

    sess.cacheableQuery();

    return sess.getList();
  }

  private static final String getNResourcesQuery =
          "from " + BwResource.class.getName() +
                  " as r where r.colPath=:path" +
                  // No deleted collections for null sync-token or not sync
                  " and (r.encoding is null or r.encoding <> :tsenc)" +
                  " order by r.created desc";

  @SuppressWarnings("unchecked")
  public List<BwResource> getNResources(final String path,
                                        final int start,
                                        final int count) throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getNResourcesQuery);

    sess.setString("path", path);

    sess.setString("tsenc", BwResource.tombstoned);

    sess.setFirstResult(start);
    sess.setMaxResults(count);

    return sess.getList();
  }

  /* ====================================================================
   *                       system parameters
   * ==================================================================== */

  private static final String getSystemParsQuery =
          "from " + BwSystem.class.getName() + " as sys " +
                  "where sys.name = :name";

  public BwSystem getSyspars(final String name) throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getSystemParsQuery);

    sess.setString("name", name);

    return (BwSystem)sess.getUnique();
  }
}
