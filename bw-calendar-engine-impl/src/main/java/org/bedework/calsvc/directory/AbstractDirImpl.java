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
package org.bedework.calsvc.directory;

import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.BwPrincipalInfo.BooleanPrincipalProperty;
import org.bedework.calfacade.BwPrincipalInfo.IntPrincipalProperty;
import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.DirectoryInfo;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.CalAddrPrefixes;
import org.bedework.calfacade.configs.CardDavInfo;
import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calsvci.CalSvcFactoryDefault;

import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.cmt.access.WhoDefs;
import edu.rpi.sss.util.DavUtil;
import edu.rpi.sss.util.DavUtil.DavChild;
import edu.rpi.sss.util.FlushMap;
import edu.rpi.sss.util.Util;
import edu.rpi.sss.util.http.BasicHttpClient;
import edu.rpi.sss.util.xml.tagdefs.CarddavTags;

import org.apache.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.xml.namespace.QName;

/** A base implementation of Directories which handles some generic directory
 * methods.
 *
 * <p>One of those is to map an apparently flat identifier space onto a
 * principal hierarchy more appropriate to the needs of webdav. For example we
 * might have a user account "jim" or a ticket "TKT12345". These could be mapped
 * on to "/principals/users/jim" and "/principals/tickets/12345".
 *
 * @author Mike Douglass douglm@rpi.edu
 * @version 1.0
 */
public abstract class AbstractDirImpl implements Directories {
  protected boolean debug;

  private static BasicSystemProperties sysRoots;
  private CalAddrPrefixes caPrefixes;
  private CardDavInfo authCdinfo;
  private CardDavInfo unauthCdinfo;
  private DirConfigProperties dirProps;

  /**
   * @author douglm
   */
  public static class CAPrefixInfo {
    private String prefix;
    private int type;

    CAPrefixInfo(final String prefix, final int type) {
      this.prefix = prefix;
      this.type = type;
    }

    /**
     * @return prefix - never null
     */
    public String getPrefix() {
      return prefix;
    }

    /**
     * @return type defined in Acl
     */
    public int getType() {
      return type;
    }
  }

  private static Collection<CAPrefixInfo> caPrefixInfo;


  /** */
  private static class DomainMatcher implements Serializable {
    /* Only simple wildcard matching *, * + chars or chars */
    String pattern;

    boolean exact;

    DomainMatcher(final String pattern) {
      this.pattern = pattern;

      if (!pattern.startsWith("*")) {
        this.pattern = pattern;
        exact = true;
      } else {
        this.pattern = pattern.substring(1);
      }
    }

    boolean matches(final String val, final int atPos) {
      if (atPos < 0) {
        return false;
      }

      int start = atPos + 1;
      int domainLen = val.length() - start;

      if (exact) {
        if (domainLen != pattern.length()) {
          return false;
        }
      } else if (domainLen < pattern.length()) {
        return false;
      }

      return val.endsWith(pattern);
    }
  }

  private DomainMatcher onlyDomain;
  private boolean anyDomain;
  private String defaultDomain;
  private Collection<DomainMatcher> domains;

  protected CallBack cb;

  private transient Logger log;

  private HashMap<String, Integer> toWho = new HashMap<String, Integer>();
  private HashMap<Integer, String> fromWho = new HashMap<Integer, String>();

  private static FlushMap<String, String> validPrincipals =
    new FlushMap<String, String>(60 * 1000 * 5, // 5 minute
                                 0); // No size limit

  private static FlushMap<String, BwPrincipalInfo> principalInfoMap =
    new FlushMap<String, BwPrincipalInfo>(60 * 1000 * 5, // 5 minute
                                          0); // No size limit

  @Override
  public void init(final CallBack cb,
                   final CalAddrPrefixes caPrefixes,
                   final CardDavInfo authCdinfo,
                   final CardDavInfo unauthCdinfo,
                   final DirConfigProperties dirProps) throws CalFacadeException {
    this.cb = cb;
    this.caPrefixes = caPrefixes;
    this.authCdinfo = authCdinfo;
    this.unauthCdinfo = unauthCdinfo;
    this.dirProps = dirProps;

    debug = getLogger().isDebugEnabled();

    setDomains();

    initWhoMaps(getSystemRoots().getUserPrincipalRoot(), WhoDefs.whoTypeUser);
    initWhoMaps(getSystemRoots().getGroupPrincipalRoot(), WhoDefs.whoTypeGroup);
    initWhoMaps(getSystemRoots().getTicketPrincipalRoot(), WhoDefs.whoTypeTicket);
    initWhoMaps(getSystemRoots().getResourcePrincipalRoot(), WhoDefs.whoTypeResource);
    initWhoMaps(getSystemRoots().getVenuePrincipalRoot(), WhoDefs.whoTypeVenue);
    initWhoMaps(getSystemRoots().getHostPrincipalRoot(), WhoDefs.whoTypeHost);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#getDirectoryInfo()
   */
  @Override
  public DirectoryInfo getDirectoryInfo() throws CalFacadeException {
    DirectoryInfo info = new DirectoryInfo();
    BasicSystemProperties sr = getSystemRoots();

    info.setPrincipalRoot(sr.getPrincipalRoot());
    info.setUserPrincipalRoot(sr.getUserPrincipalRoot());
    info.setGroupPrincipalRoot(sr.getGroupPrincipalRoot());
    info.setBwadmingroupPrincipalRoot(sr.getBwadmingroupPrincipalRoot());
    info.setTicketPrincipalRoot(sr.getTicketPrincipalRoot());
    info.setResourcePrincipalRoot(sr.getResourcePrincipalRoot());
    info.setVenuePrincipalRoot(sr.getVenuePrincipalRoot());
    info.setHostPrincipalRoot(sr.getHostPrincipalRoot());

    return info;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#validPrincipal(java.lang.String)
   */
  @Override
  public synchronized boolean validPrincipal(final String href) throws CalFacadeException {
    // XXX Not sure how we might use this for admin users.
    if (href == null) {
      return false;
    }

    /* Use a map to avoid the lookup if possible.
     * This does mean that we retain traces of a user who gets deleted until
     * we flush.
     */

    if (lookupValidPrincipal(href)) {
      return true;
    }

    boolean valid = !href.startsWith("invalid");  // allow some testing

    try {
      // Is it parseable?
      new URI(href);
    } catch (Throwable t) {
      valid = false;
    }

    if (valid) {
      addValidPrincipal(href);
    }

    return valid;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#getDirInfo(org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public BwPrincipalInfo getDirInfo(final BwPrincipal p) throws CalFacadeException {
    BwPrincipalInfo pi = principalInfoMap.get(p.getPrincipalRef());

    if (pi != null) {
      return pi;
    }

    // If carddav lookup is enabled - use that

    CardDavInfo cdi = getCardDavInfo(false);

    if ((cdi == null) || (cdi.getHost() == null)) {
      return null;
    }

    BasicHttpClient cdc = null;

    pi = new BwPrincipalInfo();

    try {
      cdc = new BasicHttpClient(cdi.getHost(), cdi.getport(), null,
                              15 * 1000);

      pi.setPropertiesFromVCard(getCard(cdc, cdi.getContextPath(), p));
    } catch (Throwable t) {
      if (getLogger().isDebugEnabled()) {
        error(t);
      }
    } finally {
      cdc.close();
    }

    principalInfoMap.put(p.getPrincipalRef(), pi);

    return pi;
  }

  @Override
  public boolean mergePreferences(final BwPreferences prefs,
                                  final BwPrincipalInfo pinfo) throws CalFacadeException {
    boolean changed = false;
    //PrincipalProperty kind = pinfo.findProperty("kind");

    /* ============ auto scheduling ================== */
    BooleanPrincipalProperty pautoSched =
      (BooleanPrincipalProperty)pinfo.findProperty("auto-schedule");

    if ((pautoSched != null) &&
        (pautoSched.getVal() != prefs.getScheduleAutoRespond())) {
      prefs.setScheduleAutoRespond(pautoSched.getVal());

      if (pautoSched.getVal()) {
        // Ensure we delete cancelled
        prefs.setScheduleAutoCancelAction(BwPreferences.scheduleAutoCancelDelete);
      }

      changed = true;
    }

    IntPrincipalProperty pschedMaxInstances =
      (IntPrincipalProperty)pinfo.findProperty("max-instances");

    if (pschedMaxInstances != null) {
      int mi = pschedMaxInstances.getVal();
      String strMi = String.valueOf(mi);

      BwProperty pmi = prefs.findProperty(BwPreferences.propertyScheduleMaxinstances);
      if (pmi == null) {
        prefs.addProperty(new BwProperty(BwPreferences.propertyScheduleMaxinstances,
                                         strMi));
      } else if (!pmi.getValue().equals(strMi)) {
        pmi.setValue(strMi);
      }

      changed = true;
    }

    return changed;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#isPrincipal(java.lang.String)
   */
  @Override
  public boolean isPrincipal(final String val) throws CalFacadeException {
    if (val == null) {
      return false;
    }

    /* assuming principal root is "principals" we expect something like
     * "/principals/users/jim".
     *
     * Anything with fewer or greater elements is a collection or entity.
     */

    int pos1 = val.indexOf("/", 1);

    if (pos1 < 0) {
      return false;
    }

    if (!val.substring(0, pos1 + 1).equals(getSystemRoots().getPrincipalRoot())) {
      return false;
    }

    int pos2 = val.indexOf("/", pos1 + 1);

    if (pos2 < 0) {
      return false;
    }

    for (String root: toWho.keySet()) {
      String pfx = root;
      if (val.startsWith(pfx)) {
        if (val.equals(pfx)) {
          // It IS a root
          return false;
        }
        return true;
      }
    }

    /*
    int pos3 = val.indexOf("/", pos2 + 1);

    if ((pos3 > 0) && (val.length() > pos3 + 1)) {
      // More than 3 elements
      return false;
    }

    if (!toWho.containsKey(val.substring(0, pos2))) {
      return false;
    }
    */

    /* It's one of our principal hierarchies */

    return false;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#getPrincipal(java.lang.String)
   */
  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    try {
      String uri = new URI(href).getPath();

      if (!isPrincipal(uri)) {
        return null;
      }

      int start = -1;

      for (String prefix: toWho.keySet()) {
        if (!uri.startsWith(prefix)) {
          continue;
        }

        if (uri.equals(prefix)) {
          // Trying to browse user principals?
          return null;
        }

        int whoType = toWho.get(prefix);
        String who = null;
        start = prefix.length();

        if ((whoType == WhoDefs.whoTypeUser) ||
            (whoType == WhoDefs.whoTypeGroup)) {
          /* Strip off the principal prefix for real users.
           */
          who = uri.substring(start, uri.length() - 1); // Remove trailing "/"
        } else {
          who = uri;
        }

        if (who == null) {
          return null;
        }

        BwPrincipal p = BwPrincipal.makePrincipal(whoType);

        p.setAccount(who);
        p.setPrincipalRef(uri);

        return p;
      }

      throw new CalFacadeException(CalFacadeException.principalNotFound);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#makePrincipalUri(java.lang.String, boolean)
   */
  @Override
  public String makePrincipalUri(final String id,
                                 final int whoType) throws CalFacadeException {
    if (isPrincipal(id)) {
      return id;
    }

    String root = fromWho.get(whoType);

    if (root == null) {
      throw new CalFacadeException(CalFacadeException.unknownPrincipalType);
    }

    return Util.buildPath(true, root, "/", id);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#getPrincipalRoot()
   */
  @Override
  public String getPrincipalRoot() throws CalFacadeException {
    return getSystemRoots().getPrincipalRoot();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#getGroups(java.lang.String, java.lang.String)
   */
  @Override
  public Collection<String> getGroups(final String rootUrl,
                                      final String principalUrl) throws CalFacadeException {
    Collection<String> urls = new TreeSet<String>();

    if (principalUrl == null) {
      /* for the moment if the root url is the user principal hierarchy root
       * just return the current user principal
       */

      /* ResourceUri should be the principals root or user principal root */
      if (!rootUrl.equals(getSystemRoots().getPrincipalRoot()) &&
          !rootUrl.equals(getSystemRoots().getUserPrincipalRoot())) {
        return urls;
      }

      urls.add(Util.buildPath(true, getSystemRoots().getUserPrincipalRoot(), "/",
                              cb.getCurrentUser().getAccount()));
    } else {
      // XXX incomplete
    }

    return urls;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#uriToCaladdr(java.lang.String)
   */
  @Override
  public String uriToCaladdr(final String val) throws CalFacadeException {
    /* Override this to do directory lookups or query vcard. The following
     * transforms may be insufficient
     */

    if (isPrincipal(val)) {
      // Leave as is
      return userToCaladdr(val);
    }

    boolean isAccount = true;

    /* check for something that looks like mailto:somebody@somewhere.com,
       scheduleto:, etc.  If exists, is not an internal Bedework account. */
    int colonPos = val.indexOf(":");
    int atPos = val.indexOf("@");
    String uri = val;

    if (colonPos > 0) {
      if (atPos < colonPos) {
        return null;
      }

      isAccount = false;
    } else if (atPos > 0) {
      uri = "mailto:" + val;
    }

    AccessPrincipal possibleAccount = caladdrToPrincipal(uri);
    if ((possibleAccount != null) &&       // Possible bedework user
        !validPrincipal(possibleAccount.getPrincipalRef())) {   // but not valid
      return null;
    }

    if (isAccount) {
      uri = userToCaladdr(uri);
    }

    return uri;
  }

  private static final int maxCaMapSize = 3000;

  private static final long maxCaRefreshTime = 1000 * 60 * 5; // 5 minutes

  protected static Map<String, String> userToCalAddrMap =
      new FlushMap<String, String>(maxCaRefreshTime, maxCaMapSize);

  protected static Map<String, BwPrincipal> calAddrToPrincipalMap =
      new FlushMap<String, BwPrincipal>(maxCaRefreshTime, maxCaMapSize);

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#principalToCaladdr(org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public String principalToCaladdr(final BwPrincipal val) throws CalFacadeException {
    return userToCaladdr(val.getAccount());
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#userToCaladdr(java.lang.String)
   */
  @Override
  public String userToCaladdr(final String val) throws CalFacadeException {
    /* Override this to do directory lookups or query vcard. The following
     * transforms may be insufficient
     */

    String ca = userToCalAddrMap.get(val);

    if (ca != null) {
      return ca;
    }

    if (isPrincipal(val)) {
      BwPrincipal p = getPrincipal(val);

      if (p.getKind() == WhoDefs.whoTypeUser) {
        ca = userToCaladdr(p.getAccount());
      } else {
        // Can't do anything with groups etc.
        ca = val;
      }

      userToCalAddrMap.put(val, ca);

      return ca;
    }

    getProps(); // Ensure all set up

    try {
      int atPos = val.indexOf("@");

      boolean hasMailto = val.toLowerCase().startsWith("mailto:");

      if (atPos > 0) {
        if (hasMailto) {
          // ensure lower case (helps with some tests)
          return "mailto:" + val.substring(7);
        }

        ca = "mailto:" + val;
        userToCalAddrMap.put(val, ca);

        return ca;
      }

      StringBuilder sb = new StringBuilder();
      if (!hasMailto) {
        sb.append("mailto:");
      }

      sb.append(val);
      sb.append("@");
      sb.append(getDefaultDomain());

      ca = sb.toString();
      userToCalAddrMap.put(val, ca);

      return ca;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#caladdrToPrincipal(java.lang.String)
   */
  @Override
  public BwPrincipal caladdrToPrincipal(final String caladdr) throws CalFacadeException {
    try {
      if (caladdr == null) {
        throw new CalFacadeException(CalFacadeException.nullCalendarUserAddr);
      }

      BwPrincipal p = calAddrToPrincipalMap.get(caladdr);
      if (p != null) {
        return p;
      }

      getProps(); // Ensure all set up

      if (isPrincipal(caladdr)) {
        p = getPrincipal(caladdr);
        calAddrToPrincipalMap.put(caladdr, p);

        return p;
      }

      String acc = null;
      String ca = caladdr;
      int atPos = ca.indexOf("@");

      if (atPos > 0) {
        ca = ca.toLowerCase();
      }

      if (onlyDomain != null) {
        if (atPos < 0) {
          acc = ca;
        }

        if (onlyDomain.matches(ca, atPos)) {
          acc = ca.substring(0, atPos);
        }
      } else if (atPos < 0) {
        // Assume default domain?
        acc = ca;
      } else if (anyDomain) {
        acc = ca;
      } else {
        for (DomainMatcher dm: domains) {
          if (dm.matches(ca, atPos)) {
            acc = ca;
            break;
          }
        }
      }

      if (acc == null) {
        // Not ours
        return null;
      }

      if (acc.toLowerCase().startsWith("mailto:")) {
        acc = acc.substring("mailto:".length());
      }

      //XXX -at this point we should validate the account

      int whoType = WhoDefs.whoTypeUser;

      for (CAPrefixInfo c: getCaPrefixInfo()) {
        if (acc.startsWith(c.getPrefix())) {
          whoType = c.getType();
          break;
        }
      }

      p = getPrincipal(makePrincipalUri(acc, whoType));
      calAddrToPrincipalMap.put(caladdr, p);

      return p;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#fixCalAddr(java.lang.String)
   */
  @Override
  public String normalizeCua(final String val) throws CalFacadeException {
    if (val == null) {
      throw new CalFacadeException(CalFacadeException.badCalendarUserAddr);
    }

    if (val.startsWith("/")) {
      return val;
    }

    int colonPos = val.indexOf(":");
    int atPos = val.indexOf("@");

    if (colonPos > 0) {
      if (atPos < colonPos) {
        throw new CalFacadeException(CalFacadeException.badCalendarUserAddr);
      }

      /* Ensure mailto is lower case. */
      return "mailto:" + val.substring(colonPos + 1);
    } else if (atPos > 0) {
      return "mailto:" + val;
    } else {
      // No colon - no at - maybe just userid
      return "mailto:" + val + "@" + getDefaultDomain();
    }
  }

  @Override
  public String getDefaultDomain() throws CalFacadeException {
    if (defaultDomain == null) {
      throw new CalFacadeException(CalFacadeException.noDefaultDomain);
    }

    return defaultDomain;
  }

  @Override
  public String getAdminGroupsIdPrefix() {
    return null;
  }

  /* ====================================================================
   *  Protected methods.
   * ==================================================================== */

  /** See if the given principal href is in our table. Allows us to short circuit
   * the validation process.
   *
   * @param href
   * @return true if we know about this one.
   */
  protected synchronized boolean lookupValidPrincipal(final String href) {
    return validPrincipals.containsKey(href);
  }

  /** Add a principal we have validated.
   *
   * @param href
   */
  protected void addValidPrincipal(final String href) {
    validPrincipals.put(href, href);
  }

  protected DirConfigProperties getProps() throws CalFacadeException {
    return dirProps;
  }

  protected BasicSystemProperties getSystemRoots() throws CalFacadeException {
    if (sysRoots != null) {
      return sysRoots;
    }

    sysRoots = new CalSvcFactoryDefault().getSystemConfig().getBasicSystemProperties();

    return sysRoots;
  }

  protected CalAddrPrefixes getCaPrefixes() throws CalFacadeException {
    return caPrefixes;
  }

  protected Collection<CAPrefixInfo> getCaPrefixInfo() throws CalFacadeException {
    if (caPrefixInfo != null) {
      return caPrefixInfo;
    }

    CalAddrPrefixes cap = getCaPrefixes();

    List<CAPrefixInfo> capInfo = new ArrayList<CAPrefixInfo>();

    if (cap != null) {
      addCaPrefix(capInfo, cap.getUser(), WhoDefs.whoTypeUser);
      addCaPrefix(capInfo, cap.getGroup(), WhoDefs.whoTypeGroup);
      addCaPrefix(capInfo, cap.getHost(), WhoDefs.whoTypeHost);
      addCaPrefix(capInfo, cap.getTicket(), WhoDefs.whoTypeTicket);
      addCaPrefix(capInfo, cap.getResource(), WhoDefs.whoTypeResource);
      addCaPrefix(capInfo, cap.getLocation(), WhoDefs.whoTypeVenue);
    }

    caPrefixInfo = Collections.unmodifiableList(capInfo);

    return caPrefixInfo;
  }

  private void addCaPrefix(final List<CAPrefixInfo> capInfo,
                           final String prefix, final int type) {
    if (prefix == null) {
      return;
    }

    capInfo.add(new CAPrefixInfo(prefix, type));
  }

  protected CardDavInfo getCardDavInfo(final boolean auth) throws CalFacadeException {
    if (auth) {
      return authCdinfo;
    }

    return unauthCdinfo;
  }

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  /* ====================================================================
   *  Private methods.
   * ==================================================================== */

  private void setDomains() {
    String prDomains = dirProps.getDomains();

    if (prDomains.equals("*")) {
      anyDomain = true;
    } else if ((prDomains.indexOf(",") < 0) &&
               !prDomains.startsWith("*")) {
      onlyDomain = new DomainMatcher(prDomains);
    } else {
      domains = new ArrayList<DomainMatcher>();

      for (String domain: dirProps.getDomains().split(",")) {
        domains.add(new DomainMatcher(domain));
      }
    }

    defaultDomain = dirProps.getDefaultDomain();
    if ((defaultDomain == null) && (onlyDomain != null)) {
      defaultDomain = prDomains;
    }
  }

  /** Get the vcard for the given principal
   *
  * @param p - who we want card for
  * @return card or null
  * @throws DavioException
  */
 private String getCard(final BasicHttpClient cl,
                        final String context,
                        final AccessPrincipal p) throws CalFacadeException {
   /* Try a propfind on the principal */

   try {
     /*
     StringBuilder sb = new StringBuilder(
         "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
         "<D:propfind xmlns:D=\"DAV:\"\n" +
         "            xmlns:C=\"urn:ietf:params:xml:ns:carddav\">\n" +
         "  <D:prop>\n" +
         "    <C:principal-address/>\n" +
         "  </D:prop>\n" +
         "</D:propfind>\n");

     byte[] content = sb.toString().getBytes();

     int res = cl.sendRequest("PROPFIND", context + p.getPrincipalRef(),
                              null, // hdrs
                              "0",    // depth
                              "text/xml",
                              content.length, content);

     int SC_MULTI_STATUS = 207; // not defined for some reason
     if (res != SC_MULTI_STATUS) {
       return null;
     }

     /* Extract the principal address from something like
      * <?xml version="1.0" encoding="UTF-8" ?>
      * <multistatus xmlns="DAV:" xmlns:ns1="urn:ietf:params:xml:ns:carddav">
      *   <response>
      *     <href>/ucarddav/principals/users/douglm/</href>
      *     <propstat>
      *       <prop>
      *         <ns1:principal-address>
      *           <href>/ucarddav/public/people/douglm.vcf/</href>
      *         </ns1:principal-address>
      *       </prop>
      *       <status>HTTP/1.1 200 ok</status>
      *     </propstat>
      *   </response>
      * </multistatus>
      * /

     DavUtil du = new DavUtil();
     MultiStatusResponse msr = du.getMultiStatusResponse(cl.getResponseBodyAsStream());

     // Expect one response only - might have responseDescription

     if (msr.responses.size() != 1) {
       throw new CalFacadeException("Bad response. Expected exactly 1 response element");
     }

     MultiStatusResponseElement msre = msr.responses.get(0);

     /* We want one propstat element with successful status * /

     if (msre.propstats.size() != 1) {
       if (debug) {
         trace("Found " + msre.propstats.size() + " propstat elements");
       }

       return null;
     }

     PropstatElement pse = msre.propstats.get(0);
     if (pse.status != HttpServletResponse.SC_OK) {
       if (debug) {
         trace("propstat status was " + pse.status);
       }

       return null;
     }

     // We expect one principal-address property
     if (pse.props.size() != 1) {
       if (debug) {
         trace("Found " + pse.props.size() + " prop elements");
       }

       return null;
     }

     Element pr = pse.props.iterator().next();

     if (!XmlUtil.nodeMatches(pr, CarddavTags.principalAddress)) {
       if (debug) {
         trace("Expected principal-address - found " + pr);
       }

       return null;
     }

     /* Expect a single href element * /
     Element href = DavUtil.getOnlyChild(pr);
     if (!XmlUtil.nodeMatches(href, WebdavTags.href)) {
       if (debug) {
         trace("Expected href element for principal-address - found " + href);
       }

       return null;
     }
     */
     DavUtil du = new DavUtil();
     Collection<QName> props = new ArrayList<QName>();

     props.add(CarddavTags.principalAddress);

     DavChild dc = du.getProps(cl, context + p.getPrincipalRef(), props);

     if (dc == null) {
       return null;
     }

     cl.release();

     /* New request for card */
     InputStream is = cl.get(dc.uri);

     if (is == null) {
       return null;
     }

     LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
     StringBuilder card = new StringBuilder();

     for (;;) {
       String ln = lnr.readLine();
       if (ln == null) {
         break;
       }
       card.append(ln);
       card.append("\n");
     }

     return card.toString();
   } catch (Throwable t) {
     throw new CalFacadeException(t);
   }
 }

  private void initWhoMaps(final String prefix, final int whoType) {
    toWho.put(prefix, whoType);
    fromWho.put(whoType, prefix);
  }
}

