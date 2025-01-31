package org.bedework.calcorei;

import org.bedework.calfacade.base.BwUnversionedDbentity;

import java.util.Iterator;

public interface CoreDumpRestoreI {

  /* ====================================================================
   *                       dump/restore methods
   *
   * These are used to handle the needs of dump/restore. Perhaps we
   * can eliminate the need at some point...
   * ==================================================================== */

  /**
   *
   * @param cl Class of objects
   * @return iterator over all the objects
   */
  <T> Iterator<T> getObjectIterator(Class<T> cl);

  /**
   *
   * @param cl Class of objects
   * @return iterator over all the objects for current principal
   */
  <T> Iterator<T> getPrincipalObjectIterator(Class<T> cl);

  /**
   *
   * @param cl Class of objects
   * @return iterator over all the public objects
   */
  <T> Iterator<T> getPublicObjectIterator(Class<T> cl);

  /**
   *
   * @param cl Class of objects
   * @param colPath for objects
   * @return iterator over all the objects with the given col path
   */
  <T> Iterator<T> getObjectIterator(Class<T> cl,
                                    String colPath);

  /** Return an iterator over hrefs for events.
   *
   * @param start first object
   * @return iterator over the objects
   */
  Iterator<String> getEventHrefs(int start);

  /**
   * @param val an entity to restore
   */
  void addRestoredEntity(BwUnversionedDbentity<?> val);
}
