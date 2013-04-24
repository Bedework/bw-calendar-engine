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
package org.bedework.calfacade.svc.prefs;

import org.bedework.calfacade.base.DumpEntity;
import org.bedework.calfacade.util.CalFacadeUtil;

import java.io.Serializable;
import java.util.Collection;
import java.util.TreeSet;

/** Represent a set of user preferences from a Collection - e.g. preferred category.
 *
 *  @author Mike Douglass douglm - rpi.edu
 *  @version 1.0
 *
 * @param <T> Type of element in the collection
 */
public class CollectionPref<T> extends DumpEntity implements Serializable {
  /** If true automatically add preference to the preferred list
   */
  protected boolean autoAdd = true;

  /** Users preferred collection.
   */
  protected Collection<T> preferred;

  /** If true we automatically add preference to the preferred list
   *
   * @param val
   */
  public void setAutoAdd(boolean val) {
    autoAdd = val;
  }

  /**
   * @return boolean true if we automatically add preference to the preferred list
   */
  public boolean getAutoAdd() {
    return autoAdd;
  }

  /**
   * @param val
   */
  public void setPreferred(Collection<T> val) {
    preferred = val;
  }

  /**
   * @return Collection of preferred entries
   */
  public Collection<T> getPreferred() {
    return preferred;
  }

  /** Add the element to the preferred collection. Return true if it was
   * added, false if it was in the list
   *
   * @param val        Element to add
   * @return boolean   true if added
   */
  public boolean add(T val) {
    Collection<T> c = getPreferred();
    if (c == null) {
      c = new TreeSet<T>();
      setPreferred(c);
    }

    if (c.contains(val)) {
      return false;
    }

    c.add(val);
    return true;
  }

  /** Remove the element from the preferred collection. Return true if it was
   * removed, false if it was not in the list
   *
   * @param val        Element to remove
   * @return boolean   true if removed
   */
  public boolean remove(T val) {
    Collection<T> c = getPreferred();

    if ((c == null) || (!c.contains(val))) {
      return false;
    }

    return c.remove(val);
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @SuppressWarnings("unchecked")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!obj.getClass().equals(getClass())) {
      return false;
    }

    CollectionPref<T> that = (CollectionPref<T>)obj;

    if (that.getAutoAdd() != getAutoAdd()) {
      return false;
    }

    return CalFacadeUtil.eqObjval(getPreferred(), that.getPreferred());
  }

  public int hashCode() {
    int h = 1;
    if (getAutoAdd()) {
      h = 2;
    }

    Collection<T> c = getPreferred();
    if (c != null) {
      h *= c.hashCode();
    }

    return h;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();

    sb.append("CollectionPref{");
    sb.append("autoAdd=");
    sb.append(getAutoAdd());

    Collection<T> c = getPreferred();

    if (c != null) {
      sb.append("preferred={");
      for (T el: c) {
        sb.append(" ");
        sb.append(el);
      }
    }
    sb.append("}");

    return sb.toString();
  }

  public Object clone() {
    CollectionPref<T> cp = new CollectionPref<T>();

    cp.setAutoAdd(getAutoAdd());

    Collection<T> c = getPreferred();

    if (c != null) {
      TreeSet<T> nc = new TreeSet<T>();
      for (T el: c) {
        nc.add(el);
      }

      cp.setPreferred(nc);
    }

    return cp;
  }
}
