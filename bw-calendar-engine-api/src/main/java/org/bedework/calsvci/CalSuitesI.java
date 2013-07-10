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
package org.bedework.calsvci;

import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** Interface for handling bedework calendar suite objects.
 *
 * @author Mike Douglass
 *
 */
public interface CalSuitesI extends Serializable {
  /** Create a new calendar suite
   *
   * @param name
   * @param adminGroupName - name of the admin group
   * @param rootCollectionPath
   * @param submissionsPath
   * @return BwCalSuiteWrapper for new object
   * @throws CalFacadeException
   */
  BwCalSuiteWrapper add(String name,
                        String adminGroupName,
                        String rootCollectionPath,
                        String submissionsPath) throws CalFacadeException;

  /** Set the current calendar suite.
   *
   * @param val   BwCalSuiteWrapper
   * @throws CalFacadeException
   */
  public void set(BwCalSuiteWrapper val) throws CalFacadeException;

  /** Get the current calendar suite.
   *
   * @return BwCalSuiteWrapper null for unknown calendar suite
   * @throws CalFacadeException
   */
  public BwCalSuiteWrapper get() throws CalFacadeException;

  /** Get a calendar suite given the name
   *
   * @param  name     String name of calendar suite
   * @return BwCalSuiteWrapper null for unknown calendar suite
   * @throws CalFacadeException
   */
  BwCalSuiteWrapper get(String name) throws CalFacadeException;

  /** Get a calendar suite given the 'owning' group
   *
   * @param  group     BwAdminGroup
   * @return BwCalSuiteWrapper null for unknown calendar suite
   * @throws CalFacadeException
   */
  public BwCalSuiteWrapper get(BwAdminGroup group)
          throws CalFacadeException;

  /** Get calendar suites to which this user has access
   *
   * @return Collection     of BwCalSuiteWrapper
   * @throws CalFacadeException
   */
  public Collection<BwCalSuite> getAll() throws CalFacadeException;

  /** Update a calendar suite. Any of the parameters to be changed may be null
   * or the current value to indicate no change.
   *
   * @param cs     BwCalSuiteWrapper object
   * @param adminGroupName - name of the admin group
   * @param rootCollectionPath
   * @param submissionsPath
   * @throws CalFacadeException
   */
  void update(BwCalSuiteWrapper cs,
              String adminGroupName,
              String rootCollectionPath,
              String submissionsPath) throws CalFacadeException;

  /** Delete a calendar suite object
   *
   * @param  val     BwCalSuiteWrapper object
   * @throws CalFacadeException
   */
  void delete(BwCalSuiteWrapper val) throws CalFacadeException;

  /** Define class of resource
   */
  public enum ResourceClass {
    /** managed by calsuite admin and stored as a calsuite resource */
    calsuite,

    /** managed by superuser and stored as a calsuite resource */
    admin,

    /** managed by superuser and stored as a global resource */
    global;
  }

  /** Get a list of resources. The content is not fetched.
   *
   * @param suite - calendar suite
   * @param cl - define class of resource
   * @return list
   * @throws CalFacadeException
   */
  public List<BwResource> getResources(BwCalSuite suite,
                                       ResourceClass cl) throws CalFacadeException;

  /** Get named resource. The content is fetched.
   *
   * @param suite - calendar suite
   * @param name
   * @param cl - define class of resource
   * @return resource or null
   * @throws CalFacadeException
   */
  public BwResource getResource(BwCalSuite suite,
                                String name,
                                ResourceClass cl) throws CalFacadeException;

  /** Add a resource. The supplied object has all fields set except for the
   * path. This will be determined by the cl parameter and set in the object.
   *
   * <p>The parent collection will be created if necessary.
   *
   * @param suite - calendar suite
   * @param res
   * @param cl - define class of resource
   * @throws CalFacadeException
   */
  public void addResource(BwCalSuite suite,
                          BwResource res,
                          ResourceClass cl) throws CalFacadeException;

  /** Delete named resource
   *
   * @param suite - calendar suite
   * @param name
   * @param cl - define class of resource
   * @throws CalFacadeException
   */
  public void deleteResource(BwCalSuite suite,
                             String name,
                             ResourceClass cl) throws CalFacadeException;
}
