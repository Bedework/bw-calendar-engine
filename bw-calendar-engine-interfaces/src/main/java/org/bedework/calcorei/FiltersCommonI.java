/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcorei;

import org.bedework.calfacade.BwEvent;

import java.io.Serializable;

/**
 * User: mike Date: 6/19/18 Time: 00:08
 */
public interface FiltersCommonI extends Serializable {
  /** This should only be called for override/annotation processing
   *
   * @param ev an event
   * @param userHref - current user for whom we are filtering
   * @return true for a match
   */
  boolean postFilter(final BwEvent ev,
                     final String userHref);
}
