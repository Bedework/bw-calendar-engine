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

/** This interface defines some calfacade values.
 *
 * @author Mike Douglass
 */
public interface CalFacadeDefs {

  /** Any object with this key is considered unsaved
   */
  public static final int unsavedItemKey = -1;

  /*     Some other stuff    */

  /** */
  public static final String bwMimeType = "bwcal";

  /** */
  public static final String bwUriPrefix = bwMimeType + "://";

  /** jasig scheduling assistant */
  public static final String jasigSchedulingAssistant = "Jasig Scheduling Assistant";
}
