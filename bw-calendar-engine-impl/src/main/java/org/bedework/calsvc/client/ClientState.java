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
package org.bedework.calsvc.client;

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.calsvc.CalSvcDb;
import org.bedework.calsvci.ClientStateI;

import edu.rpi.sss.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** This class represents the current state of a bedework client - most probably
 * the web clients.
 *
 * @author Mike Douglass
 *
 */
public class ClientState extends CalSvcDb implements ClientStateI {
  private BwView currentView;

  /* The current virtual path */
  private String vpath;

  /* The current virtual path target */
  private String vpathTarget;

  /* Filter resulting from the view or vpath */
  private FilterBase filter;

  /* Built as we build filters. */
  private ColorMap colorMap;

  private String vfilter;

  /**
   * @param svci
   */
  public ClientState(final CalSvc svci) {
    super(svci);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ClientStateI#flush()
   */
  @Override
  public void flush() throws CalFacadeException {
    filter = null;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ClientStateI#setCurrentView(java.lang.String)
   */
  @Override
  public boolean setCurrentView(final String val) throws CalFacadeException {
    if (val == null) {
      currentView = null;
      colorMap = new ColorMap();

      return true;
    }

    filter = null;
    vfilter = null;

    Collection<BwView> v = getSvc().getPrefsHandler().get().getViews();
    if ((v == null) || (v.size() == 0)) {
      return false;
    }

    for (BwView view: v) {
      if (val.equals(view.getName())) {
        currentView = view;
        vpath = null;
        vpathTarget = null;

        if (debug) {
          trace("set view to " + view);
        }

        return true;
      }
    }

    return false;
  }

  @Override
  public void setCurrentView(final BwView val) throws CalFacadeException {
    if (val == null) {
      currentView = null;
      colorMap = new ColorMap();

      return;
    }

    filter = null;
    vfilter = null;

    currentView = val;
    vpath = null;
    vpathTarget = null;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ClientStateI#getCurrentView()
   */
  @Override
  public BwView getCurrentView() throws CalFacadeException {
    return currentView;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ClientStateI#setVirtualPath(java.lang.String)
   */
  @Override
  public boolean setVirtualPath(final String vpath) throws CalFacadeException {
    /* We decompose the virtual path into it's elements and then try to
     * build a sequence of collections that include the aliases and their
     * targets until we reach the last element in the path.
     *
     * We'll assume the path is already normalized and that no "/" are allowed
     * as parts of names.
     *
     * What we're doing here is resolving aliases to aliases and accumulating
     * any filtering that might be in place as a sequence of ANDed terms. For
     * example:
     *
     * /user/eng/Lectures has the filter cat=eng and is aliased to
     * /public/aliases/Lectures which has the filter cat=lectures and is aliased to
     * /public/cals/MainCal
     *
     * We want the filter (cat=eng) & (cat=Lectures) on MainCal.
     *
     * Below, we decompose the virtual path and we save the path to an actual
     * folder or calendar collection.
     */

    Collection<BwCalendar> cols =
      getSvc().getCalendarsHandler().decomposeVirtualPath(vpath);

    if (cols == null) {
      // Bad vpath
      return false;
    }

    vfilter = null;
    this.vpath = vpath;
    vpathTarget = vpath;

    for (BwCalendar col: cols) {
      if (debug) {
        trace("      vpath collection:" + col.getPath());
      }

      if (col.getFilterExpr() != null) {
        if (vfilter == null) {
          vfilter = "(" ;
        } else {
          vfilter += " & (";
        }
        vfilter += col.getFilterExpr() + ")";
      }

      if (col.getCollectionInfo().entitiesAllowed ||
          (col.getCalType() == BwCalendar.calTypeFolder)) {
        // reached an end point
        vpathTarget = col.getPath();
      }
    }

    if (debug) {
      trace("      vpath filter: " + vfilter);
    }

    colorMap = new ColorMap();
    currentView = null;
    filter = null;
//    filter = new FilterBuilder(getSvc(),
  //                             colorMap).buildFilter(null, vfilter, true);

    return true;
  }

  @Override
  public String getVirtualPath() throws CalFacadeException {
    return vpath;
  }

  @Override
  public BwCalendar getCurrentCollection() throws CalFacadeException {
    return null; //cc currentCollection;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ClientStateI#getViewFilter(org.bedework.calfacade.BwCalendar)
   */
  @Override
  public FilterBase getViewFilter(final BwCalendar cal) throws CalFacadeException {
    List<String> paths;
    boolean conjunction = false;

    if (cal != null) {
      /* One shot collection supplied */
      paths = new ArrayList<String>();

      paths.add(cal.getPath());
      colorMap = new ColorMap();

      return new FilterBuilder(getSvc(),
                               colorMap).buildFilter(paths,
                                                     conjunction,
                                                     null, true);
    }

    if (filter != null) {
      return filter;
    }

    if (vpathTarget != null) {
      paths = new ArrayList<String>();

      paths.add(vpathTarget);
    } else if (currentView != null) {
      paths = currentView.getCollectionPaths();
      conjunction = currentView.getConjunction();
    } else {
      return null;
    }

    colorMap = new ColorMap();
    filter = new FilterBuilder(getSvc(),
                               colorMap).buildFilter(paths,
                                                     conjunction,
                                                     vfilter, true);
    return filter;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ClientStateI#setColor(java.util.Collection)
   */
  @Override
  public void setColor(final Collection<EventInfo> eis) {
    if ((colorMap == null) || Util.isEmpty(eis)) {
      return;
    }

    try {
      colorMap.setColor(eis, getSvc().getPrincipal().getPrincipalRef());
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
