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
package org.bedework.icalendar;

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.responses.GetEntityResponse;

import java.util.Collection;

/** Class to allow icaltranslator to be used from a standalone non-bedework
 * caldav server.
 *
 * @author douglm
 *
 */
public class SAICalCallback implements IcalCallback {
  private int strictness;

  private BwPrincipal principal;

  /** Constructor
   *
   * @param account
   */
  public SAICalCallback(final String account) {
    principal = BwPrincipal.makeUserPrincipal();
    principal.setAccount(account);
  }

  @Override
  public void setStrictness(final int val) throws CalFacadeException {
    strictness = val;
  }

  @Override
  public int getStrictness() throws CalFacadeException {
    return strictness;
  }

  @Override
  public BwPrincipal getPrincipal() throws CalFacadeException {
    return principal;
  }

  @Override
  public BwPrincipal getOwner() throws CalFacadeException {
    // XXX is this OK
    return principal;
  }

  @Override
  public String getCaladdr(final String val) throws CalFacadeException {
    return val;
  }

  @Override
  public BwCategory findCategory(final BwString val) throws CalFacadeException {
    return null;
  }

  @Override
  public void addCategory(final BwCategory val) throws CalFacadeException {
  }

  @Override
  public BwContact getContact(final String uid) throws CalFacadeException {
    return null;
  }

  @Override
  public BwContact findContact(final BwString val) throws CalFacadeException {
    return null;
  }

  @Override
  public void addContact(final BwContact val) throws CalFacadeException {
  }

  @Override
  public BwLocation getLocation(final String uid) throws CalFacadeException {
    return null;
  }

  @Override
  public BwLocation getLocation(final BwString address) throws CalFacadeException {
    return null;
  }

  @Override
  public GetEntityResponse<BwLocation> fetchLocationByKey(
          final String name,
          final String val) {
    return null;
  }

  @Override
  public BwLocation findLocation(final BwString address) throws CalFacadeException {
    return null;
  }

  @Override
  public GetEntityResponse<BwLocation> fetchLocationByCombined(
          final String val, final boolean persisted) {
    return null;
  }

  @Override
  public void addLocation(final BwLocation val) throws CalFacadeException {
  }

  @Override
  public Collection getEvent(final String colPath,
                             final String guid)
          throws CalFacadeException {
    return null;
  }

  @Override
  public boolean getTimezonesByReference() throws CalFacadeException {
    return false;
  }
}
