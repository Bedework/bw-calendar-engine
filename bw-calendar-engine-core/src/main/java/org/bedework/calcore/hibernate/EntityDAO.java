/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.util.misc.Util;

import java.util.Collection;
import java.util.List;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

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

  @Override
  public String getName() {
    return EntityDAO.class.getName();
  }

  /* ====================================================================
   *                       calendar suites
   * ==================================================================== */

  private static final String getCalSuiteByGroupQuery =
          "from org.bedework.calfacade.svc.BwCalSuite cal " +
                  "where cal.group=:group";

  public BwCalSuite get(final BwAdminGroup group) {
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

  public BwCalSuite getCalSuite(final String name) {
    final HibSession sess = getSess();

    sess.createQuery(getCalSuiteQuery);

    sess.setString("name", name);
    sess.cacheableQuery();

    return (BwCalSuite)sess.getUnique();
  }

  private static final String getAllCalSuitesQuery =
          "from " + BwCalSuite.class.getName();

  @SuppressWarnings("unchecked")
  public Collection<BwCalSuite> getAllCalSuites() {
    final HibSession sess = getSess();

    sess.createQuery(getAllCalSuitesQuery);

    sess.cacheableQuery();

    return (Collection<BwCalSuite>)sess.getList();
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
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime) {
    final HibSession sess = getSess();

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
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm) {
    final HibSession sess = getSess();

    sess.createQuery(eventByAlarmQuery);
    sess.setInt("alarmId", alarm.getId());

    return (Collection<BwEvent>)sess.getList();
  }

  /* ====================================================================
   *                       resources
   * ==================================================================== */

  private static final String getResourceQuery =
          "from " + BwResource.class.getName() +
                  " where name=:name and colPath=:path" +
                  " and (encoding is null or encoding <> :tsenc)";

  public BwResource getResource(final String name,
                                final String colPath,
                                final int desiredAccess) {
    final HibSession sess = getSess();

    sess.createQuery(getResourceQuery);

    sess.setString("name", name);
    sess.setString("path", colPath);
    sess.setString("tsenc", BwResource.tombstoned);
    sess.cacheableQuery();

    return (BwResource)sess.getUnique();
  }

  private static final String getResourceContentQuery =
          "from " + BwResourceContent.class.getName() +
                  " as rc where rc.colPath=:path and rc.name=:name";

  public void getResourceContent(final BwResource val) {
    final HibSession sess = getSess();

    sess.createQuery(getResourceContentQuery);
    sess.setString("path", val.getColPath());
    sess.setString("name", val.getName());
    sess.cacheableQuery();

    final BwResourceContent rc = (BwResourceContent)sess.getUnique();
    if (rc == null) {
      throw new CalFacadeException(CalFacadeErrorCode.missingResourceContent);
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
                  " or (r.lastmod=:lastmod and r.sequence>:seq))"+
                  " order by r.created desc";

  @SuppressWarnings("unchecked")
  public List<BwResource> getAllResources(final String path,
                                          final boolean forSynch,
                                          final String token,
                                          final int count) {
    final HibSession sess = getSess();

    if (forSynch && (token != null)) {
      sess.createQuery(getAllResourcesSynchQuery);
    } else {
      sess.createQuery(getAllResourcesQuery);
    }

    sess.setString("path", Util.buildPath(colPathEndsWithSlash,
                                          path));

    if (forSynch && (token != null)) {
      sess.setString("lastmod", token.substring(0, 16));
      sess.setInt("seq", Integer.parseInt(token.substring(17), 16));
    } else {
      sess.setString("tsenc", BwResource.tombstoned);
    }

    sess.setFirstResult(0);

    if (count > 0) {
      sess.setMaxResults(count);
    }

    sess.cacheableQuery();

    return (List<BwResource>)sess.getList();
  }
}
