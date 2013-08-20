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

import org.bedework.caldav.server.sysinterface.CalDAVAuthProperties;

import edu.rpi.cmt.config.ConfInfo;
import edu.rpi.cmt.jmx.MBeanInfo;

/** These are the system properties that the calendar engine needs to
 * know about, either because it needs to apply these limits or just
 * to report them to clients.
 *
 * <p>Annotated to allow use by mbeans
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "system-properties")
public interface AuthProperties extends CalDAVAuthProperties {
  /** Set the defaultChangesNotifications
   *
   * @param val
   */
  void setDefaultChangesNotifications(boolean val);

  /** Get the defaultChangesNotifications
   *
   * @return flag
   */
  @MBeanInfo("default for change notifications")
  boolean getDefaultChangesNotifications();

  /** Set the user default view name
   *
   * @param val    String
   */
  void setDefaultUserViewName(String val);

  /** Get the user default view name
   *
   * @return String   default view name
   */
  @MBeanInfo("user default view name")
  String getDefaultUserViewName();

  /**
   * @param val
   */
  void setDefaultUserHour24(boolean val);

  /**
   * @return bool
   */
  @MBeanInfo("true for default to 24hr display.")
  boolean getDefaultUserHour24();

  /** Set the max description length for public events
   *
   * @param val    int max
   */
  public void setMaxPublicDescriptionLength(int val);

  /**
   *
   * @return int
   */
  @MBeanInfo("max description length for public events.")
  int getMaxPublicDescriptionLength();

  /** Set the max description length for user events
   *
   * @param val    int max
   */
  void setMaxUserDescriptionLength(int val);

  /**
   *
   * @return int
   */
  @MBeanInfo("max description length for user events.")
  public int getMaxUserDescriptionLength();

  /** Set the default quota for users. Probably an estimate
   *
   * @param val    long default
   */
  void setDefaultUserQuota(long val);

  /**
   *
   * @return long
   */
  @MBeanInfo("Default quota for users. Probably an estimate")
  long getDefaultUserQuota();

  /** Set the max time span in years for a recurring event
   *
   * @param val    int max
   */
  void setMaxYears(int val);

  /** Get the max time span in years for a recurring event
   *
   * @return int
   */
  @MBeanInfo("Max time span in years for a recurring event")
  int getMaxYears();

  /**
   * @return copy of this
   */
  AuthProperties cloneIt();
}
