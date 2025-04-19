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
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.exc.BedeworkForbidden;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calsvci.ResourcesI;
import org.bedework.util.misc.Util;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.lang.String.format;
import static org.bedework.calfacade.exc.CalFacadeErrorCode.collectionNotFound;

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

  public boolean saveNotification(final BwResource val) {
    return save(val, true, true);
  }

  @Override
  public boolean save(final BwResource val,
                      final boolean returnIfExists) {
    return save(val, false, returnIfExists);
  }

  @Override
  public BwResource get(final String path) {
    return getCal().getResource(path, PrivilegeDefs.privRead);
  }

  @Override
  public void getContent(final BwResource val) {
    getCal().getResourceContent(val);
  }

  @Override
  public List<BwResource> getAll(final String path) {
    return getCal().getResources(path, false, null, -1);
  }

  @Override
  public List<BwResource> get(final String path,
                              final int count) {
    return getCal().getResources(path, false, null, count);
  }

  @Override
  public void update(final BwResource val,
                     final boolean updateContent) {
    checkAccess(val, PrivilegeDefs.privWrite, false);

    try {
      val.updateLastmod(getCurrentTimestamp());

      getCal().update(val);

      if (updateContent && (val.getContent() != null)) {
        final BwResourceContent rc = val.getContent();
        rc.setColPath(val.getColPath());
        rc.setName(val.getName());

        getCal().updateContent(val, rc);
      }

      getSvc().touchCollection(getCols().get(val.getColPath()));
    } catch (final BedeworkException be) {
      getSvc().rollbackTransaction();
      throw be;
    }
  }

  @Override
  public void delete(final String path) {
    getCal().deleteResource(path);
  }

  @Override
  public boolean copyMove(final BwResource val,
                          final String to,
                          final String name,
                          final boolean copy,
                          final boolean overwrite) {
    try {
      getSvc().setupSharableEntity(val, getPrincipal().getPrincipalRef());

      final BwCollection collTo = getCols().get(to);

      if (collTo == null) {
        throw new BedeworkException(collectionNotFound, to);
      }

      if (collTo.getCalType() != BwCollection.calTypeFolder) {
        // Only allowed into a folder.
        throw new BedeworkException(CalFacadeErrorCode.badRequest, to);
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
          throw new BedeworkException(CalFacadeErrorCode.targetExists,
                                      val.getName());
        }

        getContent(r);
        r.setContentType(val.getContentType());
        r.setContentLength(val.getContentLength());
        r.updateLastmod(getCurrentTimestamp());

        final BwResourceContent rc = r.getContent();

        rc.setByteValue(val.getContent().getByteValue());

        getCal().update(r);
        getCal().updateContent(r, rc);
      } else {
        /* Create a new resource */

        r = new BwResource();

        getSvc().setupSharableEntity(r, getPrincipal().getPrincipalRef());

        r.setName(name);
        r.setColPath(collTo.getPath());
        r.setContentType(val.getContentType());
        r.setContentLength(val.getContentLength());
        r.updateLastmod(getCurrentTimestamp());

        getCal().add(r);

        final BwResourceContent fromRc = val.getContent();
        final BwResourceContent rc = new BwResourceContent();

        rc.setColPath(collTo.getPath());
        rc.setName(val.getName());
        rc.setByteValue(fromRc.getByteValue());

        getCal().addContent(val, rc);

        createdNew = true;
      }

      if (!copy) {
        // Delete the old one

        final BwCollection collFrom = getCols().get(val.getColPath());
        checkAccess(collFrom, PrivilegeDefs.privUnbind, false);

        getCal().delete(val);
        getSvc().touchCollection(val.getColPath());
      }

      getSvc().touchCollection(to);

      return createdNew;
    } catch (final BedeworkException be) {
      getSvc().rollbackTransaction();
      throw be;
    } catch (final Throwable t) {
      getSvc().rollbackTransaction();
      throw new BedeworkException(t);
    }
  }

  @Override
  public ReindexCounts reindex(final BwIndexer indexer,
                               final BwIndexer contentIndexer,
                               final BwIndexer collectionIndexer) {
    final Iterator<BwResource> ents;
    final ReindexCounts res = new ReindexCounts();

    if (isPublicAdmin()) {
      ents = getSvc().getPublicObjectIterator(BwResource.class);
    } else {
      ents = getSvc().getPrincipalObjectIterator(BwResource.class);
    }

    final Set<String> checkedCollections = new TreeSet<>();
    final var cols = getSvc().getCollectionsHandler();

    while (ents.hasNext()) {
      final BwResource ent = ents.next();
      info("Resources: index resource " + ent.getHref());
      final String parentPath = ent.getColPath();

      if (ent.getTombstoned()) {
        final var token = ent.getEtagValue();
        if (!cols.getSyncTokenIsValid(token, parentPath)) {
          res.skippedTombstonedResources++;
          info(format("      skipped tombstoned resource %s",
                      ent.getHref()));
          continue;
        }
      } else {
        try {
          getContent(ent);
        } catch (final Throwable t) {
          error(t);
        }
      }

      // We might have to manufacture a collection
      boolean create = true;

      if (!checkedCollections.contains(parentPath)) {
        try {
          final BwCollection col = cols.get(parentPath);
          if (col != null) {
            create = false;

            if (getSvc().getCollectionsHandler().getIdx(parentPath) == null) {
              // Ensure it's indexed
              collectionIndexer.indexEntity(col);
            }
          }
        } catch (final Throwable t) {
          error(t);
        }

        if (create) {
          info("Resources: manufacture parent for resource " + ent.getHref());

          final SplitResult sr = splitUri(parentPath);
          final BwCollection parent = new BwCollection();

          parent.setCalType(BwCollection.calTypeFolder);
          parent.setColPath(sr.path);
          parent.setPath(parentPath);
          parent.setName(sr.name);
          parent.setPublick(ent.getPublick());
          parent.setOwnerHref(ent.getOwnerHref());
          parent.setCreatorHref(ent.getCreatorHref());

          try {
            // This will get indexed in the wrong place
            final BwCollection newCol =
                    getSvc().getCollectionsHandler().add(parent, sr.path);

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
      res.resources++;
      if (ent.getContent() != null) {
        contentIndexer.indexEntity(ent.getContent());
        res.resourceContents++;
      }
    }

    return res;
  }

  List<BwResource> getSynchResources(final String path,
                                     final String lastmod) {
    return getCal().getResources(path, true, lastmod, -1);
  }

  private boolean save(final BwResource val,
                       final boolean forNotification,
                       final boolean returnIfExists) {
    try {
      final String path = val.getColPath();
      if (path == null) {
        throw new BedeworkException("No col path for " + val.getName());
      }


      final BwCollection coll = getCols().get(path);

      if (coll == null) {
        throw new BedeworkException(collectionNotFound, path);
      }

      if (forNotification) {
        // We allow this for subscription only
        if (coll.getCalType() != BwCollection.calTypeNotifications) {
          throw new BedeworkException(CalFacadeErrorCode.badRequest, path);
        }
      } else if (getSvc().getPrincipalInfo().getSubscriptionsOnly()) {
        throw new BedeworkForbidden("User has read only access");
      }

      final BwResource r = getCal().getResource(val.getHref(),
                                                PrivilegeDefs.privAny);

      if (r != null) {
        if (returnIfExists) {
          return false;
        }

        throw new BedeworkException(CalFacadeErrorCode.duplicateResource,
                                     val.getName());
      }

      final BwResourceContent rc = val.getContent();

      if (rc == null) {
        throw new BedeworkException(CalFacadeErrorCode.missingResourceContent);
      }

      getSvc().setupSharableEntity(val, getPrincipal().getPrincipalRef());

      val.setColPath(path);

      if ((coll.getCalType() == BwCollection.calTypeCalendarCollection) ||
              (coll.getCalType() == BwCollection.calTypeExtSub)) {
        throw new BedeworkException(CalFacadeErrorCode.badRequest, path);
      }

      checkAccess(coll, PrivilegeDefs.privBind, false);

      val.updateLastmod(getCurrentTimestamp());
      getCal().add(val);

      rc.setColPath(val.getColPath());
      rc.setName(val.getName());

      getCal().addContent(val, rc);

      getSvc().touchCollection(coll);

      return true;
    } catch (final BedeworkException be) {
      getSvc().rollbackTransaction();
      throw be;
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
  private SplitResult splitUri(final String uri) {
    int end = uri.length();
    if (uri.endsWith("/")) {
      end--;
    }

    final int pos = uri.lastIndexOf("/", end);
    if (pos < 0) {
      // bad uri
      throw new BedeworkException("Invalid uri: " + uri);
    }

    if (pos == 0) {
      return new SplitResult(uri, null);
    }

    return new SplitResult(uri.substring(0, pos), uri.substring(pos + 1, end));
  }
}
