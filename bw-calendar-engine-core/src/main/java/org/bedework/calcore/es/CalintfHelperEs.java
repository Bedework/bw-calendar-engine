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

import org.bedework.calcore.CalintfHelper;
import org.bedework.calcore.indexing.BwIndexEsImpl;
import org.bedework.calcore.indexing.ESQueryFilter;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.sysevents.events.SysEvent;

import edu.rpi.cmt.access.Acl;
import edu.rpi.cmt.access.PrivilegeDefs;

import java.io.Serializable;
import java.sql.Timestamp;

/** Class used as basis for a number of helper classes.
 *
 * @author Mike Douglass   douglm@rpi.edu
 */
public abstract class CalintfHelperEs extends CalintfHelper
          implements CalintfDefs, PrivilegeDefs, Serializable {
  /**
   */
  public interface CalintfHelperEsCb extends Serializable {
    /**
     * @return BwIndexer
     * @throws CalFacadeException
     */
    public BwIndexer getIndexer() throws CalFacadeException;

    /** Only valid during a transaction.
     *
     * @return a timestamp from the db
     * @throws org.bedework.calfacade.exc.CalFacadeException
     */
    public Timestamp getCurrentTimestamp() throws CalFacadeException;

    /**
     * @return BwStats
     * @throws org.bedework.calfacade.exc.CalFacadeException
     */
    public BwStats getStats() throws CalFacadeException;

    /** Used to fetch a calendar from the cache - assumes any access
     *
     * @param path
     * @return BwCalendar
     * @throws org.bedework.calfacade.exc.CalFacadeException
     */
    public BwCalendar getCollection(String path) throws CalFacadeException;

    /** Used to fetch a category from the cache - assumes any access
     *
     * @param uid
     * @return BwCategory
     * @throws org.bedework.calfacade.exc.CalFacadeException
     */
    public BwCategory getCategory(String uid) throws CalFacadeException;

    /** Used to fetch a calendar from the cache
     *
     * @param path
     * @param desiredAccess
     * @param alwaysReturn
     * @return BwCalendar
     * @throws org.bedework.calfacade.exc.CalFacadeException
     */
    public BwCalendar getCollection(String path,
                                    int desiredAccess,
                                    boolean alwaysReturn) throws CalFacadeException;

    /** Called to notify container that an event occurred. This method should
     * queue up notifications until after transaction commit as consumers
     * should only receive notifications when the actual data has been written.
     *
     * @param ev
     * @throws org.bedework.calfacade.exc.CalFacadeException
     */
    public void postNotification(final SysEvent ev) throws CalFacadeException;

    /**
     * @return true if restoring
     */
    public boolean getForRestore();
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

  private CalintfHelperEsCb calintfCb;

  private AccessChecker ac =new AccessChecker();

  protected BwCalendar getCollection(String path,
                                     int desiredAccess,
                                     boolean alwaysReturn) throws CalFacadeException {
    return getCalintfCb().getCollection(path, desiredAccess,
                                        alwaysReturn);
  }

  /**
   * @param calintfCb
   */
  public CalintfHelperEs(final CalintfHelperEsCb calintfCb) {
    this.calintfCb = calintfCb;
  }

  protected BwIndexer getIndexer() throws CalFacadeException {
    return calintfCb.getIndexer();
  }

  protected AccessChecker getAccessChecker() throws CalFacadeException {
    return ac;
  }

  protected ESQueryFilter getFilters() throws CalFacadeException {
    return ((BwIndexEsImpl)getIndexer()).getFilters();
  }

  protected CalintfHelperEsCb getCalintfCb() throws CalFacadeException {
    return calintfCb;
  }

  /** Only valid during a transaction.
   *
   * @return a timestamp from the db
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  public Timestamp getCurrentTimestamp() throws CalFacadeException {
    return calintfCb.getCurrentTimestamp();
  }

  protected BwCalendar getCollection(final String path) throws CalFacadeException {
    return calintfCb.getCollection(path);
  }

  protected boolean getForRestore() {
    return calintfCb.getForRestore();
  }

  /** Called to notify container that an event occurred. This method should
   * queue up notifications until after transaction commit as consumers
   * should only receive notifications when the actual data has been written.
   *
   * @param ev
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  public void postNotification(final SysEvent ev) throws CalFacadeException {
    calintfCb.postNotification(ev);
  }

  @Override
  protected void throwException(final CalFacadeException cfe) throws CalFacadeException {
    //getSess().rollback();
    throw cfe;
  }

  protected void throwException(final String pname) throws CalFacadeException {
    //getSess().rollback();
    throw new CalFacadeException(pname);
  }

  protected void throwException(final String pname, final String extra) throws CalFacadeException {
    //getSess().rollback();
    throw new CalFacadeException(pname, extra);
  }
}
