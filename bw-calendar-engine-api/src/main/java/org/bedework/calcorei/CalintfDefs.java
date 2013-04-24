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

package org.bedework.calcorei;

/** Some definitions.
 *
 * @author Mike Douglass   douglm@rpi.edu
 */
public interface CalintfDefs {
  /** We can run in the following modes:
   */

   /**
   * guestMode: only public events
   */
  static final int guestMode = 0;

  /**
   * userMode: ordinary or departmental user - personal events
   */
  static final int userMode = 1;
  /**
   * publicUserMode: an orgUnit user - public events
   */
  static final int publicUserMode = 2;
  /**
   * publicAdminMode: public events owned by user
   */
  static final int publicAdminMode = 3;

  /** Sometimes we want a no access exception when we try to retrieve
      something, other times we wan an exception.
   */
  boolean returnResultAlways = true;
}

