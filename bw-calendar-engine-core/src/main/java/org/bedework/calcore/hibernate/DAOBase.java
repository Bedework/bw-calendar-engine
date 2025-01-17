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

import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import java.io.InputStream;
import java.sql.Blob;

/** Class used as basis for a number of DAO classes.
 *
 * @author Mike Douglass   douglm  bedework.org
 */
public abstract class DAOBase implements Logged {
  private HibSession sess;

  /**
   * @param sess the session
   */
  public DAOBase(final HibSession sess) {
    this.sess = sess;
  }

  /**
   * 
   * @return a unique name for the instance.
   */
  public abstract String getName();

  public void setSess(final HibSession val) {
    sess = val;
  }

  protected HibSession getSess() {
    return sess;
  }

  protected void rollback() {
    getSess().rollback();
  }

  protected void saveOrUpdate(final BwUnversionedDbentity val) {
    getSess().saveOrUpdate(val);
  }

  protected void save(final BwUnversionedDbentity val) {
    getSess().save(val);
  }

  protected void update(final BwUnversionedDbentity val) {
    getSess().update(val);
  }

  protected void delete(final BwUnversionedDbentity val) {
    getSess().delete(val);
  }

  public BwUnversionedDbentity merge(final BwUnversionedDbentity val) {
    return (BwUnversionedDbentity)sess.merge(val);
  }

  public Blob getBlob(final byte[] val) {
    return sess.getBlob(val);
  }

  public Blob getBlob(final InputStream val,
                      final long length) {
    return sess.getBlob(val, length);
  }

  protected void throwException(final CalFacadeException cfe) {
    getSess().rollback();
    throw cfe;
  }

  protected void throwException(final String pname) {
    getSess().rollback();
    throw new CalFacadeException(pname);
  }

  protected void throwException(final String pname, final String extra) {
    getSess().rollback();
    throw new CalFacadeException(pname, extra);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
