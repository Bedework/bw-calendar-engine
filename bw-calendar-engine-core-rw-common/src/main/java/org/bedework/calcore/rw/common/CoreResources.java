/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.rw.common;

import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.response.GetEntityResponse;
import org.bedework.base.response.Response;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.ResourcesDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreResourcesI;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.util.AccessChecker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeResource;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResourceContent;

/**
 * User: mike Date: 2/1/20 Time: 14:49
 */
public class CoreResources extends CalintfHelper
        implements CoreResourcesI {
  private final ResourcesDAO entityDao;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CoreResources(final ResourcesDAO dao,
                       final Calintf intf,
                       final AccessChecker ac,
                       final boolean sessionless) {
    entityDao = dao;
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    entityDao.rollback();
    throw be;
  }

  @Override
  public BwResource getResource(final String href,
                                final int desiredAccess) {
    final int pos = href.lastIndexOf("/");
    if (pos <= 0) {
      throw new RuntimeException("Bad href: " + href);
    }

    final String name = href.substring(pos + 1);

    final String colPath = href.substring(0, pos);

    if (debug()) {
      debug("Get resource " + colPath + " -> " + name);
    }
    final BwResource res = entityDao.getResource(name, colPath,
                                                 desiredAccess);
    if (res == null) {
      return null;
    }

    final CurrentAccess ca =
            getAccessChecker().checkAccess(res, desiredAccess, true);

    if (!ca.getAccessAllowed()) {
      return null;
    }

    return res;
  }

  @Override
  public GetEntityResponse<BwResource> fetchResource(final String href,
                                                     final int desiredAccess) {
    final int pos = href.lastIndexOf("/");
    if (pos <= 0) {
      throw new BedeworkException("Bad href: " + href);
    }

    final String name = href.substring(pos + 1);

    final String colPath = href.substring(0, pos);

    if (debug()) {
      debug("Fetch resource " + colPath + " -> " + name);
    }

    final GetEntityResponse<BwResource> resp = new GetEntityResponse<>();

    try {
      final BwResource res = entityDao.getResource(name, colPath,
                                                   desiredAccess);
      if (res == null) {
        return resp.notFound();
      }

      final CurrentAccess ca =
              getAccessChecker()
                      .checkAccess(res, desiredAccess, true);

      if (!ca.getAccessAllowed()) {
        return resp.notOk(Response.Status.forbidden);
      }

      resp.setEntity(res);
      return resp;
    } catch (final BedeworkException be) {
      return resp.error(be);
    }
  }

  @Override
  public void getResourceContent(final BwResource val) {
    entityDao.getResourceContent(val);
  }

  @Override
  public List<BwResource> getResources(final String path,
                                       final boolean forSynch,
                                       final String token,
                                       final int count) {
    return postProcess(entityDao.getAllResources(path,
                                                 forSynch,
                                                 token,
                                                 count));
  }

  @Override
  public void add(final BwResource val) {
    entityDao.add(val);

    intf.indexEntity(val);
  }

  @Override
  public void addContent(final BwResource r,
                         final BwResourceContent rc) {
    removeTombstoned(r);

    entityDao.add(rc);

    intf.indexEntity(rc);
  }

  @Override
  public void update(final BwResource val) {
    entityDao.update(val);
    intf.indexEntity(val);
  }

  @Override
  public void updateContent(final BwResource r,
                            final BwResourceContent val) {
    entityDao.update(val);
    intf.indexEntity(val);
  }

  @Override
  public void deleteResource(final String href) {
    // if it exists we'll try to delete.
    // If not we'll try to unindex.
    final GetEntityResponse<BwResource> ger =
            fetchResource(href, PrivilegeDefs.privUnbind);

    if (ger.isOk()) {
      try {
        delete(ger.getEntity());
      } catch (final BedeworkException ignored) {
        // ignore - we'll probably fail later
      }

      return;
    }

    if (ger.getStatus() != Response.Status.notFound) {
      return; // ignore
    }

    /* It's not in the db. It might be in the index if there was some
       previous error. We will have reported it for e.g. a propfind
       Unindex it.
     */

    try {
      intf.getIndexer(docTypeResource).unindexEntity(href);
      intf.getIndexer(docTypeResourceContent).unindexEntity(href);
    } catch (final BedeworkException be) {
      error(be);
    }
  }

  @Override
  public void delete(final BwResource r) {
    removeTombstoned(r);

    // Have to unindex - the name gets changed with a suffix.
    intf.getIndexer(docTypeResource).unindexEntity(r.getHref());

    final BwResourceContent rc = r.getContent();

    r.setContent(null);
    r.tombstone();
    r.updateLastmod(intf.getCurrentTimestamp());

    entityDao.update(r);

    if (rc != null) {
      deleteContent(r, rc);
    }

    intf.indexEntity(r);
    intf.touchCollection(r.getColPath());
  }

  @Override
  public void deleteContent(final BwResource r,
                            final BwResourceContent val) {
    entityDao.delete(val);
    intf.getIndexer(docTypeResourceContent).unindexEntity(val.getHref());
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void removeTombstoned(final BwResource r) {
    final BwResource tr =
            getResource(r.getHref() + BwResource.tombstonedSuffix,
                        PrivilegeDefs.privUnbind);

    if (tr != null) {
      // Just delete resource - content was deleted when tombstoned
      entityDao.delete(tr);
      getIndexer(tr).unindexEntity(tr.getHref());
    }
  }

  private List<BwResource> postProcess(final Collection<BwResource> ress) {
    final List<BwResource> resChecked = new ArrayList<>();

    for (final BwResource res: ress) {
      if (getAccessChecker().checkAccess(res,
                                         PrivilegeDefs.privRead,
                                         true).getAccessAllowed()) {
        resChecked.add(res);
      }
    }

    return resChecked;
  }
}
