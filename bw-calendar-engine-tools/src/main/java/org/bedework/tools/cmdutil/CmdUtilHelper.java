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
package org.bedework.tools.cmdutil;

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.DirectoryInfo;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.EventsI;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

import java.io.StreamTokenizer;
import java.util.List;

/** Handle processing for CmdUtil
 *
 * @author douglm
 *
 */
public abstract class CmdUtilHelper implements Logged {
  protected ProcessState pstate;

  CmdUtilHelper(final ProcessState pstate) {
    this.pstate = pstate;
  }

  abstract boolean process() throws Throwable;

  /**
   *
   * @return command e.g. "add"
   */
  abstract String command();

  abstract String description();

  protected void addInfo(final String val) {
    pstate.addInfo(val);
  }

  protected void addError(final String val) {
    pstate.addError(val);
  }

  protected CalSvcI getSvci() {
    return pstate.getSvci();
  }

  protected CalendarsI getCols() throws CalFacadeException {
    return pstate.getSvci().getCalendarsHandler();
  }

  protected EventsI getEvents() throws CalFacadeException {
    return pstate.getSvci().getEventsHandler();
  }

  public DirectoryInfo getDirectoryInfo() throws CalFacadeException {
    return getSvci().getDirectories().getDirectoryInfo();
  }

  protected void open() throws Throwable {
    final CalSvcI svci = getSvci();

    if (svci == null) {
      return;
    }
    svci.open();
    svci.beginTransaction();
  }

  protected void close() {
    try {
      final CalSvcI svci = getSvci();

      if (svci == null) {
        return;
      }
      svci.endTransaction();
      svci.close();
      pstate.setSvci(null);
    } catch (final Throwable t) {
      error(t);
    }
  }

  public BwIndexer getIndexer(final String docType) {
    return getSvci().getIndexer(true, docType);
  }

  public FilterBase parseQuery(final String query) {
    final SimpleFilterParser.ParseResult pr = getSvci().getFilterParser().parse(query, false, null);

    if (pr.ok) {
      return pr.filter;
    }

    error("Expression " + query +
                  " failed to parse: " + pr.message);
    return null;
  }

  /* expect a possibly quoted path next
   */
  protected BwCalendar getCal() throws Throwable {
    return getCal(wordOrQuotedVal());
  }

  protected BwCalendar getCal(final String path) throws Throwable {
    if (path == null) {
      error("Expected a path");
      return null;
    }

    final BwCalendar cal = getSvci().getCalendarsHandler().get(path);

    if (cal == null) {
      error("Unable to access calendar " + path);
    }

    return cal;
  }

  protected void setUser(final String account,
                         final boolean superUser) throws Throwable {
    if (account.equals(pstate.getAccount())) {
      info("Account is already " + account);
      return; // No change
    }

    info("Setting account to " + account);

    pstate.closeSvci();

    pstate.setAccount(account);
    pstate.setSuperUser(superUser);

    // Open to force creation of account
    try {
      open();
    } finally {
      close();
    }
  }

  protected BwAdminGroup makeAdminGroup(final String account,
                                        final String description,
                                        final String owner,
                                        final String eventOwner) throws Throwable {
    if (description == null) {
      addError("Must supply admin group description");
      return null;
    }

    if (owner == null) {
      addError("Must supply admin group owner");
      return null;
    }

    final BwAdminGroup grp = new BwAdminGroup();

    grp.setAccount(account);

    final DirectoryInfo di = getDirectoryInfo();
    String href = di.getBwadmingroupPrincipalRoot();
    if (!href.endsWith("/")) {
      href += "/";
    }

    grp.setPrincipalRef(href + account);

    grp.setDescription(description);

    final BwPrincipal adgPr = getUserAlways(owner);
    if (adgPr == null) {
      return null;
    }

    grp.setGroupOwnerHref(adgPr.getPrincipalRef());

    final BwPrincipal adePr;
    if (eventOwner == null) {
      adePr = getUserAlways("agrp_" + account);
    } else {
      adePr = getUserAlways(eventOwner);
    }

    if (adePr == null) {
      return null;
    }

    grp.setOwnerHref(adePr.getPrincipalRef());

    getSvci().getAdminDirectories().addGroup(grp);

    return grp;
  }

  protected boolean addToAdminGroup(final String accountToAdd,
                                    final String kind,
                                    final String groupName) throws Throwable {
    if (accountToAdd == null) {
      pstate.addError("Must supply account");
      return false;
    }

    final boolean group;

    if ("group".equals(kind)) {
      group = true;
    } else if ("user".equals(kind)) {
      group = false;
    } else {
      pstate.addError("Invalid kind: " + kind);
      return false;
    }

    final BwAdminGroup grp =
            (BwAdminGroup)getSvci().getAdminDirectories()
                                   .findGroup(groupName);

    if (grp == null) {
      pstate.addError("Unknown group " + groupName);
      return false;
    }

    if (grp.isMember(accountToAdd, group)) {
      pstate.addError("Already a member: " + accountToAdd);
      return false;
    }

    final BwPrincipal nmbr = newMember(accountToAdd, !group);

    getSvci().getAdminDirectories().addMember(grp, nmbr);
    getSvci().getAdminDirectories().updateGroup(grp);

    return true;
  }

  protected BwCalendar makeCollection(final String type,
                                      final String parentPath,
                                      final String calName,
                                      final String calSummary,
                                      final String aliasTarget,
                                      final String ownerHref,
                                      final String creatorHref,
                                      final String description,
                                      final String filter,
                                      final List<String> catuids) throws Throwable {
    final int calType;
    boolean topicalArea  = false;

    if (type == null) {
      error("Expected a collection type");
      return null;
    }

    switch (type) {
      case "folder":
        calType = BwCalendar.calTypeFolder;
        break;
      case "calendar":
        calType = BwCalendar.calTypeCalendarCollection;
        break;
      case "alias":
        calType = BwCalendar.calTypeAlias;
        break;
      case "topic":
        calType = BwCalendar.calTypeAlias;
        topicalArea = true;
        break;
      default:
        error("Expected a collection type 'folder', 'calendar', 'alias' or 'topic'");
        return null;
    }

    if (parentPath == null) {
      error("No parent path");
      return null;
    }

    if (calName == null) {
      error("Expected a collection name");
      return null;
    }

    if (calSummary == null) {
      error("Expected a collection display-name");
      return null;
    }

    if (ownerHref == null) {
      error("Expected an owner href");
      return null;
    }

    if (creatorHref == null) {
      error("Expected a creator href");
      return null;
    }

    final BwCalendar cal = new BwCalendar();

    cal.setName(calName);
    cal.setSummary(calSummary);
    cal.setCalType(calType);
    //cal.setPath(parentPath + "/" + calName);

    if (calType == BwCalendar.calTypeAlias) {
      final BwCalendar target = getCal(aliasTarget);

      if (target == null) {
        error("Require a target for alias");
        return null;
      }

      cal.setAliasUri(BwCalendar.internalAliasUriPrefix +
                              target.getPath());

      if (topicalArea) {
        cal.setIsTopicalArea(true);
      }
    }

    /* Owner and creator href */
    cal.setOwnerHref(ownerHref);
    cal.setCreatorHref(creatorHref);
    cal.setDescription(description);

    /* filter */

    boolean filterSupplied = false;

    if (filter != null) {
      cal.setFilterExpr(filter);
      filterSupplied = true;
    }

    if ((catuids != null) && (catuids.size() > 0)) {
      final StringBuilder filterExpr = new StringBuilder(
              "catuid=(");
      String delim = "";

      /* Now we have the owner find or add the categories */

      for (final String catStr : catuids) {
        final GetEntityResponse<BwCategory> resp =
                getCatPersistent(ownerHref, catStr);

        final BwCategory cat;

        if (resp.getStatus() == Response.Status.notFound) {
          cat = BwCategory.makeCategory();

          cat.setPublick(true);
          cat.setWordVal(catWd(catStr));
          //cat.setOwner(svci.getUser());

          getSvci().getCategoriesHandler().add(cat);
        } else if (resp.isOk()) {
          cat = resp.getEntity();
        } else {
          error("get cat: Status: " + resp.getStatus() +
                        " message: " + resp.getMessage());
          throw new RuntimeException("Unable to fetch category");
        }

        cal.addCategory(cat);

        filterExpr.append("\"");
        filterExpr.append(cat.getUid());
        filterExpr.append("\"");
        filterExpr.append(delim);
        delim = ",";
      }

      filterExpr.append(")");

      if (!filterSupplied) {
        cal.setFilterExpr(filterExpr.toString());
      }
    }

    try {
      getSvci().getCalendarsHandler().add(cal, parentPath);
    } catch (final CalFacadeException cfe) {
      if (CalFacadeException.duplicateCalendar.equals(cfe.getMessage())) {
        error("Collection " + calName + " already exists on path " + parentPath);
        return null;
      }

      if (CalFacadeException.collectionNotFound.equals(cfe.getMessage())) {
        error("Collection " + parentPath + " does not exist");
        return null;
      }

      throw cfe;
    }

    return cal;
  }

  /* expect a quoted uid next
   */
  protected BwLocation getPersistentLoc() throws Throwable {
    final String uid = quotedVal();

    if (uid == null) {
      error("Expected a quoted uid");
      return null;
    }

    final BwLocation loc = getSvci().getLocationsHandler().getPersistent(uid);

    if (loc == null) {
      error("Unable to access location " + uid);
    }

    return loc;
  }

  protected BwCalendar getAliasTarget(final BwCalendar col) throws Throwable {
    return getSvci().getCalendarsHandler().resolveAlias(col,
                                                        true,
                                                        false);
  }

  /* expect a possibly quoted path next
   */
  protected String getAliasPath() throws Throwable {
    final BwCalendar cal = getCal();

    if (cal == null) {
      return null;
    }

    if (cal.getCalType() != BwCalendar.calTypeAlias) {
      error(cal.getPath() + " is not an alias");
      return null;
    }

    return cal.getPath();
  }

  public BwPrincipal getUserAlways(final String val) {
    try {
      return getSvci().getUsersHandler().getAlways(val);
    } catch (final Throwable t) {
      pstate.addError("Unable to retrieve user " + val);
      return null;
    }
  }

  public BwPrincipal addUser(final String val) throws Throwable {
    getSvci().getUsersHandler().add(val);
    return getUserAlways(val);
  }

  public BwPreferences getPrefs() throws Throwable {
    return getSvci().getPrefsHandler().get();
  }

  public BwAuthUser getAuthUser(final String val) throws Throwable {
    return getSvci().getUserAuth().getUser(val);
  }

  public BwPrincipal findGroup(final String val) throws Throwable {
    return getSvci().getDirectories().findGroup(val);
  }

  public BwPrincipal newMember(final String account,
                               final boolean user) throws Throwable {

    if (user) {
      BwPrincipal p = getSvci().getUsersHandler().getUser(account);

      if (p == null) {
        p = addUser(account);
      }

      /* Ensure the authorised user exists - create an entry if not
       *
       * @param val      BwUser account
       */

      BwAuthUser au = getSvci().getUserAuth().getUser(p.getAccount());

      if ((au != null) && au.isUnauthorized()) {
        pstate.addError("Unauthorised user " + account);
        return null;
      }

      if (au == null) {
        au = BwAuthUser.makeAuthUser(p.getPrincipalRef(),
                                     UserAuth.publicEventUser);
        getSvci().getUserAuth().addUser(au);
      }

      return p;
    }

    // group
    final BwPrincipal p = findGroup(account);

    if (p == null) {
      pstate.addError("Unknown group " + account);
    }

    return p;
  }

  /* Get an event given path and name
   */
  protected EventInfo getEvent(final String path,
                               final String name) throws Throwable {
    if (path == null) {
      error("Expected a path");
      return null;
    }

    final EventInfo ent = getSvci().getEventsHandler().get(path, name);

    if (ent == null) {
      error("Unable to fetch event " + path + "/" + name);
    }

    return ent;
  }

  @SuppressWarnings("unused")
  protected BwCategory getCat(final String ownerHref)  throws Throwable {
    final String catVal = wordOrQuotedVal();

    if (catVal == null) {
      error("Expected a category");
      return null;
    }

    return getCat(ownerHref, catVal);
  }

  @SuppressWarnings("unused")
  protected boolean expect(final String val) throws Throwable {
    final String next = word();

    if (!next.equals(val)) {
      error("Expected " + val + " got " + next);
      return false;
    }

    return true;
  }

  protected boolean test(final String val) throws Throwable {
    return pstate.getTokenizer().testToken(val);
  }

  protected String catWd(final String val) {
    if (val.startsWith("/public/.bedework/categories/")) {
      return val.substring("/public/.bedework/categories/".length());
    }

    if (val.startsWith("/")) {
      final int pos = val.lastIndexOf("categories/");
      return val.substring(pos + "categories/".length());
    }

    return val;
  }

  protected BwCategory getCat(final String ownerHref,
                              final String catVal) throws Throwable {
    final BwCategory cat =
            getSvci().getCategoriesHandler().find(new BwString(null,
                                                               catWd(catVal)));

    if (cat == null) {
      error("Unable to access category " + catVal +
                    " for owner " + ownerHref);
    }

    return cat;
  }

  protected GetEntityResponse<BwCategory> getCatPersistent(final String ownerHref,
                                                           final String catVal) {
    return getSvci().getCategoriesHandler().
            findPersistent(new BwString(null,
                                        catWd(catVal)));
  }

  @SuppressWarnings("unused")
  protected int nextToken() throws CalFacadeException {
    final int tkn = pstate.getTokenizer().next();

    if (!debug()) {
      return tkn;
    }

    if (tkn == StreamTokenizer.TT_WORD) {
      debug("tkn = word: " + pstate.getTokenizer().sval);
    } else if (tkn > 0) {
      debug("tkn = " + (char)tkn);
    } else {
      debug("tkn = " + tkn);
    }

    return tkn;
  }

  protected boolean testToken(final String token) throws CalFacadeException {
    return pstate.getTokenizer().testToken(token);
  }

  protected boolean testToken(final int token) throws CalFacadeException {
    return pstate.getTokenizer().testToken(token);
  }

  protected void assertToken(final int token) throws CalFacadeException {
    pstate.getTokenizer().assertToken(token);
  }

  protected boolean cmdEnd() throws Throwable {
    return testToken(StreamTokenizer.TT_EOL) ||
            testToken(StreamTokenizer.TT_EOF);
  }

  protected Boolean boolFor(final String wd) throws Throwable {
    if (testToken(StreamTokenizer.TT_WORD)) {
      final String val = pstate.getTokenizer().sval;
      if ("true".equals(val)) {
        return true;
      }
      if ("false".equals(val)) {
        return false;
      }
    }

    warn("Expected boolean value for " + wd);
    return null;
  }

  protected String word() throws Throwable {
    if (testToken(StreamTokenizer.TT_WORD)) {
      return pstate.getTokenizer().sval;
    }

    return null;
  }

  protected String wordOrQuotedVal() throws Throwable {
    final String wd = word();
    if (wd != null) {
      return wd;
    }

    return quotedVal();
  }

  protected String qstringFor(final String wd) throws Throwable {
    final String s = quotedVal();
    if (s != null) {
      return s;
    }

    warn("Expected String value for " + wd);
    return null;
  }

  protected String quotedVal() throws Throwable {
    if (!pstate.getTokenizer().testString()) {
      return null;
    }

    return pstate.getTokenizer().sval;
  }

  @Override
  public void error(final String msg) {
    pstate.addError(msg);
    Logged.super.error(msg);
  }

  @Override
  public void warn(final String msg) {
    pstate.addInfo(msg);
    Logged.super.warn(msg);
  }

  @Override
  public void info(final String msg) {
    pstate.addInfo(msg);
    Logged.super.info(msg);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
