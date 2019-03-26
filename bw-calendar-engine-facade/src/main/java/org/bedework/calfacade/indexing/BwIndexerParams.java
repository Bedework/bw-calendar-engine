/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.indexing;

import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.util.AccessChecker;

/**
 * Everything except the doctype and index name
 * User: mike Date: 9/5/18 Time: 10:04
 */
public class BwIndexerParams {
  public final Configurations configs;
  public final boolean publick;
  public final String principalHref;
  public final boolean superUser;
  public final int currentMode;
  public final AccessChecker accessCheck;

  public BwIndexerParams(final Configurations configs,
                         final boolean publick,
                         final String principalHref,
                         final boolean superUser,
                         final int currentMode,
                         final AccessChecker accessCheck) {
    this.configs = configs;
    this.publick = publick;
    this.principalHref = principalHref;
    this.superUser = superUser;
    this.currentMode = currentMode;
    this.accessCheck = accessCheck;
  }
}
