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

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.Acl;
import org.bedework.access.Privilege;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.Privileges;
import org.bedework.access.WhoDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.dumprestore.AliasEntry;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.InfoLines;
import org.bedework.dumprestore.nrestore.RestorePrincipal;
import org.bedework.dumprestore.nrestore.RestorePublic;
import org.bedework.dumprestore.restore.rules.RestoreRuleSet;
import org.bedework.util.misc.Logged;
import org.bedework.util.misc.Util;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.RegexMatcher;
import org.apache.commons.digester.RegexRules;
import org.apache.commons.digester.SimpleRegexMatcher;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** Application to restore from an XML calendar dump..
 *
 * @author Mike Douglass   douglm rpi.edu
 * @version 3.1
 */
public class Restore extends Logged implements Defs, AutoCloseable {
  /* File we restore from */
  private String filename;

  private final RestoreGlobals globals;

  private final String adminUserAccount = "admin";
  
  private final boolean newRestoreFormat;

  /* True if we are creating a clean system */
  private boolean newSystem;

  private String rootId;

  /* ===================================================================
   *                       Constructor
   *  =================================================================== */

  /**
   * @throws Throwable on fatal error
   */
  public Restore(final boolean newRestoreFormat) throws Throwable {
    globals = new RestoreGlobals();
    globals.svci = getSvci();
    this.newRestoreFormat = newRestoreFormat;
  }

  /* ===================================================================
   *                       Restore methods
   *  =================================================================== */

  /**
   * @param val - filename to restore from or directory for new restore
   */
  public void setFilename(final String val) {
    filename = val;
    globals.setDirPath(filename);
  }

  /**
   * @throws Throwable on fatal error
   */
  public void open(final boolean forNewSystem) throws Throwable {
    globals.svci.open();
    globals.rintf = globals.svci.getRestoreHandler();
    globals.rintf.setLogger(new RLogger(globals));

    if (forNewSystem) {
      globals.rintf.checkEmptySystem();
    }
  }

  /**
   */
  public void close() {
    try {
      if (globals.svci != null) {
        globals.svci.close();
      }
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Restore a single user from a new style dump.
   * 
   * @param account of user
   * @param merge don't replace entities - add new ones.
   * @param info - to track status
   * @return true if restored - otherwise there's a message
   * @throws CalFacadeException on error
   */
  public boolean restoreUser(final String account,
                             final boolean merge,
                             final boolean dryRun,
                             final InfoLines info) throws CalFacadeException {
    globals.setRoots(getSvci());
    final BwPrincipal userPr = BwPrincipal.makeUserPrincipal();

    userPr.setAccount(account);

    userPr.setPrincipalRef(
            Util.buildPath(colPathEndsWithSlash,
                           RestoreGlobals.getUserPrincipalRoot(), 
                           "/", 
                           account));
    globals.info = info;
    globals.setPrincipalHref(userPr);
    globals.setMerging(merge);
    globals.setDryRun(dryRun);

    try (RestorePrincipal restorer = new RestorePrincipal(globals)) {
      restorer.open(userPr);
    
      restorer.doRestore();
    }
    return true;
  }

  /** Restore public data from a new style dump.
   *
   * @param merge don't replace entities - add new ones.
   * @param dryRun true means just pretend.
   * @param info - to track status
   * @return true if restored - otherwise there's a message
   * @throws CalFacadeException on error
   */
  public boolean restorePublic(final boolean merge,
                               final boolean dryRun,
                               final InfoLines info) throws CalFacadeException {
    globals.info = info;
    globals.setMerging(merge);
    globals.setDryRun(dryRun);

    try (RestorePublic restorer = new RestorePublic(globals)) {
      restorer.open();

      restorer.doRestore();
    }
    return true;
  }
  
  /** 'Classic' restore - single XML file.
   * @param info - to track status
   * @throws Throwable on fatal error
   */
  public void doRestore(final InfoLines info) throws Throwable {
    if (newSystem) {
      createNewSystem();

      return;
    }

    globals.info = info;
    
    if (newRestoreFormat) {
      // Do something
      return;
    }
    
    // Old style single file.
    globals.digester = new Digester();

    final RegexMatcher m = new SimpleRegexMatcher();
    globals.digester.setRules(new RegexRules(m));

    globals.digester.addRuleSet(new RestoreRuleSet(globals));
    globals.digester.parse(new InputStreamReader(new FileInputStream(filename),
                                         "UTF-8"));
  }

  /**
   * @return list of external subscriptions
   */
  public List<AliasInfo> getExternalSubs() {
    return globals.externalSubs;
  }

  /**
   * @return table of aliases by path
   */
  public Map<String, AliasEntry> getAliasInfo() {
    return globals.aliasInfo;
  }

  private void createNewSystem() throws Throwable {
    // Create the public user.

    final BwPrincipal pu = BwPrincipal.makeUserPrincipal();

    pu.setAccount(BwPrincipal.publicUser);
    globals.setPrincipalHref(pu);

    globals.rintf.restorePrincipal(pu);

    // Create the root user.

    final BwPrincipal rootUser = BwPrincipal.makeUserPrincipal();

    rootUser.setAccount(rootId);
    globals.setPrincipalHref(rootUser);

    globals.rintf.restorePrincipal(rootUser);

    // Create the an authuser entry for the root user.

    final BwAuthUser au = new BwAuthUser();
    au.setUserHref(rootUser.getPrincipalRef());
    au.setUsertype(UserAuth.allAuth);
    au.setPrefs(BwAuthUserPrefs.makeAuthUserPrefs());

    globals.rintf.restoreAuthUser(au);

    // Create a group for all public admin groups

    final BwAdminGroup g = new BwAdminGroup();

    final String publicAdminGroupsAccount = 
            "publicAdminGroups";   // XXX Put into config
    g.setAccount(publicAdminGroupsAccount);
    g.setGroupOwnerHref(pu.getPrincipalRef());
    g.setOwnerHref(pu.getPrincipalRef());

    if (!globals.onlyUsersMap.check(g.getGroupOwnerHref())) {
      g.setGroupOwnerHref(globals.getPublicUser().getPrincipalRef());
    }

    globals.rintf.restoreAdminGroup(g);

    // Create the public root.

    final Collection<Privilege> privs = new ArrayList<>();
    privs.add(Privileges.makePriv(PrivilegeDefs.privRead));

    final Collection<Ace> aces = new ArrayList<>();

    aces.add(Ace.makeAce(AceWho.other, privs, null));

    privs.clear();
    privs.add(Privileges.makePriv(PrivilegeDefs.privRead));
    privs.add(Privileges.makePriv(PrivilegeDefs.privWriteContent));

    final AceWho who = AceWho.getAceWho(publicAdminGroupsAccount,
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

    final BwCalendar userRoot = 
            makeCal(null, pu,
                    BwCalendar.calTypeFolder,
                    RestoreGlobals.getBasicSyspars().getUserCalendarRoot(),
                    new String(new Acl(aces).encode()));

    makeUserHome(userRoot, pu);
    makeUserHome(userRoot, rootUser);
  }

  private void makeUserHome(final BwCalendar userRoot,
                            final BwPrincipal user) throws Throwable {
    // Create root user home and default calendar

    final BwCalendar userHome = makeCal(userRoot, user,
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
    final BwCalendar cal = new BwCalendar();

    cal.setCalType(type);

    String ppath = null;
    if (parent != null) {
      ppath = parent.getPath();
    }

    cal.setColPath(ppath);
    cal.setName(name);
    cal.setPath(Util.buildPath(colPathEndsWithSlash, 
                               ppath, "/", cal.getName()));
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
   * @throws Throwable on fatal error
   */
  public void getConfigProperties() throws Throwable {
    globals.init();
  }

  private CalSvcI getSvci() throws CalFacadeException {
    final CalSvcIPars pars = CalSvcIPars.getRestorePars(adminUserAccount);
    return new CalSvcFactoryDefault().getSvc(pars);
  }

  /**
   * @param val less than 100
   * @return 2 digit val
   */
  public static String twoDigits(final long val) {
    if (val < 10) {
      return "0" + val;
    }

    return String.valueOf(val);
  }
}
