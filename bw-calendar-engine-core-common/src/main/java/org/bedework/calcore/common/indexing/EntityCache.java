/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.common.indexing;

import org.bedework.calfacade.base.BwUnversionedDbentity;

import java.util.HashMap;
import java.util.Map;

/**
 * User: mike Date: 10/12/17 Time: 23:56
 */
public class EntityCache<T extends BwUnversionedDbentity> {
  final int maxSize = 700;

  final Map<String, T> ents = new HashMap<>();

  synchronized void put(final T val) {
    if (ents.size() >= maxSize) {
      ents.clear();
    }

    ents.put(val.getHref(), val);
  }

  synchronized void put(final T val,
                        final int desiredAccess) {
    if (ents.size() >= maxSize) {
      ents.clear();
    }

    ents.put(val.getHref() + "\t" + desiredAccess, val);
  }

  synchronized T get(final String href) {
    return ents.get(href);
  }

  synchronized T get(final String href,
                     final int desiredAccess) {
    return ents.get(href + "\t" + desiredAccess);
  }

  synchronized void clear() {
    ents.clear();
  }
}
