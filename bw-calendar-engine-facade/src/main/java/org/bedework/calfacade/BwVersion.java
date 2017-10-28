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

/** This defines the current version
 *
 * @author Mike Douglass
 */
public class BwVersion {
  /** When this changes - everything is different */
  public static final int bedeworkMajorVersion = 3;

  /** When this changes - schema and api usually have significant changes */
  public static final int bedeworkMinorVersion = 11;

  /** Minor functional updates */
  public static final int bedeworkUpdateVersion = 1;

  /** Patches which might introduce schema incompatibility if needed.
   * Essentially a bug fix
   */
  public static final String bedeworkPatchLevel = null;

  /** Result of concatenating the above */
  public static final String bedeworkVersion =
    makeVersion(bedeworkMajorVersion,
                bedeworkMinorVersion,
                bedeworkUpdateVersion,
                bedeworkPatchLevel);

  private static String makeVersion(final int major,
                                    final int minor,
                                    final int update,
                                    final String patch) {
    StringBuilder sb = new StringBuilder();
    sb.append(major);

    if ((minor != 0) ||
        (update != 0)) {
      sb.append( ".");
      sb.append(minor);
    }

    if (update != 0) {
      sb.append(".");
      sb.append(update);
    }

    if (patch != null) {
      sb.append("-");
      sb.append(patch);
    }

    return sb.toString();
  }
}
