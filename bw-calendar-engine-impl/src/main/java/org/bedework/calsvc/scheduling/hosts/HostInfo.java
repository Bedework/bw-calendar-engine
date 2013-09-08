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
package org.bedework.calsvc.scheduling.hosts;

import org.bedework.caldav.server.sysinterface.Host;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.misc.ToString;

import java.util.Comparator;
import java.util.List;

/** This class provides information about a host. This should eventually come
 * from some form of dns-like lookup based on the CUA.
 *
 * <p>Currently we are adding dynamic look-up and DKIM security to the model.
 * Even with that in place there will be a need for hard-wired connections, with
 * and without DKIM.
 *
 * <p>To increase security we should use some form of authentication. However,
 * if we use servlet authentication we need to create accounts to authenticate
 * against. Those accounts need to be given to administrators at other sites
 * which is probably unacceptable. On the other hand we can run it through the
 * unauthenticated service and check the id/pw ourselves.
 *
 * <p>The information here can be used for outgoing or can provide us with
 * information to handle incoming requests. For incoming we need to resolve the
 * host name and we then search for an entry prefixed with *IN*. We'll need to
 * progressively shorten the name by removing leading elements until we get a
 * match or there's nothing left. For example, if we get an incoming request
 * for cal.example.org we check:<ol>
 * <li> *IN*cal.example.org</li>
 * <li> *IN*example.org</li>
 * <li> *IN*org</li>
 * <li> *IN*</li>
 * </ul>
 *
 * <p>The last entry, if it exists, provides a default behavior. If absent we
 * disallow all unidentified incoming requests. If present they must satisfy the
 * requirements specified, e.g. DKIM
 *
 * <p>To avoid any need to rebuild the db the host info shown here doesn't match
 * up to the column names in the db. At some point we'll fix that.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
@ConfInfo(elementName = "bwhost")
public class HostInfo extends ConfigBase<HostInfo>
        implements Host, Comparator<HostInfo> {
  private String hostname;

  private int port;

  private boolean secure;

  private boolean localService;

  /* Hosts come in different flavors */

  private String caldavUrl;
  private String caldavPrincipal;
  private String caldavCredentials;

  private String iScheduleUrl;
  private String iSchedulePrincipal;
  private String iScheduleCredentials;
  List<String> dkimPublicKeys;
  private boolean iScheduleUsePublicKey;

  private String fbUrl;

  /* derived values */

  private boolean supportsBedework;

  private boolean supportsCaldav;

  private boolean supportsISchedule;

  private boolean supportsFreebusy;

  /** Set the hostname
   *
   *  @param val     hostname
   */
  public void setHostname(final String val) {
    hostname = val;
  }

  /**
   *
   * @return String hostname
   */
  @Override
  public String getHostname() {
    return hostname;
  }

  /**
   * @param val
   */
  public void setPort(final int val) {
    port = val;
  }

  /**
   * @return int
   */
  @Override
  public int getPort() {
    return port;
  }

  /**
   * @param val
   */
  public void setSecure(final boolean val) {
    secure = val;
  }

  /**
   * @return String
   */
  @Override
  public boolean getSecure() {
    return secure;
  }

  /** Set localService flag
   *
   *  @param val    boolean localService
   */
  public void setLocalService(final boolean val) {
    localService = val;
  }

  /**
   *
   * @return boolean localService
   */
  @Override
  public boolean getLocalService() {
    return localService;
  }

  /**
   *
   *  @param val    String
   */
  public void setCaldavUrl(final String val) {
    caldavUrl = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getCaldavUrl() {
    return caldavUrl;
  }

  /**
   *
   *  @param val    String
   */
  public void setCaldavPrincipal(final String val) {
    caldavPrincipal = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getCaldavPrincipal() {
    return caldavPrincipal;
  }

  /**
   *
   *  @param val    String
   */
  public void setCaldavCredentials(final String val) {
    caldavCredentials = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getCaldavCredentials() {
    return caldavCredentials;
  }

  /**
   *
   *  @param val    String
   */
  public void setIScheduleUrl(final String val) {
    iScheduleUrl = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getIScheduleUrl() {
    return iScheduleUrl;
  }

  /**
   *
   *  @param val    String
   */
  public void setISchedulePrincipal(final String val) {
    iSchedulePrincipal = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getISchedulePrincipal() {
    return iSchedulePrincipal;
  }

  /**
   *
   *  @param val    String
   */
  public void setIScheduleCredentials(final String val) {
    iScheduleCredentials = val;
  }

  /** Set the dkim public keys
   *
   * @param selector
   * @param val
   */
  public void setDkimPublicKeys(final List<String> val) {
    dkimPublicKeys = val;
  }

  /**
   *
   * @return String val
   */
  @Override
  @ConfInfo(collectionElementName = "dkimPublicKey")
  public List<String> getDkimPublicKeys() {
    return dkimPublicKeys;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getIScheduleCredentials() {
    return iScheduleCredentials;
  }

  /** Add a dkim public key
   *
   * @param selector
   * @param val
   */
  public void addDkimPublicKey(final String selector,
                               final String val) {
    setDkimPublicKeys(addListProperty(getDkimPublicKeys(),
                                      selector, val));
  }

  /** Get a dkim public key
   *
   * @param selector
   * @return value or null
   */
  public String getDkimPublicKey(final String selector) {
    return getProperty(getDkimPublicKeys(), selector);
  }

  /** Remove a dkim public key
   *
   * @param selector
   */
  public void removeDkimPublicKey(final String selector) {
    removeProperty(getDkimPublicKeys(), selector);
  }

  /** Set a dkim public key
   *
   * @param selector
   * @param val
   */
  public void setDkimPublicKey(final String selector,
                               final String val) {
    try {
      removeDkimPublicKey(selector);
      addDkimPublicKey(selector, val);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   *
   *  @param val    boolean
   */
  public void setIScheduleUsePublicKey(final boolean val) {
    iScheduleUsePublicKey = val;
  }

  /** True if we delivered our public key for use for dkim
   *
   * @return boolean
   */
  @Override
  public boolean getIScheduleUsePublicKey() {
    return iScheduleUsePublicKey;
  }

  /**
   *
   *  @param val    String
   */
  public void setFbUrl(final String val) {
    fbUrl = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getFbUrl() {
    return fbUrl;
  }

  /**
   *  @param  val    boolean true if supports Bedework
   */
  public void setSupportsBedework(final boolean val) {
    supportsBedework = val;
  }

  /**
   *  @return boolean    true if caldav supported
   */
  @Override
  public boolean getSupportsBedework() {
    return supportsBedework;
  }

  /**
   *  @param  val    boolean true if supports CalDAV
   */
  public void setSupportsCaldav(final boolean val) {
    supportsCaldav = val;
  }

  /**
   *  @return boolean    true if caldav supported
   */
  @Override
  public boolean getSupportsCaldav() {
    return supportsCaldav;
  }

  /**
   *  @param  val    boolean true if supports iSchedule
   */
  public void setSupportsISchedule(final boolean val) {
    supportsISchedule = val;
  }

  /**
   *  @return boolean    true if iSchedule supported
   */
  @Override
  public boolean getSupportsISchedule() {
    return supportsISchedule;
  }

  /**
   *  @param  val    boolean true if supports Freebusy
   */
  public void setSupportsFreebusy(final boolean val) {
    supportsFreebusy = val;
  }

  /**
   *  @return boolean    true if Freebusy supported
   */
  @Override
  public boolean getSupportsFreebusy() {
    return supportsFreebusy;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   * @param indent
   */
  @Override
  public void toStringSegment(final ToString ts) {
    ts.append("hostname", getHostname());
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compare(final HostInfo o1, final HostInfo o2) {
    if (o1 == o2) {
      return 0;
    }

    return o1.getHostname().compareTo(o2.getHostname());
  }

  @Override
  public int compareTo(final HostInfo o2) {
    return compare(this, o2);
  }

  @Override
  public int hashCode() {
    return getHostname().hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof HostInfo)) {
      return false;
    }

    return compareTo((HostInfo)obj) == 0;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
