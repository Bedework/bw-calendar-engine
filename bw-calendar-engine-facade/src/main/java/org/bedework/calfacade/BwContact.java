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

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.CollatableEntity;
import org.bedework.calfacade.base.SizedEntity;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.QuotaUtil;
import org.bedework.util.misc.ToString;

import java.util.Comparator;

/** Class representing rfc contact information. The phone and email is additional
 * for the web clients.
 *
 * @author Mike Douglass
 * @version 1.0
 */
@Dump(elementName="contact", keyFields={"uid"})
public class BwContact extends BwEventProperty<BwContact>
        implements CollatableEntity, Comparator<BwContact>,
                   SizedEntity {
  private BwString name;  // The rfc value
  private String phone;
  private String email;
  private String link;  // The rfc altrep

  /** Constructor
   *
   */
  public BwContact() {
    super();
  }

  /** Set the name
   *
   * @param val    BwString name
   */
  public void setName(final BwString val) {
    name = val;
  }

  /** Get the name
   *
   * @return BwString   name
   */
  public BwString getName() {
    return name;
  }

  /**
   * @param val
   */
  public void setPhone(final String val) {
    phone = val;
  }

  /**
   * @return String phone number
   */
  public String getPhone() {
    return phone;
  }

  /**
   * @param val
   */
  public void setEmail(final String val) {
    email = val;
  }

  /**
   * @return String email
   */
  public String getEmail() {
    return email;
  }

  /** Set the sponsor's URL
   *
   * @param link   String URL
   */
  public void setLink(final String link) {
    this.link = link;
  }

  /**
   * @return String url
   */
  public String getLink() {
    return link;
  }

  /**
   * @return contact with uid filled in.
   */
  public static BwContact makeContact() {
    return (BwContact)new BwContact().initUid();
  }

  /** Delete the contact's name - this must be called rather than setting
   * the value to null.
   *
   */
  public void deleteName() {
    addDeletedEntity(getName());
    setName(null);
  }

  /* ====================================================================
   *                   EventProperty methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwEventProperty#getFinderKeyValue()
   */
  @Override
  @NoDump
  public BwString getFinderKeyValue() {
    return getName();
  }

  /* ====================================================================
   *                   CollatableEntity methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.CollatableEntity#getCollateValue()
   */
  @Override
  @NoDump
  public String getCollateValue() {
    return getName().getValue();
  }

  /* ====================================================================
   *                   Action methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.BwDbentity#afterDeletion()
   */
  @Override
  public void afterDeletion() {
    addDeletedEntity(getName());
  }

  /** Size to use for quotas.
   *
   * @return int
   */
  @Override
  @NoDump
  public int getSize() {
    return super.length() +
           QuotaUtil.size(getName()) +
           QuotaUtil.size(getPhone()) +
           QuotaUtil.size(getEmail()) +
           QuotaUtil.size(getLink());
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compare(final BwContact thisone, final BwContact thatone) {
    if (thisone.equals(thatone)) {
      return 0;
    }

    return CalFacadeUtil.cmpObjval(thisone.getName().getValue(),
                                   thatone.getName().getValue());
  }

  @Override
  public int compareTo(final BwContact that) {
    if (this == that) {
      return 0;
    }

    return CalFacadeUtil.cmpObjval(getUid(), that.getUid());
  }

  @Override
  public int hashCode() {
    return getUid().hashCode();
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("uid", getUid());
    ts.append("name", getName());
    ts.append("phone", getPhone());
    ts.append("email", getEmail());
    ts.append("link", getLink());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwContact sp = new BwContact();

    super.copyTo(sp);

    sp.setName((BwString)getName().clone());
    sp.setPhone(getPhone());
    sp.setEmail(getEmail());
    sp.setLink(getLink());

    return sp;
  }
}
