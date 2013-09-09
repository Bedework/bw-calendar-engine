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

import org.bedework.calcore.CalintfHelper;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.base.AlarmsEntity;
import org.bedework.calfacade.base.AttachmentsEntity;
import org.bedework.calfacade.base.AttendeesEntity;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.base.CommentedEntity;
import org.bedework.calfacade.base.ContactedEntity;
import org.bedework.calfacade.base.DescriptionEntity;
import org.bedework.calfacade.base.PropertiesEntity;
import org.bedework.calfacade.base.RecurrenceEntity;
import org.bedework.calfacade.base.ResourcedEntity;
import org.bedework.calfacade.base.SummaryEntity;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.sysevents.events.SysEvent;

import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.PrivilegeDefs;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Collection;

/** Class used as basis for a number of helper classes.
 *
 * @author Mike Douglass   douglm@bedework.edu
 */
public abstract class CalintfHelperHib extends CalintfHelper
          implements CalintfDefs, PrivilegeDefs, Serializable {
  /**
   */
  public interface CalintfHelperHibCb extends Serializable {
    /**
     * @return HibSession
     * @throws CalFacadeException
     */
    public HibSession getSess() throws CalFacadeException;

    /** Only valid during a transaction.
     *
     * @return a timestamp from the db
     * @throws CalFacadeException
     */
    public Timestamp getCurrentTimestamp() throws CalFacadeException;

    /**
     * @return BwStats
     * @throws CalFacadeException
     */
    public BwStats getStats() throws CalFacadeException;

    /** Used to fetch a calendar from the cache - assumes any access
     *
     * @param path
     * @return BwCalendar
     * @throws CalFacadeException
     */
    public BwCalendar getCollection(String path) throws CalFacadeException;

    /** Used to fetch a calendar from the cache
     *
     * @param path
     * @param desiredAccess
     * @param alwaysReturn
     * @return BwCalendar
     * @throws CalFacadeException
     */
    public BwCalendar getCollection(String path,
                                    int desiredAccess,
                                    boolean alwaysReturn) throws CalFacadeException;

    /** Called to notify container that an event occurred. This method should
     * queue up notifications until after transaction commit as consumers
     * should only receive notifications when the actual data has been written.
     *
     * @param ev
     * @throws CalFacadeException
     */
    public void postNotification(final SysEvent ev) throws CalFacadeException;

    /**
     * @return true if restoring
     */
    public boolean getForRestore();
  }

  private CalintfHelperHibCb calintfCb;

  /**
   * @param calintfCb
   */
  public CalintfHelperHib(final CalintfHelperHibCb calintfCb) {
    this.calintfCb = calintfCb;
  }

  protected HibSession getSess() throws CalFacadeException {
    return calintfCb.getSess();
  }

  /** Only valid during a transaction.
   *
   * @return a timestamp from the db
   * @throws CalFacadeException
   */
  public Timestamp getCurrentTimestamp() throws CalFacadeException {
    return calintfCb.getCurrentTimestamp();
  }

  protected BwCalendar getCollection(final String path) throws CalFacadeException {
    return calintfCb.getCollection(path);
  }

  protected boolean getForRestore() {
    return calintfCb.getForRestore();
  }

  protected BwCalendar getEntityCollection(final String path,
                                           final int nonSchedAccess,
                                           final boolean scheduling,
                                           final boolean alwaysReturn) throws CalFacadeException {
    int desiredAccess;

    if (!scheduling) {
      desiredAccess = nonSchedAccess;
    } else {
      desiredAccess = privAny;
    }

    BwCalendar cal = calintfCb.getCollection(path, desiredAccess,
                                             alwaysReturn | scheduling);
    if (cal == null) {
      return cal;
    }

    if (!cal.getCalendarCollection()) {
      throwException(new CalFacadeAccessException());
    }

    if (!scheduling) {
      return cal;
    }

    CurrentAccess ca;

    if (cal.getCalType() == BwCalendar.calTypeInbox) {
      ca = access.checkAccess(cal, privScheduleDeliver, true);
      if (!ca.getAccessAllowed()) {
        // try old style
        ca = access.checkAccess(cal, privScheduleRequest, alwaysReturn);
      }
    } else if (cal.getCalType() == BwCalendar.calTypeOutbox) {
      ca = access.checkAccess(cal, privScheduleSend, true);
      if (!ca.getAccessAllowed()) {
        // try old style
        ca = access.checkAccess(cal, privScheduleReply, alwaysReturn);
      }
    } else {
      throw new CalFacadeAccessException();
    }

    if (!ca.getAccessAllowed()) {
      return null;
    }

    return cal;
  }

  /** Called to notify container that an event occurred. This method should
   * queue up notifications until after transaction commit as consumers
   * should only receive notifications when the actual data has been written.
   *
   * @param ev
   * @throws CalFacadeException
   */
  public void postNotification(final SysEvent ev) throws CalFacadeException {
    calintfCb.postNotification(ev);
  }

  /** Just encapsulate building a query out of a number of parts
   *
   * @param parts
   * @throws CalFacadeException
   */
  public void makeQuery(final String[] parts) throws CalFacadeException {
    StringBuilder sb = new StringBuilder();

    for (String s: parts) {
      sb.append(s);
    }

    getSess().createQuery(sb.toString());
  }

  protected void tombstoneEntity(final BwShareableContainedDbentity val) {
    if (val instanceof AlarmsEntity) {
      clearCollection(((AlarmsEntity)val).getAlarms());
    }

    if (val instanceof AttachmentsEntity) {
      clearCollection(((AttachmentsEntity)val).getAttachments());
    }

    if (val instanceof AttendeesEntity) {
      clearCollection(((AttendeesEntity)val).getAttendees());
    }

    if (val instanceof CategorisedEntity) {
      clearCollection(((CategorisedEntity)val).getCategories());
    }

    if (val instanceof CommentedEntity) {
      clearCollection(((CommentedEntity)val).getComments());
    }

    if (val instanceof ContactedEntity) {
      clearCollection(((ContactedEntity)val).getContacts());
    }

    if (val instanceof DescriptionEntity) {
      clearCollection(((DescriptionEntity)val).getDescriptions());
    }

    if (val instanceof RecurrenceEntity) {
      RecurrenceEntity re = (RecurrenceEntity)val;

      re.setRecurring(false);
      clearCollection(re.getExdates());
      clearCollection(re.getExrules());
      clearCollection(re.getRdates());
      clearCollection(re.getRrules());
    }

    if (val instanceof ResourcedEntity) {
      clearCollection(((ResourcedEntity)val).getResources());
    }

    if (val instanceof SummaryEntity) {
      clearCollection(((SummaryEntity)val).getSummaries());
    }

    if (val instanceof PropertiesEntity) {
      clearCollection(((PropertiesEntity)val).getProperties());
    }
  }

  protected void clearCollection(final Collection val) {
    if (val == null) {
      return;
    }

    val.clear();
  }

  protected void throwException(final CalFacadeException cfe) throws CalFacadeException {
    getSess().rollback();
    throw cfe;
  }

  protected void throwException(final String pname) throws CalFacadeException {
    getSess().rollback();
    throw new CalFacadeException(pname);
  }

  protected void throwException(final String pname, final String extra) throws CalFacadeException {
    getSess().rollback();
    throw new CalFacadeException(pname, extra);
  }
}
