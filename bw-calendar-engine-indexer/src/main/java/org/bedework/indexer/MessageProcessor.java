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
package org.bedework.indexer;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.sysevents.events.CollectionDeletedEvent;
import org.bedework.sysevents.events.CollectionUpdateEvent;
import org.bedework.sysevents.events.EntityDeletedEvent;
import org.bedework.sysevents.events.EntityUpdateEvent;
import org.bedework.sysevents.events.SysEvent;

import org.apache.log4j.Logger;

/**
 * Class to handle incoming system event messages and fire off index processes
 * <p>
 * We assume that we do not need to figure out side effects of a change. For
 * example, if a collection is moved we will also get events for each affected
 * entity so we don't need to figure out what children are affected.
 * <p>
 * This may not hold true for recurring events. We possibly need to figure out
 * how to remove all instances and overrides. This might require a search
 * capability for path + uid
 *
 * @author douglm
 */
public class MessageProcessor extends CalSys {
  private transient Logger log;

  private final boolean debug;

  transient private BwIndexer publicIndexer;

  transient private BwIndexer userIndexer;

  transient private String userIndexerPrincipal;

  protected long collectionsUpdated;
  protected long collectionsDeleted;

  protected long entitiesUpdated;
  protected long entitiesDeleted;

  private static final int maxRetryCt = 10;

  /**
   * @param props index properties
   * @throws CalFacadeException
   */
  public MessageProcessor(final IndexProperties props) throws CalFacadeException {
    super("MessageProcessor", props.getAccount(), null);

    debug = getLog().isDebugEnabled();
  }

  /**
   * @param msg the incoming message
   * @throws CalFacadeException
   */
  public void processMessage(final SysEvent msg) throws CalFacadeException {
    for (int ct = 0; ct < maxRetryCt; ct++) {
      try {
        if (debug) {
          debugMsg("Event " + msg.getSysCode());
        }

        if (msg instanceof CollectionUpdateEvent) {
          collectionsUpdated++;
          doCollectionChange((CollectionUpdateEvent)msg);
          return;
        }

        if (msg instanceof EntityUpdateEvent) {
          entitiesUpdated++;
          doEntityChange((EntityUpdateEvent)msg);
          return;
        }

        if (msg instanceof EntityDeletedEvent) {
          entitiesDeleted++;
          doEntityDelete((EntityDeletedEvent)msg);
          return;
        }

        if (msg instanceof CollectionDeletedEvent) {
          collectionsDeleted++;
          doCollectionDelete((CollectionDeletedEvent)msg);
          return;
        }

        return;
      } catch (final CalFacadeAccessException cfae) {
        // No point in retrying this
        warn("No access (or deleted)");
        break;
      } catch (final Throwable t) {
        warn("Error indexing msg");
        if (ct == 0) {
          warn("Will retry " + maxRetryCt + " times");
        }

        error(t);
      }
    }

    warn("Failed after " + maxRetryCt + " retries");
  }

  /**
   * @return count processed
   */
  @SuppressWarnings("UnusedDeclaration")
  public long getCollectionsUpdated() {
    return collectionsUpdated;
  }

  /**
   * @return count processed
   */
  public long getCollectionsDeleted() {
    return collectionsDeleted;
  }

  /**
   * @return count processed
   */
  public long getEntitiesUpdated() {
    return entitiesUpdated;
  }

  /**
   * @return count processed
   */
  public long getEntitiesDeleted() {
    return entitiesDeleted;
  }

  private void doCollectionDelete(final CollectionDeletedEvent cde)
          throws CalFacadeException {
    try (BwSvc bw = getBw()) {
      getIndexer(bw.getSvci(),
                 cde.getPublick(), cde.getOwnerHref()).
              unindexEntity(cde.getHref());
    }
  }

  private void doCollectionChange(final CollectionUpdateEvent cce)
                                                   throws CalFacadeException {
    setCurrentPrincipal(null);

    try (BwSvc bw = getBw()) {
      final BwCalendar col = getCollection(bw.getSvci(), cce.getHref());

      if (col != null) {
        // Null if no access or removed.
        add(bw.getSvci(), col);
      }
    }
  }

  private void doEntityDelete(final EntityDeletedEvent ede)
       throws CalFacadeException {
    /* Treat the delete of a recurrence instance as an update */

    if (ede.getRecurrenceId() != null) {
      setCurrentPrincipal(ede.getOwnerHref());
      try (BwSvc bw = getBw()) {

        add(getEvent(bw.getSvci(),
                     getParentPath(ede.getHref()),
                     getName(ede.getHref())) /*, false */);
      }
    } else {
      try (BwSvc bw = getBw()) {
        getIndexer(bw.getSvci(), ede.getPublick(),
                   ede.getOwnerHref()).unindexEntity(ede.getHref());
      }
    }
  }

  private void doEntityChange(final EntityUpdateEvent ece)
       throws CalFacadeException {
    setCurrentPrincipal(ece.getOwnerHref());

    try (BwSvc bw = getBw()) {
      add(getEvent(bw.getSvci(),
                   getParentPath(ece.getHref()),
                   getName(ece.getHref())) /*, false */);
    }
  }

  /*
   * ====================================================================
   * Private methods
   * ====================================================================
   */

  private void add(final CalSvcI svci,
                   final BwCalendar val) throws CalFacadeException {
    getIndexer(svci, val).indexEntity(val);
  }

  /*
  private void add(final EventInfo val,
                   final boolean firstRecurrence) throws CalFacadeException {
    boolean first = true;
    if (!Util.isEmpty(val.getOverrides())) {
      for (EventInfo ei : val.getOverrides()) {
        add(ei, first);
        first = false;
      }
    }

    if (firstRecurrence && (val.getEvent().getRecurrenceId() != null)) {
      // Indexing an override. Reindex the master event
      BwEventProxy proxy = (BwEventProxy)val.getEvent();

      EventInfo ei = new EventInfo(proxy.getTarget());

      getIndexer(val).indexEntity(ei);
    }

    getIndexer(val).indexEntity(val);
  }
  */
  private void add(final EventInfo val) throws CalFacadeException {
    try (BwSvc bw = getBw()) {
      getIndexer(bw.getSvci(), val).indexEntity(val);
    }
  }

  @SuppressWarnings("rawtypes")
  private BwIndexer getIndexer(final CalSvcI svci,
                               final Object val) throws CalFacadeException {
    boolean publick = false;

    String principal = null;

    final BwOwnedDbentity ent;

    if (val instanceof BwOwnedDbentity) {
      ent = (BwOwnedDbentity)val;
    } else if (val instanceof EventInfo) {
      ent = ((EventInfo)val).getEvent();
    } else {
      throw new CalFacadeException("org.bedework.index.unexpected.class");
    }

    if (ent != null) {
      if (ent.getPublick() == null) {
        debugMsg("This is wrong");
      }
      publick = ent.getPublick();
      principal = ent.getOwnerHref();
    }

    return getIndexer(svci, publick, principal);
  }

  private BwIndexer getIndexer(final CalSvcI svci,
                               final boolean publick,
                               final String principal) throws CalFacadeException {
    try {
      if (publick) {
        if (publicIndexer == null) {
          publicIndexer = svci.getIndexer(true);
        }
        return publicIndexer;
      }

      if ((userIndexerPrincipal != null) &&
              (!userIndexerPrincipal.equals(principal))) {
        userIndexer = null;
      }

      if (userIndexer == null) {
        userIndexer = svci.getIndexer(principal);
      }

      return userIndexer;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  @Override
  protected void debugMsg(final String msg) {
    getLog().debug(msg);
  }

  @Override
  protected void info(final String msg) {
    getLog().info(msg);
  }

  protected void warn(final String msg) {
    getLog().warn(msg);
  }
}
