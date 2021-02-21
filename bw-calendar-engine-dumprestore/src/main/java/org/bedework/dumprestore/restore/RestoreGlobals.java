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

import org.bedework.access.AccessException;
import org.bedework.access.AccessPrincipal;
import org.bedework.access.WhoDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwUser;
import org.bedework.calfacade.DirectoryInfo;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.RestoreIntf;
import org.bedework.dumprestore.AliasEntry;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Counters;
import org.bedework.dumprestore.InfoLines;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.Timezones;
import org.bedework.util.timezones.TimezonesException;

import org.apache.commons.digester.Digester;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Globals for the restore phase
 *
 * @author Mike Douglass   douglm   rpi.edu
 * @version 1.0
 */
public class RestoreGlobals extends Counters {
  /* ********************************************************************
   * Properties of the dump, version date etc.
   * ******************************************************************** */

  private static String principalRoot;
  private static String userPrincipalRoot;
  private static String groupPrincipalRoot;

  private static int groupPrincipalRootLen;

  public static String getPrincipalRoot() {
    return principalRoot;
  }

  public static int getGroupPrincipalRootLen() {
    return groupPrincipalRootLen;
  }

  public static void setRoots(final CalSvcI svc) throws CalFacadeException {
    if (principalRoot != null) {
      return;
    }

    final DirectoryInfo di =  svc.getDirectories().getDirectoryInfo();
    principalRoot = di.getPrincipalRoot();
    userPrincipalRoot = di.getUserPrincipalRoot();
    groupPrincipalRoot = di.getGroupPrincipalRoot();

    //principalRootLen = principalRoot.length();
    //userPrincipalRootLen = userPrincipalRoot.length();
    groupPrincipalRootLen = groupPrincipalRoot.length();
  }

  /** Where we do new style dump */
  private String dirPath;
  
  /* true if we are merging data into existing system */
  private boolean merging;

  /* true if we don't actually create anything */
  private boolean dryRun;
  
  /* Where collections are in the dumped data - so we knwo if we are restoring home */
  public String colsHome;

  /** */
  public PrincipalInfo principalInfo;

  /** undefined version numbers */
  public final static int bedeworkVersionDefault = -1;

  /** When this changes - everything is different */
  public int bedeworkMajorVersion = bedeworkVersionDefault;

  /** When this changes - schema and api usually have significant changes */
  public int bedeworkMinorVersion = bedeworkVersionDefault;

  /** Minor functional updates */
  public int bedeworkUpdateVersion = bedeworkVersionDefault;

  /** Patches which might introduce schema incompatibility if needed.
   * Essentially a bug fix
   */
  public String bedeworkPatchLevel = null;

  /** Result of concatenating the above */
  public String bedeworkVersion;

  /** If non-null messages should be added to this.
   *
   */
  public InfoLines info;

  /** Date from dump data */
  public String dumpDate;

  /** */
  public String adminUserAccount = "admin";

  /* ********************************************************************
   * Timezones
   * ******************************************************************** */

  /** Accumulate unmatched ids */
  public Set<String> unmatchedTzids = new TreeSet<String>();

  /** */
  public long convertedTzids;

  /** */
  public long discardedTzs;

  /**
   * The digester.
   */
  public Digester digester;

  /* ********************************************************************
   * Restore flags and variables.
   * ******************************************************************** */

  /** If we are converting we either convert all or the default calendar only.
   */
  public boolean convertScheduleDefault;

  /** This is not the way to use the digester. We could possibly build the xml
   * rules directly from the hibernate schema or from java annotations.
   *
   * For the moment I just need to get this going.
   */
  public boolean inOwnerKey;

  /** Set false at start of entity, set true on entity error
   */
  public boolean entityError;

  /** Map user with id of zero on to this id - fixes oversight */
  public static int mapUser0 = 1;
  private SystemProperties syspars;

  /** Messages for subscriptions changed   // PRE3.5
   */
  public ArrayList<String> subscriptionFixes = new ArrayList<String>();

  /** User entry for owner of public entities. This is used to fix up entries.
   */
  private BwPrincipal publicUser;

  /** Incremented if we can't map something */
  public int calMapErrors = 0;

  /** If true stop restore on any error, otherwise just flag it.
   */
  public boolean failOnError = false;

  /** Incremented for each start datetime but end date. We drop the end.
   */
  public int fixedNoEndTime;

  /** counter */
  public int errors;

  /** counter */
  public int warnings;

  /** So we can output messages on the jmx console */
  public static class RestoreMessage {
    /** log level */
    public String level;

    /** The message */
    public String msg;

    RestoreMessage(final String level, final String msg) {
      this.level = level;
      this.msg = msg;
    }

    @Override
    public String toString() {
      return level + ": " + msg;
    }
  }

  /** Messages we output */
  public class RestoreMessages extends ArrayList<RestoreMessage> {
    /** Add a warning
     * @param msg
     */
    public void warningMessage(final String msg) {
      if (info != null) {
        info.addLn(" WARN:" + msg);
      }
      add(new RestoreMessage(" WARN", msg));
    }

    /** Add an error
     * @param msg
     */
    public void errorMessage(final String msg) {
      if (info != null) {
        info.addLn(" ERROR:" + msg);
      }
      add(new RestoreMessage("ERROR", msg));
    }
  }

  /**
   */
  public RestoreMessages messages = new RestoreMessages();

  /** Only Users mapping */
  public OnlyUsersMap onlyUsersMap = new OnlyUsersMap();

  /**
   */
  public static class EventKeyMap extends HashMap<Integer, ArrayList<Integer>> {
    /**
     * @param keyid
     * @param eventid
     */
    public void put(final int keyid, final int eventid) {
      ArrayList<Integer> al = get(keyid);
      if (al == null) {
        al = new ArrayList<Integer>();
        put(keyid, al);
      }

      al.add(eventid);
    }
  }

  /** Save ids of alias events and their targets
   */
  public static class AliasMap extends HashMap<Integer, Integer> {
    /**
     * @param val
     */
    public void put(final BwEventAnnotation val) {
      put(val.getId(), val.getTarget().getId());
    }
  }

  /**
   */
  public static class CalendarMap extends HashMap<String, BwCalendar> {
    /**
     * @param val
     */
    public void put(final BwCalendar val) {
      put(val.getPath(), val);
    }
  }

  /** Collections marked as external subscriptions. We may need to resubscribe
   */
  public List<AliasInfo> externalSubs = new ArrayList<>();

  /** Collections marked as aliases. We may need to fix sharing
   */
  public Map<String, AliasEntry> aliasInfo = new HashMap<>();

  /** */
  public PrincipalMap principalsTbl = new PrincipalMap();

  /** Members to add to admin groups */
  public HashMap<String, ArrayList<PrincipalHref>> adminGroupMembers =
    new HashMap<String, ArrayList<PrincipalHref>>();

  /** */
  public CalendarMap calendarsTbl = new CalendarMap();

  /** */
  public CalSvcI svci;

  /** */
  public RestoreIntf rintf;

  public RestoreGlobals() {
  }

  public void setDirPath(final String val) {
    dirPath = val;
  }

  public String getDirPath() {
    return dirPath;
  }

  public void setMerging(final boolean val) {
    merging = val;
  }
  
  public boolean getMerging() {
    return merging;
  }

  public void setDryRun(final boolean val) {
    dryRun = val;
  }

  public boolean getDryRun() {
    return dryRun;
  }

  private String defaultTzid;

  /** This must be called after syspars has been initialised.
   *
   */
  public void setTimezones() {
    if (defaultTzid != null) {
      // Already set
      return;
    }

    if (syspars.getTzServeruri() == null) {
      throw new RuntimeException("No timezones server URI defined in syspars");
    }
    if (syspars.getTzid() == null) {
      throw new RuntimeException("No default TZid defined in syspars");
    }

    try {
      Timezones.initTimezones(syspars.getTzServeruri());
    } catch (final TimezonesException tze) {
      throw new RuntimeException(tze);
    }

    Timezones.setSystemDefaultTzid(syspars.getTzid());

    defaultTzid = syspars.getTzid();
  }

  /**
   * @param val
   */
  public void setPublicUser(final BwPrincipal val) {
    publicUser = val;
  }

  /** Get the account which owns public entities
   *
   * @return BwPrincipal account
   * @throws Throwable if account name not defined
   */
  public BwPrincipal getPublicUser() throws Throwable {
    return publicUser;
  }

  /** Who we are pretending to be for the core classes
   */
  public BwPrincipal currentUser;

  /**
   * @return system params
   */
  public SystemProperties getSyspars() {
    return syspars;
  }

  private Collection<String> rootUsers;

  /**
   * @return collection string
   * @throws Exception
   */
  public Collection<String> getRootUsers() throws Exception {
    if (rootUsers != null) {
      return rootUsers;
    }

    rootUsers = new ArrayList<String>();

    String rus = getSyspars().getRootUsers();

    if (rus == null) {
      return rootUsers;
    }

    try {
      int pos = 0;

      while (pos < rus.length()) {
        int nextPos = rus.indexOf(",", pos);
        if (nextPos < 0) {
          rootUsers.add(rus.substring(pos));
          break;
        }

        rootUsers.add(rus.substring(pos, nextPos));
        pos = nextPos + 1;
      }
    } catch (Throwable t) {
      throw new Exception(t);
    }

    return rootUsers;
  }

  /**
   *
   */
  @Override
  public void stats(final List<String> infoLines) {
    if (!subscriptionFixes.isEmpty()) {
      for (String m: subscriptionFixes) {
        info(infoLines, m);
      }

      info(infoLines, " ");
    }

    if (messages.size() > 0) {
      info(infoLines, "Errors and warnings. See log for details. ");
      info(infoLines, " ");

      for (RestoreMessage rm: messages) {
        info(infoLines, rm.toString());
      }

      info(infoLines, " ");
    }

    if (!unmatchedTzids.isEmpty()) {
      info(infoLines, "    Unmatched timezone ids: " + unmatchedTzids.size());
      for (String tzid: unmatchedTzids) {
        StringBuilder sb = new StringBuilder();

        sb.append(tzid);

        info(infoLines, sb.toString());
      }
    } else {
      info(infoLines, "    No unmatched timezone ids");
    }

    info(infoLines, " ");
    info(infoLines, "    Converted tzids: " + convertedTzids);
    info(infoLines, "    Discarded tzs: " + discardedTzs);

    super.stats(infoLines);

    info(infoLines, " ");
    info(infoLines, "    Fixed end times: " + fixedNoEndTime);
    info(infoLines, " ");
    info(infoLines, "           warnings: " + warnings);
    info(infoLines, "             errors: " + errors);
    info(infoLines, " ");
  }

  /**
   * @throws Throwable
   */
  public void init() throws Throwable {
    Configurations conf = CalSvcFactoryDefault.getSystemConfig();
    syspars = conf.getSystemProperties();
  }

  private Map<String, BwPrincipal> principalMap = new HashMap<String, BwPrincipal>();

  private long lastFlush = System.currentTimeMillis();
  private static final long flushInt = 1000 * 30 * 5; // 5 minutes

  private BwPrincipal mappedPrincipal(final String val) {
    long now = System.currentTimeMillis();

    if ((now - lastFlush) > flushInt) {
      principalMap.clear();
      lastFlush = now;
      return null;
    }

    return principalMap.get(val);
  }

  /**
   * @return principal root
   */
  public static String getUserPrincipalRoot() {
    return BwPrincipal.userPrincipalRoot;
  }

  /**
   * @return principal root
   */
  public static String getGroupPrincipalRoot()  {
    return BwPrincipal.groupPrincipalRoot;
  }

  /**
   * @return principal root
   */
  public static String getBwadmingroupPrincipalRoot() {
    return BwPrincipal.bwadmingroupPrincipalRoot;
  }

  /**
   * @param p
   * @return prefix
   */
  public static String getPrincipalHrefPrefix(final BwPrincipal p) {
    try {
      if (p instanceof BwUser) {
        return getUserPrincipalRoot();
      }

      if (p instanceof BwAdminGroup) {
        return getBwadmingroupPrincipalRoot();
      }

      if (p instanceof BwGroup) {
        return getGroupPrincipalRoot();
      }
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

    return null;
  }

  private static int badUserCount = 0;

  /**
   * @param p a principal
   */
  public void setPrincipalHref(final BwPrincipal p) {
    String account = p.getAccount();

    if (account == null) {
      account = "BadUserAccount_" + badUserCount;
      p.setAccount(account);
      badUserCount++;
      errors++;
      String ln = "Approximately at line number " +
          digester.getDocumentLocator().getLineNumber();

      messages.errorMessage(ln + ": bad user");
      error(ln + ": bad user");
    }

    if (account.startsWith("/")) {
      // Assume a principal

      p.setPrincipalRef(account);
      return;
    }

    if (p instanceof BwUser) {
      p.setPrincipalRef(Util.buildPath(false, getUserPrincipalRoot(), "/", p.getAccount()));
      return;
    }

    if (p instanceof BwAdminGroup) {
      p.setPrincipalRef(Util.buildPath(false, getBwadmingroupPrincipalRoot(), "/", p.getAccount()));
      return;
    }

    if (p instanceof BwGroup) {
      p.setPrincipalRef(Util.buildPath(false, getGroupPrincipalRoot(), "/", p.getAccount()));
      return;
    }
  }

  /**
   * @param id
   * @param whoType
   * @return Sring principal
   * @throws Throwable
   */
  public String getPrincipalHref(final String id, final int whoType) throws Throwable {
    if (id.startsWith("/")) {
      // Assume a principal

      return id;
    }

    if (whoType == WhoDefs.whoTypeUser) {
      return Util.buildPath(false, getUserPrincipalRoot(), "/", id);
    }

    if (whoType == WhoDefs.whoTypeGroup) {
      return Util.buildPath(false, getBwadmingroupPrincipalRoot(), "/", id);
    }

    return id;
  }

  /**
   * @param val
   * @return BwPrincipal
   * @throws Throwable
   */
  public BwPrincipal getPrincipal(final String val) throws Throwable {
    BwPrincipal p = mappedPrincipal(val);

    if (p != null) {
      return p;
    }

    if (!val.startsWith(BwPrincipal.principalRoot)) {
      return null;
    }

    if (val.startsWith(BwPrincipal.userPrincipalRoot)) {
      BwPrincipal pr = rintf.getPrincipal(val);

      if (pr != null) {
        principalMap.put(val, pr);
      }

      return pr;
    }

    if (val.startsWith(getGroupPrincipalRoot())) {
      throw new Exception("unimplemented");
    }

    return null;
  }

  private class RestorePrincipalInfo extends PrincipalInfo {
    RestorePrincipalInfo(final BwPrincipal principal,
                         final BwPrincipal authPrincipal) {
      super(principal, authPrincipal, null, false);
    }

    @Override
    public AccessPrincipal getPrincipal(final String href) throws CalFacadeException {
      try {
        return RestoreGlobals.this.getPrincipal(href);
      } catch (final Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    void setSuperUser(final boolean val) {
      superUser = val;
    }

    void setPrincipal(final BwPrincipal principal) {
      this.principal = principal;
      authPrincipal = principal;
      calendarHomePath = null;
    }

    @Override
    public String makeHref(final String id, final int whoType) throws AccessException {
      try {
        return getPrincipalHref(id, whoType);
      } catch (final Throwable t) {
        throw new AccessException(t);
      }
    }
  }

  /**
   * @param pr
   * @return initialized principal info
   */
  public PrincipalInfo getPrincipalInfo(final BwPrincipal pr) {
    if (principalInfo != null) {
      if (principalInfo.getAuthPrincipal().equals(pr)) {
        return principalInfo;
      }

      ((RestorePrincipalInfo)principalInfo).setPrincipal(pr);
      ((RestorePrincipalInfo)principalInfo).setSuperUser(true);
      return principalInfo;
    }

    principalInfo = new RestorePrincipalInfo(pr, pr);
    ((RestorePrincipalInfo)principalInfo).setSuperUser(true);

    return principalInfo;
  }
}
