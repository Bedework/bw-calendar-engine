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
package org.bedework.dumprestore;

import org.apache.log4j.Logger;

import java.util.List;

/** Some counters we use in dump and restore
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class Counters {
  private transient Logger log;

  /** */
  public final int syspars = 0;

  /** */
  public final int users = 1;

  /** */
  public final int timezones = 2;

  /** */
  public final int collections = 3;

  /** */
  public final int locations = 4;

  /** */
  public final int contacts = 5;

  /** */
  public final int valarms = 6;

  /** */
  public final int categories = 7;

  /** */
  public final int authusers = 8;

  /** */
  public final int events = 9;

  /** */
  public final int eventOverrides = 10;

  /** */
  public final int eventAnnotations = 11;

  /** */
  public final int adminGroups = 12;

  /** */
  public final int userPrefs = 13;

  /** */
  public final int filters = 14;

  /** */
  public final int calSuites = 15;

  /** */
  public final int resources = 16;

  /** */
  public final int externalSubscriptions = 17;

  /** */
  public final int aliases = 18;

  /**   */
  public int[] counts = new int[19];

  /** */
  public String[] countNames = {
             "syspars",
             "users",
             "timezones",
             "collections",
             "locations",
             "contacts",
             "alarms",
             "categories",
             "authusers",
             "events",
             "eventOverrides",
             "eventAnnotations",
             "adminGroups",
             "userPrefs",
             "filters",
             "calendar suites",
             "resources",
             "external subscriptions",
             "aliases",
  };

  /**
   *
   * @param infoLines - null for logged output only
   */
  public void stats(final List<String> infoLines) {
    for (int cti = 0; cti < counts.length; cti++) {
      stat(infoLines, cti);
    }
  }

  private final String blanks = "                                    ";
  private final int paddedNmLen = 18;

  private void stat(final List<String> infoLines, final int cti) {
    StringBuilder sb = new StringBuilder();

    String name = countNames[cti];

    if (name.length() < paddedNmLen) {
      sb.append(blanks.substring(0, paddedNmLen - name.length()));
    }

    sb.append(name);
    sb.append(": ");
    sb.append(counts[cti]);

    info(infoLines, sb.toString());
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void info(final List<String> infoLines, final String msg) {
    if (infoLines != null) {
      infoLines.add(msg + "\n");
    }

    info(msg);
  }

  protected void info(final String msg) {
    getLog().info(msg);
  }
}
