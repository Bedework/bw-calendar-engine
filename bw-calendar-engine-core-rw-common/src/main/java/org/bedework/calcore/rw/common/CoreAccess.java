/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.rw.common;

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.AccessDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreAccessI;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.util.AccessChecker;

import java.util.Collection;

/**
 * User: mike Date: 2/1/20 Time: 14:49
 */
public class CoreAccess extends CalintfHelper
        implements CoreAccessI {
  private final AccessDAO dao;
  private final AccessChecker checker;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CoreAccess(final AccessDAO dao,
                    final Calintf intf,
                    final AccessChecker ac,
                    final boolean sessionless) {
    this.dao = dao;
    checker = ac;
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    dao.rollback();
    throw be;
  }

  @Override
  public void changeAccess(final ShareableEntity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    if (ent instanceof BwCalendar) {
      changeAccess((BwCalendar)ent, aces, replaceAll);
      return;
    }
    checker.getAccessUtil().changeAccess(ent, aces, replaceAll);
    dao.update((BwUnversionedDbentity<?>)ent);
  }

  @Override
  public void defaultAccess(final ShareableEntity ent,
                            final AceWho who) {
    if (ent instanceof BwCalendar) {
      defaultAccess((BwCalendar)ent, who);
      return;
    }
    intf.checkAccess(ent, privWriteAcl, false);
    checker.getAccessUtil().defaultAccess(ent, who);
    dao.update((BwUnversionedDbentity<?>)ent);
  }
}
