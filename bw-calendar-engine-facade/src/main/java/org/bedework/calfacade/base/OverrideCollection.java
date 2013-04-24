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

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TreeSet;

/** A class to allow operations on overridden Collection values which leave the
 * master collection untouched. The problem is to allow an event to be annotated
 * in some manner by a user with no access. We also use this when overriding a
 * recurring instance.
 *
 * <p>Some cases are relatively easy to handle, if we have a master collection
 * with entries A, B, C and we want an override with A and C we create an override
 * collection with just those two entries.
 *
 *  <p>If we want an override with A, B, C, D we just copy the master collection
 *  and add to the copy.
 *
 *  <p>However, what if we want an override with an empty Collection? Here we
 *  conflict with the need to determine if there is any override at all.
 *  In the database there is no equivalent to a non-null empty Collection.
 *
 *  <p>It's also the case that hibernate appears to return empty Collections for
 *  no members so we never get null Collections. (need to confirm)
 *
 *  <p>To indicate the collection has been removed by the override we need a flag
 *  in the annotation. This is queried by the getOverrideIsEmpty method.
 *
 *  <p>A further problem arises with collections of complex classes such as
 *  attendees. Assume we have a recurring meeting with 2 attendees. For one
 *  instance we add a third attendee. We MUST clone the objects otherwise we
 *  end up with the annotation pointing at the master properties. Then if we
 *  try to remove one of the original 2 attendees we get a foreign key
 *  constraint exception because we try to delete an object referred to by the
 *  master.
 *
 *  <p>However, this leads to the situatiuon where updates to the master event
 *  don't get reflected automatically in the overrides. We have to go through
 *  each overrride and adjust the attendees manually.
 *
 * @author Mike Douglass douglm - rpi.edu
 * @param <T>
 * @param <C> Base Collection type, e.g List<T>
 */
public abstract class OverrideCollection <T, C extends Collection<T>>
      implements Collection<T> {
  BwEvent.ProxiedFieldIndex fieldIndex;
  BwEventAnnotation ann;
  ChangeFlag cf;

  /**
   * @param fieldIndex
   * @param ann
   * @param cf
   */
  public OverrideCollection(final BwEvent.ProxiedFieldIndex fieldIndex,
                            final BwEventAnnotation ann,
                            final ChangeFlag cf) {
    this.fieldIndex = fieldIndex;
    this.ann = ann;
    this.cf = cf;
  }

  /** Set the override Collection
   *
   * @param val
   */
  public abstract void setOverrideCollection(C val);

  /** Get the override collection
   * @return Collection<T>
   */
  public abstract C getOverrideCollection();

  /** Get a new empty override collection
   * @return empty Collection<T>
   */
  public abstract C getEmptyOverrideCollection();

  /** Copy the master contents into the override collection
   */
  public abstract void copyIntoOverrideCollection();

  /** Set the override is explicitly emptied.
   *
   * @param val   boolean true if value is explicitly emptied or null.
   */
  public void setOverrideIsEmpty(final boolean val) {
    ann.setEmptyFlag(fieldIndex, val);
    cf.setChangeFlag(true);
  }

  /** Determine if the override is explicitly emptied.
   *
   * @return boolean true if collection is explicitly emptied.
   */
  public boolean getOverrideIsEmpty() {
    return ann.getEmptyFlag(fieldIndex);
  }

  /** Get the master collection
   * @return the master Collection
   */
  public abstract C getMasterCollection();

  /* (non-Javadoc)
   * @see java.util.Collection#size()
   */
  public int size() {
    Collection<T> c = getCollection();
    if (c == null) {
      return 0;
    }

    return c.size();
  }

  /* (non-Javadoc)
   * @see java.util.Collection#isEmpty()
   */
  public boolean isEmpty() {
    Collection<T> c = getCollection();
    if (c == null) {
      return true;
    }

    return c.isEmpty();
  }

  /* (non-Javadoc)
   * @see java.util.Collection#contains(java.lang.Object)
   */
  public boolean contains(final Object o) {
    Collection<T> c = getCollection();
    if (c == null) {
      return false;
    }

    return c.contains(o);
  }

  /**
   * @author douglm
   * @param <E>
   */
  private class EmptyIterator<E> implements Iterator<E> {
    /* (non-Javadoc)
     * @see java.util.Iterator#hasNext()
     */
    public boolean hasNext() {
      return false;
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#next()
     */
    public E next() {
      throw new NoSuchElementException();
    }

    public void remove() {
      throw new IllegalStateException();
    }
  }

  /* (non-Javadoc)
   * @see java.util.Collection#iterator()
   */
  public Iterator<T> iterator() {
    Collection<T> c = getCollection();
    if (c == null) {
      return new EmptyIterator<T>();
    }

    return c.iterator();
  }

  /* (non-Javadoc)
   * @see java.util.Collection#toArray()
   */
  public Object[] toArray() {
    Collection<T> c = getCollection();
    if (c == null) {
      return new Object[]{};
    }

    return c.toArray();
  }

  public <T1> T1[] toArray(final T1[] a) {
    Collection<T> c = getCollection();
    if (c == null) {
      return new TreeSet<T>().toArray(a);
    }

    return c.toArray(a);
  }

  // Modification Operations

  /* (non-Javadoc)
   * @see java.util.Collection#add(java.lang.Object)
   */
  public boolean add(final T o) {
    Collection<T> c = getModCollection();

    setOverrideEmptyFlag(false);
    cf.setChangeFlag(true);
    return c.add(o);
  }

  /* (non-Javadoc)
   * @see java.util.Collection#remove(java.lang.Object)
   */
  public boolean remove(final Object o) {
    Collection<T> c = getModCollection();
    boolean changed = c.remove(o);

    if (c.isEmpty()) {
      setOverrideEmptyFlag(true);
    }
    cf.setChangeFlag(true);

    return changed;
  }

  // Bulk Operations

  /* (non-Javadoc)
   * @see java.util.Collection#containsAll(java.util.Collection)
   */
  public boolean containsAll(final Collection<?> c) {
    Collection<T> cc = getCollection();
    if (cc == null) {
      return false;
    }

    return cc.containsAll(c);
  }

  /* (non-Javadoc)
   * @see java.util.Collection#addAll(java.util.Collection)
   */
  public boolean addAll(final Collection<? extends T> c){
    C cc = getModCollection();

    setOverrideEmptyFlag(false);
    cf.setChangeFlag(true);
    return cc.addAll(c);
  }

  /* (non-Javadoc)
   * @see java.util.Collection#removeAll(java.util.Collection)
   */
  public boolean removeAll(final Collection<?> c) {
    Collection<T> cc = getModCollection();

    boolean changed = cc.removeAll(c);

    setOverrideEmptyFlag(true);
    cf.setChangeFlag(true);

    return changed;
  }

  /* (non-Javadoc)
   * @see java.util.Collection#retainAll(java.util.Collection)
   */
  public boolean retainAll(final Collection<?> c){
    Collection<T> cc = getModCollection();

    boolean changed = cc.retainAll(c);

    if (cc.isEmpty()) {
      setOverrideEmptyFlag(true);
    }
    cf.setChangeFlag(true);

    return changed;
  }

  /* (non-Javadoc)
   * @see java.util.Collection#clear()
   */
  public void clear() {
    getModCollection().clear();
    setOverrideEmptyFlag(true);
    cf.setChangeFlag(true);
  }

  // Comparison and hashing

  @Override
  public boolean equals(final Object o) {
    return getCollection().equals(o);
  }

  @Override
  public int hashCode() {
    return getCollection().hashCode();
  }

  protected void setOverrideEmptyFlag(final boolean val) {
    if (val == getOverrideIsEmpty()) {
      return;
    }

    setOverrideIsEmpty(val);
  }

  /* Only call for read operations
   */
  protected C getCollection() {
    C c = getOverrideCollection();

    if ((c != null) && !c.isEmpty()) {
      return c;
    }

    if (getOverrideIsEmpty()) {
      return c;
    }

    return getMasterCollection();
  }

  protected C getModCollection() {
    C over = getOverrideCollection();
    if ((over != null) && !over.isEmpty()) {
      return over;
    }

    if (over == null) {
      over = getEmptyOverrideCollection();
      setOverrideCollection(over);
    }

    if (getOverrideIsEmpty()) {
      return over;
    }

    /* Copy master into new override.
     */
    copyIntoOverrideCollection();

    return over;
  }
}
