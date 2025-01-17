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

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.InputStream;
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
   */
  public void init(SessionFactory sessFactory);

  /**
   * @return Session
   */
  public Session getSession();

  /**
   * @return boolean true if open
   */
  public boolean isOpen();

  /** If we had a hibernate exception this will return non-null. The session
   * needs to be discarded.
   *
   * @return current exception or null.
   */
  public Throwable getException();

  /** Disconnect a session
   *
   */
  public void disconnect();

  /** set the flushmode
   *
   * @param val
   */
  public void setFlushMode(FlushMode val);

  /** Begin a transaction
   *
   */
  public void beginTransaction();

  /** Return true if we have a transaction started
   *
   * @return boolean
   */
  public boolean transactionStarted();

  /** Commit a transaction
   *
   */
  public void commit();

  /** Rollback a transaction
   *
   */
  public void rollback();

  /** Did we rollback the transaction?
   *
   * @return boolean
   */
  public boolean rolledback();

  /**
   * @return a timestamp from the db
   */
  public Timestamp getCurrentTimestamp();

  /**
   * @return a blob
   */
  Blob getBlob(byte[] val);

  /**
   * @return a blob
   */
  Blob getBlob(InputStream val, long length);

  /** Evict an object from the session.
   *
   * @param val          Object to evict
   */
  public void evict(Object val);

  /** Create a query ready for parameter replacement or execution.
   *
   * @param s             String hibernate query
   */
  public void createQuery(String s);

  /** Create a query ready for parameter replacement or execution and flag it
   * for no flush. This assumes that any queued changes will not affect the
   * result of the query.
   *
   * @param s             String hibernate query
   */
  public void createNoFlushQuery(String s);

  /**
   * @return query string
   */
  public String getQueryString();

  /** Mark the query as cacheable
   *
   */
  void cacheableQuery();

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      String parameter value
   */
  void setString(String parName, String parVal);

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      boolean parameter value
   */
  void setBool(String parName, boolean parVal);

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      int parameter value
   */
  void setInt(String parName, int parVal);

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      long parameter value
   */
  void setLong(String parName, long parVal);

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Object parameter value
   */
  void setEntity(String parName, Object parVal);

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Object parameter value
   */
  void setParameter(String parName, Object parVal);

  /** Set the named parameter with the given Collection
   *
   * @param parName     String parameter name
   * @param parVal      Collection parameter value
   */
  void setParameterList(String parName,
                        Collection<?> parVal);

  /** Set the first result for a paged batch
   *
   * @param val      int first index
   */
  public void setFirstResult(int val);

  /** Set the max number of results for a paged batch
   *
   * @param val      int max number
   */
  public void setMaxResults(int val);

  /** Return the single object resulting from the query.
   *
   * @return Object          retrieved object or null
   */
  public Object getUnique();

  /** Return a list resulting from the query.
   *
   * @return List          list from query
   */
  public List<?> getList();

  /**
   * @return int number updated
   */
  public int executeUpdate();

  /** Update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   */
  public void update(Object obj);

  /** Merge and update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @return Object   the persiatent object
   */
  public Object merge(Object obj);

  /** Save a new object or update an object which may have been loaded in a
   * previous hibernate session
   *
   * @param obj
   */
  public void saveOrUpdate(Object obj);

  /** Copy the state of the given object onto the persistent object with the
   * same identifier. If there is no persistent instance currently associated
   * with the session, it will be loaded. Return the persistent instance.
   * If the given instance is unsaved or does not exist in the database,
   * save it and return it as a newly persistent instance. Otherwise, the
   * given instance does not become associated with the session.
   *
   * @param obj
   * @return Object
   */
  public Object saveOrUpdateCopy(Object obj);

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    A serializable key
   * @return Object
   */
  public Object get(Class<?> cl, Serializable id);

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    int key
   * @return Object
   */
  public Object get(Class<?> cl, int id);

  /** Save a new object.
   *
   * @param obj
   */
  public void save(Object obj);

  /** Delete an object
   *
   * @param obj
   */
  public void delete(Object obj);

  /** Save a new object with the given id. This should only be used for
   * restoring the db from a save.
   *
   * @param obj
   */
  public void restore(Object obj);

  /**
   * @param val
   */
  public void reAttach(BwUnversionedDbentity<?> val);

  /**
   * @param o
   */
  public void lockRead(Object o);

  /**
   * @param o
   */
  public void lockUpdate(Object o);

  /**
   */
  public void flush();

  /**
   */
  public void clear();

  /**
   */
  public void close();
}
