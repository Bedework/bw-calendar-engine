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

import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.util.misc.Util;

import org.apache.log4j.Logger;
import org.hibernate.FlushMode;
import org.hibernate.LockOptions;
import org.hibernate.NonUniqueResultException;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StaleStateException;
import org.hibernate.Transaction;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/** Convenience class to do the actual hibernate interaction. Intended for
 * one use only.
 *
 * @author Mike Douglass douglm@rpi.edu
 */
public class HibSessionImpl implements HibSession {
  transient Logger log;

  Session sess;
  transient Transaction tx;
  boolean rolledBack;

  transient Query q;

  /** Exception from this session. */
  Throwable exc;

  private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

  /** Set up for a hibernate interaction. Throw the object away on exception.
   *
   * @param sessFactory
   * @param log
   * @throws CalFacadeException
   */
  @Override
  public void init(final SessionFactory sessFactory,
                   final Logger log) throws CalFacadeException {
    try {
      this.log = log;
      sess = sessFactory.openSession();
      rolledBack = false;
      //sess.setFlushMode(FlushMode.COMMIT);
//      tx = sess.beginTransaction();
    } catch (Throwable t) {
      exc = t;
      tx = null;  // not even started. Should be null anyway
      close();
    }
  }

  @Override
  public Session getSession() throws CalFacadeException {
    return sess;
  }

  /**
   * @return boolean true if open
   * @throws CalFacadeException
   */
  @Override
  public boolean isOpen() throws CalFacadeException {
    try {
      if (sess == null) {
        return false;
      }
      return sess.isOpen();
    } catch (Throwable t) {
      handleException(t);
      return false;
    }
  }

  @Override
  public Throwable getException() {
    return exc;
  }

  /** Disconnect a session
   *
   * @throws CalFacadeException
   */
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
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** set the flushmode
   *
   * @param val
   * @throws CalFacadeException
   */
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
    } catch (Throwable t) {
      exc = t;
      throw new CalFacadeException(t);
    }
  }

  /** Begin a transaction
   *
   * @throws CalFacadeException
   */
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
    } catch (CalFacadeException cfe) {
      exc = cfe;
      throw cfe;
    } catch (Throwable t) {
      exc = t;
      throw new CalFacadeException(t);
    }
  }

  /** Return true if we have a transaction started
   *
   * @return boolean
   */
  @Override
  public boolean transactionStarted() {
    return tx != null;
  }

  /** Commit a transaction
   *
   * @throws CalFacadeException
   */
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
    } catch (Throwable t) {
      exc = t;

      if (t instanceof StaleStateException) {
        throw new CalFacadeStaleStateException(t);
      }
      throw new CalFacadeException(t);
    }
  }

  /** Rollback a transaction
   *
   * @throws CalFacadeException
   */
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
          !tx.wasCommitted() &&
          !tx.wasRolledBack()) {
        if (getLogger().isDebugEnabled()) {
          getLogger().debug("About to rollback");
        }
        tx.rollback();
        tx = null;
        sess.clear();
        rolledBack = true;
      }
    } catch (Throwable t) {
      exc = t;
      throw new CalFacadeException(t);
    }
  }

  @Override
  public boolean rolledback() throws CalFacadeException {
    return rolledBack;
  }

  private static final String getCurrentTimestampQuery =
      "select current_timestamp() from " + BwSystem.class.getName();

  @Override
  public Timestamp getCurrentTimestamp() throws CalFacadeException {
    try {
      List l = sess.createQuery(getCurrentTimestampQuery).list();

      if (Util.isEmpty(l)) {
        return null;
      }

      return (Timestamp)l.get(0);
    } catch (Throwable t) {
      handleException(t);
      return null;
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#evict(java.lang.Object)
   */
  @Override
  public void evict(final Object val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      sess.evict(val);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#createQuery(java.lang.String)
   */
  @Override
  public void createQuery(final String s) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q = sess.createQuery(s);
    } catch (Throwable t) {
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
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#getQueryString()
   */
  @Override
  public String getQueryString() throws CalFacadeException {
    if (q == null) {
      return "*** no query ***";
    }

    try {
      return q.getQueryString();
    } catch (Throwable t) {
      handleException(t);
      return null;
    }
  }

  /** Mark the query as cacheable
   *
   * @throws CalFacadeException
   */
  @Override
  public void cacheableQuery() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setCacheable(true);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      String parameter value
   * @throws CalFacadeException
   */
  @Override
  public void setString(final String parName, final String parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setString(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Date parameter value
   * @throws CalFacadeException
   */
  @Override
  public void setDate(final String parName, final Date parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      // Remove any time component
      synchronized (dateFormatter) {
        q.setDate(parName, java.sql.Date.valueOf(dateFormatter.format(parVal)));
      }
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      boolean parameter value
   * @throws CalFacadeException
   */
  @Override
  public void setBool(final String parName, final boolean parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setBoolean(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      int parameter value
   * @throws CalFacadeException
   */
  @Override
  public void setInt(final String parName, final int parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setInteger(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      long parameter value
   * @throws CalFacadeException
   */
  @Override
  public void setLong(final String parName, final long parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setLong(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Object parameter value
   * @throws CalFacadeException
   */
  @Override
  public void setEntity(final String parName, final Object parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setEntity(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setParameter(java.lang.String, java.lang.Object)
   */
  @Override
  public void setParameter(final String parName, final Object parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setParameter(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setParameterList(java.lang.String, java.util.Collection)
   */
  @Override
  public void setParameterList(final String parName, final Collection parVal) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setParameterList(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setFirstResult(int)
   */
  @Override
  public void setFirstResult(final int val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setFirstResult(val);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setMaxResults(int)
   */
  @Override
  public void setMaxResults(final int val) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      q.setMaxResults(val);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#getUnique()
   */
  @Override
  public Object getUnique() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      return q.uniqueResult();
    } catch (NonUniqueResultException nure) {
      // Always bad news
      handleException(nure);
      return null;  // Don't get here
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /** Return a list resulting from the query.
   *
   * @return List          list from query
   * @throws CalFacadeException
   */
  @Override
  public List getList() throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      List l = q.list();

      if (l == null) {
        return new ArrayList();
      }

      return l;
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /**
   * @return int number updated
   * @throws CalFacadeException
   */
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
    } catch (Throwable t) {
      handleException(t);
      return 0;  // Don't get here
    }
  }

  /** Update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @throws CalFacadeException
   */
  @Override
  public void update(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      Object ent = obj;

      beforeSave(ent);

 //     if ((ent instanceof BwDbentity) &&
 //         (((BwDbentity)ent).getId() != CalFacadeDefs.unsavedItemKey)) {
 //       ent = sess.merge(ent);
 //     } else {
        sess.update(ent);
 //     }
      deleteSubs(obj);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Merge and update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @return Object   the persistent object
   * @throws CalFacadeException
   */
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
    } catch (Throwable t) {
      handleException(t, obj);
      return null;
    }
  }

  /** Save a new object or update an object which may have been loaded in a
   * previous hibernate session
   *
   * @param obj
   * @throws CalFacadeException
   */
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
          (((BwDbentity)ent).getId() != CalFacadeDefs.unsavedItemKey)) {
        ent = sess.merge(ent);
      } else {
        sess.saveOrUpdate(ent);
      }
      deleteSubs(obj);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Copy the state of the given object onto the persistent object with the
   * same identifier. If there is no persistent instance currently associated
   * with the session, it will be loaded. Return the persistent instance.
   * If the given instance is unsaved or does not exist in the database,
   * save it and return it as a newly persistent instance. Otherwise, the
   * given instance does not become associated with the session.
   *
   * @param obj
   * @return Object
   * @throws CalFacadeException
   */
  @Override
  public Object saveOrUpdateCopy(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      return sess.merge(obj);
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    A serializable key
   * @return Object
   * @throws CalFacadeException
   */
  @Override
  public Object get(final Class cl, final Serializable id) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      return sess.get(cl, id);
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    int key
   * @return Object
   * @throws CalFacadeException
   */
  @Override
  public Object get(final Class cl, final int id) throws CalFacadeException {
    return get(cl, new Integer(id));
  }

  /** Save a new object.
   *
   * @param obj
   * @throws CalFacadeException
   */
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
    } catch (Throwable t) {
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

  /** Delete an object
   *
   * @param obj
   * @throws CalFacadeException
   */
  @Override
  public void delete(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      beforeDelete(obj);

      sess.delete(obj);
      deleteSubs(obj);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Save a new object with the given id. This should only be used for
   * restoring the db from a save.
   *
   * @param obj
   * @throws CalFacadeException
   */
  @Override
  public void restore(final Object obj) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
      sess.replicate(obj, ReplicationMode.IGNORE);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#reAttach(org.bedework.calfacade.base.BwUnversionedDbentity)
   */
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
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @param o
   * @throws CalFacadeException
   */
  @Override
  public void lockRead(final Object o) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
//      sess.lock(o, LockMode.READ);
      sess.buildLockRequest(LockOptions.READ).lock(o);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @param o
   * @throws CalFacadeException
   */
  @Override
  public void lockUpdate(final Object o) throws CalFacadeException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new CalFacadeException(exc);
    }

    try {
//      sess.lock(o, LockMode.UPGRADE);
      sess.buildLockRequest(LockOptions.UPGRADE).lock(o);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @throws CalFacadeException
   */
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
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @throws CalFacadeException
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
    } catch (Throwable t) {
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
      if (getLogger().isDebugEnabled()) {
        getLogger().debug("handleException called");
        if (o != null) {
          getLogger().debug(o.toString());
        }
        getLogger().error(this, t);
      }
    } catch (Throwable dummy) {}

    try {
      if (tx != null) {
        try {
          tx.rollback();
        } catch (Throwable t1) {
          rollbackException(t1);
        }
        tx = null;
      }
    } finally {
      try {
        sess.close();
      } catch (Throwable t2) {}
      sess = null;
    }

    exc = t;

    if (t instanceof StaleStateException) {
      throw new CalFacadeStaleStateException(t);
    }

    throw new CalFacadeException(t);
  }

  private void beforeSave(final Object o) throws CalFacadeException {
    if (!(o instanceof BwDbentity)) {
      return;
    }

    BwDbentity ent = (BwDbentity)o;

    ent.beforeSave();
  }

  private void beforeDelete(final Object o) throws CalFacadeException {
    if (!(o instanceof BwDbentity)) {
      return;
    }

    BwDbentity ent = (BwDbentity)o;

    ent.beforeDeletion();
  }

  private void deleteSubs(final Object o) throws CalFacadeException {
    if (!(o instanceof BwDbentity)) {
      return;
    }

    BwDbentity ent = (BwDbentity)o;

    Collection<BwDbentity> subs = ent.getDeletedEntities();
    if (subs == null) {
      return;
    }

    for (BwDbentity sub: subs) {
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
    if (getLogger().isDebugEnabled()) {
      getLogger().debug("HibSession: ", t);
    }
    getLogger().error(this, t);
  }

  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}
