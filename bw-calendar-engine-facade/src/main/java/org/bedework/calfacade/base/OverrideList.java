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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

/** An override collection which is a List of T.
 *
 * @author Mike Douglass douglm - rpi.edu
 * @param <T>
 */
public abstract class OverrideList <T> extends OverrideCollection <T,
                                                                   List<T>>
        implements List<T> {
  /**
   * @param fieldIndex
   * @param ann
   * @param cf
   */
  public OverrideList(BwEvent.ProxiedFieldIndex fieldIndex,
                      BwEventAnnotation ann,
                      ChangeFlag cf) {
    super(fieldIndex, ann, cf);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.OverrideCollection#getEmptyOverrideCollection()
   */
  public List<T> getEmptyOverrideCollection() {
    return new ArrayList<T>();
  }

  /* ************************************************************************
   *            Collection methods
   */

  /* (non-Javadoc)
   * @see java.util.Collection#addAll(java.util.Collection)
   */
  public boolean addAll(Collection<? extends T> c){
    return super.addAll(c);
  }

  /* ************************************************************************
   *            List methods
   */

  /* (non-Javadoc)
   * @see java.util.List#add(int, java.lang.Object)
   */
  public void add(int index, T element) {
    List<T> cc = getModCollection();

    setOverrideEmptyFlag(false);
    cf.setChangeFlag(true);
    cc.add(index, element);
  }

  /* (non-Javadoc)
   * @see java.util.List#addAll(int, java.util.Collection)
   */
  public boolean addAll(int index, Collection<? extends T> c) {
    List<T> cc = getModCollection();

    setOverrideEmptyFlag(false);
    cf.setChangeFlag(true);
    return cc.addAll(index, c);
  }

  /* (non-Javadoc)
   * @see java.util.List#get(int)
   */
  public T get(int index) {
    List<T> c = getCollection();
    if (c == null) {
      return null;
    }

    return c.get(index);
  }

  /* (non-Javadoc)
   * @see java.util.List#indexOf(java.lang.Object)
   */
  public int indexOf(Object o) {
    List<T> c = getCollection();
    if (c == null) {
      return -1;
    }

    return c.indexOf(o);
  }

  /* (non-Javadoc)
   * @see java.util.List#lastIndexOf(java.lang.Object)
   */
  public int lastIndexOf(Object o) {
    List<T> c = getCollection();
    if (c == null) {
      return -1;
    }

    return c.lastIndexOf(o);
  }

  /* (non-Javadoc)
   * @see java.util.List#listIterator()
   */
  public ListIterator<T> listIterator() {
    List<T> c = getCollection();
    if (c == null) {
      return null;
    }

    return c.listIterator();
  }

  /* (non-Javadoc)
   * @see java.util.List#listIterator(int)
   */
  public ListIterator<T> listIterator(int index) {
    List<T> c = getCollection();
    if (c == null) {
      return null;
    }

    return c.listIterator(index);
  }

  /* (non-Javadoc)
   * @see java.util.List#remove(int)
   */
  public T remove(int index) {
    List<T> c = getCollection();
    if (c == null) {
      return null;
    }

    cf.setChangeFlag(true);
    return c.remove(index);
  }

  /* (non-Javadoc)
   * @see java.util.List#set(int, java.lang.Object)
   */
  public T set(int index, T element) {
    List<T> cc = getModCollection();

    setOverrideEmptyFlag(false);
    cf.setChangeFlag(true);
    return cc.set(index, element);
  }


  /* (non-Javadoc)
   * @see java.util.List#subList(int, int)
   */
  public List<T> subList(int fromIndex, int toIndex) {
    List<T> c = getCollection();
    if (c == null) {
      return null;
    }

    return c.subList(fromIndex, toIndex);
  }
}
