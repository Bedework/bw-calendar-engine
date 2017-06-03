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
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.DirectoryInfo;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.EventsI;
import org.bedework.util.misc.Logged;

import java.io.StreamTokenizer;

/** Handle processing for CmdUtil
 *
 * @author douglm
 *
 */
public abstract class CmdUtilHelper extends Logged {
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

  protected void close() throws Throwable {
    final CalSvcI svci = getSvci();

    if (svci == null) {
      return;
    }
    svci.endTransaction();
    svci.close();
  }
  
  public BwIndexer getIndexer() throws CalFacadeException {
    return getSvci().getIndexer(true);
  }

  public FilterBase parseQuery(final String query) throws CalFacadeException {
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
    final String path = wordOrQuotedVal();

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

  protected BwCategory getCat(final String ownerHref)  throws Throwable {
    final String catVal = wordOrQuotedVal();

    if (catVal == null) {
      error("Expected a category");
      return null;
    }

    return getCat(ownerHref, catVal);
  }

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
    if (val.startsWith("/")) {
      final int pos = val.lastIndexOf("/");
      return val.substring(pos + 1);
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

  protected BwCategory getCatPersistent(final String ownerHref,
                              final String catVal) throws Throwable {
    final BwCategory cat =
            getSvci().getCategoriesHandler().findPersistent(new BwString(null,
                                                               catWd(catVal)));

    if (cat == null) {
      error("Unable to access category " + catVal +
                    " for owner " + ownerHref);
    }

    return cat;
  }

  protected int nextToken() throws CalFacadeException {
    final int tkn = pstate.getTokenizer().next();

    if (!debug) {
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
  
  public void error(final String msg) {
    pstate.addError(msg);
    super.error(msg);
  }

  public void warn(final String msg) {
    pstate.addInfo(msg);
    super.warn(msg);
  }

  public void info(final String msg) {
    pstate.addInfo(msg);
    super.info(msg);
  }
}
