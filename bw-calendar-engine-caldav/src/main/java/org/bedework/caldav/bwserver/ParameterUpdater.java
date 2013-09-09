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
package org.bedework.caldav.bwserver;

import org.bedework.caldav.server.sysinterface.SysIntf.UpdateResult;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.BaseParameterType;

import javax.xml.namespace.QName;

/** Implemented by classes registered to apply updates specified by CalWs SOAP
 * update operation.
 *
 * @author douglm
 *
 */
public interface ParameterUpdater {
  /** Defines the update for a property parameter
   *
   * <p>The update may be add, remove or change (the value).
   *
   * @author douglm
   */
  public interface UpdateInfo {
    /**
     * @return true for add parameter
     */
    public boolean isAdd();

    /**
     * @return true for change value
     */
    public boolean isChange();

    /**
     * @return true for remove
     */
    public boolean isRemove();

    /** The information for the parent property for the updates.
     *
     * @return property
     */
    public PropertyUpdater.UpdateInfo getPropInfo();

    /** For add this is the parameter to add
     * <p>For remove this is the parameter to remove
     * <p>For change this is the parameter to locate - getUpparam returns the new
     * value for changes.
     *
     * @return parameter
     */
    public BaseParameterType getParam();

    /** The returned value may be null if the change is to parameters for
     * instance or we are deleting the selected value
     *
     * @return update parameter
     */
    public BaseParameterType getUpdparam();

    /**
     * @return QName for parameter being updated
     */
    public QName getParamName();
  }

  /** Update the parameter in the current calendar property from the information
   * supplied.
   *
   * @param ei - calendar entity
   * @param property - the particular property being updated.
   * @param ui - update information
   * @return UpdateResult - success or failure
   * @throws WebdavException
   */
  UpdateResult applyUpdate(final EventInfo ei,
                           final Object property,
                           final UpdateInfo ui) throws WebdavException;

}
