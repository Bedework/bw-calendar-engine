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

import org.bedework.access.AccessPrincipal;
import org.bedework.access.WhoDefs;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.BwPrincipalInfo.BooleanPrincipalProperty;
import org.bedework.calfacade.BwPrincipalInfo.IntPrincipalProperty;
import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.DirectoryInfo;
import org.bedework.calfacade.configs.CalAddrPrefixes;
import org.bedework.calfacade.configs.CardDavInfo;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.dav.DavUtil;
import org.bedework.util.dav.DavUtil.MultiStatusResponse;
import org.bedework.util.dav.DavUtil.MultiStatusResponseElement;
import org.bedework.util.dav.DavUtil.PropstatElement;
import org.bedework.util.http.HttpUtil;
import org.bedework.util.http.PooledHttpClient;
import org.bedework.util.http.PooledHttpClient.ResponseHolder;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.XmlEmit.NameSpace;
import org.bedework.util.xml.XmlUtil;
import org.bedework.util.xml.tagdefs.BedeworkServerTags;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.CarddavTags;
import org.bedework.util.xml.tagdefs.WebdavTags;
import org.bedework.webdav.servlet.shared.WebdavProperty;

import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.vcard.Property;
import net.fortuna.ical4j.vcard.Property.Id;
import net.fortuna.ical4j.vcard.VCard;
import net.fortuna.ical4j.vcard.property.Kind;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletResponse;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** A base implementation of Directories which handles some generic directory
 * methods.
 *
 * <p>One of those is to map an apparently flat identifier space onto a
 * principal hierarchy more appropriate to the needs of webdav. For example we
 * might have a user account "jim" or a ticket "TKT12345". These could be mapped
 * on to "/principals/users/jim" and "/principals/tickets/12345".
 *
 * @author Mike Douglass douglm  rpi.edu
 * @version 1.0
 */
public abstract class AbstractDirImpl implements Logged, Directories {
  public final static int SC_MULTI_STATUS = 207; // not defined for some reason

  private CalAddrPrefixes caPrefixes;
  private CardDavInfo authCdinfo;
  private CardDavInfo unauthCdinfo;
  private DirConfigProperties dirProps;

  /**
   * @author douglm
   */
  public static class CAPrefixInfo {
    private final String prefix;
    private final int type;

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

      final int start = atPos + 1;
      final int domainLen = val.length() - start;

      if (exact) {
        if (domainLen != pattern.length()) {
          return false;
        }
      } else if (domainLen < pattern.length()) {
        return false;
      }

      return val.endsWith(pattern);
    }

    boolean matches(final String domain) {
      final int domainLen = domain.length();

      if (exact) {
        if (domainLen != pattern.length()) {
          return false;
        }
      } else if (domainLen < pattern.length()) {
        return false;
      }

      return domain.endsWith(pattern);
    }
  }

  private DomainMatcher onlyDomain;
  private boolean anyDomain;
  private String defaultDomain;
  private Collection<DomainMatcher> domains;

  protected CallBack cb;

  private final HashMap<String, Integer> toWho = new HashMap<>();
  private final HashMap<Integer, String> fromWho = new HashMap<>();

  private static final FlushMap<String, String> validPrincipals =
          new FlushMap<>(60 * 1000 * 5, // 5 minute
                         0); // No size limit

  private static final FlushMap<String, BwPrincipalInfo> principalInfoMap =
          new FlushMap<>(60 * 1000 * 5, // 5 minute
                         0); // No size limit

  @Override
  public void init(final CallBack cb,
                   final Configurations configs) {
    this.cb = cb;
    this.caPrefixes = configs.getDirConfig(getConfigName())
                             .getCalAddrPrefixes();
    this.authCdinfo = configs.getCardDavInfo(true);
    this.unauthCdinfo = configs.getCardDavInfo(false);
    this.dirProps = configs.getDirConfig(getConfigName());

    setDomains();

    initWhoMaps(BwPrincipal.userPrincipalRoot, WhoDefs.whoTypeUser);
    initWhoMaps(BwPrincipal.groupPrincipalRoot, WhoDefs.whoTypeGroup);
    initWhoMaps(BwPrincipal.ticketPrincipalRoot, WhoDefs.whoTypeTicket);
    initWhoMaps(BwPrincipal.resourcePrincipalRoot, WhoDefs.whoTypeResource);
    initWhoMaps(BwPrincipal.venuePrincipalRoot, WhoDefs.whoTypeVenue);
    initWhoMaps(BwPrincipal.hostPrincipalRoot, WhoDefs.whoTypeHost);
  }

  @Override
  public DirectoryInfo getDirectoryInfo() {
    final DirectoryInfo info = new DirectoryInfo();

    info.setPrincipalRoot(BwPrincipal.principalRoot);
    info.setUserPrincipalRoot(BwPrincipal.userPrincipalRoot);
    info.setGroupPrincipalRoot(BwPrincipal.groupPrincipalRoot);
    info.setBwadmingroupPrincipalRoot(BwPrincipal.bwadmingroupPrincipalRoot);
    info.setTicketPrincipalRoot(BwPrincipal.ticketPrincipalRoot);
    info.setResourcePrincipalRoot(BwPrincipal.resourcePrincipalRoot);
    info.setVenuePrincipalRoot(BwPrincipal.venuePrincipalRoot);
    info.setHostPrincipalRoot(BwPrincipal.hostPrincipalRoot);

    return info;
  }

  @Override
  public synchronized boolean validPrincipal(final String href) {
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
    } catch (final Throwable t) {
      valid = false;
    }

    if (valid) {
      addValidPrincipal(href);
    }

    return valid;
  }

  @Override
  public BwPrincipalInfo getDirInfo(final BwPrincipal<?> p) {
    BwPrincipalInfo pi = principalInfoMap.get(p.getPrincipalRef());

    if (pi != null) {
      return pi;
    }

    // If carddav lookup is enabled - use that

    final CardDavInfo cdi = getCardDavInfo(false);

    if ((cdi == null) || (cdi.getUrl() == null)) {
      return null;
    }

    PooledHttpClient cdc = null;

    pi = new BwPrincipalInfo();

    try {
      cdc = new PooledHttpClient(new URI(cdi.getUrl()));

      pi.setPropertiesFromVCard(getCard(cdc, p),
                                "text/vcard");
    } catch (final Throwable t) {
      if (getLogger().isDebugEnabled()) {
        error(t);
      }
    } finally {
      if (cdc != null) {
        cdc.release();
      }
    }

    principalInfoMap.put(p.getPrincipalRef(), pi);

    return pi;
  }

  @Override
  public FindPrincipalsResult find(
          final List<WebdavProperty> props,
          final List<WebdavProperty> returnProps,
          final String cutype) {
    final CardDavInfo cdi = getCardDavInfo(false);

    if ((cdi == null) || (cdi.getUrl() == null)) {
      return null;
    }

    PooledHttpClient cdc = null;

    final String path = getCutypePath(cutype, cdi);

    String addrCtype = null;

    /* See if we want address data in any particular form */
    for (final WebdavProperty wd: returnProps) {
      if (!wd.getTag().equals(CarddavTags.addressData)) {
        continue;
      }

      addrCtype = wd.getAttr("content-type");
      break;
    }

    try {
      try {
        cdc = new PooledHttpClient(new URI(cdi.getUrl()));
      } catch (final URISyntaxException e) {
        throw new BedeworkException(e);
      }
      final List<MatchResult> mrs = matching(cdc,
                                             path,
                                             addrCtype,
                                             props);

      final List<BwPrincipalInfo> pis = new ArrayList<>();

      if (mrs == null) {
        return new FindPrincipalsResult(pis, false);
      }

      for (final MatchResult mr: mrs) {
        final BwPrincipalInfo pi = new BwPrincipalInfo();

        pi.setPropertiesFromVCard(mr.card, addrCtype);
        pis.add(pi);
      }

      return new FindPrincipalsResult(pis, false);
    } finally {
      if (cdc != null) {
        cdc.release();
      }
    }
  }

  @Override
  public FindPrincipalsResult find(final String cua,
                                   final String cutype,
                                   final boolean expand) {
    final CardDavInfo cdi = getCardDavInfo(false);

    if ((cdi == null) || (cdi.getUrl() == null)) {
      return null;
    }

    PooledHttpClient cdc = null;

    try {
      try {
        cdc = new PooledHttpClient(new URI(cdi.getUrl()));
      } catch (final URISyntaxException e) {
        throw new RuntimeException(e);
      }

      final List<BwPrincipalInfo> pis = find(cdc, cdi,
                                             cua, cutype);

      if (!expand) {
        return new FindPrincipalsResult(pis, false);
      }

      /* if any of the returned entities represent a group then get
         the info for each member
       */

      for (final BwPrincipalInfo pi: pis) {
        if (!Kind.GROUP.getValue().equalsIgnoreCase(pi.getKind())) {
          continue;
        }

        final List<BwPrincipalInfo> memberPis = new ArrayList<>();

        final VCard card = pi.getCard();

        final List<Property> members = card.getProperties(Id.MEMBER);

        if (members == null) {
          continue;
        }

        for (final Property p: members) {
          final BwPrincipalInfo memberPi = fetch(cdc, cdi, p.getValue());

          if (memberPi != null) {
            memberPis.add(memberPi);
          }
        }

        pi.setMembers(memberPis);
      }

      return new FindPrincipalsResult(pis, false);
    } finally {
      if (cdc != null) {
        cdc.release();
      }
    }
  }

  private BwPrincipalInfo fetch(final PooledHttpClient cdc,
                                final CardDavInfo cdi,
                                final String uri) {
    final List<BwPrincipalInfo> pis = find(cdc, cdi, uri,
                                           CuType.INDIVIDUAL.getValue());

    if ((pis == null) || (pis.size() != 1)) {
      return null;
    }

    return pis.get(0);
  }

  private List<BwPrincipalInfo> find(final PooledHttpClient cdc,
                                     final CardDavInfo cdi,
                                     final String cua,
                                     final String cutype) {
    /* Typically a group entry in a directory doesn't have a mail -
       The cua is a uri which often looks like a mailto.
     */

    final List<WebdavProperty> props = new ArrayList<>();
    final CalAddr ca = new CalAddr(cua);

    if ((cutype != null) &&
            cutype.equalsIgnoreCase(CuType.GROUP.getValue())) {
      final WebdavProperty fnProp = new WebdavProperty(WebdavTags.displayname,
                                                       ca.getId());

      props.add(fnProp);
    }


    final WebdavProperty emailProp = new WebdavProperty(BedeworkServerTags.emailProp,
                                                        ca.getNoScheme());

    props.add(emailProp);

    final List<BwPrincipalInfo> pis = new ArrayList<>();

    final List<MatchResult> mrs = matching(cdc,
                                           getCutypePath(cutype, cdi),
                                           null,
                                           props);

    if (mrs == null) {
      return pis;
    }

    for (final MatchResult mr: mrs) {
      final BwPrincipalInfo pi = new BwPrincipalInfo();

      pi.setPropertiesFromVCard(mr.card, null);
      pis.add(pi);
    }

    return pis;
  }

  private static class CutypeMap extends HashMap<String, String> {
    String configValue; // What we built this from
    String defaultPath;

    String init(final String configValue) {
      if (configValue == null) {
        clear();

        defaultPath = "/directory/";
        return "No cutype mapping in carddav info";
      }

      if (configValue.equals(this.configValue)) {
        return "";
      }

      this.configValue = configValue;

      final String[] split = configValue.split(",");

      String err = "";

      for (final String s: split) {
        final String[] keyVal = s.split(":");

        if (keyVal.length != 2) {
          err += "\nBad value in cutype mapping: " + s;
          continue;
        }

        if (keyVal[0].equals("*")) {
          if (defaultPath != null) {
            err += "\nMore than one default path in cutype mapping";
          } else {
            defaultPath = keyVal[1];
          }

          continue;
        }

        put(keyVal[0], keyVal[1]);
      }

      if (defaultPath == null) {
        err += "\nNo default path in cutype mapping";
        defaultPath = "/directory/";
      }

      return err;
    }

    String getDefaultPath() {
      return defaultPath;
    }

    Set<String> getCutypes() {
      return keySet();
    }
  }

  private static final CutypeMap cutypeMap = new CutypeMap();

  private String getCutypePath(final String cutype,
                               final CardDavInfo cdi) {
    final String msg = cutypeMap.init(cdi.getCutypeMapping());
    if (msg.length() > 0) {
      warn(msg);
    }

    if (cutype == null) {
      return cutypeMap.getDefaultPath();
    }

    final String cutypePath = cutypeMap.get(cutype.toLowerCase());

    if (cutypePath != null) {
      return cutypePath;
    }

    return cutypeMap.getDefaultPath();
  }

  private Set<String> getCutypes(final CardDavInfo cdi) {
    cutypeMap.init(cdi.getCutypeMapping());

    return cutypeMap.getCutypes();
  }

  @Override
  public boolean mergePreferences(final BwPreferences prefs,
                                  final BwPrincipalInfo pinfo) {
    boolean changed = false;
    //PrincipalProperty kind = pinfo.findProperty("kind");

    /* ============ auto scheduling ================== */
    final BooleanPrincipalProperty pautoSched =
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

    final IntPrincipalProperty pschedMaxInstances =
      (IntPrincipalProperty)pinfo.findProperty("max-instances");

    if (pschedMaxInstances != null) {
      final int mi = pschedMaxInstances.getVal();
      final String strMi = String.valueOf(mi);

      final BwProperty pmi = prefs.findProperty(BwPreferences.propertyScheduleMaxinstances);
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

  @Override
  public boolean isPrincipal(final String val) {
    return BwPrincipal.isPrincipal(val);
  }

  @Override
  public String accountFromPrincipal(final String val) {
    final String userProot = fromWho.get(WhoDefs.whoTypeUser);

    if (!val.startsWith(userProot)) {
      return null;
    }

    String acc = val.substring(userProot.length());

    if (acc.endsWith("/")) {
      acc = acc.substring(0, acc.length() - 1);
    }

    if (acc.indexOf("/") > 0) {
      return null;
    }

    return acc;
  }

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    return BwPrincipal.makePrincipal(href);
  }

  @Override
  public String makePrincipalUri(final String id,
                                 final int whoType) {
    if (isPrincipal(id)) {
      return id;
    }

    final String root = fromWho.get(whoType);

    if (root == null) {
      throw new RuntimeException(CalFacadeErrorCode.unknownPrincipalType);
    }

    return Util.buildPath(colPathEndsWithSlash, root, "/", id);
  }

  @Override
  public Collection<String> getGroups(final String rootUrl,
                                      final String principalUrl) {
    final Collection<String> urls = new TreeSet<>();

    if (principalUrl == null) {
      /* for the moment if the root url is the user principal hierarchy root
       * just return the current user principal
       */
      final String r = Util.buildPath(true, rootUrl);

      /* ResourceUri should be the principals root or user principal root */
      if (!r.equals(BwPrincipal.principalRoot) &&
          !r.equals(BwPrincipal.userPrincipalRoot)) {
        return urls;
      }

      urls.add(Util.buildPath(colPathEndsWithSlash,
                              BwPrincipal.userPrincipalRoot, "/",
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
  public String uriToCaladdr(final String val) {
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
    final int colonPos = val.indexOf(":");
    final int atPos = val.indexOf("@");
    String uri = val;

    if (colonPos > 0) {
      if (atPos < colonPos) {
        return null;
      }

      isAccount = false;
    } else if (atPos > 0) {
      uri = "mailto:" + val;
    }

    final AccessPrincipal possibleAccount = caladdrToPrincipal(uri);
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
          new FlushMap<>(maxCaRefreshTime, maxCaMapSize);

  protected static Map<String, BwPrincipal<?>> calAddrToPrincipalMap =
      new FlushMap<>(maxCaRefreshTime, maxCaMapSize);

  @Override
  public String principalToCaladdr(final AccessPrincipal val) {
    return userToCaladdr(val.getAccount());
  }

  @Override
  public String userToCaladdr(final String val) {
    /* Override this to do directory lookups or query vcard. The following
     * transforms may be insufficient
     */

    String ca = userToCalAddrMap.get(val);

    if (ca != null) {
      return ca;
    }

    if (isPrincipal(val)) {
      final BwPrincipal<?> p = getPrincipal(val);

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

    final int atPos = val.indexOf("@");

    final boolean hasMailto = val.toLowerCase().startsWith("mailto:");

    if (atPos > 0) {
      if (hasMailto) {
        // ensure lower case (helps with some tests)
        return "mailto:" + val.substring(7);
      }

      ca = "mailto:" + val;
      userToCalAddrMap.put(val, ca);

      return ca;
    }

    final StringBuilder sb = new StringBuilder();
    if (!hasMailto) {
      sb.append("mailto:");
    }

    sb.append(val);
    sb.append("@");
    sb.append(getDefaultDomain());

    ca = sb.toString();
    userToCalAddrMap.put(val, ca);

    return ca;
  }

  @Override
  public BwPrincipal<?> caladdrToPrincipal(final String caladdr) {
    if (caladdr == null) {
      throw new RuntimeException(CalFacadeErrorCode.nullCalendarUserAddr);
    }

    BwPrincipal<?> p = calAddrToPrincipalMap.get(caladdr);
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
    final int atPos = ca.indexOf("@");

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
      for (final DomainMatcher dm: domains) {
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

    for (final CAPrefixInfo c: getCaPrefixInfo()) {
      if (acc.startsWith(c.getPrefix())) {
        whoType = c.getType();
        break;
      }
    }

    p = getPrincipal(makePrincipalUri(acc, whoType));
    calAddrToPrincipalMap.put(caladdr, p);

    return p;
  }

  private static class CalAddr {
    private String scheme;
    private String id;
    private String domain;

    CalAddr(final String val) {
      final int atPos = val.indexOf("@");

      if (atPos > 0) {
        domain = val.substring(atPos + 1).toLowerCase();
        id = val.substring(0, atPos);
      } else {
        id = val;
      }

      final int colonPos = id.indexOf(":");

      if (colonPos > 0) {
        scheme = id.substring(0, colonPos);
        id = id.substring(colonPos + 1);
      }
    }

    String getScheme() {
      return scheme;
    }

    String getId() {
      return id;
    }

    String getDomain() {
      return domain;
    }

    String getNoScheme() {
      if (domain == null) {
        return id;
      }

      return id + "@" + domain;
    }
  }

  @Override
  public String normalizeCua(final String val) {
    if (val == null) {
      throw new BedeworkException(CalFacadeErrorCode.badCalendarUserAddr);
    }

    if (val.startsWith("/")) {
      return val;
    }

    final int colonPos = val.indexOf(":");
    final int atPos = val.indexOf("@");

    if (colonPos > 0) {
      if (atPos < colonPos) {
        throw new BedeworkException(CalFacadeErrorCode.badCalendarUserAddr);
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
  public String getDefaultDomain() {
    if (defaultDomain == null) {
      throw new RuntimeException(CalFacadeErrorCode.noDefaultDomain);
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
   * @param href principal href
   */
  protected void addValidPrincipal(final String href) {
    validPrincipals.put(href, href);
  }

  protected DirConfigProperties getProps() {
    return dirProps;
  }

  protected CalAddrPrefixes getCaPrefixes() {
    return caPrefixes;
  }

  protected Collection<CAPrefixInfo> getCaPrefixInfo() {
    if (caPrefixInfo != null) {
      return caPrefixInfo;
    }

    final CalAddrPrefixes cap = getCaPrefixes();

    final List<CAPrefixInfo> capInfo = new ArrayList<>();

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

  protected CardDavInfo getCardDavInfo(final boolean auth) {
    if (auth) {
      return authCdinfo;
    }

    return unauthCdinfo;
  }

  /* ====================================================================
   *  Private methods.
   * ==================================================================== */

  private void setDomains() {
    final String prDomains = dirProps.getDomains();

    if (prDomains.equals("*")) {
      anyDomain = true;
    } else if ((!prDomains.contains(",")) &&
               !prDomains.startsWith("*")) {
      onlyDomain = new DomainMatcher(prDomains);
    } else {
      domains = new ArrayList<>();

      for (final var domain: dirProps.getDomains().split(",")) {
        domains.add(new DomainMatcher(domain));
      }
    }

    defaultDomain = dirProps.getDefaultDomain();
    if ((defaultDomain == null) && (onlyDomain != null)) {
      defaultDomain = prDomains;
    }
  }

  private static class MatchResult {
    String href;
    String etag;
    String card;
  }

  private List<MatchResult> matching(final PooledHttpClient cl,
                                     final String url,
                                     final String addrDataCtype,
                                     final List<WebdavProperty> props) {
    /* Try a search of the cards

   <?xml version="1.0" encoding="utf-8" ?>
   <C:addressbook-query xmlns:D="DAV:"
                     xmlns:C="urn:ietf:params:xml:ns:carddav">
     <D:prop>
       <D:getetag/>
       <C:address-data>
         <C:prop name="VERSION"/>
         <C:prop name="UID"/>
         <C:prop name="NICKNAME"/>
         <C:prop name="EMAIL"/>
         <C:prop name="FN"/>
       </C:address-data>
     </D:prop>
     <C:filter test="anyof">
       <C:prop-filter name="FN">
         <C:text-match collation="i;unicode-casemap"
                       match-type="contains"
         >daboo</C:text-match>
       </C:prop-filter>
       <C:prop-filter name="EMAIL">
         <C:text-match collation="i;unicode-casemap"
                       match-type="contains"
         >daboo</C:text-match>
       </C:prop-filter>
     </C:filter>
   </C:addressbook-query>
     */

    try {
      final XmlEmit xml = new XmlEmit();
      xml.addNs(new NameSpace(WebdavTags.namespace, "DAV"), true);
      xml.addNs(new NameSpace(CarddavTags.namespace, "C"), false);

      final StringWriter sw = new StringWriter();

      xml.startEmit(sw);

      xml.openTag(CarddavTags.addressbookQuery);
      xml.openTag(WebdavTags.prop);
      xml.emptyTag(WebdavTags.getetag);

      if (addrDataCtype == null) {
        xml.emptyTag(CarddavTags.addressData);
      } else {
        xml.emptyTag(CarddavTags.addressData,
                     "content-type", addrDataCtype);
      }

      xml.closeTag(WebdavTags.prop);

      xml.openTag(CarddavTags.filter, "test", "anyof");

      for (final WebdavProperty wd: props) {
        if (wd.getTag().equals(CaldavTags.calendarUserType)) {
          // Should match onto KIND
          continue;
        }

        if (wd.getTag().equals(BedeworkServerTags.emailProp)) {
          // Match Email
          xml.openTag(CarddavTags.propFilter, "name", "EMAIL");

          xml.startTagSameLine(CarddavTags.textMatch);
          xml.attribute("collation", "i;unicode-casemap");
          xml.attribute("match-type", "contains");
          xml.endOpeningTag();
          xml.value(wd.getPval());
          xml.closeTagSameLine(CarddavTags.textMatch);

          xml.closeTag(CarddavTags.propFilter);
          continue;
        }

        if (wd.getTag().equals(WebdavTags.displayname)) {
          // Match FN
          xml.openTag(CarddavTags.propFilter, "name", "FN");

          xml.startTagSameLine(CarddavTags.textMatch);
          xml.attribute("collation", "i;unicode-casemap");
          xml.attribute("match-type", "contains");
          xml.endOpeningTag();
          xml.value(wd.getPval());
          xml.closeTagSameLine(CarddavTags.textMatch);

          xml.closeTag(CarddavTags.propFilter);
          //continue;
        }
      }

      xml.closeTag(CarddavTags.filter);

      xml.closeTag(CarddavTags.addressbookQuery);

      final byte[] content = sw.toString().getBytes();

      final ResponseHolder<?> resp;
      try {
        resp = cl.report(url, "infinity",
                                              new String(content),
                                              this::processMatchingResponse);
      } catch (final HttpException e) {
        throw new RuntimeException(e);
      }

      if (resp.failed) {
        if (debug()) {
          debug("Got response " + resp.status + " for path " + url);
        }

        return null;
      }

      return (List<MatchResult>)resp.response;
    } finally {
      try {
        cl.release();
      } catch (final Throwable ignored) {}
    }
  }

  final ResponseHolder<?> processMatchingResponse(final String path,
                                               final CloseableHttpResponse resp) {
    try {
      final int status = HttpUtil.getStatus(resp);

      if (status != SC_MULTI_STATUS) {
        return new ResponseHolder<>(status,
                                    "Failed response from server");
      }

      if (resp.getEntity() == null) {
        return new ResponseHolder<>(status,
                                    "No content in response from server");
      }

      final InputStream is = resp.getEntity().getContent();
      final DavUtil du = new DavUtil();

      final MultiStatusResponse msr =
              du.getMultiStatusResponse(is);
      final List<MatchResult> mrs = new ArrayList<>();

      for (final MultiStatusResponseElement msre: msr.responses) {
        final MatchResult mr = new MatchResult();
        mrs.add(mr);

        mr.href = msre.href;

        for (final PropstatElement pe: msre.propstats) {
          if (pe.status != HttpServletResponse.SC_OK) {
            continue;
          }

          for (final Element e: pe.props) {
            if (XmlUtil.nodeMatches(e, WebdavTags.getetag)) {
              mr.etag = XmlUtil.getElementContent(e);
              continue;
            }

            if (XmlUtil.nodeMatches(e, CarddavTags.addressData)) {
              mr.card = XmlUtil.getElementContent(e);
            }
          }
        }

      }

      final ResponseHolder<List<MatchResult>> response =
              new ResponseHolder<>(mrs);
      return response;
    } catch (final Throwable t) {
      return new ResponseHolder<>(t);
    }
  }

  /** Get the vcard for the given principal
   *
  * @param p - who we want card for
  * @return card or null
  */
  private String getCard(final PooledHttpClient cl,
                         final AccessPrincipal p) {
    /* Try a propfind on the principal */

    try {
      final String content =
              "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                      "<D:propfind xmlns:D=\"DAV:\"\n" +
                      "            xmlns:C=\"urn:ietf:params:xml:ns:carddav\">\n" +
                      "  <D:prop>\n" +
                      "    <C:principal-address/>\n" +
                      "  </D:prop>\n" +
                      "</D:propfind>\n";

      final var resp = cl.propfind(p.getPrincipalRef(),
                                   "0",
                                   content,
                                              this::processGetCardHrefResponse);

      if (resp.failed) {
        if (debug()) {
          debug("Got response " + resp.status + " for path " +
                        p.getPrincipalRef());
        }

        return null;
      }

      final String href = (String)resp.response;

      /* New request for card */
      return cl.getString(href, "text/vcard");
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    } finally {
      try {
        cl.release();
      } catch (final Throwable ignored) {}
    }
  }

  final ResponseHolder<?> processGetCardHrefResponse(
          final String path,
          final CloseableHttpResponse resp) {
    try {
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
       */
      final int status = HttpUtil.getStatus(resp);

      if (status != SC_MULTI_STATUS) {
        return new ResponseHolder<>(status,
                                    "Failed response from server");
      }

      if (resp.getEntity() == null) {
        return new ResponseHolder<>(status,
                                    "No content in response from server");
      }

      final InputStream is = resp.getEntity().getContent();

      final DavUtil du = new DavUtil();
      final MultiStatusResponse msr = du.getMultiStatusResponse(is);

      // Expect one response only - might have responseDescription

      if (msr.responses.size() != 1) {
        throw new BedeworkException(
                "Bad response. Expected exactly 1 response element");
      }

      final var msre = msr.responses.get(0);

      /* We want one propstat element with successful status */

      if (msre.propstats.size() != 1) {
        if (debug()) {
          debug("Found " + msre.propstats
                  .size() + " propstat elements");
        }

        return new ResponseHolder<>(HttpStatus.SC_NOT_ACCEPTABLE,
                                    "Found " + msre.propstats
                                          .size() + " propstat elements");
      }

      final var pse = msre.propstats.get(0);
      if (pse.status != HttpServletResponse.SC_OK) {
        if (debug()) {
          debug("propstat status was " + pse.status);
        }

        return new ResponseHolder<>(HttpStatus.SC_NOT_ACCEPTABLE,
                                  "propstat status was " + pse.status);
      }

      // We expect one principal-address property
      if (pse.props.size() != 1) {
        if (debug()) {
          debug("Found " + pse.props.size() + " prop elements");
        }

        return new ResponseHolder<>(HttpStatus.SC_NOT_ACCEPTABLE,
                                  "Found " + pse.props.size() + " prop elements");
      }

      final Element pr = pse.props.iterator().next();

      if (!XmlUtil.nodeMatches(pr, CarddavTags.principalAddress)) {
        if (debug()) {
          debug("Expected principal-address - found " + pr);
        }

        return new ResponseHolder<>(HttpStatus.SC_NOT_ACCEPTABLE,
                                  "Expected principal-address - found " + pr);
      }

      /* Expect a single href element */
      final Element hrefEl = DavUtil.getOnlyChild(pr);
      if (!XmlUtil.nodeMatches(hrefEl, WebdavTags.href)) {
        if (debug()) {
          debug("Expected href element for principal-address - found " +
                        hrefEl);
        }

        return new ResponseHolder<>(HttpStatus.SC_NOT_ACCEPTABLE,
                                  "Expected href element for principal-address - found " +
                                          hrefEl);
      }

      return new ResponseHolder<>(URLDecoder.decode(XmlUtil.getElementContent(
              hrefEl), StandardCharsets.UTF_8)); // href should be escaped
    } catch (final Throwable t) {
      return new ResponseHolder<>(t);
    }
  }

  private void initWhoMaps(final String prefix, final int whoType) {
    toWho.put(prefix, whoType);
    fromWho.put(whoType, prefix);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}

