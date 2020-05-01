/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert;

import java.util.Set;
import java.util.TreeSet;

/**
 * User: mike Date: 4/22/20 Time: 23:43
 */
public class DifferSetResult<T extends Comparable<T>>
        extends DifferResult<T, Set<T>> {
  public DifferSetResult() {
  }

  public DifferSetResult(final boolean differs) {
    super(differs);
  }

  Set<T> newCollection() {
    return new TreeSet<>();
  }
}
