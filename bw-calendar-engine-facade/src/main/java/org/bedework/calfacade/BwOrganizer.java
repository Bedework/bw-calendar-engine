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
import org.bedework.calfacade.base.Differable;
import org.bedework.calfacade.base.DumpEntity;
import org.bedework.calfacade.base.SizedEntity;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.QuotaUtil;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import java.io.Serializable;

/** Represent an organizer
 *
 *  @author Mike Douglass   douglm@bedework.edu
 */
@Dump(elementName="organizer", keyFields={"organizerUri"})
public class BwOrganizer extends DumpEntity<BwOrganizer>
          implements Comparable<BwOrganizer>, SizedEntity,
                     Differable<BwOrganizer>, Serializable {
  /* Params fields */

  private String cn;
  private String dir;
  private String language;
  private String sentBy;

  /** The uri */
  private String organizerUri;

  /** UTC datetime as specified in rfc - from replies */
  private String dtstamp;

  private String scheduleStatus;

  /** Constructor
   *
   */
  public BwOrganizer() {
    super();
  }

  /* ====================================================================
   *                      Bean methods
   * ==================================================================== */

  /** Set the cn
   *
   *  @param  val   String cn
   */
  public void setCn(final String val) {
    cn = val;
  }

  /** Get the cn
   *
   *  @return String     cn
   */
  public String getCn() {
    return cn;
  }

  /** Set the dir (directory url for lookup)
   *
   *  @param  val   String dir
   */
  public void setDir(final String val) {
    dir = val;
  }

  /** Get the dir
   *
   *  @return String     dir
   */
  public String getDir() {
    return dir;
  }

  /** Set the language
   *
   *  @param  val   String language
   */
  public void setLanguage(final String val) {
    language = val;
  }

  /** Get the language
   *
   *  @return String     language
   */
  public String getLanguage() {
    return language;
  }

  /** Set the sentBy
   *
   *  @param  val   String sentBy
   */
  public void setSentBy(final String val) {
    sentBy = val;
  }

  /** Get the sentBy
   *
   *  @return String     sentBy
   */
  public String getSentBy() {
    return sentBy;
  }

  /** Set the organizerUri
   *
   *  @param  val   String organizerUri
   */
  public void setOrganizerUri(final String val) {
    organizerUri = val;
  }

  /** Get the organizerUri
   *
   *  @return String     organizerUri
   */
  public String getOrganizerUri() {
    return organizerUri;
  }

  /**
   * @param val datestamp
   */
  public void setDtstamp(final String val) {
    dtstamp = val;
  }

  /**
   * @return String datestamp
   */
  public String getDtstamp() {
    return dtstamp;
  }

  /** Set the schedule status
   *
   * @param val    schedule status
   */
  public void setScheduleStatus(final String val) {
    scheduleStatus = val;
  }

  /** Get the schedule status
   *
   * @return String    schedule status
   */
  public String getScheduleStatus() {
    return scheduleStatus;
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /** Figure out what's different and update it. This should reduce the number
   * of spurious changes to the db.
   *
   * @param from organizer copy
   * @return true if we changed something.
   */
  public boolean update(final BwOrganizer from) {
    boolean changed = false;

    if (CalFacadeUtil.cmpObjval(getCn(), from.getCn()) != 0) {
      setCn(from.getCn());
      changed = true;
    }

    if (CalFacadeUtil.cmpObjval(getDir(), from.getDir()) != 0) {
      setDir(from.getDir());
      changed = true;
    }

    if (CalFacadeUtil.cmpObjval(getLanguage(), from.getLanguage()) != 0) {
      setLanguage(from.getLanguage());
      changed = true;
    }

    if (CalFacadeUtil.cmpObjval(getSentBy(), from.getSentBy()) != 0) {
      setSentBy(from.getSentBy());
      changed = true;
    }

    if (CalFacadeUtil.cmpObjval(getOrganizerUri(), from.getOrganizerUri()) != 0) {
      setOrganizerUri(from.getOrganizerUri());
      changed = true;
    }

    if (CalFacadeUtil.cmpObjval(getDtstamp(), from.getDtstamp()) != 0) {
      setDtstamp(from.getDtstamp());
      changed = true;
    }

    return changed;
  }

  /** Size to use for quotas.
   *
   * @return int
   */
  @NoDump
  public int getSize() {
    return QuotaUtil.size(getCn()) +
           QuotaUtil.size(getDir()) +
           QuotaUtil.size(getLanguage()) +
           QuotaUtil.size(getSentBy()) +
           QuotaUtil.size(getOrganizerUri()) +
           QuotaUtil.size(getDtstamp());
  }

  /* ====================================================================
   *                   Differable methods
   * ==================================================================== */

  public boolean differsFrom(final BwOrganizer val) {
    return (Util.compareStrings(val.getCn(), getCn()) != 0) ||
           (Util.compareStrings(val.getDir(), getDir()) != 0) ||
           (Util.compareStrings(val.getLanguage(), getLanguage()) != 0) ||
           (Util.compareStrings(val.getSentBy(), getSentBy()) != 0) ||
           (Util.compareStrings(val.getOrganizerUri(), getOrganizerUri()) != 0);
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  public int compareTo(final BwOrganizer that) {
    if (this == that) {
      return 0;
    }

    return getOrganizerUri().compareTo(that.getOrganizerUri());
  }

  @Override
  public int hashCode() {
    int hc = 1;

    if (getOrganizerUri() != null) {
      hc *= getOrganizerUri().hashCode();
    }

    return hc;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof BwOrganizer)) {
      return false;
    }

    return compareTo((BwOrganizer)o) == 0;
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    ts.append("cn", getCn());
    ts.append("dir", getDir());
    ts.append("language", getLanguage());
    ts.append("sentBy", getSentBy());
    ts.append("organizerUri", getOrganizerUri());
    ts.append("dtstamp", getDtstamp());
    ts.append("scheduleStatus", getScheduleStatus());

    return ts.toString();
  }

  @Override
  public Object clone() {
    final BwOrganizer nobj = new BwOrganizer();

    nobj.setCn(getCn());
    nobj.setDir(getDir());
    nobj.setLanguage(getLanguage());
    nobj.setSentBy(getSentBy());
    nobj.setOrganizerUri(getOrganizerUri());
    nobj.setScheduleStatus(getScheduleStatus());
    nobj.setDtstamp(getDtstamp());

    return nobj;
  }
}

