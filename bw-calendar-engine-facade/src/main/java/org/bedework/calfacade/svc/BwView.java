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

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.util.misc.ToString;

import java.util.ArrayList;
import java.util.List;

/** A view in Bedework. This is a named collection of collections used to
 * provide different views of the events.
 *
 * @author Mike Douglass douglm  bedework.edu
 */
@Dump(elementName="view", keyFields={"owner", "name"})
public class BwView extends BwDbentity<BwView> {
  /** A printable name for the view
   */
  private String name;

  /** The collection paths
   */
  private List<String> collectionPaths;

  // Non db for the moment
  private boolean conjunction;

  /** Constructor
   *
   */
  public BwView() {
    super();
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /** Set the name
   *
   * @param val    String name
   */
  public void setName(final String val) {
    name = val;
  }

  /** Get the name
   *
   * @return String   name
   */
  public String getName() {
    return name;
  }

  /** List of collection paths for this view
   *
   * @param val        List of string paths
   */
  public void setCollectionPaths(final List<String> val) {
    collectionPaths = val;
  }

  /** Get the collection paths for this view
   *
   * @return List    of String
   */
  @Dump(collectionElementName = "path")
  public List<String> getCollectionPaths() {
    return collectionPaths;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /** Add a collection path
   *
   * @param val
   */
  public void addCollectionPath(final String val) {
    List<String> c = getCollectionPaths();
    if (c == null) {
      c = new ArrayList<String>();
      setCollectionPaths(c);
    }
    c.add(val);
  }

  /** Remove a collection path
   *
   * @param val
   */
  public void removeCollectionPath(final String val) {
    List<String> c = getCollectionPaths();
    if (c != null) {
      c.remove(val);
    }
  }

  /* ====================================================================
   *                   Non db methods
   * ==================================================================== */

  /** true if we AND the expressions for each path
   *
   * @param val
   */
  public void setConjunction(final boolean val) {
    conjunction = val;
  }

  /**
   * @return true if we AND the expressions for each path
   */
  @NoDump
  public boolean getConjunction() {
    return conjunction;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  /** Comapre this view and an object
   *
   * @param  that    object to compare.
   * @return int -1, 0, 1
   */
  @Override
  public int compareTo(final BwView that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
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

    ts.append("name", getName());
    ts.append("collections", getCollectionPaths());

    return ts.toString();
  }
}
