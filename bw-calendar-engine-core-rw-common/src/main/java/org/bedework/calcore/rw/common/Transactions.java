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
package org.bedework.calcore.rw.common;

import org.bedework.base.exc.BedeworkException;

/** Allow transaction support. This is implemented by modules which
 * may want to do some cleanup at start and end of transactions.
 * 
 * <p>Thsi is not the start/end of the persistence layer transaction
 * though it conincides with it. That is handled elsewhere</p>
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public interface Transactions {
  /** Called to allow setup
   *
   */
  void startTransaction();

  /** Called to allow cleanup
   *
   */
  void endTransaction();

  /** Called to cleanup
   *
   */
  void rollback();

  /** Rollback and throw
   * 
   * @param cfe the error
   */
  <T> T throwException(final BedeworkException cfe);
}
