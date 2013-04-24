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

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;

import java.io.Serializable;
import java.util.Collection;

/** This clas represents the current state of a bedework client - most probably
 * the web clients.
 *
 * @author Mike Douglass
 *
 */
public interface ClientStateI extends Serializable {
  /** Called if anything is changed which affects the state of the client, e.g
   * switching display flags, deleting collections.
   *
   * @throws CalFacadeException
   */
  public abstract void flush() throws CalFacadeException;

  /* ====================================================================
   *                   Current selection
   * This defines how we select events to display.
   * ==================================================================== */

  /** Set the view to the given named view. Null means reset to default.
   * Unset current calendar.
   *
   * @param  val     String view name - null for default
   * @return boolean false - view not found.
   * @throws CalFacadeException
   */
  public abstract boolean setCurrentView(String val) throws CalFacadeException;

  /** Set the view to the given view object. Null means reset to default.
   * Unset current calendar.
   *
   * @param  val     view name - null for default
   * @throws CalFacadeException
   */
  public abstract void setCurrentView(BwView val) throws CalFacadeException;

  /** Get the current view we have set
   *
   * @return BwView    named Collection of Collections or null for default
   * @throws CalFacadeException
   */
  public abstract BwView getCurrentView() throws CalFacadeException;

  /** Set the virtual path and unset any current view.
   *
   * <p>A virtual path is the apparent path for a user looking at an explorer
   * view of collections.
   *
   * <p>We might have,
   * <pre>
   *    home-->Arts-->Theatre
   * </pre>
   *
   * <p>In reality the Arts collection might be an alias to another alias which
   * is an alias to a collection containing aliases including "Theatre".
   *
   * <p>So the real picture might be something like...
   * <pre>
   *    home-->Arts             (categories="ChemEng")
   *            |
   *            V
   *           Arts             (categories="Approved")
   *            |
   *            V
   *           Arts-->Theatre   (categories="Arts" AND categories="Theatre")
   *                     |
   *                     V
   *                    MainCal
   * </pre>
   * where the vertical links are aliasing. The importance of thsi is that
   * each alias might introduce another filtering term, the intent of which is
   * to restrict the retrieval to a specific subset. The parenthesized terms
   * represent example filters.
   *
   * <p>The desired filter is the ANDing of all the above.
   *
   * @param  vpath  a String virtual path
   * @return false for bad path
   * @throws CalFacadeException
   */
  public abstract boolean setVirtualPath(String vpath) throws CalFacadeException;

  /**
   * @return non-null if setVirtualPath was called succesfully
   * @throws CalFacadeException
   */
  public abstract String getVirtualPath() throws CalFacadeException;

  /**
   * @return BwCalendar
   * @throws CalFacadeException
   */
  public abstract BwCalendar getCurrentCollection()
          throws CalFacadeException;

  /** Given a possible collection object return whatever is appropriate for the
   * current view.
   *
   * <p>If the collection is non-null go with that, otherwise go with the
   * current selected collection or the current selected view.
   *
   * @param cal
   * @return BwFilter or null
   * @throws CalFacadeException
   */
  public abstract FilterBase getViewFilter(BwCalendar cal)
          throws CalFacadeException;

  /** Attempt to set the color for the given events. If there is no appropriate
   * mapping the event color will be set to null.
   *
   * @param eis
   */
  public abstract void setColor(Collection<EventInfo> eis);
}
