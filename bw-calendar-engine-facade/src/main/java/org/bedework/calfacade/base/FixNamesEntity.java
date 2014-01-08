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
package org.bedework.calfacade.base;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.BasicSystemProperties;

/** Interface which defines an implementing class as needing names fixed..
 *
 * <p>For 4.0 we should modify the schemas so that we persist the
 * href in the db. Currently we have classes- categories etc - which
 * have no collection. Implementing classes will fake up a collection
 * path and name and also provide an href.
 *
 * @author Mike Douglass
 * @version 1.0
 *
 */
public interface FixNamesEntity {
  /**
   *
   * @param props - needed for path names
   * @param principal  - needed to locate home
   */
  void fixNames(BasicSystemProperties props,
                BwPrincipal principal);

  /**
   *
   * @param val
   */
  void setHref(String val);
  /**
   *
   * @return href for this object
   */
  String getHref();
}
