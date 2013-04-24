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

package org.bedework.dumprestore.restore;

import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;

import java.util.ArrayList;
import java.util.HashMap;

/** Handle the onlyusers processing.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class OnlyUsersMap {
  /** If true we should discard all but users in onlyUsers
   * This helps when building demo data
   */
  private boolean onlyUsers;

  /* Users we preserve */
  private HashMap<String, String> map = new HashMap<String, String>();

  /** Users we skipped */
  private HashMap<String, String> skipped = new HashMap<String, String>();

  /** User patterns we preserve */
  private ArrayList<String> patterns = new ArrayList<String>();

  /**
   *
   * @param val
   */
  public void setOnlyUsers(boolean val) {
    onlyUsers = val;
  }

  /**
   *
   * @return boolean
   */
  public boolean getOnlyUsers() {
    return onlyUsers;
  }

  /**
   * @param val
   */
  public void add(String val) {
    if (val.endsWith("*")) {
      patterns.add(val.substring(0, val.length() - 1));
    } else {
      map.put(val, val);
    }
  }

  /** See if we skip this one
   *
   * @param href to check
   * @return boolean true if user if OK
   */
  public boolean check(String href) {
    if (!onlyUsers) {
      return true;
    }

    if (map.get(href) == null) {
      if (skipped.get(href) != null) {
        return false;
      }

      for (String prefix: patterns) {
        if (href.startsWith(prefix)) {
          map.put(href, href);
          return true;
        }
      }

      // Bypass the above next time
      skipped.put(href, href);
      return false;
    }

    return true;
  }

  /** See if we skip this one
   *
   * @param ent Owned entity to check
   * @return boolean true if user if OK
   */
  public boolean check(BwOwnedDbentity ent) {
    if (!onlyUsers) {
      return true;
    }

    return check(ent.getOwnerHref());
  }

  /** Check and modify a shareable entity with creator different from owner.
   *
   * @param ent Sahreable entity
   * @return boolean true if owner if OK
   */
  public boolean checkOnlyUser(BwShareableDbentity ent) {
    if (!onlyUsers) {
      return true;
    }

    if (!check(ent.getOwnerHref())) {
      return false;
    }

    if (!check(ent.getCreatorHref())) {
      ent.setCreatorHref(ent.getOwnerHref());
    }

    return true;
  }
}
