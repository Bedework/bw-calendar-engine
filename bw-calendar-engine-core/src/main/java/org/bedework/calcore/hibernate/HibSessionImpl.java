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
package org.bedework.calcore.hibernate;

import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.exc.CalFacadeConstraintViolationException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;

import org.hibernate.FlushMode;
import org.hibernate.Hibernate;
import org.hibernate.LockOptions;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StaleStateException;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;

import java.io.InputStream;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.OptimisticLockException;

/** Convenience class to do the actual hibernate interaction. Intended for
 * one use only.
 *
 * @author Mike Douglass douglm@rpi.edu
 */
public class HibSessionImpl implements Logged, HibSession {
  Session sess;
  transient Transaction tx;
  boolean rolledBack;

  transient Query q;

  /** Exception from this session. */
  Throwable exc;

  private final SimpleDateFormat dateFormatter =
          new SimpleDateFormat("yyyy-MM-dd");

  @Override
  public void init(final SessionFactory sessFactory) throws CalFacadeException {
    try {
      sess = sessFactory.openSession();
      rolledBack = false;
      //sess.setFlushMode(FlushMode.COMMIT);
//      tx = sess.beginTransaction();
    } catch (final Throwable t) {
      exc = t;
      tx = null;  // not even started. Should be null anyway
      close();
    }
  }

  @Override
  public Session getSession() {
    return sess;
  }

  @Override
  public boolean isOpen() throws CalFacadeException {
    try {
      if (sess == null) {
        return false;
      }
      return sess.isOpen();
    } catch (final Throwable t) {
      handleException(t);
      return false;
    }
  }

  @Override
  public Throwable getException() {
    return exc;
  }

  @Override
  public void disconnect() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      if (exc instanceof CalFacadeException) {
        throw (CalFacadeException)exc;
      }
      throw new CalFacadeException(exc);
    }

    try {
      sess.disconnect();
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setFlushMode(final FlushMode val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      if (tx != null) {
        throw new CalFacadeException("Transaction already started");
      }

      sess.setFlushMode(val);
    } catch (final Throwable t) {
      exc = t;
      throw new CalFacadeException(t);
    }
  }

  @Override
  public void beginTransaction() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      if (tx != null) {
        throw new CalFacadeException("Transaction already started");
      }

      tx = sess.beginTransaction();
      rolledBack = false;
      if (tx == null) {
        throw new CalFacadeException("Transaction not started");
      }
    } catch (final CalFacadeException cfe) {
      exc = cfe;
      throw cfe;
    } catch (final Throwable t) {
      exc = t;
      throw new CalFacadeException(t);
    }
  }

  @Override
  public boolean transactionStarted() {
    return tx != null;
  }

  @Override
  public void commit() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
//      if (tx != null &&
//          !tx.wasCommitted() &&
//          !tx.wasRolledBack()) {
        //if (getLogger().isDebugEnabled()) {
        //  getLogger().debug("About to comnmit");
        //}
      if (tx != null) {
        tx.commit();
      }

      tx = null;
    } catch (final Throwable t) {
      exc = t;

      if (t instanceof StaleStateException) {
        throw new CalFacadeStaleStateException(t);
      }

      final Class<?> obj;
      try {
        obj = t.getClass().getClassLoader().loadClass("javax.persistence.OptimisticLockException");
      } catch (final ClassNotFoundException cnfe) {
        throw new RuntimeException(cnfe);
      }
      if (t.getClass().isAssignableFrom(obj)) {
        throw new CalFacadeStaleStateException(t);
      }

      throw new CalFacadeException(t);
    }
  }

  @Override
  public void rollback() throws CalFacadeException {
/*    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }
*/
    if (getLogger().isDebugEnabled()) {
      getLogger().debug("Enter rollback");
    }
    try {
      if ((tx != null) &&
          !rolledBack) {
        if (getLogger().isDebugEnabled()) {
          getLogger().debug("About to rollback");
        }
        tx.rollback();
        tx = null;
        sess.clear();
      }
    } catch (final Throwable t) {
      exc = t;
      throw new CalFacadeException(t);
    } finally {
      rolledBack = true;
    }
  }

  @Override
  public boolean rolledback() {
    return rolledBack;
  }

  private static final String getCurrentTimestampQuery =
      "select current_timestamp() from " + BwSystem.class.getName();

  @Override
  public Timestamp getCurrentTimestamp() throws CalFacadeException {
    try {
      final List<?> l = sess.createQuery(getCurrentTimestampQuery).list();

      if (Util.isEmpty(l)) {
        return null;
      }

      return (Timestamp)l.get(0);
    } catch (final Throwable t) {
      handleException(t);
      return null;
    }
  }

  @Override
  public Blob getBlob(final byte[] val) {
    return Hibernate.getLobCreator(sess).createBlob(val);
  }

  public Blob getBlob(final InputStream val, final long length) {
    return Hibernate.getLobCreator(sess).createBlob(val, length);
  }

  @Override
  public void evict(final Object val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      sess.evict(val);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void createQuery(final String s) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q = sess.createQuery(s);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void createNoFlushQuery(final String s) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q = sess.createQuery(s);
      q.setFlushMode(FlushMode.COMMIT);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public String getQueryString() throws CalFacadeException {
    if (q == null) {
      return "*** no query ***";
    }

    try {
      return q.getQueryString();
    } catch (final Throwable t) {
      handleException(t);
      return null;
    }
  }

  @Override
  public void cacheableQuery() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setCacheable(true);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setString(final String parName, final String parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setString(parName, parVal);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setBool(final String parName, final boolean parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setBoolean(parName, parVal);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setInt(final String parName, final int parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setInteger(parName, parVal);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setLong(final String parName, final long parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setLong(parName, parVal);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setEntity(final String parName, final Object parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setEntity(parName, parVal);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setParameter(final String parName, final Object parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setParameter(parName, parVal);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setParameterList(final String parName,
                               final Collection<?> parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setParameterList(parName, parVal);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setFirstResult(final int val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setFirstResult(val);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void setMaxResults(final int val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setMaxResults(val);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public Object getUnique() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      return q.uniqueResult();
    } catch (final Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  @Override
  public List<?> getList() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      final List<?> l = q.list();

      if (l == null) {
        return new ArrayList<>();
      }

      return l;
    } catch (final Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  @Override
  public int executeUpdate() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      if (q == null) {
        throw new CalFacadeException("No query for execute update");
      }

      return q.executeUpdate();
    } catch (final Throwable t) {
      handleException(t);
      return 0;  // Don't get here
    }
  }

  @Override
  public void update(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      final Object ent = obj;

      beforeSave(ent);

 //     if ((ent instanceof BwDbentity) &&
 //         (((BwDbentity)ent).getId() != CalFacadeDefs.unsavedItemKey)) {
 //       ent = sess.merge(ent);
 //     } else {
        sess.update(ent);
 //     }
      deleteSubs(obj);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public Object merge(Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      beforeSave(obj);

      obj = sess.merge(obj);
      deleteSubs(obj);

      return obj;
    } catch (final Throwable t) {
      handleException(t, obj);
      return null;
    }
  }

  @Override
  public void saveOrUpdate(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      Object ent = obj;

      beforeSave(ent);

      if ((ent instanceof BwDbentity) &&
          (((BwDbentity<?>)ent).getId() != CalFacadeDefs.unsavedItemKey)) {
        ent = sess.merge(ent);
      } else {
        sess.saveOrUpdate(ent);
      }
      deleteSubs(obj);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public Object saveOrUpdateCopy(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      return sess.merge(obj);
    } catch (final Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  @Override
  public Object get(final Class<?> cl,
                    final Serializable id) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      return sess.get(cl, id);
    } catch (final Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  @Override
  public Object get(final Class<?> cl, final int id) throws CalFacadeException {
    return get(cl, Integer.valueOf(id));
  }

  @Override
  public void save(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      beforeSave(obj);
      sess.save(obj);
      deleteSubs(obj);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  /* * Save a new object with the given id. This should only be used for
   * restoring the db from a save or for assigned keys.
   *
   * @param obj
   * @param id
   * @throws CalFacadeException
   * /
  public void save(Object obj, Serializable id) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      sess.save(obj, id);
    } catch (Throwable t) {
      handleException(t);
    }
  }*/

  @Override
  public void delete(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      beforeDelete(obj);

      evict(obj);
      sess.delete(sess.merge(obj));
      deleteSubs(obj);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void restore(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      sess.replicate(obj, ReplicationMode.IGNORE);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void reAttach(final BwUnversionedDbentity<?> val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      if (!val.unsaved()) {
//        sess.lock(val, LockMode.NONE);
        sess.buildLockRequest(LockOptions.NONE).lock(val);
      }
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void lockRead(final Object o) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
//      sess.lock(o, LockMode.READ);
      sess.buildLockRequest(LockOptions.READ).lock(o);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void lockUpdate(final Object o) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
//      sess.lock(o, LockMode.UPGRADE);
      sess.buildLockRequest(LockOptions.UPGRADE).lock(o);
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void flush() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    if (getLogger().isDebugEnabled()) {
      getLogger().debug("About to flush");
    }
    try {
      sess.flush();
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  @Override
  public void clear() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    if (getLogger().isDebugEnabled()) {
      getLogger().debug("About to flush");
    }
    try {
      sess.clear();
    } catch (final Throwable t) {
      handleException(t);
    }
  }

  /**
   * @throws CalFacadeException on fatal error
   */
  @Override
  public void close() throws CalFacadeException {
    if (sess == null) {
      return;
    }

//    throw new CalFacadeException("XXXXXXXXXXXXXXXXXXXXXXXXXXXXX");/*
    try {
      if (!rolledback() && sess.isDirty()) {
        sess.flush();
      }
      if ((tx != null) && !rolledback()) {
        tx.commit();
      }
    } catch (final Throwable t) {
      if (exc == null) {
        exc = t;
      }
    } finally {
      tx = null;
      if (sess != null) {
        try {
          sess.close();
        } catch (Throwable t) {}
      }
    }

    sess = null;
    if (exc != null) {
      throw new CalFacadeException(exc);
    }
//    */
  }

  private void handleException(final Throwable t) throws CalFacadeException {
    handleException(t, null);
  }

  private void handleException(final Throwable t,
                               final Object o) throws CalFacadeException {
    try {
      if (debug()) {
        debug("handleException called");
        if (o != null) {
          debug(o.toString());
        }
        error(t);
      }
    } catch (final Throwable ignored) {}

    try {
      if (tx != null) {
        try {
          tx.rollback();
        } catch (final Throwable t1) {
          rollbackException(t1);
        }
        tx = null;
      }
    } finally {
      try {
        sess.close();
      } catch (final Throwable ignored) {}
      sess = null;
    }

    exc = t;

    if (t instanceof StaleStateException) {
      throw new CalFacadeStaleStateException(t);
    }

    if (t instanceof OptimisticLockException) {
      throw new CalFacadeStaleStateException(t);
    }

    if (t instanceof ConstraintViolationException) {
      throw new CalFacadeConstraintViolationException(t);
    }

    throw new CalFacadeException(t);
  }

  private void beforeSave(final Object o) throws CalFacadeException {
    if (!(o instanceof BwDbentity)) {
      return;
    }

    final BwDbentity<?> ent = (BwDbentity<?>)o;

    ent.beforeSave();
  }

  private void beforeDelete(final Object o) throws CalFacadeException {
    if (!(o instanceof BwDbentity)) {
      return;
    }

    final BwDbentity<?> ent = (BwDbentity<?>)o;

    ent.beforeDeletion();
  }

  private void deleteSubs(final Object o) throws CalFacadeException {
    if (!(o instanceof BwDbentity)) {
      return;
    }

    final BwDbentity<?> ent = (BwDbentity<?>)o;

    final Collection<BwDbentity<?>> subs = ent.getDeletedEntities();
    if (subs == null) {
      return;
    }

    for (final BwDbentity<?> sub: subs) {
      evict(sub);
      delete(sub);
    }
  }

  /** This is just in case we want to report rollback exceptions. Seems we're
   * likely to get one.
   *
   * @param t   Throwable from the rollback
   */
  private void rollbackException(final Throwable t) {
    error(t);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
