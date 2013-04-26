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

import java.io.Serializable;
import java.util.List;

/** Interface for handling bedework stored resources.
 *
 * <p>Usually this will be an attachment but it may also be an image resource or
 * some other type of downloadable resource.
 *
 * <p>Resources are stored within folders which can be located anywhere in the
 * hierarchy but MUST NOT be calendar collections. This allows us to remain
 * compliant with CalDAV.
 *
 * @author Mike Douglass
 *
 */
public interface ResourcesI extends Serializable {
  /** Save a resource at the given collection path. The collection MUST exist.
   *
   * @param  path      String path to containing collection
   * @param  val       resource with attached content
   * @throws CalFacadeException for errors including duplicate name
   */
  public void save(String path,
                   BwResource val) throws CalFacadeException;

  /** Get a resource given the path - does not get content
   *
   * @param  path     String path to resource
   * @return BwResource null for unknown resource
   * @throws CalFacadeException
   */
  public BwResource get(String path) throws CalFacadeException;

  /** Retrieve resource content given the resource. It will be set in the resource
   * object
   *
   * @param  val BwResource
   * @throws CalFacadeException
   */
  public void getContent(BwResource val) throws CalFacadeException;

  /** Get resources to which this user has access - content is not fetched.
   *
   * @param  path           String path to containing collection
   * @return List     of BwResource
   * @throws CalFacadeException
   */
  public List<BwResource> getAll(String path) throws CalFacadeException;

  /** Update a resource.
   *
   * @param  val          resource
   * @param updateContent if true we also update the content
   * @throws CalFacadeException for errors including duplicate name
   */
  public void update(BwResource val,
                     boolean updateContent) throws CalFacadeException;

  /** Delete a resource and content given the path
   *
   * @param  path     String path to resource
   * @throws CalFacadeException
   */
  public void delete(String path) throws CalFacadeException;

  /** Move or copy the given resource to the destination collection.
   *
   * @param  val BwResource
   * @param to        Path of estination collection
   * @param name      String name of new entity
   * @param copy      true for copying
   * @param overwrite destination exists
   * @return true if destination created (i.e. not updated)
   * @throws CalFacadeException
   */
  public boolean copyMove(BwResource val,
                          String to,
                          String name,
                          boolean copy,
                          boolean overwrite) throws CalFacadeException;
}
