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

import org.bedework.access.AccessException;
import org.bedework.access.AccessPrincipal;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.PrivilegeSet;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwUser;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calsvci.DumpIntf;
import org.bedework.util.misc.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Class which implements the functions needed to dump the
 * calendar using a jdbc connection.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
@SuppressWarnings("unchecked")
public class DumpImpl extends CalSvcDb implements DumpIntf {
  //DumpPrincipalInfo pinfo;

  /* *
   * @param sysRoots
   * /
  public DumpImpl(final SystemRoots sysRoots) {
    this.sysRoots = sysRoots;
  }
  */

  DumpImpl(final CalSvc svci) {
    super(svci);
  }

  @Override
  public Iterator<BwAdminGroup> getAdminGroups() {
    final Collection<BwAdminGroup> c = getCal().getAdminGroups();

    for (final BwGroup grp: c) {
      getAdminMembers(grp);
    }

    return c.iterator();
  }

  @Override
  public Iterator<BwAuthUser> getAuthUsers() {
    return getObjects(BwAuthUser.class);
  }

  @Override
  public Iterator<BwCalendar> getCalendars() {
    final Collection<BwCalendar> cols = new ArrayList<>();

    cols.add(getCal().getCalendar(
            BasicSystemProperties.publicCalendarRootPath,
            PrivilegeDefs.privAny, false));
    cols.add(getCal().getCalendar(
            BasicSystemProperties.userCalendarRootPath,
            PrivilegeDefs.privAny, false));

    return cols.iterator();
  }

  @Override
  public Collection<BwCalendar> getChildren(final BwCalendar val) {
    return getCal().getCalendars(val, null);
  }

  @Override
  public Iterator<BwCalSuite> getCalSuites() {
    return getObjects(BwCalSuite.class);
  }

  @Override
  public Iterator<BwCategory> getCategories() {
    return getObjects(BwCategory.class);
  }

  private class EventIterator implements Iterator<BwEvent> {
    private final Iterator<BwEventObj> it;

    private EventIterator(final Iterator<BwEventObj> it) {
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public BwEvent next() {
      try {
        final BwEvent ev = it.next();
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

  private class EventInfoIterator implements Iterator<EventInfo> {
    private final Iterator<BwEventObj> it;

    private EventInfoIterator(final Iterator<BwEventObj> it) {
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public EventInfo next() {
      try {
        final BwEvent ev = it.next();
        
        final EventInfo evi;
        
        if (ev.testRecurring()) {
          final Set<EventInfo> overrides = new TreeSet<>();

          final Collection<BwEventAnnotation> ovevs = getOverrides(
                  ev);

          if (!Util.isEmpty(ovevs)) {
            for (final BwEventAnnotation ovev : ovevs) {
              overrides.add(new EventInfo(new BwEventProxy(ovev)));
            }
          }

          evi = new EventInfo(ev, overrides);
        } else {
          evi = new EventInfo(ev);
        }

        return evi;
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }

    @Override
    public void remove() {
      throw new RuntimeException("Forbidden");
    }
  }

  private class ResourceIterator implements Iterator<BwResource> {
    private final Iterator<BwResource> it;

    private ResourceIterator(final Iterator<BwResource> it) {
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public BwResource next() {
      try {
        final BwResource res = it.next();

        getResourceContent(res);

        return res;
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }

    @Override
    public void remove() {
      throw new RuntimeException("Forbidden");
    }
  }

  static class IterableImpl<T> implements Iterable<T> {
    private final Iterator<T> it;

    IterableImpl(final Iterator<T> it){
      this.it = it;
    }

    @Override
    public Iterator<T> iterator() {
      return it;
    }
  }

  @Override
  public Iterator<BwEvent> getEvents() {
    return new EventIterator(
            getCal().getObjectIterator(BwEventObj.class));
  }

  @Override
  public Iterable<EventInfo> getEventInfos(final String colPath) {
    return new IterableImpl<>(new EventInfoIterator(
            getCal().getObjectIterator(BwEventObj.class,
                                       colPath)));
  }

  @Override
  public Iterable<BwResource> getResources(final String colPath) {
    return new IterableImpl<>(new ResourceIterator(
            getCal().getObjectIterator(BwResource.class,
                                       colPath)));
  }

  @Override
  public Iterator<String> getEventHrefs(final int start) {
    return getCal().getEventHrefs(start);
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() {
    return getCal().getEventAnnotations();
  }

  @Override
  public Iterator<BwFilterDef> getFilters() {
    return getObjects(BwFilterDef.class);
  }

  @Override
  public Iterator<BwLocation> getLocations() {
    return getObjects(BwLocation.class);
  }

  @Override
  public Iterator<BwPreferences> getPreferences() {
    return getObjects(BwPreferences.class);
  }

  @Override
  public Iterator<BwContact> getContacts() {
    return getObjects(BwContact.class);
  }

  @Override
  public Iterator<BwPrincipal<?>> getAllPrincipals() {
    return getObjects(BwUser.class);
  }

  @Override
  public Iterator<BwResource> getResources() {
    return getObjects(BwResource.class);
  }

  @Override
  public void getResourceContent(final BwResource res) {
    try {
      getCal().getResourceContent(res);
    } catch (final BedeworkException be){
      if (be.getMessage().equals(CalFacadeErrorCode.missingResourceContent)) {
        return; // Caller will flag this.
      }

      throw be;
    }

  }

  @Override
  public Iterator<BwView> getViews() {
    return getObjects(BwView.class);
  }

  @Override
  public void startPrincipal(final BwPrincipal val) {
    getSvc().pushPrincipal(val);
  }

  @Override
  public void endPrincipal(final BwPrincipal val) {
    getSvc().popPrincipal();
  }

  private <T> Iterator<T> getObjects(final Class<?> cl) {
    return (Iterator<T>)getCal().getObjectIterator(cl);
  }

  private void getAdminMembers(final BwGroup group) {
    group.setGroupMembers(getCal().getMembers(group, true));
  }

  @SuppressWarnings("unused")
  private void getMembers(final BwGroup group) {
    group.setGroupMembers(getCal().getMembers(group, false));
  }

  private Collection<BwEventAnnotation> getOverrides(final BwEvent ev) {
    return getCal().getEventOverrides(ev);
  }

  /**
   * @author douglm
   *
   */
  private static final class DumpPrincipalInfo extends PrincipalInfo {
    private final DumpImpl dump;

    private final Map<String, BwPrincipal> principals = new HashMap<>();

    private static class StackedState {
      BwPrincipal principal;
      //boolean superUser;
      //String calendarHomePath;
      //PrivilegeSet maxAllowedPrivs;
    }

    private final Deque<StackedState> stack = new ArrayDeque<>();

    DumpPrincipalInfo(final DumpImpl dump,
                      final BwPrincipal principal,
                      final BwPrincipal authPrincipal,
                      final PrivilegeSet maxAllowedPrivs) {
      super(principal, authPrincipal, maxAllowedPrivs, false);
      this.dump = dump;
   }

    @Override
    public AccessPrincipal getPrincipal(final String href) {
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

    void setPrincipal(final BwPrincipal principal) {
      this.principal = principal;
      calendarHomePath = null;
    }

    /*
    void pushPrincipal(final BwPrincipal principal) {
      final StackedState ss = new StackedState();
      ss.principal = this.principal;
      ss.superUser = superUser;
      ss.calendarHomePath = calendarHomePath;
      ss.maxAllowedPrivs = maxAllowedPrivs;

      stack.push(ss);

      setPrincipal(principal);
      superUser = false;
      maxAllowedPrivs = null;
    }

    void popPrincipal() {
      final StackedState ss = stack.pop();

      if (ss == null) {
        throw new BedeworkException("Nothing to pop");
      }

      setPrincipal(ss.principal);
      calendarHomePath = ss.calendarHomePath;
      superUser = ss.superUser;
      maxAllowedPrivs = ss.maxAllowedPrivs;
    }*/

    @Override
    public String makeHref(final String id,
                           final int whoType) throws AccessException {
      return BwPrincipal.makePrincipalHref(id,whoType);
    }
  }
}
