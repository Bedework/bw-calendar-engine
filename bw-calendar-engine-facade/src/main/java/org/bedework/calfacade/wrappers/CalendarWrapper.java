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
package org.bedework.calfacade.wrappers;

import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.CollectionInfo;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.util.misc.ToString;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** An object to wrap a calendar entity in Bedework. This allows us to limit
 * access to methods and attach thread or session information to the entity.
 *
 * <p>We should block access to methods if the caller does not have appropriate
 * access.
 *
 * <p>XXX This should be generated by annotations
 *
 * @author Mike Douglass douglm @ rpi.edu
 * @version 1.0
 */
public class CalendarWrapper extends BwCalendar
        implements EntityWrapper<BwCalendar> {
  private final AccessUtilI accessUtil;

  private BwCalendar entity;

  private BwCalendar aliasTarget;

  private BwCalendar aliasOrigin;

  /* Current access for the user.
   */
  private CurrentAccess currentAccess;

  private Map<Integer, CurrentAccess> caMap = new HashMap<>(20);

  private int lastDesiredAccess;

  // ui support
  private String virtualPath;

  // ui support
  private boolean open;

  /* True if we cannot reach the target - once per session and not saved. */
  private boolean disabled;

  private Collection<BwCalendar> children;

  /** Constructor
   *
   * @param entity to be wrapped
   * @param accessUtil for access checks
   */
  public CalendarWrapper(final BwCalendar entity, final AccessUtilI accessUtil) {
    this.entity = entity;
    this.accessUtil = accessUtil;
  }

  @Override
  public void putEntity(final BwCalendar val) {
    entity = val;
  }

  @Override
  public BwCalendar fetchEntity() {
    return entity;
  }

  /* ====================================================================
   *                   BwDbentity methods
   * ==================================================================== */

  @Override
  public void setId(final int val) {
    entity.setId(val);
  }

  @Override
  public int getId() {
    return entity.getId();
  }

  @Override
  public void setSeq(final int val) {
    entity.setSeq(val);
  }

  @Override
  public int getSeq() {
    return entity.getSeq();
  }

  /* ====================================================================
   *                   BwOwnedDbentity methods
   * ==================================================================== */

  /** Set the owner
   *
   * @param val     String owner of the entity
   */
  @Override
  public void setOwnerHref(final String val) {
    entity.setOwnerHref(val);
  }

  /**
   *
   * @return String    owner of the entity
   */
  @Override
  public String getOwnerHref() {
    return entity.getOwnerHref();
  }

  /**
   * @param val public flag
   */
  @Override
  public void setPublick(final Boolean val) {
    entity.setPublick(val);
  }

  /**
   * @return boolean true for public
   */
  @Override
  public Boolean getPublick() {
    return entity.getPublick();
  }

  /* ====================================================================
   *                   BwShareableDbentity methods
   * ==================================================================== */

  @Override
  public void setCreatorHref(final String val) {
    entity.setCreatorHref(val);
  }

  @Override
  public String getCreatorHref() {
    return entity.getCreatorHref();
  }

  @Override
  public void setAccess(final String val) {
    entity.setAccess(val);
  }

  @Override
  public String getAccess() {
    return entity.getAccess();
  }

  /* ====================================================================
   *                   BwShareableContainedDbentity methods
   * ==================================================================== */

  /** Set the object's collection path
   *
   * @param val    String path
   */
  @Override
  public void setColPath(final String val) {
    entity.setColPath(val);
  }

  /** Get the object's collection path
   *
   * @return String   path
   */
  @Override
  public String getColPath() {
    return entity.getColPath();
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  @Override
  public void setName(final String val) {
    // Immutable - use the rename api method.
    throw new RuntimeException("org.bedework.noaccess");
  }

  @Override
  public String getName() {
    return entity.getName();
  }

  @Override
  public void setPath(final String val) {
    entity.setPath(val);
  }

  @Override
  public String getPath() {
    // Can come from constructor
    if (entity != null) {
      return entity.getPath();
    }

    return null;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#setSummary(java.lang.String)
   */
  @Override
  public void setSummary(final String val) {
    entity.setSummary(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#getSummary()
   */
  @Override
  public String getSummary() {
    return entity.getSummary();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#setDescription(java.lang.String)
   */
  @Override
  public void setDescription(final String val) {
    entity.setDescription(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#getDescription()
   */
  @Override
  public String getDescription() {
    return entity.getDescription();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#setMailListId(java.lang.String)
   */
  @Override
  public void setMailListId(final String val) {
    entity.setMailListId(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#getMailListId()
   */
  @Override
  public String getMailListId() {
    return entity.getMailListId();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#setCalType(int)
   */
  @Override
  public void setCalType(final int val) {
    // Immutable - use the rename api method.
    throw new RuntimeException("org.bedework.noaccess");
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#getCalType()
   */
  @Override
  public int getCalType() {
    /*
    BwCalendar entColl = entity.getAliasedEntity();

    if (entColl == null) {
      return entity.getCalType();
    }

    return entColl.getCalType();
    */
    return entity.getCalType();
  }

  /**
   * @param val lastmod info
   */
  @Override
  public void setLastmod(final BwCollectionLastmod val) {
    // Can come from constructor
    if (entity != null) {
      entity.setLastmod(val);
    }
  }

  /**
   * @return BwLastMod lastmod
   */
  @Override
  public BwCollectionLastmod getLastmod() {
    return entity.getLastmod();
  }

  @Override
  public void setAliasUri(final String val) {
    if (val != null) {
      final boolean internal = val.startsWith(CalFacadeDefs.bwUriPrefix);
      if (internal) {
        entity.setCalType(BwCalendar.calTypeAlias);
      } else {
        entity.setCalType(BwCalendar.calTypeExtSub);
      }
    }
    entity.setAliasUri(val);
  }

  @Override
  public String getAliasUri() {
    return entity.getAliasUri();
  }

  @Override
  public void setPwNeedsEncrypt(final boolean val) {
    entity.setPwNeedsEncrypt(val);
  }

  /**
   *
   * @return boolean  true if the password needs encrypting
   */
  @Override
  public boolean getPwNeedsEncrypt() {
    return entity.getPwNeedsEncrypt();
  }

  @Override
  public void setFilterExpr(final String val) {
    entity.setFilterExpr(val);
  }

  @Override
  public String getFilterExpr() {
    return entity.getFilterExpr();
  }

  @Override
  public void setRefreshRate(final int val) {
    entity.setRefreshRate(val);
  }

  @Override
  public int getRefreshRate() {
    return entity.getRefreshRate();
  }

  @Override
  public void setLastRefresh(final String val) {
    entity.setLastRefresh(val);
  }

  @Override
  public String getLastRefresh() {
    return entity.getLastRefresh();
  }

  @Override
  public void setLastEtag(final String val) {
    entity.setLastEtag(val);
  }

  @Override
  public String getLastEtag() {
    return entity.getLastEtag();
  }

  @Override
  public void setRemoteId(final String val) {
    entity.setRemoteId(val);
  }

  @Override
  public String getRemoteId() {
    return entity.getRemoteId();
  }

  @Override
  public void setRemotePw(final String val) {
    entity.setRemotePw(val);
  }

  @Override
  public String getRemotePw() {
    return entity.getRemotePw();
  }

  @Override
  public void setDisplay(final boolean val) {
    entity.setDisplay(val);
  }

  @Override
  public boolean getDisplay() {
    return entity.getDisplay();
  }

  @Override
  public void setAffectsFreeBusy(final boolean val) {
    entity.setAffectsFreeBusy(val);
  }

  @Override
  public boolean getAffectsFreeBusy() {
    return entity.getAffectsFreeBusy();
  }

  @Override
  public void setUnremoveable(final boolean val) {
    entity.setUnremoveable(val);
  }

  @Override
  public boolean getUnremoveable() {
    return entity.getUnremoveable();
  }

  /* ====================================================================
   *               CategorisedEntity interface methods
   * ==================================================================== */

  @Override
  public void setCategories(final Set<BwCategory> val) {
    entity.setCategories(val);
  }

  @Override
  public Set<BwCategory> getCategories() {
    return entity.getCategories();
  }

  @Override
  public void setCategoryHrefs(final Set<String> val) {
    entity.setCategoryHrefs(val);
  }

  @Override
  public Set<String> getCategoryHrefs() {
    return entity.getCategoryHrefs();
  }

  /* ====================================================================
   *                   Property methods
   * ==================================================================== */

  @Override
  public void setProperties(final Set<BwProperty> val) {
    entity.setProperties(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#getProperties()
   */
  @Override
  public Set<BwProperty> getProperties() {
    return entity.getProperties();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwCalendar#getCalendarCollection()
   */
  @Override
  public boolean getCalendarCollection() {
    return entity.getCalendarCollection();
  }

  /* ====================================================================
   *                   Wrapper object methods
   * ==================================================================== */

  /**
   */
  public void clearCurrentAccess() {
    caMap.clear();
    currentAccess = null;
  }

  @Override
  @JsonIgnore
  public CurrentAccess getCurrentAccess() {
    if (currentAccess != null) {
      return currentAccess;
    }

    return getCurrentAccess(PrivilegeDefs.privAny);
  }

  /**
   * @param desiredAccess as key to access objects
   * @return currentAccess 
   */
  public CurrentAccess getCurrentAccess(final int desiredAccess) {
    if ((desiredAccess == lastDesiredAccess) &&
        (currentAccess != null)) {
      return currentAccess;
    }

    currentAccess = caMap.get(desiredAccess);
    lastDesiredAccess = desiredAccess;

    return currentAccess;
  }

  @Override
  public void setCurrentAccess(final CurrentAccess val) {
    entity.setCurrentAccess(val);
  }
  
  /**
   * @param ca CurrentAccess object
   * @param desiredAccess associated access
   */
  public void setCurrentAccess(final CurrentAccess ca, final int desiredAccess) {
    currentAccess = ca;
    lastDesiredAccess = desiredAccess;
    caMap.put(desiredAccess , ca);
  }

  /**
   * @return set of accesses we already evaluated. Used when re-evaluating
   */
  public Set<Integer> evaluatedAccesses() {
    return caMap.keySet();
  }

  /**
   * @return int last desiredAccess
   */
  @JsonIgnore
  public int getLastDesiredAccess() {
    return lastDesiredAccess;
  }

  @Override
  public void setVirtualPath(final String val) {
    virtualPath = val;
  }

  @Override
  @NoDump
  public String getVirtualPath() {
    return virtualPath;
  }

  @Override
  public void setOpen(final boolean val) {
    open = val;
  }

  @Override
  public boolean getOpen() {
    return open;
  }

  @Override
  public void setDisabled(final boolean val) {
    disabled = val;
  }

  @Override
  public boolean getDisabled() {
    return disabled;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  @Override
  @JsonIgnore
  public BwCalendar getAliasedEntity() {
    final BwCalendar ent = entity.getAliasedEntity();
    if (ent == entity) {
      // Not aliased - return this wrapper
      return this;
    }

    return ent;
  }

  @Override
  public boolean getAlias() {
    return entity.getAlias();
  }

  @Override
  public boolean getInternalAlias() {
    return entity.getCalType() == calTypeAlias;
  }

  @Override
  public boolean getExternalSub() {
    return entity.getCalType() == calTypeExtSub;
  }

  @Override
  public String getInternalAliasPath() {
    return entity.getInternalAliasPath();
  }

  @Override
  public void setChildren(final Collection<BwCalendar> val) {
    children = val;
  }

  @Override
  public Collection<BwCalendar> getChildren() {
    return children;
  }

  @Override
  public CollectionInfo getCollectionInfo() {
    return getCollectionInfo(entity.getCalType());
  }

  @Override
  public String getEncodedPath() {
    return entity.getEncodedPath();
  }

  @Override
  public void updateLastmod(final Timestamp val) {
    entity.updateLastmod(val);
  }

  /* ====================================================================
   *                   db entity methods
   * ==================================================================== */

  /** Set the href
   *
   * @param val    String href
   */
  @Override
  public void setHref(final String val) {
    entity.setHref(val);
  }

  @Override
  public String getHref() {
    return entity.getHref();
  }

  /* ====================================================================
   *                   Non-db methods
   * ==================================================================== */

  @Override
  public void setAliasTarget(final BwCalendar val) {
    entity.setAliasTarget(val);
    aliasTarget = null;  // Force refetch
  }

  @Override
  public BwCalendar getAliasTarget() {
    if (aliasTarget == null) {
      aliasTarget = entity.getAliasTarget();
      if (aliasTarget == null) {
        return null;
      }

      if (!(aliasTarget instanceof CalendarWrapper)) {
        aliasTarget = new CalendarWrapper(aliasTarget, accessUtil);
      }
    }

    return aliasTarget;
  }

  @Override
  public void setAliasOrigin(final BwCalendar val) {
    entity.setAliasOrigin(val);
    aliasOrigin = null;  // Force refetch
  }

  @Override
  public BwCalendar getAliasOrigin() {
    if (aliasOrigin == null) {
      aliasOrigin = entity.getAliasOrigin();
      if (aliasOrigin == null) {
        return null;
      }

      if (!(aliasOrigin instanceof CalendarWrapper)) {
        aliasOrigin = new CalendarWrapper(aliasOrigin, accessUtil);
      }
    }

    return aliasOrigin;
  }

  @Override
  public void tombstone() {
    entity.tombstone();
  }

  @Override
  public boolean getTombstoned() {
    return entity.getTombstoned();
  }

  public void dump(final File f) throws CalFacadeException {
    entity.dump(f);
  }
  
  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.BwDbentity#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo(final BwCalendar o) {
    return entity.compareTo(o);
  }

  @Override
  public int hashCode() {
    return entity.hashCode();
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    ts.append(entity);

    ts.append("currentAccess", getCurrentAccess());
    ts.append("virtualPath", getVirtualPath());

    return ts.toString();
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Object clone() {
    final CalendarWrapper cw = new CalendarWrapper((BwCalendar)entity.clone(),
                                                   accessUtil);

    cw.currentAccess = currentAccess;
    cw.caMap = caMap;
    cw.lastDesiredAccess = lastDesiredAccess;
    cw.open = open;

    return cw;
  }

  @Override
  public BwCalendar cloneWrapper() {
    final CalendarWrapper cw = new CalendarWrapper(entity,
                                                   accessUtil);

    cw.currentAccess = currentAccess;
    cw.caMap = caMap;
    cw.lastDesiredAccess = lastDesiredAccess;
    cw.open = open;
    if (children != null) {
      cw.children = new ArrayList<>(children);
    }

    return cw;
  }

  @Override
  public BwCalendar shallowClone() {
    final CalendarWrapper cw = new CalendarWrapper(entity.shallowClone(),
                                                   accessUtil);

    cw.currentAccess = currentAccess;
    cw.caMap = caMap;
    cw.lastDesiredAccess = lastDesiredAccess;
    cw.open = open;

    return cw;
  }
}
