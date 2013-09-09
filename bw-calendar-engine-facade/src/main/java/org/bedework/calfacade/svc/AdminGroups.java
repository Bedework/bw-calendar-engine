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

package org.bedework.calfacade.svc;

import org.bedework.calfacade.ifs.Directories;

/** An interface to handle calendar admin groups.
 *
 * <p>Groups may be stored in a site specific manner so the actual
 * implementation used is a build-time configuration option. They may be
 * ldap directory based or implemented by storing in the calendar database.
 *
 * <p>Methods may throw an unimplemented exception if functions are not
 * available.
 *
 * <p>If a user is a member of more than one group the admin client should
 * ask which group they want.
 *
 * <p>Events etc are owned by the group which has it's own owner id. It's
 * important that the group owner ids are distinct from user ids. This is
 * dealt with by a configurable prefix and suffix appended to all group ids.
 *
 * <p>If a user is not a member of any group they own the events.
 *
 * @author Mike Douglass douglm@bedework.edu
 * @version 2.2
 */
public interface AdminGroups extends Directories {
  /* * Find a group given the event owner.
   *
   * @param  owner          BwUser event owner
   * @return BwAdminGroup   group object
   * @exception CalFacadeException If there's a problem
   * UNUSED??
  public BwAdminGroup findGroupByEventOwner(BwUser owner)
      throws CalFacadeException; */
}
