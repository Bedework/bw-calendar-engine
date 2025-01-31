/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.rw.common;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.CalSuitesDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreCalSuitesI;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.util.AccessChecker;

import java.util.Collection;

import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;

/**
 * User: mike Date: 2/1/20 Time: 14:49
 */
public class CoreCalSuites extends CalintfHelper
        implements CoreCalSuitesI {
  private final CalSuitesDAO dao;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CoreCalSuites(final CalSuitesDAO dao,
                       final Calintf intf,
                       final AccessChecker ac,
                       final boolean sessionless) {
    this.dao = dao;
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    dao.rollback();
    throw be;
  }

  @Override
  public BwCalSuite get(final BwAdminGroup group) {
    return dao.get(group);
  }

  @Override
  public BwCalSuite getCalSuite(final String name) {
    return dao.getCalSuite(name);
  }

  @Override
  public Collection<BwCalSuite> getAllCalSuites() {
    return dao.getAllCalSuites();
  }

  @Override
  public void add(final BwCalSuite val) {
    dao.add(val);
    indexEntity(val);
  }

  @Override
  public void update(final BwCalSuite val) {
    dao.update(val);
    indexEntity(val);
  }

  @Override
  public void delete(final BwCalSuite val) {
    dao.delete(val);
    intf.getIndexer(docTypePrincipal).unindexEntity(val.getHref());
  }
}
