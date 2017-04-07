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
package org.bedework.calsvc;

import org.bedework.access.PrivilegeDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calsvci.ResourcesI;

import java.util.List;

/** This acts as an interface to the database for resources.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class ResourcesImpl extends CalSvcDb implements ResourcesI {
  /* Used for resource manipulation */

  /** Constructor
   *
   * @param svci service interface
   */
  ResourcesImpl(final CalSvc svci) {
    super(svci);
  }

  public boolean saveNotification(final String path,
                           final BwResource val) throws CalFacadeException {
    return save(path, val, true, true);
  }

  @Override
  public boolean save(final String path,
                      final BwResource val,
                      final boolean returnIfExists) throws CalFacadeException {
    return save(path, val, false, returnIfExists);
  }

  @Override
  public BwResource get(final String path) throws CalFacadeException {
    final CollectionAndName cn = getCollectionAndName(path);

    return getCal().getResource(cn.name, cn.coll, PrivilegeDefs.privRead);
  }

  @Override
  public void getContent(final BwResource val) throws CalFacadeException {
    getCal().getResourceContent(val);
  }

  @Override
  public List<BwResource> getAll(final String path) throws CalFacadeException {
    return getCal().getAllResources(path, false, null);
  }

  @Override
  public List<BwResource> get(final String path,
                              final int start,
                              final int count) throws CalFacadeException {
    return getCal().getNResources(path, start, count);
  }

  @Override
  public void update(final BwResource val,
                     final boolean updateContent) throws CalFacadeException {
    checkAccess(val, PrivilegeDefs.privWrite, false);

    try {
      val.updateLastmod(getCurrentTimestamp());

      getCal().saveOrUpdate(val);

      if (updateContent && (val.getContent() != null)) {
        final BwResourceContent rc = val.getContent();
        rc.setColPath(val.getColPath());
        rc.setName(val.getName());

        getCal().saveOrUpdate(rc);
      }

      touchCalendar(getCols().get(val.getColPath()));
    } catch (final CalFacadeException cfe) {
      getSvc().rollbackTransaction();
      throw cfe;
    }
  }

  @Override
  public void delete(final String path) throws CalFacadeException {
    final CollectionAndName cn = getCollectionAndName(path);

    final BwResource r = getCal().getResource(cn.name,
                                              cn.coll, PrivilegeDefs.privUnbind);

    if (r == null) {
      throw new CalFacadeException(CalFacadeException.unknownResource, path);
    }

    try {
      getContent(r);
    } catch (final CalFacadeException cfe) {
      if (!cfe.getMessage().equals(CalFacadeException.missingResourceContent)) {
        getSvc().rollbackTransaction();
        throw cfe;
      }

      // Otherwise we just swallow it
    }

    /* Remove any previous tombstoned version */
    final BwResource tr = getCal().getResource(cn.name + BwResource.tombstonedSuffix,
                                               cn.coll, PrivilegeDefs.privUnbind);

    if (tr != null) {
      getCal().delete(tr);
    }

    final BwResourceContent rc = r.getContent();

    r.setContent(null);
    r.tombstone();
    r.updateLastmod(getCurrentTimestamp());

    getCal().saveOrUpdate(r);

    if (rc != null) {
      getCal().delete(rc);
    }

    touchCalendar(cn.coll);
  }

  @Override
  public boolean copyMove(final BwResource val,
                          final String to,
                          final String name,
                          final boolean copy,
                          final boolean overwrite) throws CalFacadeException {
    try {
      setupSharableEntity(val, getPrincipal().getPrincipalRef());

      final BwCalendar collTo = getCols().get(to);

      if (collTo == null) {
        throw new CalFacadeException(CalFacadeException.collectionNotFound, to);
      }

      if (collTo.getCalType() != BwCalendar.calTypeFolder) {
        // Only allowed into a folder.
        throw new CalFacadeException(CalFacadeException.badRequest, to);
      }

      final int access;
      if (copy) {
        access = PrivilegeDefs.privWrite;
      } else {
        access = PrivilegeDefs.privBind;
      }
      checkAccess(collTo, access, false);

      BwResource r = getCal().getResource(val.getName(), collTo, access);
      boolean createdNew = false;

      getContent(val);

      if (r != null) {
        /* Update of the target from the source */
        if (!overwrite) {
          throw new CalFacadeException(CalFacadeException.targetExists,
                                       val.getName());
        }

        getContent(r);
        r.setContentType(val.getContentType());

        final BwResourceContent rc = r.getContent();
        final BwResourceContent toRc = val.getContent();

        r.setContentLength(toRc.getValue().length());
        r.updateLastmod(getCurrentTimestamp());

        rc.setValue(val.getContent().getValue());

        getCal().saveOrUpdate(r);
        getCal().saveOrUpdate(rc);
      } else {
        /* Create a new resource */

        r = new BwResource();

        setupSharableEntity(r, getPrincipal().getPrincipalRef());

        r.setName(name);
        r.setColPath(collTo.getPath());
        r.setContentType(val.getContentType());
        r.setContentLength(val.getContentLength());
        r.updateLastmod(getCurrentTimestamp());

        getCal().saveOrUpdate(r);

        final BwResourceContent fromRc = val.getContent();
        final BwResourceContent rc = new BwResourceContent();

        rc.setColPath(collTo.getPath());
        rc.setName(val.getName());
        rc.setValue(fromRc.getValue());

        getCal().saveOrUpdate(rc);

        createdNew = true;
      }

      if (!copy) {
        // Delete (tombstone) the old one

        final BwCalendar collFrom = getCols().get(val.getColPath());
        checkAccess(collFrom, PrivilegeDefs.privUnbind, false);

        final BwResourceContent rc = val.getContent();

        getCal().delete(rc);

        /* Remove any previous tombstoned version */
        final BwResource tr = getCal().getResource(val.getName() +
                                                           BwResource.tombstonedSuffix,
                                                   collFrom, PrivilegeDefs.privUnbind);

        if (tr != null) {
          getCal().delete(tr);
        }

        val.setContent(null);
        val.tombstone();
        val.updateLastmod(getCurrentTimestamp());

        getCal().saveOrUpdate(val);
        touchCalendar(collFrom);
      }

      touchCalendar(collTo);

      return createdNew;
    } catch (final CalFacadeException cfe) {
      getSvc().rollbackTransaction();
      throw cfe;
    } catch (final Throwable t) {
      getSvc().rollbackTransaction();
      throw new CalFacadeException(t);
    }
  }

  List<BwResource> getSynchResources(final String path,
                                     final String lastmod) throws CalFacadeException {
    return getCal().getAllResources(path, true, lastmod);
  }

  private boolean save(final String path,
                       final BwResource val,
                       final boolean forNotification,
                       final boolean returnIfExists) throws CalFacadeException {
    try {
      final BwCalendar coll = getCols().get(path);

      if (coll == null) {
        throw new CalFacadeException(CalFacadeException.collectionNotFound, path);
      }

      if (forNotification) {
        // We allow this for subscription only
        if (coll.getCalType() != BwCalendar.calTypeNotifications) {
          throw new CalFacadeException(CalFacadeException.badRequest, path);
        }
      } else if (getPrincipalInfo().getSubscriptionsOnly()) {
        throw new CalFacadeForbidden("User has read only access");
      }

      final BwResource r = getCal().getResource(val.getName(),
                                                coll, PrivilegeDefs.privAny);

      if (r != null) {
        if (returnIfExists) {
          return false;
        }

        throw new CalFacadeException(CalFacadeException.duplicateResource,
                                     val.getName());
      }

      final BwResourceContent rc = val.getContent();

      if (rc == null) {
        throw new CalFacadeException(CalFacadeException.missingResourceContent);
      }

      setupSharableEntity(val, getPrincipal().getPrincipalRef());

      val.setColPath(path);

      if ((coll.getCalType() == BwCalendar.calTypeCalendarCollection) ||
              (coll.getCalType() == BwCalendar.calTypeExtSub)) {
        throw new CalFacadeException(CalFacadeException.badRequest, path);
      }

      checkAccess(coll, PrivilegeDefs.privBind, false);

      val.updateLastmod(getCurrentTimestamp());
      getCal().add(val);

      rc.setColPath(val.getColPath());
      rc.setName(val.getName());

      getCal().add(rc);

      touchCalendar(coll);

      return true;
    } catch (final CalFacadeException cfe) {
      getSvc().rollbackTransaction();
      throw cfe;
    }
  }
}
