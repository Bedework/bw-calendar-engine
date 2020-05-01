/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert;

import java.util.ArrayList;
import java.util.List;

/**
 * User: mike Date: 4/22/20 Time: 23:44
 */
public class DifferListResult<T extends Comparable<T>>
        extends DifferResult<T, List<T>> {
  public DifferListResult() {
  }

  public DifferListResult(final boolean differs) {
    super(differs);
  }

  List<T> newCollection() {
    return new ArrayList<>();
  }
}
