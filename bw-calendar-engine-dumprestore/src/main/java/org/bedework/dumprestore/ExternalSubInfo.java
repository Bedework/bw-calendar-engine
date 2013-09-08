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

import org.bedework.util.misc.ToString;

/** Information about an external subscription
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class ExternalSubInfo {
  /** */
  public String path;

  /** */
  public boolean publick;

  /** */
  public String owner;

  /**
   * @param path
   * @param publick
   * @param owner
   */
  public ExternalSubInfo(final String path,
                         final boolean publick,
                         final String owner) {
    this.path = path;
    this.publick = publick;
    this.owner = owner;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("path", path);
    ts.append("publick", publick);
    ts.append("owner", owner);

    return ts.toString();
  }
}
