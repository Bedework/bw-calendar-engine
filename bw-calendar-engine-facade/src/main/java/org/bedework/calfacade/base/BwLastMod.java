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
package org.bedework.calfacade.base;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.annotations.NoWrap;

import edu.rpi.sss.util.DateTimeUtil;
import edu.rpi.sss.util.ToString;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.LastModified;

import java.sql.Timestamp;
import java.util.Date;

/** This is used to store the last modification times for some entities. We do
 * this to avoid the overhead and errors caused by versioning when not needed.
 *
 * <p>For example, we frequently update a calendar collections last mod when
 * events are changed. We need this lastmod to flag collection that have changed,
 * on the other hand we don't need the possibility of StaleStateExceptions.
 *
 * <p>The touch method will update this object only and save it avoiding most
 * of tha overhead.
 *
 * @author Mike Douglass
 * @version 1.0
 * @param <T>     type we are a lastmod for
 * @param <T1>    the actual class.
 */
public class BwLastMod<T extends BwDbentity, T1>
        extends BwUnversionedDbentity<T1> {
  private int id = CalFacadeDefs.unsavedItemKey;

  private T dbEntity;

  /** UTC datetime */
  private String timestamp;

  /** Ensure uniqueness - lastmod only down to second.
   */
  private int sequence;

  /** No date constructor
   *
   * @param dbEntity
   */
  public BwLastMod(final T dbEntity) {
    this.dbEntity = dbEntity;
  }

  /** Constructor to set last mod
   * @param dbEntity
   * @param dt
   */
  public BwLastMod(final T dbEntity, final Date dt) {
    this(dbEntity);
    setTimestamp(DateTimeUtil.isoDateTimeUTC(dt));
  }

  /**
   * @param val
   */
  @Override
  public void setId(final int val) {
    id = val;
  }

  /**
   * @return int id
   */
  @Override
  @NoDump
  public int getId() {
    return id;
  }

  /**
   * @param val
   */
  public void setDbEntity(final T val) {
    dbEntity = val;
  }

  /**
   * @return T
   */
  @NoDump
  public T getDbEntity() {
    return dbEntity;
  }

  /**
   * @param val
   */
  public void setTimestamp(final String val) {
    timestamp = val;
  }

  /**
   * @return String lastmod
   */
  public String getTimestamp() {
    return timestamp;
  }

  /** Set the sequence
   *
   * @param val    sequence number
   */
  public void setSequence(final int val) {
    sequence = val;
  }

  /** Get the sequence
   *
   * @return int    the sequence
   */
  public int getSequence() {
    return sequence;
  }

  /**
   * @return true if this entity is not saved.
   */
  @Override
  public boolean unsaved() {
    return getId() == CalFacadeDefs.unsavedItemKey;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /** Update last mod fields
   * @param val
   */
  public void updateLastmod(final Timestamp val) {
    DateTime dt = new DateTime(val);
    setTimestamp(new LastModified(dt).getValue());
    setSequence(val.getNanos() / 100000);
  }

  /** Return a value that can be used for etag and ctag support
   *
   * @return String tag value
   */
  @NoDump
  public String getTagValue() {
    return getTagValue(getTimestamp(), getSequence());
  }

  /** Return a value that can be used for etag and ctag support
   *
   * @param timestamp
   * @param sequence
   * @return String tag value
   */
  @NoDump
  public static String getTagValue(final String timestamp, final int sequence) {
    return timestamp + "-" + BwEvent.hex4(sequence);
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   */
  @Override
  protected void toStringSegment(final ToString ts) {
    ts.append("id", getId());
    ts.append("timestamp", getTimestamp());
    ts.append("sequence", getSequence());
  }

  /* ====================================================================
   *                   Object methods
   * The following are required for a db object.
   * ==================================================================== */

  @Override
  @NoWrap
  public int hashCode() {
    return getTagValue().hashCode();
  }
}
