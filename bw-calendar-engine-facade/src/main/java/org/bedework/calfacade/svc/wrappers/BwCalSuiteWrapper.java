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
package org.bedework.calfacade.svc.wrappers;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.wrappers.EntityWrapper;
import org.bedework.util.misc.ToString;

import org.bedework.access.Acl.CurrentAccess;

/** This object represents a calendar suite in bedework. The calendar suites all
 * share common data but have their own set of preferences associated with a
 * run-as user.
 *
 *  @author Mike Douglass douglm@bedework.edu
 *  @version 1.0
 */
public class BwCalSuiteWrapper extends BwCalSuite
    implements EntityWrapper<BwCalSuite> {
  private BwCalSuite entity;

  /* CurrentAccess to this object by current user
   */
  private CurrentAccess currentAccess;

  /* Path to the calednar home of the owning principal
   */
  private String resourcesHome;

  /** Constructor
   *
   */
  public BwCalSuiteWrapper() {
    super();
  }

  /** Constructor
   *
   * @param entity
   */
  public BwCalSuiteWrapper(final BwCalSuite entity) {
    super();
    this.entity = entity;
  }

  /** Constructor
   *
   * @param entity
   * @param currentAccess
   */
  public BwCalSuiteWrapper(final BwCalSuite entity, final CurrentAccess currentAccess) {
    super();
    this.entity = entity;
    this.currentAccess = currentAccess;
  }

  /* ====================================================================
   *                   EntityWrapper methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.wrappers.EntityWrapper#putEntity(org.bedework.calfacade.base.BwDbentity)
   */
  @Override
  public void putEntity(final BwCalSuite val) {
    entity = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.wrappers.EntityWrapper#fetchEntity()
   */
  @Override
  public BwCalSuite fetchEntity() {
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
   * @param val
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
   *                   Bean methods
   * ==================================================================== */

  @Override
  public void setName(final String val) {
    entity.setName(val);
  }

  @Override
  public String getName() {
    return entity.getName();
  }

  @Override
  public String getContext() {
    return entity.getContext();
  }

  @Override
  public void setContext(final String context) {
    entity.setContext(context);
  }

  @Override
  public boolean isDefaultContext() {
    return entity.isDefaultContext();
  }

  @Override
  public void setDefaultContext(final boolean defaultContext) {
    entity.setDefaultContext(defaultContext);
  }

  @Override
  public void setGroup(final BwAdminGroup val) {
    entity.setGroup(val);
  }

  @Override
  public BwAdminGroup getGroup() {
    return entity.getGroup();
  }

  @Override
  public void setRootCollectionPath(final String val) {
    entity.setRootCollectionPath(val);
  }

  @Override
  public String getRootCollectionPath() {
    return entity.getRootCollectionPath();
  }

  @Override
  public void setSubmissionsRootPath(final String val) {
    entity.setSubmissionsRootPath(val);
  }

  @Override
  public String getSubmissionsRootPath() {
    return entity.getSubmissionsRootPath();
  }

  /* ====================================================================
   *                   Non-db methods
   * ==================================================================== */

  /** Set the root calendar
   *
   * @param val    BwCalendar rootCalendar
   */
  @Override
  public void setRootCollection(final BwCalendar val) {
    entity.setRootCollection(val);
  }

  /** Get the root calendar
   *
   * @return BwCalendar   rootCollection
   */
  @Override
  public BwCalendar getRootCollection() {
    return entity.getRootCollection();
  }

  /** Set the submissions root
  *
  * @param val    String submissions root
  */
  @Override
  public void setSubmissionsRoot(final BwCalendar val) {
    entity.setSubmissionsRoot(val);
  }

  /** Get the submissions root
  *
  * @return String   submissions root
  */
  @Override
  public BwCalendar getSubmissionsRoot() {
    return entity.getSubmissionsRoot();
  }

  /* ====================================================================
   *                   Wrapper object methods
   * ==================================================================== */

  /** Only call for cloned object
   *
   * @param val CurrentAccess
   */
  public void setCurrentAccess(final CurrentAccess val) {
    currentAccess = val;
  }

  /**
   * @return CurrentAccess
   */
  public CurrentAccess getCurrentAccess() {
    return currentAccess;
  }

  /** Path to the calendar home of the owning principal
   *
   * @param val path
   */
  public void setResourcesHome(final String val) {
    resourcesHome = val;
  }

  /** Path to the calendar home of the owning principal
   *
   * @return path
   */
  public String getResourcesHome() {
    return resourcesHome;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final BwCalSuite that) {
    if (that == this) {
      return 0;
    }

    return getName().compareTo(that.getName());
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
