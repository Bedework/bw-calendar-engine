/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.common.indexing;

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.svc.BwPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * User: mike Date: 10/12/17 Time: 23:56
 */
public class EntityCaches {
  final static Map<Class,
          EntityCache<? extends BwUnversionedDbentity>> caches =
          new HashMap<>();

  static {
    register(BwCategory.class, new EntityCache<>());

    register(BwLocation.class, new EntityCache<>());

    register(BwContact.class, new EntityCache<>());

    register(BwPrincipal.class, new EntityCache<>());

    register(BwPreferences.class, new EntityCache<>());

    register(BwFilterDef.class, new EntityCache<>());

    register(BwResource.class, new EntityCache<>());
  }

  synchronized <T extends BwUnversionedDbentity> void put(final T val) {
    final Class cl;

    if (val instanceof BwPrincipal) {
      cl = BwPrincipal.class;
    } else {
      cl = val.getClass();
    }

    EntityCache cache = getCache(cl);

    cache.put(val);
  }

  synchronized <T extends BwUnversionedDbentity> T get(final String href,
                                                       final Class<T> resultType) {
    EntityCache cache = getCache(resultType);

    return (T)cache.get(href);
  }

  synchronized void clear() {
    for (final EntityCache cache: caches.values()) {
      cache.clear();
    }
  }

  private <T extends BwUnversionedDbentity> EntityCache<T> getCache(final Class<T> type) {
    EntityCache cache = caches.get(type);
    if (cache == null) {
      throw new RuntimeException("No cache for " + type);
    }

    return cache;
  }

  private static void register(final Class cl,
                               final EntityCache cache) {
    caches.put(cl, cache);
  }
}
