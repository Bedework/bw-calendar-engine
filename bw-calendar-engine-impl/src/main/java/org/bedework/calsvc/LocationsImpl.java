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
package org.bedework.calsvc;

import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.Locations;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.util.Collection;

/** Class which handles manipulation of Locations.
 *
 * @author Mike Douglass   douglm - rpi.edu
 */
public class LocationsImpl
        extends EventPropertiesImpl<BwLocation>
        implements Locations {
  /** Constructor
  *
  * @param svci calsvc object
  */
  public LocationsImpl(final CalSvc svci) {
    super(svci);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void init(final boolean adminCanEditAllPublic) {
    super.init(BwLocation.class.getCanonicalName(),
               adminCanEditAllPublic);
  }

  @Override
  Collection<BwLocation> fetchAllIndexed(final boolean publick,
                                         final String ownerHref)
          throws CalFacadeException {
    return getIndexer(publick, ownerHref).fetchAllLocations();
  }

  @Override
  BwLocation fetchIndexedByUid(final String uid) throws CalFacadeException {
    return getIndexer().fetchLocation(uid, PropertyInfoIndex.UID);
  }

  @Override
  BwLocation findPersistent(final BwLocation val,
                            final String ownerHref) throws CalFacadeException {
    return findPersistent(val.getAddress(), ownerHref);
  }

  @Override
  public boolean exists(final BwLocation val) throws CalFacadeException {
    return findPersistent(val.getFinderKeyValue(), val.getOwnerHref()) != null;
  }

  @Override
  public BwLocation find(final BwString val) throws CalFacadeException {
    return getIndexer().fetchLocation("address.value",
                                      PropertyInfoIndex.ADDRESS,
                                      PropertyInfoIndex.VALUE);
  }
}

