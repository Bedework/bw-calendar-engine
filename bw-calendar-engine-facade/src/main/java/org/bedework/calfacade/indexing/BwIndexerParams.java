/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.indexing;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.util.AccessChecker;

/**
 * Everything except the doctype and index name
 * User: mike Date: 9/5/18 Time: 10:04
 */
public class BwIndexerParams {
  public final Configurations configs;
  public final boolean publick;
  public final BwPrincipal principal;
  public final boolean superUser;
  public final int currentMode;
  public final AccessChecker accessCheck;

  public BwIndexerParams(final Configurations configs,
                         final boolean publick,
                         final BwPrincipal principal,
                         final boolean superUser,
                         final int currentMode,
                         final AccessChecker accessCheck) {
    this.configs = configs;
    this.publick = publick;
    this.principal = principal;
    this.superUser = superUser;
    this.currentMode = currentMode;
    this.accessCheck = accessCheck;
  }
}
