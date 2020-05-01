/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert;

import java.util.Collection;

/**
 * User: mike Date: 4/22/20 Time: 23:39
 */
public class DifferResult<T extends Comparable<T>,
        CT extends Collection<T>> {
  /* true if not instance and value was not null.
     true if recurring and any changes (details below for collections).
     false otherwise.
   */
  public boolean differs;

  public boolean addAll;
  public CT added;
  public boolean removeAll;
  public CT removed;

  public DifferResult() {
  }

  public DifferResult(final boolean differs) {
    this.differs = differs;
  }

  public DifferResult(final boolean addAll, final CT added,
                      final boolean removeAll,
                      final CT removed) {
    differs = true;
    this.addAll = addAll;
    this.added = added;
    this.removeAll = removeAll;
    this.removed = removed;
  }

  public void toAdd(final T val) {
    if (added == null) {
      added = newCollection();
    }

    added.add(val);
    differs = true;
  }

  public void toRemove(final T val) {
    if (removed == null) {
      removed = newCollection();
    }

    removed.add(val);
    differs = true;
  }

  CT newCollection() {
    return null;
  }
}
