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

import java.util.ArrayList;

/** Help for returning output
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class InfoLines extends ArrayList<String> {
  /** Appends newline
   * @param ln
   */
  public void addLn(final String ln) {
    add(ln + "\n");
  }

  /** Emit the exception message
   * @param t
   */
  public void exceptionMsg(final Throwable t) {
    addLn("Exception - check logs: " + t.getMessage());
  }
}
