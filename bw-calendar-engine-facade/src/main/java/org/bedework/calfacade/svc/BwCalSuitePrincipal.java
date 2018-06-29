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

package org.bedework.calfacade.svc;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

/** NOTE - this is only used when indexing for the moment
 *
 * This object represents a calendar suite in bedework. The calendar suites all
 * share common data but have their own set of preferences associated with a
 * run-as user.
 *
 * <p>All public views of the calendar system are based on the root calendar. At
 * the moment this generally points at the root of the calendar system but in a
 * hosted environment it would point to the root for the hosted organization.
 *
 *  @author Mike Douglass douglm@bedework.edu
 *  @version 1.0
 */
@Dump(elementName="cal-suite", keyFields={"name"})
public class BwCalSuitePrincipal extends BwPrincipal {
  /** The admin group which 'owns' this calendar suite
   */
  private BwAdminGroup group;

  /** The root collection
   */
  private String rootCollectionPath;

  /** The submissions root
   */
  private String submissionsRootPath;

  private String groupHref;

  /** Constructor
   *
   */
  public BwCalSuitePrincipal() {
    super();
  }

  @Override
  public int getKind() {
    return -1;
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /** Set the owning group
   *
   * @param val    BwAdminGroup group
   */
  public void setGroup(final BwAdminGroup val) {
    group = val;
  }

  /** Get the owning group
   *
   * @return BwAdminGroup   group
   */
  public BwAdminGroup getGroup() {
    return group;
  }

  /** Set the root collection path
   *
   * @param val    String rootCalendar path
   */
  public void setRootCollectionPath(final String val) {
    rootCollectionPath = val;
  }

  /** Get the root collection path
   *
   * @return String   rootCollection path
   */
  public String getRootCollectionPath() {
    return rootCollectionPath;
  }

  /** Set the submissions root path
   *
   * @param val    String submissions root path
   */
  public void setSubmissionsRootPath(final String val) {
    submissionsRootPath = val;
  }

  /** Get the submissions root path
   *
   * @return String   submissions root path
   */
  public String getSubmissionsRootPath() {
    return submissionsRootPath;
  }

  /* ====================================================================
   *                   Non-db methods
   * ==================================================================== */

  /** Set the owning group
   *
   * @param val    BwAdminGroup group
   */
  public void setGroupHref(final String val) {
    groupHref = val;
  }

  /** Get the owning group
   *
   * @return BwAdminGroup   group
   */
  public String getGroupHref() {
    return groupHref;
  }

  /** Set from Calsuite
   *
   * @param val    BwCalsuite
   */
  @NoDump
  public static BwCalSuitePrincipal from(final BwCalSuite val) {
    final BwCalSuitePrincipal csp = new BwCalSuitePrincipal();

    csp.setPrincipalRef(Util.buildPath(true,
                                   BwPrincipal.calsuitePrincipalRoot,
                                   "/", val.getName()));
    csp.setAccount(val.getName());
    csp.setGroup(val.getGroup());
    csp.setRootCollectionPath(val.getRootCollectionPath());
    csp.setSubmissionsRootPath(val.getSubmissionsRootPath());

    return csp;
  }

  /** Get calsuite from this
   *
   * @return BwCalSuite
   */
  @NoDump
  public BwCalSuite getFrom() {
    final BwCalSuite cs = new BwCalSuite();

    cs.setName(getAccount());
    cs.setGroup(getGroup());
    cs.setRootCollectionPath(getRootCollectionPath());
    cs.setSubmissionsRootPath(getSubmissionsRootPath());

    return cs;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param ts    StringBuilder for result
   */
   @Override
   protected void toStringSegment(final ToString ts) {
     super.toStringSegment(ts);

    ts.append("group", getGroup());
    ts.append("rootCollection", getRootCollectionPath());
    ts.append("submissionsRoot", getSubmissionsRootPath());
   }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int hashCode() {
    return getPrincipalRef().hashCode();
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  @Override
  public Object clone() {
    final BwCalSuitePrincipal cs = new BwCalSuitePrincipal();

    copyTo(cs);
    cs.setGroup((BwAdminGroup)getGroup().clone());
    cs.setRootCollectionPath(getRootCollectionPath());
    cs.setSubmissionsRootPath(getSubmissionsRootPath());

    return cs;
  }
}
