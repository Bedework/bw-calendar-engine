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

import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.exc.CalFacadeException;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

/** Interface to do hibernate interactions.
 *
 * @author Mike Douglass douglm at rpi.edu
 */
public interface HibSession extends Serializable {
  /** Set up for a hibernate interaction. Throw the object away on exception.
   *
   * @param sessFactory
   * @throws CalFacadeException on fatal error
   */
  public void init(SessionFactory sessFactory) throws CalFacadeException;

  /**
   * @return Session
   */
  public Session getSession();

  /**
   * @return boolean true if open
   * @throws CalFacadeException on fatal error
   */
  public boolean isOpen() throws CalFacadeException;

  /** If we had a hibernate exception this will return non-null. The session
   * needs to be discarded.
   *
   * @return current exception or null.
   */
  public Throwable getException();

  /** Disconnect a session
   *
   * @throws CalFacadeException on fatal error
   */
  public void disconnect() throws CalFacadeException;

  /** set the flushmode
   *
   * @param val
   * @throws CalFacadeException on fatal error
   */
  public void setFlushMode(FlushMode val) throws CalFacadeException;

  /** Begin a transaction
   *
   * @throws CalFacadeException on fatal error
   */
  public void beginTransaction() throws CalFacadeException;

  /** Return true if we have a transaction started
   *
   * @return boolean
   */
  public boolean transactionStarted();

  /** Commit a transaction
   *
   * @throws CalFacadeException on fatal error
   */
  public void commit() throws CalFacadeException;

  /** Rollback a transaction
   *
   * @throws CalFacadeException on fatal error
   */
  public void rollback() throws CalFacadeException;

  /** Did we rollback the transaction?
   *
   * @return boolean
   */
  public boolean rolledback();

  /**
   * @return a timestamp from the db
   * @throws CalFacadeException on fatal error
   */
  public Timestamp getCurrentTimestamp() throws CalFacadeException;

  /**
   * @return a blob
   */
  Blob getBlob(byte[] val);
  
  /** Evict an object from the session.
   *
   * @param val          Object to evict
   * @throws CalFacadeException on fatal error
   */
  public void evict(Object val) throws CalFacadeException;

  /** Create a query ready for parameter replacement or execution.
   *
   * @param s             String hibernate query
   * @throws CalFacadeException on fatal error
   */
  public void createQuery(String s) throws CalFacadeException;

  /** Create a query ready for parameter replacement or execution and flag it
   * for no flush. This assumes that any queued changes will not affect the
   * result of the query.
   *
   * @param s             String hibernate query
   * @throws CalFacadeException on fatal error
   */
  public void createNoFlushQuery(String s) throws CalFacadeException;

  /**
   * @return query string
   * @throws CalFacadeException on fatal error
   */
  public String getQueryString() throws CalFacadeException;

  /** Mark the query as cacheable
   *
   * @throws CalFacadeException on fatal error
   */
  public void cacheableQuery() throws CalFacadeException;

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      String parameter value
   * @throws CalFacadeException on fatal error
   */
  public void setString(String parName, String parVal) throws CalFacadeException;

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      boolean parameter value
   * @throws CalFacadeException on fatal error
   */
  public void setBool(String parName, boolean parVal) throws CalFacadeException;

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      int parameter value
   * @throws CalFacadeException on fatal error
   */
  public void setInt(String parName, int parVal) throws CalFacadeException;

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      long parameter value
   * @throws CalFacadeException on fatal error
   */
  public void setLong(String parName, long parVal) throws CalFacadeException;

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Object parameter value
   * @throws CalFacadeException on fatal error
   */
  public void setEntity(String parName, Object parVal) throws CalFacadeException;

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Object parameter value
   * @throws CalFacadeException on fatal error
   */
  public void setParameter(String parName, Object parVal) throws CalFacadeException ;

  /** Set the named parameter with the given Collection
   *
   * @param parName     String parameter name
   * @param parVal      Collection parameter value
   * @throws CalFacadeException on fatal error
   */
  public void setParameterList(String parName,
                               Collection<?> parVal) throws CalFacadeException ;

  /** Set the first result for a paged batch
   *
   * @param val      int first index
   * @throws CalFacadeException on fatal error
   */
  public void setFirstResult(int val) throws CalFacadeException;

  /** Set the max number of results for a paged batch
   *
   * @param val      int max number
   * @throws CalFacadeException on fatal error
   */
  public void setMaxResults(int val) throws CalFacadeException;

  /** Return the single object resulting from the query.
   *
   * @return Object          retrieved object or null
   * @throws CalFacadeException on fatal error
   */
  public Object getUnique() throws CalFacadeException;

  /** Return a list resulting from the query.
   *
   * @return List          list from query
   * @throws CalFacadeException on fatal error
   */
  public List<?> getList() throws CalFacadeException;

  /**
   * @return int number updated
   * @throws CalFacadeException on fatal error
   */
  public int executeUpdate() throws CalFacadeException;

  /** Update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @throws CalFacadeException on fatal error
   */
  public void update(Object obj) throws CalFacadeException;

  /** Merge and update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @return Object   the persiatent object
   * @throws CalFacadeException on fatal error
   */
  public Object merge(Object obj) throws CalFacadeException;

  /** Save a new object or update an object which may have been loaded in a
   * previous hibernate session
   *
   * @param obj
   * @throws CalFacadeException on fatal error
   */
  public void saveOrUpdate(Object obj) throws CalFacadeException;

  /** Copy the state of the given object onto the persistent object with the
   * same identifier. If there is no persistent instance currently associated
   * with the session, it will be loaded. Return the persistent instance.
   * If the given instance is unsaved or does not exist in the database,
   * save it and return it as a newly persistent instance. Otherwise, the
   * given instance does not become associated with the session.
   *
   * @param obj
   * @return Object
   * @throws CalFacadeException on fatal error
   */
  public Object saveOrUpdateCopy(Object obj) throws CalFacadeException;

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    A serializable key
   * @return Object
   * @throws CalFacadeException on fatal error
   */
  public Object get(Class<?> cl, Serializable id) throws CalFacadeException;

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    int key
   * @return Object
   * @throws CalFacadeException on fatal error
   */
  public Object get(Class<?> cl, int id) throws CalFacadeException;

  /** Save a new object.
   *
   * @param obj
   * @throws CalFacadeException on fatal error
   */
  public void save(Object obj) throws CalFacadeException;

  /** Delete an object
   *
   * @param obj
   * @throws CalFacadeException on fatal error
   */
  public void delete(Object obj) throws CalFacadeException;

  /** Save a new object with the given id. This should only be used for
   * restoring the db from a save.
   *
   * @param obj
   * @throws CalFacadeException on fatal error
   */
  public void restore(Object obj) throws CalFacadeException;

  /**
   * @param val
   * @throws CalFacadeException on fatal error
   */
  public void reAttach(BwUnversionedDbentity<?> val) throws CalFacadeException;

  /**
   * @param o
   * @throws CalFacadeException on fatal error
   */
  public void lockRead(Object o) throws CalFacadeException;

  /**
   * @param o
   * @throws CalFacadeException on fatal error
   */
  public void lockUpdate(Object o) throws CalFacadeException;

  /**
   * @throws CalFacadeException on hibernate error
   */
  public void flush() throws CalFacadeException;

  /**
   * @throws CalFacadeException on hibernate error
   */
  public void clear() throws CalFacadeException;

  /**
   * @throws CalFacadeException on fatal error
   */
  public void close() throws CalFacadeException;
}
