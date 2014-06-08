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
package org.bedework.calsvci;

import org.bedework.caldav.util.sharing.InviteReplyType;
import org.bedework.caldav.util.sharing.InviteType;
import org.bedework.caldav.util.sharing.ShareResultType;
import org.bedework.caldav.util.sharing.ShareType;
import org.bedework.caldav.util.sharing.SharedAsType;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;

/** Interface for handling bedework sharing - looks like Apple sharing.
 *
 * @author Mike Douglass
 *
 */
public interface SharingI extends Serializable {
  /**
   * @param principalHref share as this user.
   * @param col MUST be a sharable collection
   * @param share the request
   * @return list of ok and !ok sharees
   * @throws CalFacadeException
   */
  ShareResultType share(final String principalHref,
                        final BwCalendar col,
                        final ShareType share) throws CalFacadeException;

  /**
   * @param col MUST be a sharable collection
   * @param share the request
   * @return list of ok and !ok sharees
   * @throws CalFacadeException
   */
  ShareResultType share(final BwCalendar col,
                        final ShareType share) throws CalFacadeException;

  /**
   */
  public static class ReplyResult {
    /** true for fine */
    private boolean ok;

    /** message if !ok */
    private String failMsg;

    /** Path to new alias */
    private SharedAsType sharedAs;

    /**
     * @param msg reason
     * @return a failure result
     */
    public static ReplyResult failed(final String msg) {
      final ReplyResult rr = new ReplyResult();

      rr.failMsg = msg;

      return rr;
    }

    /**
     * @param href display name for new sharee
     * @return a successful result
     */
    public static ReplyResult success(final String href) {
      final ReplyResult rr = new ReplyResult();

      rr.ok = true;
      rr.sharedAs = new SharedAsType(href);

      return rr;
    }

    /**
     * @return the ok flag
     */
    public boolean getOk() {
      return ok;
    }

    /**
     * @return the failure msg
     */
    public String getFailMsg() {
      return failMsg;
    }

    /**
     * @return the Sharedas object
     */
    public SharedAsType getSharedAs() {
      return sharedAs;
    }
  }

  /**
   * @param col MUST be current sharees home
   * @param reply the request
   * @return a ReplyResult object.
   * @throws CalFacadeException
   */
  ReplyResult reply(final BwCalendar col,
                    final InviteReplyType reply) throws CalFacadeException;

  /**
   * @param col
   * @return current invitations
   * @throws CalFacadeException
   */
  InviteType getInviteStatus(final BwCalendar col) throws CalFacadeException;

  /** Do any cleanup necessary for a collection delete.
   *
   * @param col
   * @throws CalFacadeException
   */
  void delete(final BwCalendar col) throws CalFacadeException;

  /** Publish the collection - that is make it available for subscriptions.
   *
   * @param col
   * @throws CalFacadeException
   */
  void publish(BwCalendar col) throws CalFacadeException;

  /** Unpublish the collection - that is make it unavailable for subscriptions
   * and remove any existing subscriptions.
   *
   * @param col
   * @throws CalFacadeException
   */
  void unpublish(BwCalendar col) throws CalFacadeException;

  /**
   */
  class SubscribeResult {
    /** Path to alias */
    public String path;

    /** True if user was already subscribed */
    public boolean alreadySubscribed;
  }

  /** Subscribe to the collection - must be a published collection.
   *
   * @param colPath
   * @param subscribedName name for new alias
   * @return path of new alias and flag
   * @throws CalFacadeException
   */
  SubscribeResult subscribe(String colPath,
                            String subscribedName) throws CalFacadeException;

  /** Subscribe to an external url.
   *
   * @param extUrl
   * @param subscribedName name for new alias
   * @param refresh - refresh rate in minutes <= 0 for default
   * @param remoteId - may be null
   * @param remotePw  - may be null
   * @return path of new alias and flag
   * @throws CalFacadeException
   */
  SubscribeResult subscribeExternal(String extUrl,
                                    String subscribedName,
                                    int refresh,
                                    String remoteId,
                                    String remotePw) throws CalFacadeException;

  /** Unsubscribe the collection - that is col MUST be an alias to
   * another collection. Update any existing invite status for the
   * current principal.
   *
   * @param col alias to unsubscribe
   * @throws CalFacadeException
   */
  void unsubscribe(BwCalendar col) throws CalFacadeException;
}
