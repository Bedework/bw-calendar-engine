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

import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.sysevents.events.CollectionDeletedEvent;
import org.bedework.sysevents.events.CollectionUpdateEvent;
import org.bedework.sysevents.events.EntityDeletedEvent;
import org.bedework.sysevents.events.EntityEvent;
import org.bedework.sysevents.events.EntityUpdateEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.SysEventBase;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeEvent;

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
  transient private BwIndexer publicIndexer;

  transient private BwIndexer userIndexer;

  transient private String userIndexerPrincipal;

  protected long collectionsUpdated;
  protected long collectionsDeleted;

  protected long entitiesUpdated;
  protected long entitiesDeleted;

  /**
   * @param props index properties
   */
  public MessageProcessor(final IndexProperties props) {
    super("MessageProcessor", props.getAccount(), null);
  }

  /**
   * @param msg the incoming message
   */
  public void processMessage(final SysEvent msg) {
    if (debug()) {
      debug("Event " + msg.getSysCode());
    }

    if ((msg instanceof EntityEvent) &&
            (msg.getSysCode() == SysEventBase.SysCode.REINDEX_EVENT)) {
      doEntityReindex((EntityEvent)msg);
      return;
    }

    if (msg instanceof CollectionUpdateEvent) {
      collectionsUpdated++;
      return;
    }

    if (msg instanceof EntityUpdateEvent) {
      entitiesUpdated++;
      return;
    }

    if (msg instanceof EntityDeletedEvent) {
      entitiesDeleted++;
      return;
    }

    if (msg instanceof CollectionDeletedEvent) {
      collectionsDeleted++;
    }
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

  /*
   * ====================================================================
   * Private methods
   * ====================================================================
   */

  private void doEntityReindex(final EntityEvent ece) {
    setCurrentPrincipal(ece.getOwnerHref());

    try (BwSvc bw = getBw()) {
      final EventInfo val = getEvent(bw.getSvci(),
                                     getParentPath(ece.getHref()),
                                     getName(ece.getHref()));
      if (val == null) {
        // Unindex it
        if (debug()) {
          debug("Missing event: " + ece.getHref());
        }
        getIndexer(bw.getSvci(), null,
                   docTypeEvent).unindexEntity(ece.getHref());
      } else {
        getIndexer(bw.getSvci(), val,
                   docTypeEvent).indexEntity(val);
      }
    } catch (final Throwable t) {
      error(t);
    }
  }

  @SuppressWarnings("rawtypes")
  private BwIndexer getIndexer(final CalSvcI svci,
                               final Object val,
                               final String docType) {
    boolean publick = false;

    String principal = null;

    BwOwnedDbentity ent = null;

    if (val == null) {
      principal = this.principal;
      publick = principal.equals(BwPrincipal.publicUserHref);
    } else if (val instanceof BwOwnedDbentity) {
      ent = (BwOwnedDbentity)val;
    } else if (val instanceof EventInfo) {
      ent = ((EventInfo)val).getEvent();
    } else {
      error("Cannot index class: " + val.getClass());
      throw new BedeworkException("org.bedework.index.unexpected.class");
    }

    if (ent != null) {
      if (ent.getPublick() == null) {
        debug("This is wrong");
      }
      publick = ent.getPublick();
      principal = ent.getOwnerHref();
    }

    return getIndexer(svci, publick, principal, docType);
  }

  private BwIndexer getIndexer(final CalSvcI svci,
                               final boolean publick,
                               final String principal,
                               final String docType) {
    try {
      if (publick) {
        if (publicIndexer == null) {
          publicIndexer = svci.getIndexer(true, docType);
        }
        return publicIndexer;
      }

      if ((userIndexerPrincipal != null) &&
              (!userIndexerPrincipal.equals(principal))) {
        userIndexer = null;
      }

      if (userIndexer == null) {
        userIndexer = svci.getIndexer(principal, docType);
        userIndexerPrincipal = principal;
      }

      return userIndexer;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }
}
