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
package org.bedework.calcore.es;

import org.bedework.access.Acl;
import org.bedework.calcore.hibernate.CalintfHelperHib;
import org.bedework.calcore.indexing.BwIndexEsImpl;
import org.bedework.calcore.indexing.ESQueryFilter;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.sysevents.events.SysEvent;

/** Class used as basis for a number of helper classes.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public abstract class CalintfHelperEs extends CalintfHelperHib {
  /**
   */
  public interface CalintfHelperEsCb extends CalintfHelperHibCb {
    /**
     * @return BwIndexer
     * @throws CalFacadeException
     */
    BwIndexer getIndexer() throws CalFacadeException;
  }

  protected class AccessChecker implements BwIndexer.AccessChecker {
    @Override
    public Acl.CurrentAccess checkAccess(final BwShareableDbentity ent,
                                         final int desiredAccess,
                                         final boolean returnResult)
            throws CalFacadeException {
      return access.checkAccess(ent, desiredAccess, returnResult);
    }
  }

  private AccessChecker ac = new AccessChecker();

  /**
   * @param calintfCb
   */
  public CalintfHelperEs(final CalintfHelperEsCb calintfCb) {
    super(calintfCb);
  }

  protected BwIndexer getIndexer() throws CalFacadeException {
    return ((CalintfHelperEsCb)getCalintfCb()).getIndexer();
  }

  protected AccessChecker getAccessChecker() throws CalFacadeException {
    return ac;
  }

  protected ESQueryFilter getFilters() throws CalFacadeException {
    return ((BwIndexEsImpl)getIndexer()).getFilters();
  }
}
