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

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.Serializable;
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
  void init(SessionFactory sessFactory);

  /**
   * @return Session
   */
  Session getSession();

  /**
   * @return boolean true if open
   */
  boolean isOpen();

  /** If we had a database exception this will return non-null. The session
   * needs to be discarded.
   *
   * @return current exception or null.
   */
  Throwable getException();

  /** Begin a transaction
   *
   */
  void beginTransaction();

  /** Return true if we have a transaction started
   *
   * @return boolean
   */
  boolean transactionStarted();

  /** Commit a transaction
   *
   */
  void commit();

  /** Rollback a transaction
   *
   */
  void rollback();

  /** Did we rollback the transaction?
   *
   * @return boolean
   */
  boolean rolledback();

  /**
   * @return a timestamp from the db
   */
  Timestamp getCurrentTimestamp();

  /** Evict an object from the session.
   *
   * @param val          Object to evict
   */
  void evict(Object val);

  /** Create a query ready for parameter replacement or execution.
   *
   * @param s             String hibernate query
   */
  void createQuery(String s);

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
  void setFirstResult(int val);

  /** Set the max number of results for a paged batch
   *
   * @param val      int max number
   */
  void setMaxResults(int val);

  /** Return the single object resulting from the query.
   *
   * @return Object          retrieved object or null
   */
  Object getUnique();

  /** Return a list resulting from the query.
   *
   * @return List          list from query
   */
  List<?> getList();

  /**
   * @return int number updated
   */
  int executeUpdate();

  /** Update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   */
  void update(Object obj);

  /** Merge and update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @return Object   the persiatent object
   */
  Object merge(Object obj);

  /** Save a new object or update an object which may have been loaded in a
   * previous hibernate session
   *
   * @param obj
   */
  void saveOrUpdate(Object obj);

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    A serializable key
   * @return Object
   */
  Object get(Class<?> cl, Serializable id);

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    int key
   * @return Object
   */
  Object get(Class<?> cl, int id);

  /** Save a new object.
   *
   * @param obj
   */
  void save(Object obj);

  /** Delete an object
   *
   * @param obj
   */
  void delete(Object obj);

  /**
   */
  void flush();

  /**
   */
  void clear();

  /**
   */
  void close();
}
