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

import org.bedework.calcore.CalintfHelper;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.exc.CalFacadeException;

import org.bedework.access.PrivilegeDefs;

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
     * @throws CalFacadeException
     */
    HibSession getSess() throws CalFacadeException;
  }

  private CalintfHelperHibCb calintfCb;

  /**
   * @param calintfCb
   */
  public CalintfHelperHib(final CalintfHelperHibCb calintfCb) {
    this.calintfCb = calintfCb;
  }

  protected HibSession getSess() throws CalFacadeException {
    return calintfCb.getSess();
  }

  protected CalintfHelperHibCb getCalintfCb() throws CalFacadeException {
    return calintfCb;
  }

  /** Just encapsulate building a query out of a number of parts
   *
   * @param parts
   * @throws CalFacadeException
   */
  public void makeQuery(final String[] parts) throws CalFacadeException {
    StringBuilder sb = new StringBuilder();

    for (String s: parts) {
      sb.append(s);
    }

    getSess().createQuery(sb.toString());
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
