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

import java.io.Serializable;
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
public interface SysEventBase extends Serializable {
  /** */
  static final long serialVersionUID = 1L;

  /** */
  final static int info = 1;

  /** */
  final static int trace = 2;

  /** */
  final static int debug = 4;

  /** */
  final static int warn = 8;

  /** */
  final static int severe = 16;

  /** */
  final static int fatal = 32;

  /*
   * Define access levels. Some events are privileged information.
   */

  /** privileged users only */
  final static int priv = 1;

  /** user information only */
  final static int user = 2;

  /** */
  static final boolean isIndexable = true;

  /** */
  static final boolean notIndexable = false;

  /** */
  static final boolean isNotifiableEvent = true;

  /**
   * Define system events.
   *
   * @author Mike Douglass
   */
  enum SysCode {
    /** */
    STARTUP(info, priv),

    /** */
    SHUTDOWN(info, priv),

    /** */
    EXCEPTION_WARN(warn, priv),

    /** */
    EXCEPTION_FATAL(fatal, priv),

    /* ========= System Activity =========== */

    /** HTTP Request arrived */
    WEB_IN(info, priv),

    /** HTTP Response going out */
    WEB_OUT(info, priv),

    /** CalDAV HTTP Request arrived */
    CALDAV_IN(info, priv),

    /** CalDAV HTTP Response going out */
    CALDAV_OUT(info, priv),

    /** Stats */
    STATS(info, priv),

    /** Timed events */
    TIMED_EVENT(info, priv),

    /* ========= Users =========== */

    /** A user was added to the system */
    NEW_USER(info, priv),

    /** A user logged in - probably web interface only */
    USER_LOGIN(info, priv),

    /** A user logged out - probably web interface only */
    USER_LOGOUT(info, priv),

    /**
     * New svc object initialize - this will happen a lot in the CalDAV and HTTP
     * worlds - once per request. We will not see a login for CalDAV
     */
    USER_SVCINIT(info, priv),

    /* ========= Collections =========== */

    /** A collection was added */
    COLLECTION_ADDED(info, user, isIndexable, isNotifiableEvent, true),

    /** A collection was tombstoned */
    COLLECTION_TOMBSTONED(info, user, isIndexable, isNotifiableEvent, true),

    /** A collection was deleted */
    COLLECTION_DELETED(info, user, isIndexable, isNotifiableEvent, true),

    /** A collection was moved */
    COLLECTION_MOVED(info, user, isIndexable, isNotifiableEvent, true),

    /** A collection was updated */
    COLLECTION_UPDATED(info, user, isIndexable, isNotifiableEvent, true),

    /* ========= Entities =========== */

    /** One or more events, task etc were fetched */
    ENTITY_FETCHED(info, user),

    /** An event, task etc was added */
    ENTITY_ADDED(info, user, isIndexable, isNotifiableEvent, false),

    /** An event, task etc was tombstoned */
    ENTITY_TOMBSTONED(info, user, isIndexable, isNotifiableEvent, false),

    /** An event, task etc was deleted */
    ENTITY_DELETED(info, user, isIndexable, isNotifiableEvent, false),

    /** An event, task etc was updated */
    ENTITY_UPDATED(info, user, isIndexable, isNotifiableEvent, false),

    /** An entity was moved */
    ENTITY_MOVED(info, user, isIndexable, isNotifiableEvent, false),

    /* ========= Scheduling =========== */

    /** A scheduling message arrived */
    SCHEDULE_QUEUED(info, user),

    /* ========= Services =========== */

    /** Service initialised as a user */
    SERVICE_USER_LOGIN(info, priv),

    /* ========= Public events workflow =========== */

    /** Event is approved */
    APPROVAL_STATUS(info, priv),

    /* ========= Public events =========== */

    /** Event is suggested to another group */
    SUGGESTED(info, priv),

    /** Response from suggestion to another group */
    SUGGESTED_RESPONSE(info, priv);

    private final int severity;

    private final int privLevel;

    private final boolean indexable;

    private boolean notifiableEvent;

    private boolean collectionRef;

    /**
     * Constructor
     *
     * @param severity
     *          - numeric code for the event severity.
     * @param privLevel numeric privilege level
     */
    SysCode(final int severity, final int privLevel) {
      this(severity, privLevel, notIndexable);
    }

    /**
     * Constructor
     *
     * @param severity
     *          - numeric code for the event severity.
     * @param privLevel numeric privilege level
     * @param indexable true if this indicates an indexable change may have occurred
     */
    SysCode(final int severity,
            final int privLevel,
            final boolean indexable) {
      this.severity = severity;
      this.privLevel = privLevel;
      this.indexable = indexable;
    }

    /**
     * Constructor
     *
     * @param severity
     *          - numeric code for the event severity.
     * @param privLevel numeric privilege level
     * @param indexable true if this indicates an indexable change may have occurred
     * @param notifiableEvent true if this indicates a notifiable event occurred
     * @param collectionRef - true if we are referring to a collection
     */
    SysCode(final int severity,
            final int privLevel,
            final boolean indexable,
            final boolean notifiableEvent,
            final boolean collectionRef) {
      this.severity = severity;
      this.privLevel = privLevel;
      this.indexable = indexable;
      this.notifiableEvent = isNotifiableEvent;
      this.collectionRef = collectionRef;
    }

    /**
     * @return int severity of the event code
     */
    public int getSeverity() {
      return severity;
    }

    /**
     * @return int privLevel of the event code
     */
    public int getPrivLevel() {
      return privLevel;
    }

    /**
     * @return true if this indicates an indexable change may have occurred
     */
    public boolean getIndexable() {
      return indexable;
    }

    /**
     * @return true if this indicates a notifiableEvent occurred
     */
    public boolean getNotifiableEvent() {
      return notifiableEvent;
    }

    /**
     * @return true if this indicates a notifiableEvent occurred to a collection
     */
    public boolean getCollectionRef() {
      return collectionRef;
    }
  }

  /**
   * @return SysCode sysCode
   */
  SysCode getSysCode();

  /** An attribute for the JMX message header
   *
   */
  public static class Attribute {
    public String name;
    public String value;

    public Attribute(final String name,
                     final String value) {
      this.name = name;
      this.value = value;
    }
  }

  List<Attribute> getMessageAttributes();
}
