/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.rw.common;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.FilterDefsDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreFilterDefsI;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.util.AccessChecker;

import java.util.Collection;

/**
 * User: mike Date: 2/1/20 Time: 14:49
 */
public class CoreFilterDefs extends CalintfHelper
        implements CoreFilterDefsI {
  private final FilterDefsDAO dao;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CoreFilterDefs(final FilterDefsDAO dao,
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
  public void add(final BwFilterDef val,
                  final BwPrincipal<?> owner) {
    final BwFilterDef fd = dao.fetch(val.getName(), owner);

    if (fd != null) {
      throw new BedeworkException(CalFacadeErrorCode.duplicateFilter,
                                  val.getName());
    }

    dao.add(val);
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal<?> owner) {
    return dao.fetch(name, owner);
  }

  @Override
  public Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal<?> owner) {
    return dao.getAllFilterDefs(owner);
  }

  @Override
  public void update(final BwFilterDef val) {
    dao.update(val);
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal<?> owner) {
    final BwFilterDef fd = dao.fetch(name, owner);

    if (fd == null) {
      throw new BedeworkException(CalFacadeErrorCode.unknownFilter, name);
    }

    dao.delete(fd);
  }
}
