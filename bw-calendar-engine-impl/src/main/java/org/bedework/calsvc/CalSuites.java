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

import org.bedework.access.Access;
import org.bedework.access.AccessException;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.Acl;
import org.bedework.access.CurrentAccess;
import org.bedework.access.Privilege;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.WhoDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper;
import org.bedework.calsvci.CalSuitesI;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** This acts as an interface to the database for calendar suites.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class CalSuites extends CalSvcDb implements CalSuitesI {
  private BwCalSuiteWrapper currentCalSuite;

  /** Constructor
   *
   * @param svci for interactions with api
   */
  CalSuites(final CalSvc svci) {
    super(svci);
  }

  @Override
  public BwCalSuiteWrapper add(final String name,
                               final String adminGroupName,
                               final String rootCollectionPath,
                               final String description) throws CalFacadeException {
    BwCalSuite cs = getCal().getCalSuite(name);

    if (cs != null) {
      throw new CalFacadeException(CalFacadeException.duplicateCalsuite);
    }

    cs = new BwCalSuite();
    cs.setName(name);

    cs.setDescription(description);

    setRootCol(cs, rootCollectionPath);
    getSvc().setupSharableEntity(cs, getPrincipal().getPrincipalRef());

    validateGroup(cs, adminGroupName);

    getCal().saveOrUpdate(cs);

    return wrap(cs, false);
  }

  @Override
  public void set(final BwCalSuiteWrapper val) {
    currentCalSuite = val;
  }

  @Override
  public BwCalSuiteWrapper get() throws CalFacadeException {
    if (currentCalSuite == null) {
      return null;
    }

    checkCollections(currentCalSuite);

    return currentCalSuite;
  }

  private void checkCollections(final BwCalSuite cs) throws CalFacadeException {
    if ((cs.getRootCollection() == null) &&
        (cs.getRootCollectionPath() != null)) {
      cs.setRootCollection(getCols().get(cs.getRootCollectionPath()));
    }
  }

  @Override
  public BwCalSuiteWrapper get(final String name) throws CalFacadeException {
    final BwCalSuite cs = getCal().getCalSuite(name);

    if (cs == null) {
      return null;
    }

    checkCollections(cs);

    return wrap(cs, false);
  }

  @Override
  public BwCalSuiteWrapper get(final BwAdminGroup group)
        throws CalFacadeException {
    final BwCalSuite cs = getCal().get(group);

    if (cs == null) {
      return null;
    }

    checkCollections(cs);

    return wrap(cs, false);
  }

  @Override
  public Collection<BwCalSuite> getAll() throws CalFacadeException {
    final Collection<BwCalSuite> css = getCal().getAllCalSuites();

    final TreeSet<BwCalSuite> retCss = new TreeSet<>();

    for (final BwCalSuite cs: css) {
      checkCollections(cs);

      final BwCalSuite w = wrap(cs, true);

      if (w != null) {
        retCss.add(w);
      }
    }

    return retCss;
  }

  @Override
  public void update(final BwCalSuiteWrapper csw,
                     final String adminGroupName,
                     final String rootCollectionPath,
                     final String description) throws CalFacadeException {
    final BwCalSuite cs = csw.fetchEntity();

    if (adminGroupName != null) {
      validateGroup(cs, adminGroupName);
    }

    cs.setDescription(description);

    setRootCol(cs, rootCollectionPath);

    getCal().saveOrUpdate(cs);
  }

  @Override
  public void delete(final BwCalSuiteWrapper val) throws CalFacadeException {
    getCal().delete(val.fetchEntity());
  }

  /* ====================================================================
   *                   Resource methods
   *  =================================================================== */

  public String getResourcesPath(final BwCalSuite suite,
                                 final ResourceClass cl) throws CalFacadeException {
    if (cl == ResourceClass.global) {
      return BasicSystemProperties.globalResourcesPath;
    }

    final BwPrincipal<?> eventsOwner = getPrincipal(suite.getGroup().getOwnerHref());

    final String home = getSvc().getPrincipalInfo().getCalendarHomePath(eventsOwner);

    final BwPreferences prefs = getPrefs(eventsOwner);

    String col = null;

    if (cl == ResourceClass.admin) {
      col = prefs.getAdminResourcesDirectory();

      if (col == null) {
        col = ".adminResources";
      }
    } else if (cl == ResourceClass.calsuite) {
      col = prefs.getSuiteResourcesDirectory();

      if (col == null) {
        col = ".csResources";
      }
    }

    if (col != null) {
      return Util.buildPath(colPathEndsWithSlash, home, "/", col);
    }

    throw new RuntimeException("System error");
  }

  @Override
  public List<BwResource> getResources(final BwCalSuite suite,
                                       final ResourceClass cl) throws CalFacadeException {
    return getRess().getAll(getResourcesPath(suite, cl));
  }

  @Override
  public BwResource getResource(final BwCalSuite suite,
                                final String name,
                                final ResourceClass cl) throws CalFacadeException {
    try {
      final BwResource r = getRess().get(Util.buildPath(false, getResourcesPath(suite, cl),
                                                        "/",
                                                        name));
      if (r != null) {
        getRess().getContent(r);
      }

      return r;
    } catch (final CalFacadeException cfe) {
      if (CalFacadeException.collectionNotFound.equals(cfe.getMessage())) {
        // Collection does not exist (yet)

        return null;
      }

      throw cfe;
    }
  }

  @Override
  public void addResource(final BwCalSuite suite,
                          final BwResource res,
                          final ResourceClass cl) throws CalFacadeException {
    res.setColPath(getResourcesPath(suite, cl));
    getRess().save(res, false);
  }

  @Override
  public void deleteResource(final BwCalSuite suite,
                             final String name,
                             final ResourceClass cl) throws CalFacadeException {
    getRess().delete(Util.buildPath(false, getResourcesPath(suite,
                                                     cl), "/", name));
  }

  /* ====================================================================
   *                   Private methods
   *  =================================================================== */

  private BwCalendar getResourcesDir(final BwCalSuite suite,
                                     final ResourceClass cl) throws CalFacadeException {
    final String path = getResourcesPath(suite, cl);

    if (path == null) {
      throw new CalFacadeException(CalFacadeException.noCalsuiteResCol);
    }

    BwCalendar resCol = getCols().get(path);
    if (resCol != null) {
      return resCol;
    }

    /* Create the collection. All are world readable. The calsuite class
     * collection is writable to the calsuite owner.
     */

    resCol = new BwCalendar();

    resCol.setName(path.substring(path.lastIndexOf("/") + 1));
    resCol.setSummary(resCol.getName());
    resCol.setCreatorHref(suite.getOwnerHref());

    if (cl == ResourceClass.calsuite) {
      // Owned by the suite
      resCol.setOwnerHref(suite.getOwnerHref());
    } else {
      resCol.setOwnerHref(getPublicUser().getPrincipalRef());
    }

    final String parentPath = path.substring(0, path.lastIndexOf("/"));

    resCol = getCols().add(resCol, parentPath);

    /* Ownership is enough to control writability. We have to make it
     * world-readable
     */

    try {
      final Collection<Privilege> readPrivs = new ArrayList<>();
      readPrivs.add(Access.read);

      final Collection<Ace> aces = new ArrayList<>();
      aces.add(Ace.makeAce(AceWho.all, readPrivs, null));

      getSvc().changeAccess(resCol, aces, true);
    } catch (final AccessException ae) {
      throw new CalFacadeException(ae);
    }

    return resCol;
  }

  /** Set root collection if supplied
   *
   * @param rootCollectionPath root collection path
   * @throws CalFacadeException on fatal error
   */
  private void setRootCol(final BwCalSuite cs,
                          final String rootCollectionPath) throws CalFacadeException {
    if ((rootCollectionPath == null) ||
        rootCollectionPath.equals(cs.getRootCollectionPath())) {
      return;
    }

    final BwCalendar rootCol = getCols().get(rootCollectionPath);
    if (rootCol == null) {
      throw new CalFacadeException(CalFacadeException.calsuiteUnknownRootCollection,
                                   rootCollectionPath);
    }

    cs.setRootCollection(rootCol);
    cs.setRootCollectionPath(rootCol.getPath());
  }

  /* Ensure the given group is valid for the given calendar suite
   *
   */
  private BwCalendar validateGroup(final BwCalSuite cs,
                                   final String groupName) throws CalFacadeException {
    if (groupName.length() > BwCalSuite.maxNameLength) {
      throw new CalFacadeException(CalFacadeException.calsuiteGroupNameTooLong);
    }

    final BwAdminGroup agrp =
            (BwAdminGroup)getSvc().
                    getAdminDirectories().
                    findGroup(groupName);
    if (agrp == null) {
      throw new CalFacadeException(CalFacadeException.groupNotFound,
                                   groupName);
    }

    final BwCalSuiteWrapper csw = get(agrp);

    if ((csw != null) && !csw.equals(cs)) {
      // Group already assigned to another cal suite
      throw new CalFacadeException(CalFacadeException.calsuiteGroupAssigned,
                                   csw.getName());
    }

    final BwPrincipal<?> eventsOwner = getPrincipal(agrp.getOwnerHref());

    if (eventsOwner == null) {
      throw new CalFacadeException(CalFacadeException.calsuiteBadowner);
    }

    final BwCalendar home = getCols().getHomeDb(eventsOwner, true);
    if (home == null) {
      throw new CalFacadeException(CalFacadeException.missingGroupOwnerHome);
    }

    cs.setGroup(agrp);

    /* Change access on the home for the events creator which is also the
     * owner of the calsuite resources.
     */

    final Collection<Privilege> allPrivs = new ArrayList<>();
    allPrivs.add(Access.all);

    final Collection<Privilege> readPrivs = new ArrayList<>();
    readPrivs.add(Access.read);

    final Collection<Ace> aces = new ArrayList<>();
    try {
      aces.add(Ace.makeAce(AceWho.owner, allPrivs, null));
      aces.add(Ace.makeAce(AceWho.getAceWho(eventsOwner.getAccount(),
                                            WhoDefs.whoTypeUser, false),
                                            allPrivs, null));
      aces.add(Ace.makeAce(AceWho.getAceWho(null,
                                            WhoDefs.whoTypeAuthenticated, false),
                                            readPrivs, null));
      aces.add(Ace.makeAce(AceWho.all, readPrivs, null));

      getSvc().changeAccess(home, aces, true);

      /* Same access to the calsuite itself */

      getSvc().changeAccess(cs, aces, true);

      /* Also set access so that categories, locations etc are readable */

      final String aclStr = new String(new Acl(aces).encode());

      eventsOwner.setCategoryAccess(aclStr);
      eventsOwner.setLocationAccess(aclStr);
      eventsOwner.setContactAccess(aclStr);
    } catch (final AccessException ae) {
      throw new CalFacadeException(ae);
    }

    getSvc().getUsersHandler().update(eventsOwner);


    return home;
  }

  private BwCalSuiteWrapper wrap(final BwCalSuite cs,
                                 final boolean alwaysReturn) throws CalFacadeException {
    final CurrentAccess ca = checkAccess(cs, PrivilegeDefs.privAny, alwaysReturn);

    if ((ca == null) || !ca.getAccessAllowed()) {
      return null;
    }

    final BwCalSuiteWrapper w = new BwCalSuiteWrapper(cs, ca);

    final BwAdminGroup agrp = cs.getGroup();
    if (agrp == null) {
      return w;
    }

    final BwPrincipal<?> eventsOwner =
            getSvc().getUsersHandler().getPrincipal(agrp.getOwnerHref());
    if (eventsOwner == null) {
      return w;
    }

    w.setResourcesHome(getSvc().getPrincipalInfo().getCalendarHomePath(eventsOwner));

    return w;
  }
}

