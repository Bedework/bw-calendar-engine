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
package org.bedework.calfacade.base;

import org.bedework.access.Ace;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

/** Base class for shareable database entities that live within a container,
 * i.e. a calendar
 *
 * @author Mike Douglass
 * @version 1.0
 *
 * @param <T>
 */
public abstract class BwShareableContainedDbentity<T> extends BwShareableDbentity<T> {
  /* Path to the parent collection */
  private String colPath;

  /** No-arg constructor
   *
   */
  public BwShareableContainedDbentity() {
    super();
  }

  /** Set the object's collection path
   *
   * @param val    String path
   */
  @IcalProperty(pindex = PropertyInfoIndex.COLLECTION,
                required = true,
                eventProperty = true,
                todoProperty = true,
                journalProperty = true,
                freeBusyProperty = true,
                vavailabilityProperty = true)
  public void setColPath(final String val) {
    colPath = val;
  }

  /** Get the object's collection path
   *
   * @return String   path
   */
  public String getColPath() {
    return colPath;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  protected void setColPath(final BwPrincipal principal,
                            final String userRoot,
                            final String colPathElement,
                            final String dir,
                            final String namePart) {
    if (getPublick()) {
      setColPath(Util.buildPath(true,
                                "/public",
                                "/",
                                colPathElement,
                                "/",
                                dir,
                                "/",
                                namePart));
      return;
    }

    final String homeDir;

    if (principal.getKind() == Ace.whoTypeUser) {
      homeDir = userRoot;
    } else {
      homeDir = Util.pathElement(1, principal.getPrincipalRef());
    }

    setColPath(Util.buildPath(true,
                              "/",
                              homeDir,
                              "/",
                              principal.getAccount(),
                              "/",
                              colPathElement,
                              "/",
                              dir,
                              "/",
                              namePart));
  }

  /** Add our stuff to the ToString object
   *
   * @param ts    ToString for result
   */
  @Override
  protected void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);
    ts.append("collection", getColPath());
  }

  /** Copy this objects fields into the parameter. Don't clone many of the
   * referenced objects
   *
   * @param val
   */
  public void shallowCopyTo(final BwShareableContainedDbentity<?> val) {
    super.shallowCopyTo(val);
    val.setColPath(getColPath());
  }

  /** Copy this objects fields into the parameter
   *
   * @param val
   */
  public void copyTo(final BwShareableContainedDbentity<?> val) {
    super.copyTo(val);
    val.setColPath(getColPath());
  }
}
