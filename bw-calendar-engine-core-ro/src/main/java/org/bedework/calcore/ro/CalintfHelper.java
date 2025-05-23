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
package org.bedework.calcore.ro;

import org.bedework.access.PrivilegeDefs;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.AlarmsEntity;
import org.bedework.calfacade.base.AttachmentsEntity;
import org.bedework.calfacade.base.AttendeesEntity;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.base.CommentedEntity;
import org.bedework.calfacade.base.ContactedEntity;
import org.bedework.calfacade.base.DescriptionEntity;
import org.bedework.calfacade.base.PropertiesEntity;
import org.bedework.calfacade.base.RecurrenceEntity;
import org.bedework.calfacade.base.ResourcedEntity;
import org.bedework.calfacade.base.SummaryEntity;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.util.NotificationsInfo;
import org.bedework.calfacade.wrappers.CollectionWrapper;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;

import java.io.Serializable;
import java.util.Collection;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** Class used as basis for a number of helper classes.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public abstract class CalintfHelper
        implements Logged, CalintfDefs, PrivilegeDefs, Serializable {
  protected AccessChecker ac;

  protected boolean collectTimeStats;

  protected Calintf intf;

  protected boolean sessionless;

  public SystemProperties sysprops;

  /** Initialize
   *
   * @param intf - interface for calls
   * @param ac - access checker
   * @param sessionless
   */
  public void init(final Calintf intf,
                   final AccessChecker ac,
                   final boolean sessionless) {
    this.intf = intf;
    this.ac = ac;
    this.sessionless = sessionless;
    collectTimeStats = isMetricsDebugEnabled();
  }

  public abstract <T> T throwException(BedeworkException be);

  public <T> T throwException(final String err) {
    return throwException(new BedeworkException(err));
  }

  public <T> T throwException(final String err,
                             final String extra) {
    return throwException(new BedeworkException(err, extra));
  }

  protected BwPrincipal<?> getAuthenticatedPrincipal() {
    if ((intf == null) || (intf.getPrincipalInfo() == null)) {
      return null;
    }

    return intf.getPrincipalInfo().getAuthPrincipal();
  }

  protected BwPrincipal<?> getPrincipal() {
    if ((intf == null) || (intf.getPrincipalInfo() == null)) {
      return null;
    }

    return intf.getPrincipalInfo().getPrincipal();
  }

  protected String authenticatedPrincipal() {
    final var p = getAuthenticatedPrincipal();

    if (p == null) {
      return null;
    }

    return p.getPrincipalRef();
  }

  public String currentPrincipal() {
    if (getPrincipal() == null) {
      return null;
    }

    return getPrincipal().getPrincipalRef();
  }

  public BwCollection getCollection(final String path) {
    return intf.getCollection(path, PrivilegeDefs.privAny, true);
  }

  /*
    final List<String> entityTypes = BwCollection.entityTypes.get(calType);

    final String pathTo = intf.getPrincipalInfo().getCollectionHomePath(owner);

    final GetSpecialCollectionResult gscr = new GetSpecialCollectionResult();

    if (!dao.collectionExists(pathTo)) {
      gscr.noUserHome = true;
      return gscr;
    }

    if (tryFetch){
      if (indexer != null) {
        gscr.cal = getCollectionIdx(indexer,
                                    Util.buildPath(colPathEndsWithSlash,
                                                   pathTo, "/", name),
                                    access, false);
      } else {
        gscr.cal = getCollection(Util.buildPath(colPathEndsWithSlash,
                                              pathTo, "/", name),
                               access, false);
      }

      if ((gscr.cal != null) || !create) {
        return gscr;
      }
    }

    /*
    BwCollection parent = getCollection(pathTo, privRead);

    if (parent == null) {
      throw new BedeworkException("org.bedework.calcore.collections.unabletocreate");
    }
    * /

    gscr.cal = new BwCollection();
    gscr.cal.setName(name);
    gscr.cal.setCreatorHref(owner.getPrincipalRef());
    gscr.cal.setOwnerHref(owner.getPrincipalRef());
    gscr.cal.setCalType(ctype);

    if (entityTypes != null) {
      gscr.cal.setSupportedComponents(entityTypes);
    }

    /* I think we're allowing privNone here because we don't mind if the
     * collection gets created even if the caller has no access.
     * /
    gscr.cal = add(gscr.cal, pathTo, true, access);
    gscr.created = true;

    return gscr;
  }*/

  protected boolean getForRestore() {
    return intf.getForRestore();
  }

  protected BwIndexer getIndexer(final BwOwnedDbentity<?> entity) {
    return intf.getIndexer(entity);
  }

  protected AccessChecker getAccessChecker() {
    return ac;
  }

  /*
  protected ESQueryFilter getFilters() {
    return ((BwIndexEsImpl)getIndexer()).getFilters();
  }*/

  protected void indexEntity(final EventInfo ei) {
    if (ei.getEvent().getRecurrenceId() != null) {
      // Cannot index single instance
      warn("Tried to index a recurrence instance");
      return;
    }

    if (!getForRestore()) {
      intf.indexEntity(ei);
    }
  }

  protected void indexEntity(final BwUnversionedDbentity<?> ent) {
    if (!getForRestore()) {
      intf.indexEntity(ent);
    }
  }

  protected void indexEntityNow(final BwCollection col) {
    intf.indexEntityNow(col);
  }

  protected void unindex(final BwCollection col) {
    getIndexer(col).unindexEntity(col.getHref());
  }

  /** Called to notify container that an event occurred. This method should
   * queue up notifications until after transaction commit as consumers
   * should only receive notifications when the actual data has been written.
   *
   * @param ev the system event
   */
  public void postNotification(final SysEvent ev) {
    intf.postNotification(ev);
  }

  protected BwCollection unwrap(final BwCollection val) {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CollectionWrapper)) {
      // We get these at the moment - getEvents at svci level
      return val;
      // CALWRAPPER throw new BedeworkException("org.bedework.not.wrapped");
    }

    return ((CollectionWrapper)val).fetchEntity();
  }

  protected void tombstoneEntity(final BwShareableContainedDbentity<?> val) {
    if (val instanceof AlarmsEntity) {
      clearCollection(((AlarmsEntity)val).getAlarms());
    }

    if (val instanceof AttachmentsEntity) {
      ((AttachmentsEntity)val).clearAttachments();
    }

    if (val instanceof AttendeesEntity) {
      clearCollection(((AttendeesEntity)val).getAttendees());
    }

    if (val instanceof CategorisedEntity) {
      clearCollection(((CategorisedEntity)val).getCategories());
    }

    if (val instanceof CommentedEntity) {
      clearCollection(((CommentedEntity)val).getComments());
    }

    if (val instanceof ContactedEntity) {
      clearCollection(((ContactedEntity)val).getContacts());
    }

    if (val instanceof DescriptionEntity) {
      clearCollection(((DescriptionEntity<?>)val).getDescriptions());
    }

    if (val instanceof final RecurrenceEntity re) {
      re.setRecurring(false);
      clearCollection(re.getExdates());
      clearCollection(re.getExrules());
      clearCollection(re.getRdates());
      clearCollection(re.getRrules());
    }

    if (val instanceof ResourcedEntity) {
      clearCollection(((ResourcedEntity)val).getResources());
    }

    if (val instanceof SummaryEntity) {
      clearCollection(((SummaryEntity)val).getSummaries());
    }

    if (val instanceof PropertiesEntity) {
      clearCollection(((PropertiesEntity)val).getProperties());
    }
  }

  protected void clearCollection(final Collection<?> val) {
    if (val == null) {
      return;
    }

    val.clear();
  }

  protected String fixPath(final String path) {
    return Util.buildPath(colPathEndsWithSlash, path);
  }

  protected void stat(final String name,
                      final Long startTime) {
    if (!collectTimeStats) {
      return;
    }

    postNotification(SysEvent.makeStatsEvent(name,
                                             System.currentTimeMillis() - startTime));
  }

  protected void notifyDelete(final boolean reallyDelete,
                              final BwEvent val,
                              final boolean shared) {
    final SysEvent.SysCode code;

    if (reallyDelete) {
      code = SysEvent.SysCode.ENTITY_DELETED;
    } else {
      code = SysEvent.SysCode.ENTITY_TOMBSTONED;
    }

    final String note = getChanges(code, val);
    if (note == null) {
      return;
    }

    postNotification(
            SysEvent.makeEntityDeletedEvent(code,
                                            authenticatedPrincipal(),
                                            val.getOwnerHref(),
                                            val.getHref(),
                                            shared,
                                            val.getPublick(),
                                            true, // indexed,
                                            IcalDefs.fromEntityType(
                                                    val.getEntityType()),
                                            val.getRecurrenceId(),
                                            note,
                                            null)); // XXX Emit multiple targted?
  }

  protected void notify(final SysEvent.SysCode code,
                        final BwEvent val,
                        final boolean shared) {
    final String note = getChanges(code, val);
    if (note == null) {
      return;
    }

    final boolean indexed = true;

    postNotification(
            SysEvent.makeEntityUpdateEvent(code,
                                           authenticatedPrincipal(),
                                           val.getOwnerHref(),
                                           val.getHref(),
                                           shared,
                                           indexed,
                                           val.getRecurrenceId(),
                                           note,
                                           null)); // XXX Emit multiple targeted?
  }

  protected void notifyMove(final SysEvent.SysCode code,
                            final String oldHref,
                            final boolean oldShared,
                            final BwEvent val,
                            final boolean shared) {
    postNotification(
            SysEvent.makeEntityMovedEvent(code,
                                          currentPrincipal(),
                                          val.getOwnerHref(),
                                          val.getHref(),
                                          shared,
                                          false,
                                          oldHref,
                                          oldShared));
  }

  protected void notifyInstanceChange(final SysEvent.SysCode code,
                                      final BwEvent val,
                                      final boolean shared,
                                      final String recurrenceId) {
    final String note = getChanges(code, val);
    if (note == null) {
      return;
    }

    /* We flag these as indexed. They get handled by the update for
         the master
       */
    if (code.equals(SysEvent.SysCode.ENTITY_DELETED) ||
            code.equals(SysEvent.SysCode.ENTITY_TOMBSTONED)) {
      postNotification(
              SysEvent.makeEntityDeletedEvent(code,
                                              authenticatedPrincipal(),
                                              val.getOwnerHref(),
                                              val.getHref(),
                                              shared,
                                              val.getPublick(),
                                              true, // Indexed
                                              IcalDefs.fromEntityType(
                                                      val.getEntityType()),
                                              recurrenceId,
                                              note,
                                              null)); // XXX Emit multiple targted?
    } else {
      postNotification(
              SysEvent.makeEntityUpdateEvent(code,
                                             authenticatedPrincipal(),
                                             val.getOwnerHref(),
                                             val.getHref(),
                                             shared,
                                             true, // Indexed
                                             val.getRecurrenceId(),
                                             note,  // changes
                                             null)); // XXX Emit multiple targted?
    }
  }

  protected String getChanges(final SysEvent.SysCode code,
                              final BwEvent val) {
    try {
      if (code.equals(SysEvent.SysCode.ENTITY_DELETED) ||
              code.equals(SysEvent.SysCode.ENTITY_TOMBSTONED)) {
        return NotificationsInfo.deleted(authenticatedPrincipal(),
                                         val);
      }

      if (code.equals(SysEvent.SysCode.ENTITY_UPDATED)) {
        return NotificationsInfo.updated(authenticatedPrincipal(), val);
      }

      if (code.equals(SysEvent.SysCode.ENTITY_ADDED)) {
        return NotificationsInfo.added(authenticatedPrincipal(), val);
      }

      return null;
    } catch (final Throwable t) {
      error(t);
      return null;
    }
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
