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
package org.bedework.calcore.hibernate;

import org.bedework.access.PrivilegeDefs;
import org.bedework.calcore.CalintfHelper;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;

/** Class used as basis for a number of helper classes.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public abstract class CalintfHelperHib extends CalintfHelper
          implements CalintfDefs, PrivilegeDefs, Serializable {
  /**
   */
  public interface CalintfHelperHibCb extends Serializable {
    /**
     * @return HibSession
     * @throws CalFacadeException on error
     */
    HibSession getSess() throws CalFacadeException;
  }

  private final CalintfHelperHibCb calintfCb;

  /**
   * @param calintfCb the callback
   */
  public CalintfHelperHib(final CalintfHelperHibCb calintfCb) {
    this.calintfCb = calintfCb;
  }

  protected HibSession getSess() throws CalFacadeException {
    return calintfCb.getSess();
  }

  protected void rollback() throws CalFacadeException {
    getSess().rollback();
  }

  protected CalintfHelperHibCb getCalintfCb() throws CalFacadeException {
    return calintfCb;
  }

  protected void saveOrUpdate(final BwDbentity val) throws CalFacadeException {
    getSess().saveOrUpdate(val);
  }

  protected void save(final BwDbentity val) throws CalFacadeException {
    getSess().save(val);
  }

  protected void update(final BwDbentity val) throws CalFacadeException {
    getSess().update(val);
  }

  protected void delete(final BwDbentity val) throws CalFacadeException {
    getSess().delete(val);
  }

  @Override
  protected void throwException(final CalFacadeException cfe) throws CalFacadeException {
    getSess().rollback();
    throw cfe;
  }

  protected void throwException(final String pname) throws CalFacadeException {
    getSess().rollback();
    throw new CalFacadeException(pname);
  }

  protected void throwException(final String pname, final String extra) throws CalFacadeException {
    getSess().rollback();
    throw new CalFacadeException(pname, extra);
  }
}
