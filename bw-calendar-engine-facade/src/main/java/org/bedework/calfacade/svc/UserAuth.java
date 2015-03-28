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
package org.bedework.calfacade.svc;

import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** An interface to define an application based authorisation method.
 *
 * <p>The actual authorisation of the user will be site specific.
 * For example, if we are using a roles based scheme, the implementation of
 * this interface will check the current users role.
 *
 * <p>There is no method to add a user as we are simply giving users certain
 * rights. The underlying implementation may choose to use a database and
 * remove those entries for which there are no special rights.
 *
 * @author Mike Douglass douglm@rpi.edu
 * @version 2.2
 */
public interface UserAuth extends Serializable {
  /** Define the various access levels. Note these are powers of two so
   * we can add them up.
   */
  public static final int noPrivileges = 0;

  /** A user who can add public events
   */
  public static final int publicEventUser = 64;

  /** A user who can administer any content
   */
  public static final int contentAdminUser = 128;

  /** A user who can approve content
   */
  public static final int approverUser = 256;

  /** Useful value.
   */
  public static final int allAuth = publicEventUser +
                                    contentAdminUser +
                                    approverUser;

  /** Class to be implemented by caller and passed during init.
   */
  public static abstract class CallBack implements Serializable {
    /**
     * @param account the href
     * @return BwPrincipal represented by account
     * @throws CalFacadeException
     */
    public abstract BwPrincipal getPrincipal(String account) throws CalFacadeException;

    /** Allows this class to be passed to other admin classes
     *
     * @return UserAuth
     * @throws CalFacadeException
     */
    public abstract UserAuth getUserAuth() throws CalFacadeException;

    /** Delete the entry
     *
     * @param val the authuser object
     * @throws CalFacadeException
     */
    public abstract void delete(final BwAuthUser val) throws CalFacadeException;

    /** Save a new entry
     *
     * @param val the authuser object
     * @throws CalFacadeException
     */
    public abstract void add(final BwAuthUser val) throws CalFacadeException;

    /** Update an existing entry
     *
     * @param val the authuser object
     * @throws CalFacadeException
     */
    public abstract void update(final BwAuthUser val) throws CalFacadeException;

    /**
     * @param href - principal href for the entry
     * @return auth user with preferences or null
     * @throws CalFacadeException
     */
    public abstract BwAuthUser getAuthUser(final String href) throws CalFacadeException;

    /**
     * @return list of all auth user entries
     * @throws CalFacadeException
     */
    public abstract List<BwAuthUser> getAll() throws CalFacadeException;
  }

  /* ====================================================================
   *  The following affect the state of the current user.
   * ==================================================================== */

  /** Initialise the implementing object. This method may be called repeatedly
   * with the same or different classes of object.
   *
   * <p>This is not all that well-defined. This area falls somewhere
   * between the back-end and the front-end, depending upon how a site
   * implements its authorisation.
   *
   * <p>Any implementation is free to ignore the call
   * altogether.
   *
   * @param  cb        CallBack object
   * @exception CalFacadeException If there's a problem
   */
  void initialise(CallBack cb) throws CalFacadeException;

  /** ===================================================================
   *  The following should not change the state of the current users
   *  access which is set and retrieved with the above methods.
   *  =================================================================== */

  /** Show whether user entries can be displayed or modified with this
   * class. Some sites may use other mechanisms.
   *
   * <p>This may need supplementing with changes to the jsp. For example,
   * it's hard to deal programmatically with the case of directory/roles
   * based authorisation and db based user information.
   *
   * @return boolean    true if user maintenance is implemented.
   */
  boolean getUserMaintOK();

  /** Add the user entry
   *
   * @param  val      AuthUserVO users entry
   * @throws CalFacadeException
   */
  void addUser(BwAuthUser val) throws CalFacadeException;

  /** Update the user entry
   *
   * @param  val      AuthUserVO users entry
   * @throws CalFacadeException
   */
  void updateUser(BwAuthUser val) throws CalFacadeException;

  /** Return the given authorised user. Will always return an entry (except for
   * exceptional conditions.) An unauthorised user will have a usertype of
   * noPrivileges.
   *
   * @param  userid        String user id
   * @return BwAuthUser    users entry
   * @throws CalFacadeException
   */
  BwAuthUser getUser(String userid) throws CalFacadeException;

  /** Return a collection of all authorised users
   *
   * @return Collection      of BwAuthUser for users with any special authorisation.
   * @throws CalFacadeException
   */
  Collection<BwAuthUser> getAll() throws CalFacadeException;
}
