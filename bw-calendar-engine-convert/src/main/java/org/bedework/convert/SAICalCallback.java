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
package org.bedework.convert;

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.misc.response.GetEntitiesResponse;
import org.bedework.util.misc.response.GetEntityResponse;

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
   * @param account for user
   */
  public SAICalCallback(final String account) {
    principal = BwPrincipal.makeUserPrincipal();
    principal.setAccount(account);
  }

  @Override
  public void setStrictness(final int val) {
    strictness = val;
  }

  @Override
  public int getStrictness() {
    return strictness;
  }

  @Override
  public BwPrincipal getPrincipal() {
    return principal;
  }

  @Override
  public BwPrincipal getOwner() {
    // XXX is this OK
    return principal;
  }

  @Override
  public String getCaladdr(final String val) {
    return val;
  }

  @Override
  public GetEntityResponse<BwCategory> findCategory(final BwString val) {
    return null;
  }

  @Override
  public void addCategory(final BwCategory val) {
  }

  @Override
  public GetEntityResponse<BwContact> getContact(final String uid) {
    return null;
  }

  @Override
  public GetEntityResponse<BwContact> findContact(final BwString val) {
    return null;
  }

  @Override
  public void addContact(final BwContact val) {
  }

  @Override
  public GetEntityResponse<BwLocation> getLocation(final String uid) {
    return null;
  }

  @Override
  public GetEntityResponse<BwLocation> fetchLocationByKey(
          final String name,
          final String val) {
    return null;
  }

  @Override
  public GetEntityResponse<BwLocation> findLocation(final BwString address) {
    return null;
  }

  @Override
  public GetEntityResponse<BwLocation> fetchLocationByCombined(
          final String val, final boolean persisted) {
    return null;
  }

  @Override
  public void addLocation(final BwLocation val) {
  }

  @Override
  public GetEntitiesResponse<EventInfo> getEvent(final String colPath,
                                                 final String guid) {
    return null;
  }

  @Override
  public boolean getTimezonesByReference() {
    return false;
  }
}
