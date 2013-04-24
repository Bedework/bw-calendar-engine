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
package org.bedework.calfacade;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.util.CalFacadeUtil;

import edu.rpi.sss.util.ToString;
import edu.rpi.sss.util.Util;

import java.util.Collection;

/** An arbitrary property value in bedework. This may be used to support, for
 * example, x-properties. This will probably be subclassed to provide further
 * support.
 *
 *  @version 1.0
 */
@Dump(elementName="property", keyFields={"name", "value"})
@NoDump({"byteSize"})
public class BwProperty extends BwDbentity<BwProperty> {
  private String name;

  private String value;

  /** Constructor
   */
  public BwProperty() {
    super();
  }

  /** Create a string by specifying all its fields
   *
   * @param name        String property name
   * @param value       String value
   */
  public BwProperty(final String name,
                    final String value) {
    super();
    this.name = name;
    this.value = value;
  }

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

  /** Set the value
   *
   * @param val    String value
   */
  public void setValue(final String val) {
    value = val;
  }

  /** Get the value
   *
   *  @return String   value
   */
  public String getValue() {
    return value;
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /** Search the collection for a string that matches the given name.
   *
   * <p>We return the first one we found.
   *
   * @param name
   * @param c
   * @return BwProperty or null if no strings.
   */
  public static BwProperty findName(final String name, final Collection<BwProperty> c) {
    if (c == null) {
      return null;
    }

    if (name == null) {
      return null;
    }

    for (BwProperty p: c) {
      String pname = p.getName();

      if (CalFacadeUtil.cmpObjval(name, pname) == 0) {
        return p;
      }
    }

    return null;
  }

  /** Figure out what's different and update it. This should reduce the number
   * of spurious changes to the db.
   *
   * @param from
   * @return true if we changed something.
   */
  public boolean update(final BwProperty from) {
    if (CalFacadeUtil.cmpObjval(getName(), from.getName()) != 0) {
      return false;
    }

    if (CalFacadeUtil.cmpObjval(getValue(), from.getValue()) != 0) {
      setValue(from.getValue());
      return true;
    }

    return false;
  }

  /** Check this is properly trimmed
   *
   * @return boolean true if changed
   */
  public boolean checkNulls() {
    boolean changed = false;

    String str = Util.checkNull(getName());
    if (CalFacadeUtil.compareStrings(str, getName()) != 0) {
      setName(str);
      changed = true;
    }

    str = Util.checkNull(getValue());
    if (CalFacadeUtil.compareStrings(str, getValue()) != 0) {
      setValue(str);
      changed = true;
    }

    return changed;
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final BwProperty that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    int res = CalFacadeUtil.cmpObjval(getName(), that.getName());

    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    int hc = 7;

    if (getName() != null) {
      hc *= getName().hashCode();
    }

    if (getValue() != null) {
      hc *= getValue().hashCode();
    }

    return hc;
  }

  @Override
  protected void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);
    ts.append("name", getName());
    ts.append("value", getValue());
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  @Override
  public Object clone() {
    return new BwProperty(getName(), getValue());
  }
}
