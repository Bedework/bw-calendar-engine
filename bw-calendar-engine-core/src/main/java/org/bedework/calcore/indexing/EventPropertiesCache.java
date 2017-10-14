/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.indexing;

import org.bedework.calfacade.BwEventProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * User: mike Date: 10/12/17 Time: 23:56
 */
public class EventPropertiesCache<T extends BwEventProperty> {
  final int maxSize = 700;

  final Map<String, T> props = new HashMap<>();

  synchronized void put(final T val) {
    if (props.size() >= maxSize) {
      props.clear();
    }

    props.put(val.getUid(), val);
  }

  synchronized T get(final String uid) {
    return props.get(uid);
  }

  synchronized void clear() {
    props.clear();
  }
}
