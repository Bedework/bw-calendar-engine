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
package org.bedework.dumprestore.restore;

import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.ExternalSubInfo;
import org.bedework.dumprestore.InfoLines;
import org.bedework.dumprestore.restore.rules.RestoreRuleSet;
import org.bedework.util.misc.Util;

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.Acl;
import org.bedework.access.Privilege;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.Privileges;
import org.bedework.access.WhoDefs;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.RegexMatcher;
import org.apache.commons.digester.RegexRules;
import org.apache.commons.digester.SimpleRegexMatcher;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Application to restore from an XML calendar dump..
 *
 * @author Mike Douglass   douglm rpi.edu
 * @version 3.1
 */
public class Restore implements Defs {
  private transient Logger log;

  private String appName;

  /* File we restore from */
  private String filename;

  private RestoreGlobals globals;

  private String adminUserAccount = "admin";

  /* True if we are creating a clean system */
  private boolean newSystem;

  private String rootId;

  /** ===================================================================
   *                       Constructor
   *  =================================================================== */

  /**
   * @throws Throwable
   */
  public Restore() throws Throwable {
    globals = new RestoreGlobals();
    globals.svci = getSvci();
  }

  /** ===================================================================
   *                       Restore methods
   *  =================================================================== */

  /**
   * @param val - filename to restore from
   */
  public void setFilename(final String val) {
    filename = val;
  }

  /**
   * @param val
   */
  public void setTimezonesUri(final String val) {
    globals.setTimezonesUri(val);
  }

  /**
   * @return uri for tz server
   */
  public String getTimezonesUri() {
    return globals.getTimezonesUri();
  }

  /**
   * @throws Throwable
   */
  public void open() throws Throwable {
    globals.svci.open();
    globals.rintf = globals.svci.getRestoreHandler();
    globals.rintf.setLogger(new RLogger(globals));
  }

  /**
   * @throws Throwable
   */
  public void close() throws Throwable {
    if (globals.svci != null) {
      globals.svci.close();
    }
  }

  /**
   * @param info - to track status
   * @throws Throwable
   */
  public void doRestore(final InfoLines info) throws Throwable {
    if (newSystem) {
      createNewSystem();

      return;
    }

    globals.info = info;
    globals.digester = new Digester();

    RegexMatcher m = new SimpleRegexMatcher();
    globals.digester.setRules(new RegexRules(m));

    globals.digester.addRuleSet(new RestoreRuleSet(globals));
    globals.digester.parse(new InputStreamReader(new FileInputStream(filename),
                                         "UTF-8"));
  }

  /**
   * @return list of external subscriptions
   */
  public List<ExternalSubInfo> getExternalSubs() {
    return globals.externalSubs;
  }

  private void createNewSystem() throws Throwable {
    // Create the public user.

    BwPrincipal pu = BwPrincipal.makeUserPrincipal();

    pu.setAccount(RestoreGlobals.getBasicSyspars().getPublicUser());
    globals.setPrincipalHref(pu);

    globals.rintf.restorePrincipal(pu);

    // Create the root user.

    BwPrincipal rootUser = BwPrincipal.makeUserPrincipal();

    rootUser.setAccount(rootId);
    globals.setPrincipalHref(rootUser);

    globals.rintf.restorePrincipal(rootUser);

    // Create the an authuser entry for the root user.

    BwAuthUser au = new BwAuthUser();
    au.setUserHref(rootUser.getPrincipalRef());
    au.setUsertype(UserAuth.allAuth);
    au.setPrefs(BwAuthUserPrefs.makeAuthUserPrefs());

    globals.rintf.restoreAuthUser(au);

    // Create a group for all public admin groups

    BwAdminGroup g = new BwAdminGroup();

    String publicAdminGroupsAccount = "publicAdminGroups";   // XXX Put into config
    g.setAccount(publicAdminGroupsAccount);
    g.setGroupOwnerHref(pu.getPrincipalRef());
    g.setOwnerHref(pu.getPrincipalRef());

    if (!globals.onlyUsersMap.check(g.getGroupOwnerHref())) {
      g.setGroupOwnerHref(globals.getPublicUser().getPrincipalRef());
    }

    globals.rintf.restoreAdminGroup(g);

    // Create the public root.

    Collection<Privilege> privs = new ArrayList<Privilege>();
    privs.add(Privileges.makePriv(PrivilegeDefs.privRead));

    Collection<Ace> aces = new ArrayList<Ace>();

    aces.add(Ace.makeAce(AceWho.other, privs, null));

    privs.clear();
    privs.add(Privileges.makePriv(PrivilegeDefs.privRead));
    privs.add(Privileges.makePriv(PrivilegeDefs.privWriteContent));

    AceWho who = AceWho.getAceWho(publicAdminGroupsAccount,
                                  WhoDefs.whoTypeGroup,
                                  false);
    aces.add(Ace.makeAce(who, privs, null));

    makeCal(null, pu,
            BwCalendar.calTypeFolder,
            RestoreGlobals.getBasicSyspars().getPublicCalendarRoot(),
            new String(new Acl(aces).encode()));

    // Create the user root.

    privs.clear();
    privs.add(Privileges.makePriv(PrivilegeDefs.privAll));

    aces.clear();
    aces.add(Ace.makeAce(AceWho.owner, privs, null));

    BwCalendar userRoot = makeCal(null, pu,
                                  BwCalendar.calTypeFolder,
                                  RestoreGlobals.getBasicSyspars().getUserCalendarRoot(),
                                  new String(new Acl(aces).encode()));

    makeUserHome(userRoot, pu);
    makeUserHome(userRoot, rootUser);
  }

  private void makeUserHome(final BwCalendar userRoot,
                            final BwPrincipal user) throws Throwable {
    // Create root user home and default calendar

    BwCalendar userHome = makeCal(userRoot, user,
                                  BwCalendar.calTypeFolder,
                                  user.getAccount(),
                                  null);
    makeCal(userHome, user,
            BwCalendar.calTypeCalendarCollection,
            RestoreGlobals.getBasicSyspars().getUserDefaultCalendar(),
            null);
  }

  private BwCalendar makeCal(final BwCalendar parent,
                             final BwPrincipal owner,
                             final int type,
                             final String name,
                             final String encodedAcl) throws Throwable {
    BwCalendar cal = new BwCalendar();

    cal.setCalType(type);

    String ppath = "";
    if (parent != null) {
      ppath = parent.getPath();
    }

    cal.setColPath(parent.getPath());
    cal.setName(name);
    cal.setPath(Util.buildPath(true, ppath, "/", cal.getName()));
    cal.setSummary(cal.getName());

    cal.setOwnerHref(owner.getPrincipalRef());
    cal.setCreatorHref(owner.getPrincipalRef());
    cal.setAccess(encodedAcl);

    globals.rintf.saveRootCalendar(cal);

    return cal;
  }

  /**
   *
   * @param infoLines - null for logged output only
   */
  public void stats(final List<String> infoLines) {
    globals.stats(infoLines);
  }

  /**
   * @param appName
   * @throws Throwable
   */
  public void getConfigProperties(String appName) throws Throwable {
    /* Look for the appname - the configured name for this applications
     *  and initSyspars arg
     */

    this.appName = appName;

    if (appName == null) {
      error("Missing required argument -appname");
      throw new Exception("Invalid args");
    }

    globals.init();
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void error(final String msg) {
    getLog().error(msg);
  }

  protected void info(final String msg) {
    getLog().info(msg);
  }

  protected void trace(final String msg) {
    getLog().debug(msg);
  }

  private CalSvcI getSvci() throws Throwable {
    CalSvcIPars pars = CalSvcIPars.getRestorePars(adminUserAccount);
    CalSvcI svci = new CalSvcFactoryDefault().getSvc(pars);

    return svci;
  }

  /**
   * @param val
   * @return 2 digit val
   */
  public static String twoDigits(final long val) {
    if (val < 10) {
      return "0" + val;
    }

    return String.valueOf(val);
  }
}
