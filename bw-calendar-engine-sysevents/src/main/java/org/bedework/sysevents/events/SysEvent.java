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
package org.bedework.sysevents.events;

import org.bedework.sysevents.NotificationException;

import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.sss.util.ToString;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.LastModified;

/**
 * A system event - like adding something, updating something, startup, shutdown
 * etc.
 * <p>
 * The Notifications interface uses these to carry information about system
 * events. Listeners can be registered for particular system event types.
 * <p>
 * sub-classes should define the compareTo() and hashCode methods. They should
 * also define fields and methods appropriate to the type of event.
 * <p>
 * For example, the ENTITY_UPDATE event should contain enough information to
 * identify the entity, e.g. the path for a calendar or a uid for the event. It
 * is probably NOT a good idea to have a reference to the actual entity.
 * <p>
 * Some of these events may be persisted to ensure their survival across system
 * restarts and their generation is considered part of the operation.
 * <p>
 * Note that we do not modify system events once they are persisted. We retrieve
 * them and delete them from the database.
 *
 * @author Mike Douglass
 */
public class SysEvent implements SysEventBase, Comparable<SysEvent> {
  private static final long serialVersionUID = 1L;

  private SysCode sysCode;

  private SysEvent related;

  private boolean indexable;

  /** UTC datetime */
  private String dtstamp;

  /**
   * Ensure uniqueness - dtstamp only down to second.
   */
  private int sequence;

  /**
   * Constructor
   *
   * @param sysCode
   */
  public SysEvent(final SysCode sysCode) {
    this.sysCode = sysCode;

    indexable = sysCode.getIndexable();
    updateDtstamp();
  }

  /*
   * (non-Javadoc)
   * @see org.bedework.sysevents.events.SysEventBase#getSysCode()
   */
  @Override
  public SysCode getSysCode() {
    return sysCode;
  }

  /**
   * @param val
   */
  public void setDtstamp(final String val) {
    dtstamp = val;
  }

  /**
   * @return String dtstamp
   */
  public String getDtstamp() {
    return dtstamp;
  }

  /**
   * Set the sequence
   *
   * @param val
   *          sequence number
   */
  public void setSequence(final int val) {
    sequence = val;
  }

  /**
   * Get the sequence
   *
   * @return int the sequence
   */
  public int getSequence() {
    return sequence;
  }

  /**
   * @return true if this indicates an indexable change may have occurred
   */
  public boolean getIndexable() {
    return indexable;
  }

  /**
   * This allows for linking together related events. For example a calendar
   * change event might be triggered by an event being added.
   *
   * @param val
   */
  public void setRelated(final SysEvent val) {
    related = val;
  }

  /**
   * @return SysEvent
   */
  public SysEvent getRelated() {
    return related;
  }

  /*
   * ====================================================================
   * Factory methods
   * ====================================================================
   */

  /**
   * @param code
   * @param val
   * @param millisecs - time for stats - e.g. time to process login
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makePrincipalEvent(final SysCode code,
                                            final AccessPrincipal val,
                                            final long millisecs) throws NotificationException {
    SysEvent sysev = new PrincipalEvent(code, val.getPrincipalRef(), millisecs);

    return sysev;
  }

  /**
   * @param label
   * @param millisecs - time for stats
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeTimedEvent(final String label,
                                        final long millisecs) throws NotificationException {
    SysEvent sysev = new TimedEvent(SysEvent.SysCode.TIMED_EVENT,
                                    label, millisecs);

    return sysev;
  }

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeCollectionUpdateEvent(final SysCode code,
                                                   final String authPrincipalHref,
                                                   final String ownerHref,
                                                   final String href,
                                                   final boolean shared)
                                                       throws NotificationException {
    SysEvent sysev = new CollectionUpdateEvent(code,
                                               authPrincipalHref,
                                               ownerHref, href, shared);

    return sysev;
  }

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @param publick
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeCollectionDeletedEvent(final SysCode code,
                                                    final String authPrincipalHref,
                                                    final String ownerHref,
                                                    final String href,
                                                    final boolean shared,
                                                    final boolean publick)
                                                        throws NotificationException {
    SysEvent sysev =
        new CollectionDeletedEvent(code,
                                   authPrincipalHref,
                                   ownerHref, href, shared, publick);

    return sysev;
  }

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @param oldHref
   * @param oldShared
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeCollectionMovedEvent(final SysCode code,
                                                  final String authPrincipalHref,
                                                  final String ownerHref,
                                                  final String href,
                                                  final boolean shared,
                                                  final String oldHref,
                                                  final boolean oldShared)
                                                      throws NotificationException {
    SysEvent sysev =
        new CollectionMovedEvent(code,
                                 authPrincipalHref,
                                 ownerHref, href, shared, oldHref, oldShared);

    return sysev;
  }

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @param publick
   * @param uid
   * @param rid
   * @param notification - as the event won't be around
   * @param targetPrincipalHref
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeEntityDeletedEvent(final SysCode code,
                                                final String authPrincipalHref,
                                                final String ownerHref,
                                                final String href,
                                                final boolean shared,
                                                final boolean publick,
                                                final String uid,
                                                final String rid,
                                                final String notification,
                                                final String targetPrincipalHref)
                                                    throws NotificationException {
    SysEvent sysev =
        new EntityDeletedEvent(code,
                               authPrincipalHref,
                               ownerHref,
                               href, shared, publick, uid, rid,
                               notification,
                               targetPrincipalHref);
    return sysev;
  }

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @param uid
   * @param rid
   * @param notification
   * @param targetPrincipalHref
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeEntityUpdateEvent(final SysCode code,
                                               final String authPrincipalHref,
                                               final String ownerHref,
                                               final String href,
                                               final boolean shared,
                                               final String uid,
                                               final String rid,
                                               final String notification,
                                               final String targetPrincipalHref)
                                                   throws NotificationException {
    SysEvent sysev =
        new EntityUpdateEvent(code,
                              authPrincipalHref,
                              ownerHref, href, shared, uid, rid, notification,
                              targetPrincipalHref);

    return sysev;
  }

  /**
   * @param name
   * @param strValue
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeStatsEvent(final String name,
                                        final String strValue)
                                            throws NotificationException {
    SysEvent sysev = new StatsEvent(name, strValue);

    return sysev;
  }

  /**
   * @param name
   * @param longValue
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeStatsEvent(final String name,
                                        final Long longValue)
                                            throws NotificationException {
    SysEvent sysev = new StatsEvent(name, longValue);

    return sysev;
  }

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @param oldHref
   * @param oldShared
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeEntityMovedEvent(final SysCode code,
                                              final String authPrincipalHref,
                                              final String ownerHref,
                                              final String href,
                                              final boolean shared,
                                              final String oldHref,
                                              final boolean oldShared)
                                                  throws NotificationException {
    SysEvent sysev = new EntityMovedEvent(code,
                                          authPrincipalHref,
                                          ownerHref, href, shared,
                                          oldHref, oldShared);

    return sysev;
  }

  /**
   * @param code
   * @param ownerHref
   * @param name
   * @param uid
   * @param rid
   * @param inBox
   * @return SysEvent
   */
  public static SysEvent makeEntityQueuedEvent(final SysCode code,
                                               final String ownerHref,
                                               final String name,
                                               final String uid,
                                               final String rid,
                                               final boolean inBox) {
    SysEvent sysev = new EntityQueuedEvent(code, ownerHref, name, uid, rid, inBox);

    return sysev;
  }

  /**
   * Update last mod fields
   */
  private void updateDtstamp() {
    setDtstamp(new LastModified(new DateTime(true)).getValue());
    setSequence(getSequence() + 1);
  }

  /*
   * ==================================================================== Object
   * methods
   * ====================================================================
   */

  @Override
  public int compareTo(final SysEvent val) {
    return (sysCode.compareTo(val.sysCode));
  }

  @Override
  public int hashCode() {
    return sysCode.hashCode();
  }

  /** Add our stuff to the ToString object
  *
  * @param ts    ToString for result
  */
 public void toStringSegment(final ToString ts) {
   ts.append("sysCode", String.valueOf(getSysCode()));
   ts.append("dtstamp", getDtstamp());
   ts.append("sequence", getSequence());
   ts.append("indexable", getIndexable());
 }

  /*
   * (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
