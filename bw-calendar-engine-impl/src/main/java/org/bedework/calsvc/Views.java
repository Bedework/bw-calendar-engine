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
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
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

  @Override
  public boolean add(final BwView val,
                     final boolean makeDefault) throws CalFacadeException {
    if (val == null) {
      return false;
    }

    BwPreferences prefs = getPrefs();
    checkOwnerOrSuper(prefs);

    if (!prefs.addView(val)) {
      return false;
    }

    if (makeDefault) {
      prefs.setPreferredView(val.getName());
    }

    update(prefs);

    return true;
  }

  @Override
  public boolean remove(final BwView val) throws CalFacadeException{
    if (val == null) {
      return false;
    }

    BwPreferences prefs = getPrefs();
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

    update(prefs);

    return true;
  }

  @Override
  public BwView find(String val) throws CalFacadeException {
    if (val == null) {
      BwPreferences prefs = getPrefs();

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

    /* Other user - we expect a path of the form
     *  /user/<id>/<bedework-resource-name>/views/<view-name>
     */

    final String[] pathEls = val.split("/");

    if ((pathEls.length != 5) ||
            !BasicSystemProperties.bedeworkResourceDirectory.equals(
                    pathEls[2]) ||
            !"views".equals(pathEls[3])) {
      return null;
    }

    final StringBuilder sb = new StringBuilder();

    if (BasicSystemProperties.userCalendarRoot.equals(pathEls[0])) {
      sb.append(BwPrincipal.userPrincipalRoot);
    } else {
      return null;
    }

    sb.append(pathEls[1]);  // user id

    final BwPrincipal pr = getPrincipal(sb.toString());
    if (pr == null) {
      return null;
    }

    final Collection<BwView> views = getAll(pr);
    final String viewName = pathEls[4];
    for (final BwView view: views) {
      if (view.getName().equals(viewName)) {
        return view;
      }
    }

    return null;
  }

  @Override
  public boolean addCollection(final String name,
                               final String path) throws CalFacadeException {
    BwPreferences prefs = getPrefs();
    checkOwnerOrSuper(prefs);

    BwView view = find(name);

    if (view == null) {
      return false;
    }

    view.addCollectionPath(path);

    update(prefs);

    return true;
  }

  @Override
  public boolean removeCollection(final String name,
                                  final String path) throws CalFacadeException {
    BwPreferences prefs = getPrefs(getPrincipal());
    checkOwnerOrSuper(prefs);

    BwView view = find(name);

    if (view == null) {
      return false;
    }

    view.removeCollectionPath(path);

    update(prefs);

    return true;
  }

  @Override
  public Collection<BwView> getAll() {
    Collection<BwView> c = getPrefs().getViews();
    if (c == null) {
      c = new TreeSet<>();
    }
    return c;
  }

  @Override
  public Collection<BwView> getAll(final BwPrincipal pr) {
    Collection<BwView> c = getPrefs(pr).getViews();
    if (c == null) {
      c = new TreeSet<>();
    }
    return c;
  }

  /* This checks to see if the current user has owner access based on the
   * supplied object. This is used to limit access to objects not normally
   * shared such as preferences and related objects like views and subscriptions.
   */
  private void checkOwnerOrSuper(final Object o) throws CalFacadeException {
    if (isGuest()) {
      throw new CalFacadeAccessException();
    }

    if (isSuper()) {
      // Always ok?
      return;
    }

    if (!(o instanceof BwOwnedDbentity)) {
      throw new CalFacadeAccessException();
    }

    final BwOwnedDbentity<?> ent = (BwOwnedDbentity<?>)o;

    /*if (!isPublicAdmin()) {
      // Expect a different owner - always public-user????
      return;
    }*/

    if (getPrincipal().getPrincipalRef().equals(ent.getOwnerHref())) {
      return;
    }

    throw new CalFacadeAccessException();
  }
}
