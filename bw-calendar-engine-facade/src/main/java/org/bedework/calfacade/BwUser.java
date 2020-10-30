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

package org.bedework.calfacade;

import org.bedework.access.WhoDefs;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;

/** Value object to represent a calendar user.
 *
 *   @author Mike Douglass
 *  @version 1.0
 */
@Dump(elementName="user", keyFields={"account"})
@NoDump({"byteSize"})
public class BwUser extends BwPrincipal {
  /* Temp to avoid schema change */
  private boolean instanceOwner;

  /* ====================================================================
   *                   Constructors
   * ==================================================================== */

  /** Create a guest user
   */
  public BwUser() {
    super();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwPrincipal#getKind()
   */
  @Override
  @NoDump
  public int getKind() {
    return WhoDefs.whoTypeUser;
  }

  /** An instance owner is the owner of an instance of the calendar system.
   * This is the id we run as for that particular instance, e.g. the campus
   * calendar, or the alumni calendar etc.
   *
   * @param val true for instance owner
   * @deprecated
   */
  @Deprecated
  public void setInstanceOwner(final boolean val) {
    instanceOwner = val;
  }

  /**
   * @return boolean
   * @deprecated
   */
  @Deprecated
  public boolean getInstanceOwner() {
    return instanceOwner;
  }

  /* ====================================================================
   *                   Copying methods
   * ==================================================================== */

  /** Copy this to val
   *
   * @param val BwUser
   */
  public void copyTo(final BwUser val) {
    super.copyTo(val);
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public Object clone() {
    /* We do not clone the attached subscriptions if present. These need to
       be cloned explicitly or we might set up a clone loop.
    */
    final BwUser u = new BwUser();
    copyTo(u);

    return u;
  }
}
