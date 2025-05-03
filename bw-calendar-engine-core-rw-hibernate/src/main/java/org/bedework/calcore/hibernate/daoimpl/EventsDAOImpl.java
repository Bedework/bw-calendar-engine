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
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.calcore.hibernate.EventQueryBuilder;
import org.bedework.calcore.rw.common.dao.EventsDAO;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.database.db.DbSession;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/** The events interactions with the database
 * 
 * @author Mike Douglass   douglm  - bedework.org
 */
public class EventsDAOImpl extends DAOBaseImpl
        implements EventsDAO {
  /** Constructor
   *
   * @param sess the session
   */
  public EventsDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return EventsDAOImpl.class.getName();
  }

  private static final String eventsByNameQuery =
    "select ev from BwEventObj ev " +
      "where ev.name = :name and ev.tombstoned=false and ev.colPath = :colPath";

  @Override
  public List<BwEvent> getEventsByName(final String colPath,
                                       final String name) {
    final var sess = getSess();

    //noinspection unchecked
    return (List<BwEvent>)createQuery(eventsByNameQuery)
            .setString("name", name)
            .setString("colPath", colPath)
            .getList();
  }

  private static final String eventAnnotationsByNameQuery =
          "select ev from BwEventAnnotation ev " +
                  "where ev.name = :name and " +
                  "ev.colPath = :colPath and " +
                  "ev.tombstoned=false and " +
                  "ev.override=false";

  @Override
  public BwEventAnnotation getEventsAnnotationName(
          final String colPath,
          final String name) {
    return (BwEventAnnotation)createQuery(eventAnnotationsByNameQuery)
            .setString("name", name)
            .setString("colPath", colPath)
            .getUnique();
  }

  /* TODO - we get deadlocks (at least with mysql) when
     we try to do this. For the moment move them to a purged
     collection until I figure out how to avoid the deadlocks
  private final static String deleteTombstonedEventQuery =
          "delete from BwEventObj ev " +
                  "where ev.tombstoned = true and " +
                  "ev.colPath = :path and " + 
                  "ev.uid = :uid";
   */
  private final static String deleteTombstonedEventQuery =
          "update BwEventObj ev " +
                  "set ev.colPath = '**purged**' " +
                  "where ev.tombstoned = true and " +
                  "ev.colPath = :path and " +
                  "ev.uid = :uid";

  @Override
  public void deleteTombstonedEvent(final String colPath,
                                    final String uid) {
    createQuery(deleteTombstonedEventQuery)
            .setString("path", colPath)
            .setString("uid", uid)
            .executeUpdate();
  }
  
  private static final String getSynchEventObjectsTokenQuery =
          "select ev from BwEventObj ev " +
                  "where ev.colPath = :path and " +
                  "ev.ctoken is not null and " + // XXX Only because we reused column
                  "ev.ctoken > :token";
        
  private static final String getSynchEventObjectsQuery =
          "select ev from BwEventObj ev " +
                  "where ev.colPath = :path and " +
                  // No deleted events for null sync-token
                  "ev.tombstoned = false";

  @Override
  public List<?> getSynchEventObjects(final String path,
                                      final String token) {
    if (token != null) {
      return createQuery(getSynchEventObjectsTokenQuery)
              .setString("path", path)
              .setString("token", token)
              .getList();
    }


    return createQuery(getSynchEventObjectsQuery)
              .setString("path", path)
              .getList();
  }

  /* ==============================================================
   *                  Admin support
   * ============================================================== */
  
  private final static String getChildEntitiesQuery =
          "select ev.name from BwEventObj ev " +
                  "where ev.colPath=:colPath and " +
                  // No deleted events
                  "ev.tombstoned = false " +
                  "order by ev.created, ev.name";

  @Override
  public List<String> getChildrenEntities(
          final String parentPath,
          final int start,
          final int count) {
    //noinspection unchecked
    return (List<String>)createQuery(getChildEntitiesQuery)
            .setString("colPath", parentPath)
            .setFirstResult(start)
            .setMaxResults(count)
            .getList();
  }

  /* ==========================================================
   *                       dumprestore support - to go in 4.0
   * ========================================================== */

  private static final String getEventAnnotationsQuery =
          "select ev from BwEventAnnotation ev " +
                  "where ev.recurrenceId is null";

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() {
    //noinspection unchecked
    return ((Collection<BwEventAnnotation>)createQuery(getEventAnnotationsQuery)
            .getList())
            .iterator();
  }

  private static final String getEventOverridesQuery =
          "select ev from BwEventAnnotation ev " +
                  "where ev.recurrenceId<>null " +
                  "and ev.target=:target";

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(
          final BwEvent ev) {
    //noinspection unchecked
    return (Collection<BwEventAnnotation>)createQuery(getEventOverridesQuery)
            .setEntity("target", ev)
            .getList();
  }

  /* ==========================================================
   *                   Private methods
   * ========================================================== */

  private final static String calendarGuidExistsQuery =
          "select ev.name from BwEventObj ev " +
                  "where ev.tombstoned = false and ";

  private final static String calendarGuidAnnotationExistsQuery =
          "select ev.name from BwEventAnnotation ev " +
                  "where ev.tombstoned = false and ";
  
  /* Return the name of any event which has the same uid
   */
  @Override
  public String calendarGuidExists(final BwEvent val,
                                   final boolean annotation,
                                   final boolean adding) {
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

    /* Change the createQuery to
     *     createNoFlushQuery(sb.toString());
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
     * We may have to live with it but see if we can't speed up the flush. A lot
     * of the CPU ends up in hibernate calling java.lang.Class.getInterfaces
     * which is not meant to be called frequently.
     */

    final var sess = createQuery(sb.toString());

    if (testEvent != null) {
      sess.setEntity("event", testEvent);
    }

    final var refs = sess.setString("colPath", val.getColPath())
                         .setString("uid", val.getUid())
                         .getList();

    String res = null;

    if (!refs.isEmpty()) {
      res = (String)refs.iterator().next();
    }

    return res;
  }

  private final static String collectionNameExistsQuery =
          "select count(*) from BwEventObj ev " +
                  "where ev.tombstoned = false and ";

  private final static String collectionNameAnnotationExistsQuery =
          "select count(*) from BwEventAnnotation ev " +
                  "where ev.tombstoned = false and ";

  @Override
  public boolean collectionNameExists(final BwEvent val,
                                      final boolean annotation,
                                      final boolean adding) {
    final StringBuilder sb = new StringBuilder();

    if (!annotation) {
      sb.append(collectionNameExistsQuery);
    } else {
      sb.append(collectionNameAnnotationExistsQuery);
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

    /* See above note
      createNoFlushQuery(sb.toString());
      */

    final var sess = createQuery(sb.toString());

    if (testEvent != null) {
      sess.setEntity("event", testEvent);
    }

    final var refs = sess.setString("colPath", val.getColPath())
                         .setString("name", val.getName())
                         .getList();

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
          "select ev from BwEventAnnotation ev " +
                  " where target=:target";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwEventAnnotation> getAnnotations(
          final BwEvent val) {
    return (Collection<BwEventAnnotation>)createQuery(getAnnotationsQuery)
                    .setEntity("target", val).getList();
  }

  @Override
  public <T extends BwEvent> List<T> eventQuery(
          final Class<T> cl,
          final String colPath,
          final String guid,
          final BwEvent master,
          final Boolean overrides) {
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

    final var sess = getSess();
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
