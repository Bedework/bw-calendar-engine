/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate;

import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.calcore.common.CalintfHelper;
import org.bedework.calcorei.CoreResourcesI;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

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
  private final CoreResourcesDAO entityDao;

  /** Constructor
   *
   * @param sess persistance session
   * @param intf interface
   * @param ac access checker
   * @param readOnlyMode true for a guest
   * @param sessionless if true
   */
  CoreResources(final HibSession sess,
                final CalintfImpl intf,
                final AccessChecker ac,
                final boolean readOnlyMode,
                final boolean sessionless) {
    entityDao = new CoreResourcesDAO(sess);
    this.intf = intf;
    intf.registerDao(entityDao);
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final CalFacadeException cfe)
          throws CalFacadeException {
    entityDao.rollback();
    throw cfe;
  }

  @Override
  public BwResource getResource(final String href,
                                final int desiredAccess)
          throws CalFacadeException {
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
      throw new RuntimeException("Bad href: " + href);
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
        return Response.notFound(resp);
      }

      final CurrentAccess ca =
              getAccessChecker()
                      .checkAccess(res, desiredAccess, true);

      if (!ca.getAccessAllowed()) {
        return Response.notOk(resp, Response.Status.forbidden);
      }

      resp.setEntity(res);
      return resp;
    } catch (final CalFacadeException cfe) {
      return Response.error(resp, cfe);
    }
  }

  @Override
  public void getResourceContent(final BwResource val) throws CalFacadeException {
    entityDao.getResourceContent(val);
  }

  @Override
  public List<BwResource> getResources(final String path,
                                       final boolean forSynch,
                                       final String token,
                                       final int count) throws CalFacadeException {
    return postProcess(entityDao.getAllResources(path,
                                                 forSynch,
                                                 token,
                                                 count));
  }

  @Override
  public void add(final BwResource val) throws CalFacadeException {
    entityDao.save(val);

    intf.indexEntity(val);
  }

  @Override
  public void addContent(final BwResource r,
                         final BwResourceContent rc) throws CalFacadeException {
    removeTombstoned(r);

    entityDao.save(rc);

    intf.indexEntity(rc);
  }

  @Override
  public void saveOrUpdate(final BwResource val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
    intf.indexEntity(val);
  }

  @Override
  public void saveOrUpdateContent(final BwResource r,
                                  final BwResourceContent val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
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
      } catch (final CalFacadeException cfe) {
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
    } catch (final CalFacadeException cfe) {
      error(cfe);
    }
  }

  @Override
  public void delete(final BwResource r) throws CalFacadeException {
    removeTombstoned(r);

    // Have to unindex - the name gets changed with a suffix.
    intf.getIndexer(docTypeResource).unindexEntity(r.getHref());

    final BwResourceContent rc = r.getContent();

    r.setContent(null);
    r.tombstone();
    r.updateLastmod(getCurrentTimestamp());

    entityDao.saveOrUpdate(r);

    if (rc != null) {
      deleteContent(r, rc);
    }

    intf.indexEntity(r);
    intf.touchCalendar(r.getColPath());
  }

  @Override
  public void deleteContent(final BwResource r,
                            final BwResourceContent val) throws CalFacadeException {
    entityDao.delete(val);
    intf.getIndexer(docTypeResourceContent).unindexEntity(val.getHref());
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void removeTombstoned(final BwResource r)
          throws CalFacadeException {
    final BwResource tr =
            getResource(r.getHref() + BwResource.tombstonedSuffix,
                        PrivilegeDefs.privUnbind);

    if (tr != null) {
      // Just delete resource - content was deleted when tombstoned
      entityDao.delete(tr);
      getIndexer(tr).unindexEntity(tr.getHref());
    }
  }

  private List<BwResource> postProcess(final Collection<BwResource> ress)
          throws CalFacadeException {
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
