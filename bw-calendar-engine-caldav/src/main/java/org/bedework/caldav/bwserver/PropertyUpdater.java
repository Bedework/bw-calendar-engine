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
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.CategoryMapInfo;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.calendar.XcalUtil.TzGetter;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.BasePropertyType;

import java.util.List;

import javax.xml.namespace.QName;

/** Implemented by classes registered to apply updates specified by CalWs SOAP
 * update operation.
 *
 * @author douglm
 *
 */
public interface PropertyUpdater {
  /** Holds a reference to a component. We have a fairly
   * limited set of allowable components - each of which has a method below.
   *
   * <p>This interface allows us to chain sub-components to their parent.
   */
  public interface Component {
    /**
     * @return null if no parent
     */
    public Component getParent();

    /**
     * @return the event info for this component
     */
    public EventInfo getEi();

    /**
     * @return the event for this component (a convenience)
     */
    public BwEvent getEvent();

    /**
     * @return non-null if it's an alarm we are updating
     */
    public BwAlarm getAlarm();
  }

  /** Defines the update for a property
   *
   * <p>The update may be add, remove or change (the value) or none of those if
   * the actual update is to the parameters only.
   *
   * <p>If there are any updates to parameters there will be one or more
   * ParameterUpdater.UpdateInfo objects available.
   *
   * @author douglm
   */
  public interface UpdateInfo {
    /**
     * @return mappings - null if none defined
     */
    CategoryMapInfo getCatMapInfo();

    /**
     * @return true for add property
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

    /** For add this is the property to add
     * <p>For remove this is the property to remove
     * <p>For change this is the property to locate - getUpprop returns the new
     * value for changes.
     *
     * @return property
     */
    public BasePropertyType getProp();

    /** The returned value may be null if the change is to parameters for
     * instance or we are deleting the selected value
     *
     * @return update property
     */
    public BasePropertyType getUpdprop();

    /**
     * @return QName for property being updated
     */
    public QName getPropName();

    /**
     * @return object allowing callback to api
     */
    public IcalCallback getIcalCallback();

    /** For XcalUtil
     *
     * @return a getter for timezones
     */
    public TzGetter getTzs();

    /**
     * @return the event info for this update
     */
    public EventInfo getEi();

    /**
     * @return the event for this update
     */
    public BwEvent getEvent();

    /**
     * @return the sub-component we are updating
     */
    public Component getSubComponent();

    /**
     * @return a change table entry for this property
     */
    public ChangeTableEntry getCte();

    /**
     * @return a change table entry for the given property
     */
    public ChangeTableEntry getCte(PropertyIndex.PropertyInfoIndex pi);

    /** Allows saving of arbitrary state information. The state is global to
     * the whole update process - not just the current update.
     *
     * @param name of state
     * @param val object to save
     */
    public void saveState(String name, Object val);

    /**
     * @param name of state
     * @return state-info
     */
    public Object getState(String name);

    /**
     * @return possibly empty list. Never null.
     */
    public List<ParameterUpdater.UpdateInfo> getParamUpdates();

    /**
     * @return current user href.
     */
    public String getUserHref();
  }

  /** Update the property in the given calendar object from the information
   * supplied.
   *
   * @param ui - update information
   * @return UpdateResult - success or failure
   */
  UpdateResult applyUpdate(final UpdateInfo ui);

}
