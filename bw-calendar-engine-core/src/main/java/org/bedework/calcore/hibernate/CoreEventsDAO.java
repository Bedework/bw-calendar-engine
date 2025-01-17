/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.calcore.hibernate;

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.exc.CalFacadeException;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/** The events interactions with the database
 * 
 * @author Mike Douglass   douglm  - bedework.org
 */
public class CoreEventsDAO extends DAOBase {
  /** Constructor
   *
   * @param sess the session
   */
  public CoreEventsDAO(final HibSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return CoreEventsDAO.class.getName();
  }

  private static final String eventsByNameQuery =
    "from " + BwEventObj.class.getName() + " as ev " +
      "where ev.name = :name and ev.tombstoned=false and ev.colPath = :colPath";

  protected List<BwEvent> getEventsByName(final String colPath,
                                          final String name) {
    final HibSession sess = getSess();

    sess.createQuery(eventsByNameQuery);
    sess.setString("name", name);
    sess.setString("colPath", colPath);

    //noinspection unchecked
    return (List<BwEvent>)sess.getList();
  }

  private static final String eventAnnotationsByNameQuery =
          "from " + BwEventAnnotation.class.getName() + " as ev " +
                  "where ev.name = :name and " +
                  "ev.colPath = :colPath and " +
                  "ev.tombstoned=false and " +
                  "ev.override=false";

  protected BwEventAnnotation getEventsAnnotationName(final String colPath,
                                                      final String name) {
    final HibSession sess = getSess();

    sess.createQuery(eventAnnotationsByNameQuery);
    sess.setString("name", name);
    sess.setString("colPath", colPath);

    return (BwEventAnnotation)sess.getUnique();
  }

  /* TODO - we get deadlocks (at least with mysql) when
     we try to do this. For the moment move them to a purged
     collection until I figure out how to avoid the deadlocks
  private final static String deleteTombstonedEventQuery =
          "delete from " + BwEventObj.class.getName() + " ev " +
                  "where ev.tombstoned = true and " +
                  "ev.colPath = :path and " + 
                  "ev.uid = :uid";
   */
  private final static String deleteTombstonedEventQuery =
          "update " + BwEventObj.class.getName() + " ev " +
                  "set ev.colPath = '**purged**' " +
                  "where ev.tombstoned = true and " +
                  "ev.colPath = :path and " +
                  "ev.uid = :uid";

  protected void deleteTombstonedEvent(final String colPath,
                                     final String uid) {
    final HibSession sess = getSess();

    sess.createQuery(deleteTombstonedEventQuery);

    sess.setString("path", colPath);
    sess.setString("uid", uid);

    sess.executeUpdate();
  }
  
  private static final String getSynchEventObjectsTokenQuery =
          "from " + BwEventObj.class.getName() + " ev " +
                  "where ev.colPath = :path and " +
                  "ev.ctoken is not null and " + // XXX Only because we reused column
                  "ev.ctoken > :token";
        
  private static final String getSynchEventObjectsQuery =
          "from " + BwEventObj.class.getName() + " ev " +
                  "where ev.colPath = :path and " +
                  // No deleted events for null sync-token
                  "ev.tombstoned = false";

  protected List<?> getSynchEventObjects(final String path,
                                         final String token) {
    final HibSession sess = getSess();
    
    if (token != null) {
      sess.createQuery(getSynchEventObjectsTokenQuery);
    } else {
      sess.createQuery(getSynchEventObjectsQuery);
    }

    sess.setString("path", path);

    if (token != null) {
      sess.setString("token", token);
    }

    return sess.getList();
  }

  /* ====================================================================
   *                  Admin support
   * ==================================================================== */
  
  private final static String getChildEntitiesQuery =
          "select ev.name from " + BwEventObj.class.getName() + " ev " +
                  "where ev.colPath=:colPath and " +
                  // No deleted events
                  "ev.tombstoned = false " +
                  "order by ev.created, ev.name";

  public List<String> getChildrenEntities(final String parentPath,
                                          final int start,
                                          final int count) {
    final HibSession sess = getSess();

    sess.createQuery(getChildEntitiesQuery);

    sess.setString("colPath", parentPath);

    sess.setFirstResult(start);
    sess.setMaxResults(count);

    //noinspection unchecked
    return (List<String>)sess.getList();
  }

  /* ====================================================================
   *                       dumprestore support - to go in 4.0
   * ==================================================================== */

  private static final String getEventAnnotationsQuery =
          "from " + BwEventAnnotation.class.getName() +
                  " where recurrenceId=null";

  public Iterator<BwEventAnnotation> getEventAnnotations() {
    final HibSession sess = getSess();

    sess.createQuery(getEventAnnotationsQuery);

    @SuppressWarnings("unchecked") 
    final Collection<BwEventAnnotation> anns =
            (Collection<BwEventAnnotation>)sess.getList();

    return anns.iterator();
  }

  private static final String getEventOverridesQuery =
          "from " + BwEventAnnotation.class.getName() +
                  " where recurrenceId<>null " +
                  " and target=:target";

  @SuppressWarnings("unchecked")
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) {
    final HibSession sess = getSess();

    sess.createQuery(getEventOverridesQuery);
    sess.setEntity("target", ev);

    return (Collection<BwEventAnnotation>)sess.getList();
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private final static String calendarGuidExistsQuery =
          "select ev.name from " + BwEventObj.class.getName() + " ev " +
                  "where ev.tombstoned = false and ";

  private final static String calendarGuidAnnotationExistsQuery =
          "select ev.name from " + BwEventAnnotation.class.getName() + " ev " +
                  "where ev.tombstoned = false and ";
  
  /* Return the name of any event which has the same uid
   */
  protected String calendarGuidExists(final BwEvent val,
                                      final boolean annotation,
                                      final boolean adding) {
    final HibSession sess = getSess();

    final StringBuilder sb = new StringBuilder();

    if (!annotation) {
      sb.append(calendarGuidExistsQuery);
    } else {
      sb.append(calendarGuidAnnotationExistsQuery);
    }

    BwEvent testEvent = null;

    if (!adding) {
      if (annotation) {
        if (val instanceof final BwEventProxy proxy) {
          testEvent = proxy.getRef();
        }
        sb.append("ev.override=false and ");
      } else if (!(val instanceof BwEventProxy)) {
        testEvent = val;
      }
    }

    if (testEvent != null) {
      sb.append("ev<>:event and ");
    }

    sb.append("ev.colPath=:colPath and ev.uid = :uid");

    sess.createQuery(sb.toString());
    /* Change the above to
     *     sess.createNoFlushQuery(sb.toString());
     * and we save about 50% of the cpu for some updates. However we can't do
     * just that. The savings come about in not doing the flush which is
     * expensive - however we need it to ensure we are not getting dup uids.
     *
     * To make this work we would need to accumulate uids for the current
     * transaction in a table and check that as well as the db.
     *
     * It's also the case that a concurrent transaction could add uids and
     * a no-flush call will miss those.
     *
     * We may have to live with it but see if we can't speed up the fush. A lot
     * of the COU ends up in hibernate calling java.lang.Class.getInterfaces
     * which is not meant to be called frequently.
     */


    if (testEvent != null) {
      sess.setEntity("event", testEvent);
    }

    sess.setString("colPath", val.getColPath());
    sess.setString("uid", val.getUid());


    final Collection<?> refs = sess.getList();

    String res = null;

    if (!refs.isEmpty()) {
      res = (String)refs.iterator().next();
    }

    return res;
  }

  private final static String calendarNameExistsQuery =
          "select count(*) from " + BwEventObj.class.getName() + " ev " +
                  "where ev.tombstoned = false and ";

  private final static String calendarNameAnnotationExistsQuery =
          "select count(*) from " + BwEventAnnotation.class.getName() + " ev " +
                  "where ev.tombstoned = false and ";

  protected boolean calendarNameExists(final BwEvent val,
                                       final boolean annotation,
                                       final boolean adding) {
    final HibSession sess = getSess();

    final StringBuilder sb = new StringBuilder();

    if (!annotation) {
      sb.append(calendarNameExistsQuery);
    } else {
      sb.append(calendarNameAnnotationExistsQuery);
    }

    BwEvent testEvent = null;

    if (!adding) {
      if (annotation) {
        if (val instanceof final BwEventProxy proxy) {
          testEvent = proxy.getRef();
        }
        sb.append("ev.override=false and ");
      } else if (!(val instanceof BwEventProxy)) {
        testEvent = val;
      }
    }

    if (testEvent != null) {
      sb.append("ev<>:event and ");
    }

    sb.append("ev.colPath=:colPath and ");
    sb.append("ev.name = :name");

    sess.createQuery(sb.toString());
    /* See above note
      sess.createNoFlushQuery(sb.toString());
      */

    if (testEvent != null) {
      sess.setEntity("event", testEvent);
    }

    sess.setString("colPath", val.getColPath());
    sess.setString("name", val.getName());

    final Collection<?> refs = sess.getList();

    final Object o = refs.iterator().next();

    final boolean res;

    /* Apparently some get a Long - others get Integer */
    if (o instanceof final Long ct) {
      res = ct > 0;
    } else {
      final Integer ct = (Integer)o;
      res = ct > 0;
    }

    return res;
  }

  private final static String getAnnotationsQuery =
          "from " + BwEventAnnotation.class.getName() + " ev " +
                  " where target=:target";

  @SuppressWarnings("unchecked")
  protected Collection<BwEventAnnotation> getAnnotations(final BwEvent val) {
    final HibSession sess = getSess();

    sess.createQuery(getAnnotationsQuery);
    sess.setEntity("target", val);

    final Collection<BwEventAnnotation> anns =
            (Collection<BwEventAnnotation>)sess.getList();

    if (debug()) {
      debug("getAnnotations for event " + val.getId() +
               " returns " + anns.size());
    }

    return anns;
  }

  protected <T extends BwEvent> List<T> eventQuery(final Class<T> cl,
                            final String colPath,
                            final String guid,
                            final BwEvent master,
                            final Boolean overrides) {
    final HibSession sess = getSess();
    final EventQueryBuilder qb = new EventQueryBuilder();
    final String qevName = "ev";
    final BwDateTime startDate = null;
    final BwDateTime endDate = null;

    /* SEG:   from Events ev where */
    qb.from();
    qb.addClass(cl, qevName);
    qb.where();

    /* SEG:   (<date-ranges>) and */
    if (qb.appendDateTerms(qevName, startDate, endDate, false, false)) {
      qb.and();
    }

    if (master != null) {
      qb.append(" ev.master=:master ");
    } else {
      if (colPath != null) {
        qb.append(" ev.colPath=:colPath and");
      }
      qb.append(" ev.uid=:uid ");
    }

    qb.append(" and ev.tombstoned=false ");

    if (overrides != null) {
      qb.append(" and ev.override=:override ");
    }

    qb.createQuery(sess);

    qb.setDateTermValues(startDate, endDate);

    if (master != null) {
      sess.setEntity("master", master);
    } else {
      if (colPath != null) {
        sess.setString("colPath", colPath);
      }
      sess.setString("uid", guid);
    }

    if (overrides != null) {
      sess.setBool("override", overrides);
    }

    //debug("Try query " + sb.toString());
    //noinspection unchecked
    return (List<T>)sess.getList();
  }
}
