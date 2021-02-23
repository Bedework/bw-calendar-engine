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
package org.bedework.calfacade.util;

import org.bedework.calfacade.base.BwCloneable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/** A few helpers.
 *
 * UID generation copied from hibernate.
 *
 * @author Mike Douglass     douglm - bedework.edu
 *  @version 1.0
 */
public class CalFacadeUtil implements Serializable {
  private CalFacadeUtil() {
  }

  /* * Remove any suspicious characters from the input string to produce a
   * name-worthy result.
   *
   * @param name
   * @return fixed name
   * /
  public static String fixName(final String name) {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);

      if (Character.isUnicodeIdentifierPart(c)) {
        sb.append(c);
        continue;
      }

      // Skip any others for the moment
    }

    return sb.toString();
  }
   */

  /** Update the to Collection with from elements. This is used to
   * add or remove members from a Collection managed by hibernate for example
   * where a replacement of the Collection is not allowed.
   *
   * @param <T>   class of Collections
   * @param cloned - true if we must clone entities before adding
   * @param from source of elements
   * @param to where they go
   * @return boolean true if changed
   */
  public static <T extends BwCloneable> boolean updateCollection(
          final boolean cloned,
          final Collection<T> from,
          final Collection<T> to) {
    return updateCollection(cloned, from, to, null, null);
  }

  /** Update the to Collection with from elements. This is used to
   * add or remove members from a Collection managed by hibernate for example
   * where a replacement of the Collection is not allowed.
   *
   * @param <T>   class of Collections
   * @param cloned - true if we must clone entities before adding
   * @param from source of elements
   * @param to where they go
   * @param added - may be null - updated with added entries
   * @param removed - may be null - updated with removed entries
   * @return boolean true if changed
   */
  @SuppressWarnings("unchecked")
  public static <T extends BwCloneable> boolean updateCollection(
          final boolean cloned,
          final Collection<T> from,
          final Collection<T> to,
          final Collection<T> added,
          final Collection<T> removed) {
    boolean changed = false;

    if (from != null) {
      for (T o: from) {
        if (!to.contains(o)) {
          if (added != null) {
            added.add(o);
          }

          if (cloned) {
            to.add((T)o.clone());
          } else {
            to.add(o);
          }
          changed = true;
        }
      }
    }

    /* Make set of objects to remove to avoid concurrent update exceptions. */
    Collection<T> deleted;

    deleted = Objects.requireNonNullElseGet(removed, ArrayList::new);

    for (T o: to) {
      if ((from == null) || !from.contains(o)) {
        deleted.add(o);
      }
    }

    int numDeleted = deleted.size();
    if (numDeleted != 0) {
      changed = true;

      if (numDeleted == to.size()) {
        to.clear();
      } else {
        for (T o: deleted) {
          to.remove(o);
        }
      }
    }

    return changed;
  }

  /** Compare two strings. null is less than any non-null string.
   *
   * @param s1       first string.
   * @param s2       second string.
   * @return int     0 if the s1 is equal to s2;
   *                 <0 if s1 is lexicographically less than s2;
   *                 >0 if s1 is lexicographically greater than s2.
   */
  public static int compareStrings(final String s1, final String s2) {
    if (s1 == null) {
      if (s2 != null) {
        return -1;
      }

      return 0;
    }

    if (s2 == null) {
      return 1;
    }

    return s1.compareTo(s2);
  }

  /** Compare two possibly null objects for equality
   *
   * @param thisone first object
   * @param thatone second object
   * @return boolean true if both null or equal
   */
  public static boolean eqObjval(final Object thisone, final Object thatone) {
    if (thisone == null) {
      return thatone == null;
    }

    if (thatone == null) {
      return false;
    }

    return thisone.equals(thatone);
  }

  /** Compare two possibly null objects
   *
   * @param thisone first object
   * @param thatone second object
   * @return int -1, 0, 1,
   */
  public static <T extends Comparable<T>> int cmpObjval(
          final T thisone,
          final T thatone) {
    if (thisone == null) {
      if (thatone == null) {
        return 0;
      }

      return -1;
    }

    if (thatone == null) {
      return 1;
    }

    return thisone.compareTo(thatone);
  }

  /** Compare two possibly null objects
   *
   * @param thisone first object
   * @param thatone second object
   * @return int -1, 0, 1,
   */
  public static <T extends Comparable<T>> int cmpObjval(
          final Collection<T> thisone,
          final Collection<T> thatone) {
    if (thisone == null) {
      if (thatone == null) {
        return 0;
      }

      return -1;
    }

    if (thatone == null) {
      return 1;
    }

    int thisLen = thisone.size();
    int thatLen = thatone.size();

    int res = Integer.compare(thisLen, thatLen);
    if (res != 0) {
      return res;
    }

    Iterator<T> thatIt = thatone.iterator();
    for (T c: thisone) {
      res = cmpObjval(c, thatIt.next());

      if (res != 0) {
        return res;
      }
    }

    return 0;
  }

  /** Given a class name return an object of that class.
   * The class parameter is used to check that the
   * named class is an instance of that class.
   *
   * @param className String class name
   * @param cl   Class expected
   * @return     Object checked to be an instance of that class
   * @throws RuntimeException on fatal error
   */
  public static Object getObject(final String className,
                                 final Class<?> cl) {
    try {
      final ClassLoader loader = Thread.currentThread().getContextClassLoader();

      final Class<?> clazz = loader.loadClass(className);

      if (clazz == null) {
        throw new RuntimeException("Class " + className + " not found");
      }

      final Object o = clazz.getDeclaredConstructor().newInstance();

      if (!cl.isInstance(o)) {
        throw new RuntimeException(
                "Class " + clazz +
                        " is not a subclass of " +
                        cl.getName());
      }

      return o;
    } catch (final RuntimeException re) {
      throw re;
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Turn the int minutes into a 4 digit String hours and minutes value
   *
   * @param minutes  int
   * @return String 4 digit hours + minutes
   */
  public static String getTimeFromMinutes(final int minutes) {
    return pad2(minutes / 60) + pad2(minutes % 60);
  }

  /** Return String value of par padded to 2 digits.
   * @param val to pad
   * @return String
   */
  private static String pad2(final int val) {
    if (val > 9) {
      return String.valueOf(val);
    }

    return "0" + val;
  }
}
