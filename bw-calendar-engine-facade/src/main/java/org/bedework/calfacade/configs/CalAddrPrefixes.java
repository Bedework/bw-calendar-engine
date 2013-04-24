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
package org.bedework.calfacade.configs;

import java.io.Serializable;

/** To enable mapping of calendar addresses e.g. mailto:fred@example.org
 * on to principals we need to either do a directory lookup or have
 * some sort of pattern map.

 * Setting a caladdr prefix enables pattern mapping. By default
 * calendar addresses are users
 *
 * @author Mike Douglass
 */
public class CalAddrPrefixes implements Serializable {
  /* Prefixes */
  private String user;
  private String group;
  private String bwadmingroup;
  private String resource;
  private String location;
  private String ticket;
  private String host;

  /** Set the user prefix e.g. "/principals/users"
   *
   * @param val    String
   */
  public void setUser(final String val) {
    user = val;
  }

  /** get the user prefix
   *
   * @return String
   */
  public String getUser() {
    return user;
  }

  /** Set the group prefix grp_
   *
   * @param val    String
   */
  public void setGroup(final String val) {
    group = val;
  }

  /** get the group prefix e.g. grp_
   *
   * @return String
   */
  public String getGroup() {
    return group;
  }

  /** Set the bedework admin group prefix e.g. "agrp_"
   *
   * @param val    String
   */
  public void setBwadmingroup(final String val) {
    bwadmingroup = val;
  }

  /** Get the bedework admin group prefix e.g. "agrp_"
   *
   * @return String
   */
  public String getBwadmingroup() {
    return bwadmingroup;
  }

  /** Set the resource prefix e.g. "rsrc_"
   *
   * @param val    String
   */
  public void setResource(final String val) {
    resource = val;
  }

  /** get the resource prefix e.g. "rsrc_"
   *
   * @return String
   */
  public String getResource() {
    return resource;
  }

  /** Set the location prefix e.g. "loc_"
   *
   * @param val    String
   */
  public void setLocation(final String val) {
    location = val;
  }

  /** get the location prefix e.g. "loc_"
   *
   * @return String
   */
  public String getLocation() {
    return location;
  }

  /** Set the ticket prefix e.g. "tkt_"
   *
   * @param val    String
   */
  public void setTicket(final String val) {
    ticket = val;
  }

  /** get the ticket prefix e.g. "tkt_"
   *
   * @return String
   */
  public String getTicket() {
    return ticket;
  }

  /** Set the host prefix e.g. "host_"
   *
   * @param val    String
   */
  public void setHost(final String val) {
    host = val;
  }

  /** get the host prefix e.g. "host_"
   *
   * @return String
   */
  public String getHost() {
    return host;
  }
}
