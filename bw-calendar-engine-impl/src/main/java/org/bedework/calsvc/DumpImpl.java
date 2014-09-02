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

import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwUser;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calsvci.DumpIntf;
import org.bedework.util.misc.Util;

import org.bedework.access.AccessException;
import org.bedework.access.AccessPrincipal;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.PrivilegeSet;
import org.bedework.access.WhoDefs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Class which implements the functions needed to dump the
 * calendar using a jdbc connection.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
@SuppressWarnings("unchecked")
public class DumpImpl extends CalSvcDb implements DumpIntf {
  DumpPrincipalInfo pinfo;

  private BasicSystemProperties sysRoots;

  /* *
   * @param sysRoots
   * @throws CalFacadeException
   * /
  public DumpImpl(final SystemRoots sysRoots) throws CalFacadeException {
    this.sysRoots = sysRoots;
  }
  */

  DumpImpl(final CalSvc svci) throws CalFacadeException {
    super(svci);
  }

  @Override
  public Iterator getAdminGroups() throws CalFacadeException {
    final Collection<BwGroup> c = getCal().getAllGroups(true);

    for (final BwGroup grp: c) {
      getAdminMembers(grp);
    }

    return c.iterator();
  }

  @Override
  public Iterator getAuthUsers() throws CalFacadeException {
    return getObjects(BwAuthUser.class.getName());
  }

  @Override
  public Iterator getCalendars() throws CalFacadeException {
    final Collection<BwCalendar> cols = new ArrayList<>();

    cols.add(getCal().getCalendar(Util.buildPath(true, "/",
                                                 getBasicSyspars()
                                                         .getPublicCalendarRoot()),
                              PrivilegeDefs.privAny, false));
    cols.add(getCal().getCalendar(Util.buildPath(true, "/",
                                                 getBasicSyspars().getUserCalendarRoot()),
                              PrivilegeDefs.privAny, false));

    return cols.iterator();
  }

  @Override
  public Collection<BwCalendar> getChildren(final BwCalendar val) throws CalFacadeException {
    return getCal().getCalendars(val);
  }

  @Override
  public Iterator getCalSuites() throws CalFacadeException {
    return getObjects(BwCalSuite.class.getName());
  }

  @Override
  public Iterator getCategories() throws CalFacadeException {
    return getObjects(BwCategory.class.getName());
  }

  private class EventIterator implements Iterator<BwEvent> {
    private final Iterator it;
    private boolean done;

    private EventIterator(final Iterator it) {
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public BwEvent next() {
      try {
        final BwEvent ev = (BwEvent)it.next();
        if (ev.testRecurring()) {
          ev.setOverrides(getOverrides(ev));
        }

        return ev;
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }

    @Override
    public void remove() {
      throw new RuntimeException("Forbidden");
    }
  }

  @Override
  public Iterator<BwEvent> getEvents() throws CalFacadeException {
    return new EventIterator(getCal().getObjectIterator(BwEventObj.class.getName()));
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() throws CalFacadeException {
    return getCal().getEventAnnotations();
  }

  @Override
  public Iterator getFilters() throws CalFacadeException {
    return getObjects(BwFilterDef.class.getName());
  }

  @Override
  public Iterator getLocations() throws CalFacadeException {
    return getObjects(BwLocation.class.getName());
  }

  @Override
  public Iterator getPreferences() throws CalFacadeException {
    return getObjects(BwPreferences.class.getName());
  }

  @Override
  public Iterator getContacts() throws CalFacadeException {
    return getObjects(BwContact.class.getName());
  }

  @Override
  public Iterator getAllPrincipals() throws CalFacadeException {
    return getObjects(BwUser.class.getName());
  }

  @Override
  public Iterator<BwResource> getResources() throws CalFacadeException {
    return getObjects(BwResource.class.getName());
  }

  @Override
  public void getResourceContent(final BwResource res) throws CalFacadeException {
    try {
      getCal().getResourceContent(res);
    } catch (final CalFacadeException cfe){
      if (cfe.getMessage().equals(CalFacadeException.missingResourceContent)) {
        return; // Caller will flag this.
      }

      throw cfe;
    }

  }

  @Override
  public Iterator getViews() throws CalFacadeException {
    return getObjects(BwView.class.getName());
  }

  private Iterator getObjects(final String className) throws CalFacadeException {
    return getCal().getObjectIterator(className);
  }

  private void getAdminMembers(final BwGroup group) throws CalFacadeException {
    group.setGroupMembers(getCal().getMembers(group, true));
  }

  @SuppressWarnings("unused")
  private void getMembers(final BwGroup group) throws CalFacadeException {
    group.setGroupMembers(getCal().getMembers(group, false));
  }

  private Collection<BwEventAnnotation> getOverrides(final BwEvent ev) throws CalFacadeException {
    return getCal().getEventOverrides(ev);
  }

  /**
   * @author douglm
   *
   */
  private static final class DumpPrincipalInfo extends PrincipalInfo {
    private final DumpImpl dump;

    private final HashMap<String, Integer> toWho = new HashMap<>();
    private final HashMap<Integer, String> fromWho = new HashMap<>();

    private final Map<String, BwPrincipal> principals = new HashMap<>();

    private static class StackedState {
      BwPrincipal principal;
      boolean superUser;
      String calendarHomePath;
      PrivilegeSet maxAllowedPrivs;
    }

    private final Deque<StackedState> stack = new ArrayDeque<>();

    DumpPrincipalInfo(final DumpImpl dump,
                      final BwPrincipal principal,
                      final BwPrincipal authPrincipal,
                      final PrivilegeSet maxAllowedPrivs) {
      super(principal, authPrincipal, maxAllowedPrivs);
      this.dump = dump;

      initWhoMaps(dump.sysRoots.getUserPrincipalRoot(), WhoDefs.whoTypeUser);
      initWhoMaps(dump.sysRoots.getGroupPrincipalRoot(), WhoDefs.whoTypeGroup);
      initWhoMaps(dump.sysRoots.getTicketPrincipalRoot(), WhoDefs.whoTypeTicket);
      initWhoMaps(dump.sysRoots.getResourcePrincipalRoot(), WhoDefs.whoTypeResource);
      initWhoMaps(dump.sysRoots.getVenuePrincipalRoot(), WhoDefs.whoTypeVenue);
      initWhoMaps(dump.sysRoots.getHostPrincipalRoot(), WhoDefs.whoTypeHost);
   }

    void setSuperUser(final boolean val) {
      superUser = val;
    }

    /* (non-Javadoc)
     * @see org.bedework.calfacade.util.AccessUtilI.CallBack#getPrincipal(java.lang.String)
     */
    @Override
    public AccessPrincipal getPrincipal(final String href) throws CalFacadeException {
      BwPrincipal p = principals.get(href);

      if (p == null) {
        p = dump.getCal().getPrincipal(href);
      }

      if (p != null) {
        principals.put(href, p);
      }

      return p;
      //return svci.getUsersHandler().getPrincipal(href);
    }

    @Override
    public BasicSystemProperties getSyspars() throws CalFacadeException {
      //return svci.getSysparsHandler().get();
      return dump.getBasicSyspars();
    }

    void setPrincipal(final BwPrincipal principal) {
      this.principal = principal;
      calendarHomePath = null;
    }

    void pushPrincipal(final BwPrincipal principal) {
      StackedState ss = new StackedState();
      ss.principal = this.principal;
      ss.superUser = superUser;
      ss.calendarHomePath = calendarHomePath;
      ss.maxAllowedPrivs = maxAllowedPrivs;

      stack.push(ss);

      setPrincipal(principal);
      superUser = false;
      maxAllowedPrivs = null;
    }

    void popPrincipal() throws CalFacadeException {
      StackedState ss = stack.pop();

      if (ss == null) {
        throw new CalFacadeException("Nothing to pop");
      }

      setPrincipal(ss.principal);
      calendarHomePath = ss.calendarHomePath;
      superUser = ss.superUser;
      maxAllowedPrivs = ss.maxAllowedPrivs;
    }

    @Override
    public String makeHref(final String id, final int whoType) throws AccessException {
      try {
        if (isPrincipal(id)) {
          return id;
        }

        final String root = fromWho.get(whoType);

        if (root == null) {
          throw new CalFacadeException(CalFacadeException.unknownPrincipalType);
        }

        return Util.buildPath(true, root, "/", id);
      } catch (final CalFacadeException cfe) {
        throw new AccessException(cfe);
      }
    }

    private boolean isPrincipal(final String val) throws CalFacadeException {
      if (val == null) {
        return false;
      }

      /* assuming principal root is "principals" we expect something like
       * "/principals/users/jim".
       *
       * Anything with fewer or greater elements is a collection or entity.
       */

      final int pos1 = val.indexOf("/", 1);

      if (pos1 < 0) {
        return false;
      }

      if (!val.substring(0, pos1).equals(dump.sysRoots.getPrincipalRoot())) {
        return false;
      }

      final int pos2 = val.indexOf("/", pos1 + 1);

      if (pos2 < 0) {
        return false;
      }

      if (val.length() == pos2) {
        // Trailing "/" on 2 elements
        return false;
      }

      for (final String root: toWho.keySet()) {
        String pfx = root;
        if (!pfx.endsWith("/")) {
          pfx += "/";
        }

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

    private void initWhoMaps(final String prefix, final int whoType) {
      toWho.put(prefix, whoType);
      fromWho.put(whoType, prefix);
    }
  }
}
