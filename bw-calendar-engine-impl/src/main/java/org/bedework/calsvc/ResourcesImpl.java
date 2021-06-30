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
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calsvci.ResourcesI;
import org.bedework.util.misc.Util;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

  public boolean saveNotification(final BwResource val)
          throws CalFacadeException {
    return save(val, true, true);
  }

  @Override
  public boolean save(final BwResource val,
                      final boolean returnIfExists) throws CalFacadeException {
    return save(val, false, returnIfExists);
  }

  @Override
  public BwResource get(final String path) throws CalFacadeException {
    return getCal().getResource(path, PrivilegeDefs.privRead);
  }

  @Override
  public void getContent(final BwResource val) throws CalFacadeException {
    getCal().getResourceContent(val);
  }

  @Override
  public List<BwResource> getAll(final String path) throws CalFacadeException {
    return getCal().getResources(path, false, null, -1);
  }

  @Override
  public List<BwResource> get(final String path,
                              final int count) throws CalFacadeException {
    return getCal().getResources(path, false, null, count);
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

        getCal().saveOrUpdateContent(val, rc);
      }

      touchCalendar(getCols().get(val.getColPath()));
    } catch (final CalFacadeException cfe) {
      getSvc().rollbackTransaction();
      throw cfe;
    }
  }

  @Override
  public void delete(final String path) throws CalFacadeException {
    getCal().deleteResource(path);
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

      BwResource r = getCal().getResource(Util.buildPath(
              BasicSystemProperties.colPathEndsWithSlash, to, "/",
              val.getName()),
                                          access);
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
        getCal().saveOrUpdateContent(r, rc);
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

        getCal().saveOrUpdateContent(val, rc);

        createdNew = true;
      }

      if (!copy) {
        // Delete the old one

        final BwCalendar collFrom = getCols().get(val.getColPath());
        checkAccess(collFrom, PrivilegeDefs.privUnbind, false);

        getCal().delete(val);
        touchCalendar(val.getColPath());
      }

      touchCalendar(to);

      return createdNew;
    } catch (final CalFacadeException cfe) {
      getSvc().rollbackTransaction();
      throw cfe;
    } catch (final Throwable t) {
      getSvc().rollbackTransaction();
      throw new CalFacadeException(t);
    }
  }

  @Override
  public int[] reindex(final BwIndexer indexer,
                       final BwIndexer contentIndexer,
                       final BwIndexer collectionIndexer) throws CalFacadeException {
    final Iterator<BwResource> ents;

    if (isPublicAdmin()) {
      ents = getSvc().getPublicObjectIterator(BwResource.class);
    } else {
      ents = getSvc().getPrincipalObjectIterator(BwResource.class);
    }

    int resCt = 0;
    int resContentCt = 0;
    final Set<String> checkedCollections = new TreeSet<>();

    while (ents.hasNext()) {
      final BwResource ent = ents.next();
      if (!ent.getTombstoned()) {
        try {
          getContent(ent);
        } catch (final Throwable t) {
          error(t);
        }
      }

      // We might have to manufacture a collection
      final String parentPath = ent.getColPath();
      boolean create = true;

      if (!checkedCollections.contains(parentPath)) {
        try {
          final BwCalendar col = getSvc().getCalendarsHandler().get(parentPath);
          if (col != null) {
            create = false;

            if (getSvc().getCalendarsHandler().getIdx(parentPath) == null) {
              // Ensure it's indexed
              collectionIndexer.indexEntity(col);
            }
          }
        } catch (final Throwable t) {
          if (debug()) {
            error(t);
          }
        }

        if (create) {
          final SplitResult sr = splitUri(parentPath);
          final BwCalendar parent = new BwCalendar();

          parent.setCalType(BwCalendar.calTypeFolder);
          parent.setColPath(sr.path);
          parent.setPath(parentPath);
          parent.setName(sr.name);
          parent.setPublick(ent.getPublick());
          parent.setOwnerHref(ent.getOwnerHref());
          parent.setCreatorHref(ent.getCreatorHref());

          try {
            // This will get indexed in the wrong place
            final BwCalendar newCol =
                    getSvc().getCalendarsHandler().add(parent, sr.path);

            collectionIndexer.indexEntity(newCol);
          } catch (final Throwable t) {
            if (debug()) {
              error(t);
            }
          }
        }

        checkedCollections.add(parentPath);
      }
      indexer.indexEntity(ent);
      resCt++;
      if (ent.getContent() != null) {
        contentIndexer.indexEntity(ent.getContent());
        resContentCt++;
      }
    }

    return new int[]{resCt, resContentCt};
  }

  List<BwResource> getSynchResources(final String path,
                                     final String lastmod) throws CalFacadeException {
    return getCal().getResources(path, true, lastmod, -1);
  }

  private boolean save(final BwResource val,
                       final boolean forNotification,
                       final boolean returnIfExists) throws CalFacadeException {
    try {
      final String path = val.getColPath();
      if (path == null) {
        throw new CalFacadeException("No col path for " + val.getName());
      }


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

      final BwResource r = getCal().getResource(val.getHref(),
                                                PrivilegeDefs.privAny);

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

      getCal().addContent(val, rc);

      touchCalendar(coll);

      return true;
    } catch (final CalFacadeException cfe) {
      getSvc().rollbackTransaction();
      throw cfe;
    }
  }

  private static class SplitResult {
    String path;
    String name;

    SplitResult(final String path, final String name) {
      this.path = path;
      this.name = name;
    }
  }

  /* Split the uri so that result.path is the path up to the name part result.name
   *
   */
  private SplitResult splitUri(final String uri)
          throws CalFacadeException {
    int end = uri.length();
    if (uri.endsWith("/")) {
      end--;
    }

    final int pos = uri.lastIndexOf("/", end);
    if (pos < 0) {
      // bad uri
      throw new CalFacadeException("Invalid uri: " + uri);
    }

    if (pos == 0) {
      return new SplitResult(uri, null);
    }

    return new SplitResult(uri.substring(0, pos), uri.substring(pos + 1, end));
  }
}
