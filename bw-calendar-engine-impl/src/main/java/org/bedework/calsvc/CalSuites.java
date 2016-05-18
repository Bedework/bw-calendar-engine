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
import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.Privilege;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.WhoDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
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

/** This acts as an interface to the database for calendar suites.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class CalSuites extends CalSvcDb implements CalSuitesI {
  private BwCalSuiteWrapper currentCalSuite;

  /** Constructor
   *
   * @param svci
   */
  CalSuites(final CalSvc svci) {
    super(svci);
  }

  @Override
  public BwCalSuiteWrapper add(final String name,
                               final String adminGroupName,
                               final String rootCollectionPath,
                               final String submissionsPath) throws CalFacadeException {
    BwCalSuite cs = getCal().getCalSuite(name);

    if (cs != null) {
      throw new CalFacadeException(CalFacadeException.duplicateCalsuite);
    }

    cs = new BwCalSuite();
    cs.setName(name);

    setRootCol(cs, rootCollectionPath);
    setupSharableEntity(cs, getPrincipal().getPrincipalRef());

    setSubmissionsCol(cs, submissionsPath);
    validateGroup(cs, adminGroupName);

    getCal().saveOrUpdate(cs);

    return wrap(cs, false);
  }

  @Override
  public void set(final BwCalSuiteWrapper val) throws CalFacadeException {
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
    if ((cs.getSubmissionsRoot() == null) &&
        (cs.getSubmissionsRootPath() != null)) {
      cs.setSubmissionsRoot(getCols().get(cs.getSubmissionsRootPath()));
    }

    if ((cs.getRootCollection() == null) &&
        (cs.getRootCollectionPath() != null)) {
      cs.setRootCollection(getCols().get(cs.getRootCollectionPath()));
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSuitesI#get(java.lang.String)
   */
  @Override
  public BwCalSuiteWrapper get(final String name) throws CalFacadeException {
    BwCalSuite cs = getCal().getCalSuite(name);

    if (cs == null) {
      return null;
    }

    checkCollections(cs);

    return wrap(cs, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSuitesI#get(org.bedework.calfacade.svc.BwAdminGroup)
   */
  @Override
  public BwCalSuiteWrapper get(final BwAdminGroup group)
        throws CalFacadeException {
    BwCalSuite cs = getCal().get(group);

    if (cs == null) {
      return null;
    }

    checkCollections(cs);

    return wrap(cs, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSuitesI#getAll()
   */
  @Override
  public Collection<BwCalSuite> getAll() throws CalFacadeException {
    Collection<BwCalSuite> css = getCal().getAllCalSuites();

    TreeSet<BwCalSuite> retCss = new TreeSet<BwCalSuite>();

    for (BwCalSuite cs: css) {
      checkCollections(cs);

      BwCalSuite w = wrap(cs, true);

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
                     final String submissionsPath) throws CalFacadeException {
    BwCalSuite cs = csw.fetchEntity();

    if (adminGroupName != null) {
      validateGroup(cs, adminGroupName);
    }

    setRootCol(cs, rootCollectionPath);
    setSubmissionsCol(cs, submissionsPath);

    getCal().saveOrUpdate(cs);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSuitesI#delete(org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper)
   */
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
      return getBasicSyspars().getGlobalResourcesPath();
    }

    final BwPrincipal eventsOwner = getPrincipal(suite.getGroup().getOwnerHref());

    final String home = getSvc().getPrincipalInfo().getCalendarHomePath(eventsOwner);

    final BwPreferences prefs = getSvc().getPrefsHandler().get(eventsOwner);

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
      return Util.buildPath(false, home, "/", col);
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
      BwResource r = getRess().get(Util.buildPath(false, getResourcesPath(suite, cl),
                                                  "/",
                                                  name));
      if (r != null) {
        getRess().getContent(r);
      }

      return r;
    } catch (CalFacadeException cfe) {
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
    final BwCalendar resCol = getResourcesDir(suite, cl);

    getRess().save(resCol.getPath(), res, false);
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
    String path = getResourcesPath(suite, cl);

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

    String parentPath = path.substring(0, path.lastIndexOf("/") + 1);

    resCol = getCols().add(resCol, parentPath);

    /* Ownership is enough to control writability. We have to make it
     * world-readable
     */

    try {
      Collection<Privilege> readPrivs = new ArrayList<Privilege>();
      readPrivs.add(Access.read);

      Collection<Ace> aces = new ArrayList<Ace>();
      aces.add(Ace.makeAce(AceWho.all, readPrivs, null));

      getSvc().changeAccess(resCol, aces, true);
    } catch (AccessException ae) {
      throw new CalFacadeException(ae);
    }

    return resCol;
  }

  /** Set root collection if supplied
   *
   * @param cs
   * @param rootCollectionPath
   * @throws CalFacadeException
   */
  private void setRootCol(final BwCalSuite cs,
                          final String rootCollectionPath) throws CalFacadeException {
    if ((rootCollectionPath == null) ||
        rootCollectionPath.equals(cs.getRootCollectionPath())) {
      return;
    }

    BwCalendar rootCol = getCols().get(rootCollectionPath);
    if (rootCol == null) {
      throw new CalFacadeException(CalFacadeException.calsuiteUnknownRootCollection,
                                   rootCollectionPath);
    }

    cs.setRootCollection(rootCol);
    cs.setRootCollectionPath(rootCol.getPath());
  }

  /** Set submissions collection if supplied
   *
   * @param cs
   * @param submissionsPath path
   * @throws CalFacadeException
   */
  private void setSubmissionsCol(final BwCalSuite cs,
                                 final String submissionsPath) throws CalFacadeException {
    if ((submissionsPath == null) ||
        submissionsPath.equals(cs.getSubmissionsRoot())) {
      return;
    }

    BwCalendar submissionsCol = getCols().get(submissionsPath);
    if (submissionsCol == null) {
      throw new CalFacadeException(CalFacadeException.calsuiteUnknownSubmissionsCollection,
                                   submissionsPath);
    }

    cs.setSubmissionsRoot(submissionsCol);
    cs.setSubmissionsRootPath(submissionsCol.getPath());
  }

  /** Ensure the given group is valid for the given calendar suite
   *
   * @param cs
   * @param groupName
   * @return home for the group
   * @throws CalFacadeException
   */
  private BwCalendar validateGroup(final BwCalSuite cs,
                                   final String groupName) throws CalFacadeException {
    if (groupName.length() > BwCalSuite.maxNameLength) {
      throw new CalFacadeException(CalFacadeException.calsuiteGroupNameTooLong);
    }

    BwAdminGroup agrp = (BwAdminGroup)getSvc().getAdminDirectories().findGroup(groupName);
    if (agrp == null) {
      throw new CalFacadeException(CalFacadeException.groupNotFound,
                                   groupName);
    }

    BwCalSuiteWrapper csw = get(agrp);

    if ((csw != null) && !csw.equals(cs)) {
      // Group already assigned to another cal suite
      throw new CalFacadeException(CalFacadeException.calsuiteGroupAssigned,
                                   csw.getName());
    }

    BwPrincipal eventsOwner = getPrincipal(agrp.getOwnerHref());

    if (eventsOwner == null) {
      throw new CalFacadeException(CalFacadeException.calsuiteBadowner);
    }

    BwCalendar home = getCols().getHome(eventsOwner, true);
    if (home == null) {
      throw new CalFacadeException(CalFacadeException.missingGroupOwnerHome);
    }

    cs.setGroup(agrp);

    /* Change access on the home for the events creator which is also the
     * owner of the calsuite resources.
     */

    Collection<Privilege> allPrivs = new ArrayList<Privilege>();
    allPrivs.add(Access.all);

    Collection<Privilege> readPrivs = new ArrayList<Privilege>();
    readPrivs.add(Access.read);

    Collection<Ace> aces = new ArrayList<Ace>();
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

      String aclStr = new String(new Acl(aces).encode());

      eventsOwner.setCategoryAccess(aclStr);
      eventsOwner.setLocationAccess(aclStr);
      eventsOwner.setContactAccess(aclStr);
    } catch (AccessException ae) {
      throw new CalFacadeException(ae);
    }

    getSvc().getUsersHandler().update(eventsOwner);


    return home;
  }

  private BwCalSuiteWrapper wrap(final BwCalSuite cs,
                                 final boolean alwaysReturn) throws CalFacadeException {
    CurrentAccess ca = checkAccess(cs, PrivilegeDefs.privAny, alwaysReturn);

    if ((ca == null) || !ca.getAccessAllowed()) {
      return null;
    }

    BwCalSuiteWrapper w = new BwCalSuiteWrapper(cs, ca);

    BwAdminGroup agrp = cs.getGroup();
    if (agrp == null) {
      return w;
    }

    BwPrincipal eventsOwner = getSvc().getUsersHandler().getPrincipal(agrp.getOwnerHref());
    if (eventsOwner == null) {
      return w;
    }

    BwCalendar home = getCols().getHome(eventsOwner, false);
    if (home == null) {
      return w;
    }

    w.setResourcesHome(home.getPath());

    return w;
  }
}

