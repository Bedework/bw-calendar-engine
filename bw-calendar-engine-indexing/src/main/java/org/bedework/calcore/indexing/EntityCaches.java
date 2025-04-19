/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.indexing;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.wrappers.CollectionWrapper;

import java.util.HashMap;
import java.util.Map;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeCategory;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeCollection;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeContact;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeFilter;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeLocation;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePreferences;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResource;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResourceContent;

/**
 * User: mike Date: 10/12/17 Time: 23:56
 */
public class EntityCaches {
  final Map<Class<?>,
          EntityCache<? extends BwUnversionedDbentity<?>>> caches =
          new HashMap<>();

  final Map<String, Class<?>> docTypeToClass = new HashMap<>();

  private boolean updated;

  public EntityCaches() {
    register(BwCollection.class, docTypeCollection, new EntityCache<>());

    register(CollectionWrapper.class, docTypeCollection, new EntityCache<>());

    register(BwCategory.class, docTypeCategory, new EntityCache<>());

    register(BwLocation.class, docTypeLocation, new EntityCache<>());

    register(BwContact.class, docTypeContact, new EntityCache<>());

    register(BwPrincipal.class, docTypePrincipal, new EntityCache<>());

    register(BwPreferences.class, docTypePreferences, new EntityCache<>());

    register(BwFilterDef.class, docTypeFilter, new EntityCache<>());

    register(BwResource.class, docTypeResource, new EntityCache<>());

    register(BwResourceContent.class, docTypeResourceContent, new EntityCache<>());

    // Don't have docTypeUpdateTracker, docTypeUnknown, docTypeEvent
  }

  <T extends BwUnversionedDbentity<?>> void put(final T val) {
    final Class<?> cl;

    if (val instanceof BwPrincipal) {
      cl = BwPrincipal.class;
    } else {
      cl = val.getClass();
    }

    getCache(cl).put(val);
  }

  <T extends BwUnversionedDbentity<?>> void put(final T val,
                                                final int desiredAccess) {
    final Class<?> cl;

    if (val instanceof BwPrincipal) {
      cl = BwPrincipal.class;
    } else {
      cl = val.getClass();
    }

    getCache(cl).put(val, desiredAccess);
  }

  <T extends BwUnversionedDbentity<?>> T get(final String href,
                                             final Class<T> resultType) {
    final EntityCache<?> cache = getCache(resultType);

    return (T)cache.get(href);
  }

  <T extends BwUnversionedDbentity<?>> T get(final String href,
                                             final int desiredAccess,
                                             final Class<T> resultType) {
    return (T)getCache(resultType).get(href, desiredAccess);
  }

  void clear() {
    for (final EntityCache<?> cache: caches.values()) {
      cache.clear();
    }
  }

  void markUpdate(final String docType) {
    final Class<?> cl = docTypeToClass.get(docType);
    if (cl == null) {
      return;
    }

    updated = true;

    final EntityCache<?> cache = caches.get(cl);
    if (cache != null) {
      cache.clear();
    }
  }

  boolean testResetUpdate() {
    final boolean res = updated;

    updated = false;

    return res;
  }

  private <T extends BwUnversionedDbentity<?>> EntityCache<T> getCache(final Class<?> type) {
    final EntityCache<?> cache = caches.get(type);
    if (cache == null) {
      throw new BedeworkException("No cache for " + type);
    }

    return (EntityCache<T>)cache;
  }

  private void register(final Class<?> cl,
                        final String docType,
                        final EntityCache<?> cache) {
    caches.put(cl, cache);
    docTypeToClass.put(docType, cl);
  }
}
