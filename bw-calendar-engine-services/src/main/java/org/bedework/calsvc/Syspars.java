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
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.locale.BwLocale;
import org.bedework.calsvci.SysparsI;

import edu.rpi.cmt.access.WhoDefs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/** This acts as an interface to the database for system parameters.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Syspars extends CalSvcDb implements SysparsI {
  private String systemName;

  private static BwSystem syspars;

  private static boolean restoring;

  private static long lastRefresh;
  private static long refreshInterval = 1000 * 60 * 5; // 5 mins

  private Collection<Locale>supportedLocales;

  private Collection<String> rootUsers;

  Syspars(final CalSvc svci,
          final String systemName) {
    super(svci);
    this.systemName = systemName;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#getSystemName()
   */
  @Override
  public String getSystemName() {
    return systemName;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#get()
   */
  @Override
  public BwSystem get() throws CalFacadeException {
    if (restoring) {
      return syspars;
    }

    synchronized (this) {
      if (System.currentTimeMillis() > (lastRefresh + refreshInterval)) {
        syspars = null;
        lastRefresh = System.currentTimeMillis();
      }

      if (syspars == null) {
        syspars = get(getSystemName());
      }

      return syspars;
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#get(java.lang.String)
   */
  @Override
  public BwSystem get(final String name) throws CalFacadeException {
    BwSystem sys = getCal().getSyspars(name);

    if (sys == null) {
      throw new CalFacadeException("No system parameters with name " +
                                   name);
    }

    if (debug) {
      trace("Read system parameters: " + sys);
    }

    if (name.equals(getSystemName())) {
      syspars = sys;
    }

    return sys;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#update(org.bedework.calfacade.BwSystem)
   */
  @Override
  public void update(final BwSystem val) throws CalFacadeException {
    if (!isSuper()) {
      throw new CalFacadeAccessException();
    }

    getCal().saveOrUpdate(val);

    syspars = null; // Force refresh
    restoring = false;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#getSysid()
   */
  @Override
  public String getSysid() throws CalFacadeException {
    return get().getSystemid();
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#getSupportedLocales()
   */
  @Override
  public Collection<Locale> getSupportedLocales() throws CalFacadeException {
    if (supportedLocales != null) {
      return supportedLocales;
    }

    supportedLocales = new ArrayList<Locale>();

    String ll = get().getLocaleList();

    if (ll == null) {
      supportedLocales.add(BwLocale.getLocale());
      return supportedLocales;
    }

    try {
      int pos = 0;

      while (pos < ll.length()) {
        int nextPos = ll.indexOf(",", pos);
        if (nextPos < 0) {
          supportedLocales.add(BwLocale.makeLocale(ll.substring(pos)));
          break;
        }

        supportedLocales.add(BwLocale.makeLocale(ll.substring(pos, nextPos)));
        pos = nextPos + 1;
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(CalFacadeException.badSystemLocaleList,
                                   ll);
    }

    if (supportedLocales.isEmpty()) {
      supportedLocales.add(BwLocale.getLocale());
    }

    return supportedLocales;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#addLocale(java.util.Locale)
   */
  @Override
  public void addLocale(final Locale loc) throws CalFacadeException {
    Collection<Locale> locs = getSupportedLocales();

    if (locs.contains(loc)) {
      return;
    }

    String ll = get().getLocaleList();
    StringBuilder sb;
    if (ll == null) {
      sb = new StringBuilder();
    } else {
      sb = new StringBuilder(ll);
      sb.append(",");
    }

    sb.append(loc.getLanguage());

    String country = loc.getCountry();

    if ((country != null) && (country.length() != 0)) {
      sb.append("_");
      sb.append(country);

      String variant = loc.getVariant();

      if ((variant != null) && (variant.length() != 0)) {
        sb.append("_");
        sb.append(variant);
      }
    }

    get().setLocaleList(sb.toString());
    update(get());
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#removeLocale(java.util.Locale)
   */
  @Override
  public void removeLocale(final Locale loc) throws CalFacadeException {
    Collection<Locale> locs = getSupportedLocales();

    if (!locs.contains(loc)) {
      return;
    }

    locs.remove(loc);

    if (locs.isEmpty()) {
      get().setLocaleList(null);
      update(get());
      return;
    }

    StringBuilder sb = new StringBuilder();
    for (Locale l: locs) {
      if (sb.length() == 0) {
        sb.append(",");
      }

      sb.append("(");
      sb.append(l.getLanguage());
      sb.append(",");
      sb.append(l.getCountry());

      if (l.getVariant() != null) {
        sb.append(",");
        sb.append(l.getVariant());
      }
      sb.append(")");
    }

    get().setLocaleList(sb.toString());
    update(get());
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#getRootUsers()
   */
  @Override
  public Collection<String> getRootUsers() throws CalFacadeException {
    if (rootUsers != null) {
      return rootUsers;
    }

    rootUsers = new ArrayList<String>();

    String rus = get().getRootUsers();

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
      throw new CalFacadeException(CalFacadeException.badRootUsersList,
                                   rus);
    }

    return rootUsers;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#addRootUser(java.lang.String)
   */
  @Override
  public void addRootUser(final String val) throws CalFacadeException {
    Collection<String> rus = getRootUsers();

    if (rus.contains(val)) {
      return;
    }

    String srus = get().getRootUsers();
    StringBuilder sb;
    if (srus == null) {
      sb = new StringBuilder();
    } else {
      sb = new StringBuilder(srus);
      sb.append(",");
    }

    sb.append(val);

    get().setRootUsers(sb.toString());
    update(get());
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#removeRootUser(java.lang.String)
   */
  @Override
  public void removeRootUser(final String val) throws CalFacadeException {
    Collection<String> rus = getRootUsers();

    if (!rus.contains(val)) {
      return;
    }

    rus.remove(val);

    if (rus.isEmpty()) {
      get().setRootUsers(null);
      update(get());
      return;
    }

    StringBuilder sb = new StringBuilder();
    for (String r: rus) {
      if (sb.length() == 0) {
        sb.append(",");
      }

      sb.append(r);
    }

    get().setRootUsers(sb.toString());
    update(get());
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SysparsI#isRootUser(org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public boolean isRootUser(final BwPrincipal val) throws CalFacadeException {
    if ((val == null) || val.getUnauthenticated()) {
      return false;
    }

    if (val.getKind() != WhoDefs.whoTypeUser) {
      return false;
    }

    Collection<String> rus = getRootUsers();

    return rus.contains(val.getAccount());
  }

  void setForRestore(final BwSystem val) {
    restoring = true;
    syspars = val;
  }
}
