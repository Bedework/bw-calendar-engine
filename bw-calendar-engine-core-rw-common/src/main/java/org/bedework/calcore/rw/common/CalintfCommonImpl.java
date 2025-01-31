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
package org.bedework.calcore.rw.common;

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfROImpl;
import org.bedework.calcorei.CalintfInfo;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implementation of those parts of CalIntf which are orm independent.
 *
 *
 * @author Mike Douglass   bedework.org
 */
public abstract class CalintfCommonImpl extends CalintfROImpl {
  private static final BwStats stats = new BwStats();

  private static final CalintfInfo info = new CalintfInfo(
       true,     // handlesLocations
       true,     // handlesContacts
       true      // handlesCategories
     );

  private final IfInfo ifInfo = new IfInfo();

  protected abstract CoreAccess access();

  protected abstract CoreAlarms alarms();

  protected abstract CoreCalendars calendars();

  protected abstract CoreCalSuites calSuites();

  protected abstract CoreDumpRestore dumpRestore();

  protected abstract CoreEvents events();

  //protected abstract CoreEventPropertiesI<BwCategory> categoriesHandler();

  //protected abstract CoreEventPropertiesI<BwLocation> locationsHandler();

  //protected abstract CoreEventPropertiesI<BwContact> contactsHandler();

  protected abstract CoreFilterDefs filterDefs();

  protected abstract CorePrincipalsAndPrefs principalsAndPrefs();

  protected abstract CoreResources resources();

  protected Timestamp curTimestamp;

  protected static class IndexEntry {
    final BwIndexer indexer;
    final BwUnversionedDbentity<?> entity;
    boolean forTouch;

    IndexEntry(final BwIndexer indexer,
               final BwUnversionedDbentity<?> entity,
               final boolean forTouch) {
      this.indexer = indexer;
      this.entity = entity;
      this.forTouch = forTouch;
    }

    public String getKey() {
      return indexer.getDocType() + "-" + entity.getHref();
    }

    public int hashCode() {
      return entity.getHref().hashCode();
    }

    public boolean equals(final Object o) {
      if (!(o instanceof IndexEntry)) {
        return false;
      }

      return entity.getHref().equals(((IndexEntry)o).entity.getHref());
    }
  }

  protected Map<String, IndexEntry> awaitingIndex = new HashMap<>();

  /* ====================================================================
   *                   initialisation
   * ==================================================================== */

  /** Constructor
   *
   */
  @SuppressWarnings("unused")
  public CalintfCommonImpl() {
  }

  public IfInfo getIfInfo() {
    final long now = System.currentTimeMillis();
    
    ifInfo.setLogid(getLogId());
    ifInfo.setId(getTraceId());
    ifInfo.setDontKill(getDontKill());
    ifInfo.setLastStateTime(getLastStateTime());
    ifInfo.setState(getState());
    ifInfo.setSeconds((now - getStartMillis()) / 1000);
    
    return ifInfo;
  }

  /* ==========================================================
   *                   Indexing
   * ========================================================== */

  public void closeIndexers() {
    try {
      if (!awaitingIndex.isEmpty()) {
        final var vals = awaitingIndex.values();
        final var sz = vals.size();
        var ct = 1;

        for (final IndexEntry ie : vals) {
          try {
            ie.indexer.indexEntity(ie.entity,
                                   ct == sz,
                                   ie.forTouch); // wait
            ct++;
          } catch (final BedeworkException be) {
            if (debug()) {
              error(be);
            }
            throw be;
          }
        }
      }
    } finally {
      awaitingIndex.clear();

      super.closeIndexers();
    }
  }

  @Override
  public void indexEntity(final BwUnversionedDbentity<?> entity) {
    indexEntity(getIndexer(entity), entity, false);
  }

  @Override
  public void indexEntityNow(final BwCalendar entity) {
    indexEntity(getIndexer(entity), entity, true);
  }

  public void indexEntity(final BwIndexer indexer,
                          final BwUnversionedDbentity<?> entity,
                          final boolean forTouch) {
    //indexer.indexEntity(entity, wait);

    final var ie = new IndexEntry(indexer, entity, forTouch);
    final var prevEntry = awaitingIndex.put(ie.getKey(), ie);
    if (forTouch && (prevEntry != null) && !prevEntry.forTouch) {
      ie.forTouch = false;
    }
  }

  /* ==============================================================
   *                   Access
   * ============================================================== */

  @Override
  public void changeAccess(final ShareableEntity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    if (ent instanceof BwCalendar) {
      changeAccess((BwCalendar)ent, aces, replaceAll);
      return;
    }
    checkOpen();
    access().changeAccess(ent, aces, replaceAll);
  }

  @Override
  public void changeAccess(final BwCalendar cal,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    checkOpen();
    calendars().changeAccess(cal, aces, replaceAll);
  }

  @Override
  public void defaultAccess(final ShareableEntity ent,
                            final AceWho who) {
    if (ent instanceof BwCalendar) {
      defaultAccess((BwCalendar)ent, who);
      return;
    }
    checkOpen();
    access().defaultAccess(ent, who);
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) {
    checkOpen();
    calendars().defaultAccess(cal, who);
  }

  /* =============================================================
   *                   Alarms
   * ============================================================= */

  @Override
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime) {
    checkOpen();

    return alarms().getUnexpiredAlarms(triggerTime);
  }

  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm) {
    checkOpen();

    return alarms().getEventsByAlarm(alarm);
  }

  /* ============================================================
   *                       Calendars
   * ============================================================ */

  @Override
  public void principalChanged() {
    calendars().principalChanged();
  }

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) {
    checkOpen();

    return calendars().getSynchInfo(path, token);
  }

  @Override
  public Collection<BwCalendar> getCalendars(final BwCalendar cal,
                                             final BwIndexer indexer) {
    checkOpen();

    return calendars().getCalendars(cal, indexer);
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy,
                                 final BwIndexer indexer) {
    checkOpen();

    return calendars().resolveAlias(val, resolveSubAlias,
                                  freeBusy, indexer);
  }

  @Override
  public List<BwCalendar> findAlias(final String val) {
    checkOpen();

    return calendars().findAlias(val);
  }

  @Override
  public BwCalendar getCalendar(final String path,
                                final int desiredAccess,
                                final boolean alwaysReturnResult) {
    checkOpen();

    return calendars().getCalendar(path, desiredAccess, alwaysReturnResult);
  }

  @Override
  public GetSpecialCalendarResult getSpecialCalendar(
          final BwIndexer indexer,
          final BwPrincipal<?> owner,
          final int calType,
          final boolean create,
          final int access) {
    return calendars().getSpecialCalendar(indexer,
                                        owner, calType, create, access);
  }

  @Override
  public BwCalendar add(final BwCalendar val,
                                final String parentPath) {
    checkOpen();

    return calendars().add(val, parentPath);
  }

  @Override
  public void touchCalendar(final String path) {
    checkOpen();
    calendars().touchCalendar(path);
  }

  @Override
  public void touchCalendar(final BwCalendar col) {
    checkOpen();
    calendars().touchCalendar(col);
  }

  @Override
  public void updateCalendar(final BwCalendar val) {
    checkOpen();
    calendars().updateCalendar(val);
  }

  @Override
  public void renameCalendar(final BwCalendar val,
                             final String newName) {
    checkOpen();
    calendars().renameCalendar(val, newName);
  }

  @Override
  public void moveCalendar(final BwCalendar val,
                           final BwCalendar newParent) {
    checkOpen();
    calendars().moveCalendar(val, newParent);
  }

  @Override
  public boolean deleteCalendar(final BwCalendar val,
                                final boolean reallyDelete) {
    checkOpen();

    return calendars().deleteCalendar(val, reallyDelete);
  }

  @Override
  public boolean isEmpty(final BwCalendar val) {
    checkOpen();

    return calendars().isEmpty(val);
  }

  @Override
  public void addNewCalendars(final BwPrincipal<?> user) {
    checkOpen();

    calendars().addNewCalendars(user);
  }

  @Override
  public Collection<String> getChildCollections(final String parentPath,
                                        final int start,
                                        final int count) {
    checkOpen();

    return calendars().getChildCollections(parentPath, start, count);
  }

  @Override
  public Set<BwCalendar> getSynchCols(final String path,
                                      final String lastmod) {
    return calendars().getSynchCols(path, lastmod);
  }

  @Override
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) {
    checkOpen();

    return events().getChildEntities(parentPath, start, count);
  }

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String guid) {
    checkOpen();
    return events().getEvent(colPath, guid);
  }

  @Override
  public UpdateEventResult addEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean rollbackOnError) {
    checkOpen();
    final UpdateEventResult uer = 
            events().addEvent(ei, scheduling,
                            rollbackOnError);

    if (!forRestore && uer.addedUpdated) {
      calendars().touchCalendar(ei.getEvent().getColPath());
    }

    return uer;
  }

  @Override
  public UpdateEventResult updateEvent(final EventInfo ei) {
    checkOpen();
    final UpdateEventResult ue;

    try {
      calendars().touchCalendar(ei.getEvent().getColPath());

      ue = events().updateEvent(ei);
    } catch (final BedeworkException be) {
      rollbackTransaction();
      reindex(ei);
      throw be;
    }

    return ue;
  }

  @Override
  public DelEventResult deleteEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean reallyDelete) {
    checkOpen();
    final String colPath = ei.getEvent().getColPath();
    try {
      try {
        return events().deleteEvent(ei, scheduling, reallyDelete);
      } catch (final BedeworkException be) {
        rollbackTransaction();
        reindex(ei);

        throw be;
      }
    } finally {
      calendars().touchCalendar(colPath);
    }
  }

  @Override
  public void moveEvent(final EventInfo ei,
                        final BwCalendar from,
                        final BwCalendar to) {
    checkOpen();
    events().moveEvent(ei, from, to);
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String lastmod) {
    checkOpen();
    return events().getSynchEvents(path, lastmod);
  }

  @Override
  public CoreEventInfo getEvent(final String href) {
    checkOpen();
    return events().getEvent(href);
  }

  /* ====================================================================
   *                       Restore methods
   * ==================================================================== */

  @Override
  public void addRestoredEntity(final BwUnversionedDbentity<?> val) {
    checkOpen();
    dumpRestore().addRestoredEntity(val);
  }

  /* ====================================================================
   *                       General db methods
   * ==================================================================== */

  @Override
  public BwUnversionedDbentity<?> merge(final BwUnversionedDbentity<?> val) {
    checkOpen();
    return calendars().merge(val);
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl) {
    return dumpRestore().getObjectIterator(cl);
  }

  @Override
  public <T> Iterator<T> getPrincipalObjectIterator(final Class<T> cl) {
    return dumpRestore().getPrincipalObjectIterator(cl);
  }

  @Override
  public <T> Iterator<T> getPublicObjectIterator(final Class<T> cl) {
    return dumpRestore().getPublicObjectIterator(cl);
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl,
                                           final String colPath) {
    return dumpRestore().getObjectIterator(cl, colPath);
  }

  @Override
  public Iterator<String> getEventHrefs(final int start) {
    return dumpRestore().getEventHrefs(start);
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() {
    return events().getEventAnnotations();
  }

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) {
    return events().getEventOverrides(ev);
  }

  /* ============================================================
   *                       filter defs
   * ============================================================ */

  @Override
  public void add(final BwFilterDef val,
                  final BwPrincipal<?> owner) {
    filterDefs().add(val, owner);
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal<?> owner) {
    return filterDefs().getFilterDef(name, owner);
  }

  @Override
  public Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal<?> owner) {
    return filterDefs().getAllFilterDefs(owner);
  }

  @Override
  public void update(final BwFilterDef val) {
    filterDefs().update(val);
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal<?> owner) {
    filterDefs().deleteFilterDef(name, owner);
  }

  /* ====================================================================
   *                       user auth
   * ==================================================================== */

  @Override
  public void addAuthUser(final BwAuthUser val) {
    principalsAndPrefs().addAuthUser(val);
  }

  @Override
  public BwAuthUser getAuthUser(final String href) {
    return principalsAndPrefs().getAuthUser(href);
  }

  @Override
  public void updateAuthUser(final BwAuthUser val) {
    principalsAndPrefs().updateAuthUser(val);
  }

  @Override
  public List<BwAuthUser> getAllAuthUsers() {
    return principalsAndPrefs().getAllAuthUsers();
  }

  @Override
  public void delete(final BwAuthUser val) {
    principalsAndPrefs().delete(val);
  }

  /* =========================================================
   *                       principals + prefs
   * ========================================================= */

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    return principalsAndPrefs().getPrincipal(href);
  }

  @Override
  public void add(final BwPrincipal<?> val) {
    principalsAndPrefs().add(val);
  }

  @Override
  public void update(final BwPrincipal<?> val) {
    principalsAndPrefs().update(val);
  }

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) {
    return principalsAndPrefs().getPrincipalHrefs(start, count);
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) {
    return principalsAndPrefs().getPreferences(principalHref);
  }

  @Override
  public void add(final BwPreferences val) {
    principalsAndPrefs().add(val);
  }

  @Override
  public void update(final BwPreferences val) {
    principalsAndPrefs().update(val);
  }

  @Override
  public void delete(final BwPreferences val) {
    principalsAndPrefs().delete(val);
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */
  
  @Override
  public void removeFromAllPrefs(final BwShareableDbentity<?> val) {
    principalsAndPrefs().removeFromAllPrefs(val);
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup<?> findGroup(final String account,
                           final boolean admin) {
    return principalsAndPrefs().findGroup(account, admin);
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(final BwGroup<?> group,
                                                 final boolean admin) {
    return principalsAndPrefs().findGroupParents(group, admin);
 }
 
  @Override
  public void addGroup(final BwGroup<?> group,
                       final boolean admin) {
    principalsAndPrefs().add(group);
  }

  @Override
  public void updateGroup(final BwGroup<?> group,
                          final boolean admin) {
    principalsAndPrefs().update(group);
  }

  @Override
  public void removeGroup(final BwGroup<?> group,
                          final boolean admin) {
    principalsAndPrefs().removeGroup(group, admin);
  }

  @Override
  public void addMember(final BwGroup<?> group,
                        final BwPrincipal<?> val,
                        final boolean admin) {
    principalsAndPrefs().addMember(group, val, admin);
  }

  @Override
  public void removeMember(final BwGroup<?> group,
                           final BwPrincipal<?> val,
                           final boolean admin) {
    principalsAndPrefs().removeMember(group, val, admin);
  }

  @Override
  public Collection<BwPrincipal<?>> getMembers(final BwGroup<?> group,
                                               final boolean admin) {
    return principalsAndPrefs().getMembers(group, admin);
  }

  @Override
  public Collection<BwGroup<?>> getAllGroups(final boolean admin) {
    return principalsAndPrefs().getAllGroups(admin);
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups() {
    return principalsAndPrefs().getAdminGroups();
  }

  @Override
  public Collection<BwGroup<?>> getGroups(
          final BwPrincipal<?> val,
          final boolean admin) {
    return principalsAndPrefs().getGroups(val, admin);
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups(
          final BwPrincipal<?> val) {
    return principalsAndPrefs().getAdminGroups(val);
  }

  /* ==========================================================
   *                       calendar suites
   * ========================================================== */

  @Override
  public BwCalSuite get(final BwAdminGroup group) {
    return calSuites().get(group);
  }

  @Override
  public BwCalSuite getCalSuite(final String name) {
    return calSuites().getCalSuite(name);
  }

  @Override
  public Collection<BwCalSuite> getAllCalSuites() {
    return calSuites().getAllCalSuites();
  }

  @Override
  public void add(final BwCalSuite val) {
    calSuites().add(val);
    indexEntity(val);
  }

  @Override
  public void update(final BwCalSuite val) {
    calSuites().update(val);
    indexEntity(val);
  }

  @Override
  public void delete(final BwCalSuite val) {
    calSuites().delete(val);
  }

  /* ====================================================================
   *                       resources
   * ==================================================================== */

  @Override
  public BwResource getResource(final String href,
                                final int desiredAccess) {
    return resources().getResource(href, desiredAccess);
  }

  @Override
  public void getResourceContent(final BwResource val) {
    resources().getResourceContent(val);
  }

  @Override
  public List<BwResource> getResources(final String path,
                                       final boolean forSynch,
                                       final String token,
                                       final int count) {
    return resources().getResources(path,
                                  forSynch,
                                  token,
                                  count);
  }

  @Override
  public void add(final BwResource val) {
    resources().add(val);
  }

  @Override
  public void addContent(final BwResource r,
                         final BwResourceContent rc) {
    resources().addContent(r, rc);
  }

  @Override
  public void update(final BwResource val) {
    resources().update(val);
  }

  @Override
  public void updateContent(final BwResource r,
                            final BwResourceContent val) {
    resources().updateContent(r, val);
  }

  @Override
  public void deleteResource(final String href) {
    resources().deleteResource(href);
  }

  @Override
  public void delete(final BwResource r) {
    resources().delete(r);
  }

  @Override
  public void deleteContent(final BwResource r,
                            final BwResourceContent val) {
    resources().deleteContent(r, val);
  }
}
