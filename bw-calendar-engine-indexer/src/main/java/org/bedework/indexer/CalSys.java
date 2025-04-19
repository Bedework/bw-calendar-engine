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
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.CalSvcIPars;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcFactory;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.EventsI;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import java.util.Collection;

import static java.lang.String.format;

/** An interface to the calendar system for the indexer.
 *
 * @author Mike Douglass
 *
 */
public abstract class CalSys implements Logged {
  protected String name;

  protected String adminAccount;
  protected String adminPrincipal;

  protected String principal;

  private final int collectionBatchSize = 10;

  private final int entityBatchSize = 50;

  private final int principalBatchSize = 10;

  protected static ThreadPool entityThreadPool;
  protected static ThreadPool principalThreadPool;

  /**
   * @param name for object
   * @param adminAccount admin account to use
   * @param principal href
   */
  public CalSys(final String name,
                final String adminAccount,
                final String principal) {
    this.name = name;
    this.adminAccount = adminAccount;
    this.principal = principal;

    adminPrincipal = "/principals/users/" + adminAccount;
  }

  protected void setThreadPools(final int maxEntityThreads,
                                final int maxPrincipalThreads) {
    entityThreadPool = new ThreadPool("Entity", maxEntityThreads);
    principalThreadPool = new ThreadPool("Principal", maxPrincipalThreads);
  }

  /**
   *
   */
  public void checkThreads() {
    entityThreadPool.checkThreads();
    principalThreadPool.checkThreads();
  }

  /**
   *
   */
  public void join() {
    entityThreadPool.waitForProcessors();
    principalThreadPool.waitForProcessors();
  }

  protected IndexerThread getEntityThread(final Processor proc) {
    return entityThreadPool.getThread(proc);
  }

  protected IndexerThread getPrincipalThread(final Processor proc) {
    return principalThreadPool.getThread(proc);
  }

  /**
   * @param principal the href
   */
  public void setCurrentPrincipal(final String principal) {
    this.principal = principal;
  }

  protected String getParentPath(final String href) {
    final int pos = href.lastIndexOf("/");

    if (pos <= 0) {
      return null;
    }

    return href.substring(0, pos);
  }

  protected String getName(final String href) {
    final int pos = href.lastIndexOf("/");

    if (pos <= 0) {
      return href;
    }

    if (pos == href.length() - 1) {
      return null;
    }

    return href.substring(pos + 1);
  }

  static class BwSvc implements AutoCloseable {
    private final static CalSvcFactory factory = new CalSvcFactoryDefault();
    private CalSvcI svci;

    /* Who the svci object is for */
    private String curAccount;
    private boolean curPublicAdmin;

    private String principal;
    private final String adminAccount;

    BwSvc(final String principal,
            final String adminAccount) {
      this.principal = principal;
      this.adminAccount = adminAccount;
    }

    BwSvc(final String adminAccount) {
      this.adminAccount = adminAccount;
    }

    /**
     * Get an svci object and return it. Also embed it in this
     * object.
     *
     * @return svci object
       */
    public CalSvcI getSvci() {
      if ((svci != null) && svci.isOpen()) {
        // We shouldn't need to check if it's the same account.
        return svci;
      }

      String account = adminAccount;
      boolean publicAdmin = true;
      final String userPrincipalPrefix = "/principals/users/";

      if (principal != null) {
        if (principal.startsWith(userPrincipalPrefix)) {
          account = principal.substring(userPrincipalPrefix.length());

          if (account.endsWith("/")) {
            account = account.substring(0, account.length() - 1);
          }
        }

        publicAdmin = false;
      }

      if ((svci == null) ||
              !account.equals(curAccount) ||
              (publicAdmin != curPublicAdmin)) {
        curAccount = account;
        curPublicAdmin = publicAdmin;

        final CalSvcIPars pars = CalSvcIPars.getIndexerPars(account,
                                                            publicAdmin);   // Allow super user
        svci = factory.getSvc(
                getClass().getClassLoader(), pars);
      }

      svci.open();
      svci.beginTransaction();

      return svci;
    }

    @Override
    public void close() {
      if ((svci == null) || !svci.isOpen()) {
        return;
      }

      close(svci);

      svci = null;
    }

    /**
     * @param svci service interface
     */
    public void close(final CalSvcI svci) {
      if ((svci == null) || !svci.isOpen()) {
        return;
      }

      try {
        svci.endTransaction();
      } catch (final Throwable ignored) {
      }

      try {
        svci.close();
      } catch (final Throwable ignored) {
      }
    }
  }

  /**
   * @return Bw object for principal use
   */
  public BwSvc getBw() {
    return new BwSvc(principal, adminAccount);
  }

  /**
   * @return Bw object for admin use
   */
  public BwSvc getAdminBw() {
    return new BwSvc(adminAccount);
  }

  protected boolean hasAccess(final BwCollection col) {
    // XXX This should do a real access check so we can index subscriptions.

    if (col.getPublick()) {
      return true;
    }

    //if (publick) {
    //  return false;
    //}

    if (principal == null) {
      // We aren't handling a principal yet.
      return true;
    }

    return col.getCreatorHref().equals(principal);
  }

  protected boolean hasAccess(final BwEvent ent) {
    // XXX This should do a real access check so we can index subscriptions.

    if (ent.getPublick()) {
      return true;
    }

    //if (publick) {
    //  return false;
    //}

    return ent.getOwnerHref().equals(principal);
  }

  private String showPrincipal() {
    if (principal != null) {
      return "principal=" + principal;
    }

    return "account=" + adminAccount;
  }

  /**
   *
   */
  public static class Refs {
    /** Where we are in the list */
    public int index;

    /** How many to request */
    public int batchSize;

    /** List of references - names or hrefs depending on context */
    public Collection<String> refs;
  }

  /** Get the next batch of principal hrefs.
   *
   * @param refs - null on first call.
   * @return next batch of hrefs or null for no more.
   */
  protected Refs getPrincipalHrefs(final Refs refs) {
    Refs r = refs;

    if (r == null) {
      r = new Refs();
      r.batchSize = principalBatchSize;
    }

    try (final BwSvc bw = getAdminBw()) {
      r.refs = bw.getSvci().getUsersHandler().getPrincipalHrefs(r.index, r.batchSize);

      if (r.refs == null) {
        info(format("    getPrincipalHrefs(%s): Found none",
                    principal));
        return null;
      };

      final var found = r.refs.size();
      info(format("    getPrincipalHrefs(%s): Found %d",
                  principal, found));

      r.index += found;

      return r;
    } catch (final BedeworkException be) {
      error(be);
      return null;
    }
  }

  /** Get the next batch of child collection paths.
   *
   * @param col - parent
   * @param refs - null on first call.
   * @return next batch of hrefs or null for no more.
   */
  protected Refs getChildCollections(final BwCollection col,
                                     final Refs refs) {
    Refs r = refs;

    if (r == null) {
      r = new Refs();
      r.batchSize = collectionBatchSize;
    }

    final var start = r.index;

    final var path = col.getPath();
    info(format("getChildCollections(%s): start=%d",
                path, start));

    try (final BwSvc bw = getAdminBw()) {
      if (!hasAccess(col)) {
        error(format("      No access to %s for %s",
                     path, principal));
        return null;
      }

      r.refs = bw.getSvci().getAdminHandler().getChildCollections(path, r.index, r.batchSize);

      if (r.refs == null) {
        info(format("    getChildCollections(%s): Found none",
                    path));
        return null;
      };

      final var found = r.refs.size();
      info(format("    getChildCollections(%s): Found %d",
                  path, found));

      r.index += found;

      return r;
    } catch (final BedeworkException be) {
      error(be);
      return null;
    }
  }

  /** Get the next batch of child entity names.
   *
   * @param col - parent
   * @param refs - null on first call.
   * @return next batch of hrefs or null for no more.
   */
  protected Refs getChildEntities(final BwCollection col,
                                  final Refs refs) {
    Refs r = refs;

    if (r == null) {
      r = new Refs();
      r.batchSize = entityBatchSize;
    }

    final var start = r.index;

    final var path = col.getPath();
    info(format("getChildEntities(%s): start=%d",
                path, start));

    try (final BwSvc bw = getAdminBw()) {
      if (!hasAccess(col)) {
        error(format("      No access to %s for %s",
                     path, principal));
        return null;
      }

      r.refs = bw.getSvci().getAdminHandler().
                 getChildEntities(path, r.index, r.batchSize);

      if (r.refs == null) {
        info(format("    getChildEntities(%s): Found none",
                    path));
        return null;
      };

      final var found = r.refs.size();
      info(format("    getChildEntities(%s): Found %d",
                  path, found));

      r.index += found;

      return r;
    } catch (final BedeworkException be) {
      error(be);
      return null;
    }
  }

  protected EventInfo getEvent(final CalSvcI svci,
                               final String colPath,
                               final String name) {
    final EventsI evhandler = svci.getEventsHandler();

    return evhandler.get(colPath, name);
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
