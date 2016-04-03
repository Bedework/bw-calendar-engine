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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calsvci.ViewsI;

import java.util.Collection;
import java.util.TreeSet;

/** This acts as an interface to the database for views.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Views extends CalSvcDb implements ViewsI {
  Views(final CalSvc svci) {
    super(svci);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ViewsI#add(org.bedework.calfacade.svc.BwView, boolean)
   */
  @Override
  public boolean add(final BwView val,
                     final boolean makeDefault) throws CalFacadeException {
    if (val == null) {
      return false;
    }

    BwPreferences prefs = getSvc().getPrefsHandler().get();
    checkOwnerOrSuper(prefs);

    if (!prefs.addView(val)) {
      return false;
    }

    if (makeDefault) {
      prefs.setPreferredView(val.getName());
    }

    getSvc().getPrefsHandler().update(prefs);

    return true;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ViewsI#remove(org.bedework.calfacade.svc.BwView)
   */
  @Override
  public boolean remove(final BwView val) throws CalFacadeException{
    if (val == null) {
      return false;
    }

    BwPreferences prefs = getSvc().getPrefsHandler().get();
    checkOwnerOrSuper(prefs);

    //setupOwnedEntity(val, getUser());

    Collection<BwView> views = prefs.getViews();
    if ((views == null) || (!views.contains(val))) {
      return false;
    }

    String name = val.getName();

    views.remove(val);

    if (name.equals(prefs.getPreferredView())) {
      prefs.setPreferredView(null);
    }

    getSvc().getPrefsHandler().update(prefs);

    return true;
  }

  @Override
  public BwView find(String val) throws CalFacadeException {
    if (val == null) {
      BwPreferences prefs = getSvc().getPrefsHandler().get();

      val = prefs.getPreferredView();
      if (val == null) {
        return null;
      }
    }

    /* val may be a name in which case it's for the current user or it
     * may be a fully qualified path referencing another users views.
     */
    if (!val.startsWith("/")) {
      // This user
      Collection<BwView> views = getAll();
      for (BwView view: views) {
        if (view.getName().equals(val)) {
          return view;
        }
      }

      return null;
    }

    /* Other user - we expect a path of th eform
     *  /user/<id>/<bedework-resource-name>/views/<view-name>
     */

    String[] pathEls = val.split("/");

    BasicSystemProperties bsp = getBasicSyspars();

    if ((pathEls.length != 5) ||
            !bsp.getBedeworkResourceDirectory().equals(pathEls[2]) ||
            !"views".equals(pathEls[3])) {
      return null;
    }

    StringBuilder sb = new StringBuilder();

    if (bsp.getUserCalendarRoot().equals(pathEls[0])) {
      sb.append(bsp.getUserPrincipalRoot());
    } else {
      return null;
    }

    sb.append(pathEls[1]);  // user id

    BwPrincipal pr = getPrincipal(sb.toString());
    if (pr == null) {
      return null;
    }

    Collection<BwView> views = getAll(pr);
    String viewName = pathEls[4];
    for (BwView view: views) {
      if (view.getName().equals(viewName)) {
        return view;
      }
    }

    return null;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ViewsI#addCollection(java.lang.String, java.lang.String)
   */
  @Override
  public boolean addCollection(final String name,
                               final String path) throws CalFacadeException {
    BwPreferences prefs = getSvc().getPrefsHandler().get();
    checkOwnerOrSuper(prefs);

    BwView view = find(name);

    if (view == null) {
      return false;
    }

    view.addCollectionPath(path);

    getSvc().getPrefsHandler().update(prefs);

    return true;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ViewsI#removeCollection(java.lang.String, java.lang.String)
   */
  @Override
  public boolean removeCollection(final String name,
                                  final String path) throws CalFacadeException {
    BwPreferences prefs = getSvc().getPrefsHandler().get(getPrincipal());
    checkOwnerOrSuper(prefs);

    BwView view = find(name);

    if (view == null) {
      return false;
    }

    view.removeCollectionPath(path);

    getSvc().getPrefsHandler().update(prefs);

    return true;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.ViewsI#getAll()
   */
  @Override
  public Collection<BwView> getAll() throws CalFacadeException {
    Collection<BwView> c = getSvc().getPrefsHandler().get().getViews();
    if (c == null) {
      c = new TreeSet<BwView>();
    }
    return c;
  }

  @Override
  public Collection<BwView> getAll(final BwPrincipal pr) throws CalFacadeException {
    Collection<BwView> c = getSvc().getPrefsHandler().get(pr).getViews();
    if (c == null) {
      c = new TreeSet<BwView>();
    }
    return c;
  }
}
