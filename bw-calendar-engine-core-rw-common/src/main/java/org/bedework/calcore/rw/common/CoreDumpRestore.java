/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.rw.common;

import org.bedework.base.exc.BedeworkException;
import org.bedework.base.exc.BedeworkForbidden;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.IteratorsDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreDumpRestoreI;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.util.misc.Util;

import java.util.Iterator;
import java.util.List;

/**
 * User: mike Date: 2/1/20 Time: 14:49
 */
public class CoreDumpRestore extends CalintfHelper
        implements CoreDumpRestoreI {
  private final IteratorsDAO dao;

  private static class ObjectIterator<T> implements Iterator<T> {
    protected final IteratorsDAO dao;

    protected final String className;
    protected final Class<T> cl;
    protected final String colPath;
    protected final String ownerHref;
    protected final boolean publicAdmin;
    protected List<?> batch;
    protected int index;
    protected boolean done;
    protected int start;
    protected final int batchSize = 100;

    private ObjectIterator(final IteratorsDAO dao,
                           final Class<T> cl) {
      this(dao, cl, null, null, false, 0);
    }

    private ObjectIterator(final IteratorsDAO dao,
                           final Class<T> cl,
                           final String colPath) {
      this(dao, cl, colPath, null, false, 0);
    }

    private ObjectIterator(final IteratorsDAO dao,
                           final Class<T> cl,
                           final String colPath,
                           final String ownerHref,
                           final boolean publicAdmin,
                           final int start) {
      this.dao = dao;
      this.className = cl.getName();
      this.cl = cl;
      this.colPath = colPath;
      this.ownerHref = ownerHref;
      this.publicAdmin = publicAdmin;
      this.start = start;
    }

    @Override
    public boolean hasNext() {
      return more();
    }

    @Override
    public synchronized T next() {
      if (!more()) {
        return null;
      }

      index++;
      return (T)batch.get(index - 1);
    }

    @Override
    public void remove() {
      throw new BedeworkForbidden("Forbidden");
    }

    protected synchronized boolean more() {
      if (done) {
        return false;
      }

      if ((batch == null) || (index == batch.size())) {
        nextBatch();
      }

      return !done;
    }

    protected void nextBatch() {
      batch = dao.getBatch(className, colPath, ownerHref,
                           publicAdmin, start, batchSize);
      start += batchSize;

      index = 0;

      if (Util.isEmpty(batch)) {
        done = true;
      }
    }
  }

  private class EventHrefIterator extends ObjectIterator<String> {
    private EventHrefIterator(final IteratorsDAO dao,
                              final int start) {
      super(dao, String.class, null, null, false, start);
    }

    @Override
    public synchronized String next() {
      if (!more()) {
        return null;
      }

      final Object[] pathName = (Object[])batch.get(index);
      index++;

      if ((pathName.length != 2) ||
              (!(pathName[0] instanceof String)) ||
              (!(pathName[1] instanceof String))) {
        throw new BedeworkException("Expected 2 strings");
      }

      return pathName[0] + "/" + pathName[1];
    }

    @Override
    protected void nextBatch() {
      batch = dao.getBatch(BwEventObj.class.getName(),
                           start, batchSize);

      start += batchSize;
      index = 0;

      if (Util.isEmpty(batch)) {
        done = true;
      }
    }
  }

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CoreDumpRestore(final IteratorsDAO dao,
                         final Calintf intf,
                         final AccessChecker ac,
                         final boolean sessionless) {
    this.dao = dao;
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    dao.rollback();
    throw be;
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl) {
    return new ObjectIterator<>(dao, cl);
  }

  @Override
  public <T> Iterator<T> getPrincipalObjectIterator(final Class<T> cl) {
    return new ObjectIterator<>(dao, cl, null,
                                intf.getPrincipalRef(), false, 0);
  }

  @Override
  public <T> Iterator<T> getPublicObjectIterator(final Class<T> cl) {
    return new ObjectIterator<>(dao, cl, null, null, true, 0);
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl,
                                           final String colPath) {
    return new ObjectIterator<T>(dao, cl, colPath);
  }

  @Override
  public Iterator<String> getEventHrefs(final int start) {
    return new EventHrefIterator(dao, start);
  }

  /* ====================================================================
   *                       Restore methods
   * ==================================================================== */

  @Override
  public void addRestoredEntity(final BwUnversionedDbentity<?> val) {
    dao.add(val);
  }
}
