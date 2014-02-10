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
import org.bedework.util.misc.ToString;

import org.bedework.access.AccessPrincipal;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.LastModified;

import java.util.ArrayList;
import java.util.List;

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

  private boolean indexed;

  /** UTC datetime */
  private String dtstamp;

  /**
   * Ensure uniqueness - dtstamp only down to second.
   */
  private int sequence;

  /**
   * Constructor
   *
   * @param sysCode the system event code
   */
  public SysEvent(final SysCode sysCode) {
    this.sysCode = sysCode;

    indexable = sysCode.getIndexable();
    updateDtstamp();
  }

  @Override
  public SysCode getSysCode() {
    return sysCode;
  }

  @Override
  public List<Attribute> getMessageAttributes() {
    final List<Attribute> attrs = new ArrayList<>();

    attrs.add(new Attribute("syscode", String.valueOf(getSysCode())));
    attrs.add(new Attribute("indexable",
                            String.valueOf(getIndexable())));
    attrs.add(new Attribute("indexed",
                            String.valueOf(getIndexed())));
    attrs.add(new Attribute("changeEvent",
                            String.valueOf(getSysCode().getChangeEvent())));

    return attrs;
  }

  /**
   * @param val a date stamp
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
   * This, if true, flags that an indexable event has already been
   * indexed. This is needed to make test suites run better by
   * reducing the delay between creation and indexing.
   *
   * <p>It is not recommended that the system be run in that state
   * as indexing can cause a significant delay for clients,
   * especially for recurring events.</p>
   *
   * @param val true for already indexed
   */
  public void setIndexed(final boolean val) {
    indexed = val;
  }

  /**
   * @return true if entity already indexed
   */
  public boolean getIndexed() {
    return indexed;
  }

  /**
   * This allows for linking together related events. For example a calendar
   * change event might be triggered by an event being added.
   *
   * @param val the related system event
   */
  public void setRelated(final SysEvent val) {
    related = val;
  }

  /**
   * @return  the related system event
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
   * @param code the system event code
   * @param pr - the principal
   * @param millisecs - time for stats - e.g. time to process login
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makePrincipalEvent(final SysCode code,
                                            final AccessPrincipal pr,
                                            final long millisecs) throws NotificationException {
    return new PrincipalEvent(code, pr.getPrincipalRef(), millisecs);
  }

  /**
   * @param label a useful label
   * @param millisecs - time for stats
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeTimedEvent(final String label,
                                        final long millisecs) throws NotificationException {
    return new TimedEvent(SysEvent.SysCode.TIMED_EVENT,
                          label, millisecs);
  }

  /**
   * @param code the system event code
   * @param authPrincipalHref principal href of authenticated user
   * @param ownerHref principal href of the owner
   * @param href of the entity
   * @param shared true if this is shared (for notifications)
   * @param indexed - true if already indexed
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeCollectionUpdateEvent(final SysCode code,
                                                   final String authPrincipalHref,
                                                   final String ownerHref,
                                                   final String href,
                                                   final boolean shared,
                                                   final boolean indexed)
                                                       throws NotificationException {
    return new CollectionUpdateEvent(code,
                                     authPrincipalHref,
                                     ownerHref, href,
                                     shared, indexed);
  }

  /**
   * @param code the system event code
   * @param authPrincipalHref principal href of authenticated user
   * @param ownerHref principal href of the owner
   * @param href of the entity
   * @param shared true if this is shared (for notifications)
   * @param publick true if it's flagged as public
   * @param indexed - true if already indexed
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeCollectionDeletedEvent(final SysCode code,
                                                    final String authPrincipalHref,
                                                    final String ownerHref,
                                                    final String href,
                                                    final boolean shared,
                                                    final boolean publick,
                                                    final boolean indexed)
                                                        throws NotificationException {
    return new CollectionDeletedEvent(code,
                                      authPrincipalHref,
                                      ownerHref, href,
                                      shared, publick, indexed);
  }

  /**
   * @param code the system event code
   * @param authPrincipalHref principal href of authenticated user
   * @param ownerHref principal href of the owner
   * @param href of the entity
   * @param shared true if this is shared (for notifications)
   * @param indexed - true if already indexed
   * @param oldHref where it was
   * @param oldShared true if this was shared (for notifications)
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeCollectionMovedEvent(final SysCode code,
                                                  final String authPrincipalHref,
                                                  final String ownerHref,
                                                  final String href,
                                                  final boolean shared,
                                                  final boolean indexed,
                                                  final String oldHref,
                                                  final boolean oldShared)
                                                      throws NotificationException {
    return new CollectionMovedEvent(code,
                                    authPrincipalHref,
                                    ownerHref, href,
                                    shared, indexed,
                                    oldHref, oldShared);
  }

  /**
   * @param code the system event code
   * @param authPrincipalHref principal href of authenticated user
   * @param ownerHref principal href of the owner
   * @param href of the entity
   * @param shared true if this is shared (for notifications)
   * @param publick true if flagged public
   * @param indexed - true if already indexed
   * @param type of entity
   * @param rid recurrence id
   * @param notification - as the event won't be around
   * @param targetPrincipalHref - possibly unused
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeEntityDeletedEvent(final SysCode code,
                                                final String authPrincipalHref,
                                                final String ownerHref,
                                                final String href,
                                                final boolean shared,
                                                final boolean publick,
                                                final boolean indexed,
                                                final String type,
                                                final String rid,
                                                final String notification,
                                                final String targetPrincipalHref)
                                                    throws NotificationException {
    return new EntityDeletedEvent(code,
                                  authPrincipalHref,
                                  ownerHref,
                                  href,
                                  shared, publick, indexed,
                                  type, rid,
                                  notification,
                                  targetPrincipalHref);
  }

  /**
   * @param code the system event code
   * @param authPrincipalHref principal href of authenticated user
   * @param ownerHref principal href of the owner
   * @param href of the entity
   * @param shared true if this is shared (for notifications)
   * @param indexed - true if already indexed
   * @param rid recurrence id
   * @param notification the message
   * @param targetPrincipalHref - possibly unused
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeEntityUpdateEvent(final SysCode code,
                                               final String authPrincipalHref,
                                               final String ownerHref,
                                               final String href,
                                               final boolean shared,
                                               final boolean indexed,
                                               final String rid,
                                               final String notification,
                                               final String targetPrincipalHref)
          throws NotificationException {
    return new EntityUpdateEvent(code,
                                 authPrincipalHref,
                                 ownerHref, href,
                                 shared, indexed,
                                 rid, notification,
                                 targetPrincipalHref);
  }

  /* *
   * @param name for the event
   * @param strValue a string value
   * @return SysEvent
   * @throws NotificationException
   * /
  public static SysEvent makeStatsEvent(final String name,
                                        final String strValue)
                                            throws NotificationException {
    return new StatsEvent(name, strValue);
  }*/

  /**
   * @param name for the event
   * @param longValue a value - often a time
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeStatsEvent(final String name,
                                        final Long longValue)
          throws NotificationException {
    return new StatsEvent(name, longValue);
  }

  /**
   * @param code the system event code
   * @param authPrincipalHref principal href of authenticated user
   * @param ownerHref principal href of the owner
   * @param href of the entity
   * @param shared true if this is shared (for notifications)
   * @param oldHref where it was
   * @param oldShared true if this was shared (for notifications)
   * @return SysEvent
   * @throws NotificationException
   */
  public static SysEvent makeEntityMovedEvent(final SysCode code,
                                              final String authPrincipalHref,
                                              final String ownerHref,
                                              final String href,
                                              final boolean shared,
                                              final boolean indexed,
                                              final String oldHref,
                                              final boolean oldShared)
          throws NotificationException {
    return new EntityMovedEvent(code,
                                authPrincipalHref,
                                ownerHref, href,
                                shared, indexed,
                                oldHref, oldShared);
  }

  /**
   * @param code the system event code
   * @param ownerHref principal href of the owner
   * @param name of the event
   * @param inBox true for an inbound event
   * @return SysEvent
   */
  public static SysEvent makeEntityQueuedEvent(final SysCode code,
                                               final String ownerHref,
                                               final String name,
                                               final boolean inBox) {
    return new EntityQueuedEvent(code,
                                 ownerHref,
                                 name,
                                 inBox);
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
   ts.append("indexed", getIndexed());
 }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
