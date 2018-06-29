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
package org.bedework.calfacade;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.annotations.NoWrap;
import org.bedework.calfacade.annotations.ical.NoProxy;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.DateTimeUtil;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.LastModified;

import java.sql.Timestamp;
import java.util.Date;

/** Represent a resource stored within the system, e.g an attachment or an
 * image. The actual content is stored in a BwResourceContent object to allow us
 * to put off retrieval of content - or maybe even store outside of the db.
 *
 *  @author Mike Douglass   douglm - bedework.edu
 */
@Dump(elementName="resource", keyFields={"name"})
public class BwResource extends BwShareableContainedDbentity<BwResource> {
  private String name;

  /** UTC datetime */
  private String created;

  /** UTC datetime */
  private String lastmod;

  /** Ensure uniqueness - lastmod only down to second.
   */
  private int sequence;

  private String contentType;

  private String encoding;

  private long contentLength;

  /** Value of encoding for a tombstoned resource
   */
  public static final String tombstoned = "--TOMBSTONED--";

  /** Value of suffix on path for a tombstoned resource
   */
  public static final String tombstonedSuffix = "(--TOMBSTONED--)";

  /* ====================================================================
   *                  Non-db fields - should be in a wrapper
   * ==================================================================== */

  /** Set the href - ignored
   *
   * @param val    String href
   */
  public void setHref(final String val) { }

  public String getHref() {
    return Util.buildPath(true, getColPath(),
                          "/",
                          getName());
  }

  private String prevLastmod;

  private int prevSeq;

  private BwResourceContent content;

  /** Constructor
   *
   */
  public BwResource() {
    super();

    Date dt = new Date();
//    setLastmod(DateTimeUtil.isoDateTimeUTC(dt));
    setCreated(DateTimeUtil.isoDateTimeUTC(dt));
  }

  /* ====================================================================
   *                      Bean methods
   * ==================================================================== */

  /** Set the name
   *
   * @param val    String name
   */
  public void setName(final String val) {
    name = val;
  }

  /** Get the name
   *
   * @return String   name
   */
  public String getName() {
    return name;
  }

  /**
   * @param val
   */
  public void setCreated(final String val) {
    created = val;
  }

  /**
   * @return String created
   */
  public String getCreated() {
    return created;
  }

  /**
   * @param val
   */
  public void setLastmod(final String val) {
    lastmod = val;
  }

  /**
   * @return String lastmod
   */
  public String getLastmod() {
    return lastmod;
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

  /** Set the contentType - may be null for unknown. For internal use extra
   * information can be appended by adding a TAB character then the extra. On
   * delivery the TAB and anything after stripped
   *
   *  @param  val   String contentType
   */
  public void setContentType(final String val) {
    contentType = val;
  }

  /** Get the contentType
   *
   *  @return String     contentType
   */
  public String getContentType() {
    return contentType;
  }

  /** Set the encoding
   *
   *  @param  val   String encoding
   */
  public void setEncoding(final String val) {
    encoding = val;
  }

  /** Get the encoding
   *
   *  @return String     encoding
   */
  public String getEncoding() {
    return encoding;
  }

  /** Set the length
   *
   *  @param  val   int
   */
  public void setContentLength(final long val) {
    contentLength = val;
  }

  /** Get the length
   *
   *  @return long     length
   */
  public long getContentLength() {
    return contentLength;
  }

  /* ====================================================================
   *                   Other non-db methods
   * ==================================================================== */

  /** Get the contentType stripped
   *
   *  @return String     contentType
   */
  @NoDump
  public String getContentTypeStripped() {
    String ct = getContentType();

    if ((ct == null) || (ct.indexOf("\t") < 0)) {
      return ct;
    }

    return ct.substring(0, ct.indexOf("\t"));
  }

  /** Get the extra stuff appended to the content type
   *
   *  @return String  extra data
   */
  @NoDump
  public String getContentTypeExtra() {
    String ct = getContentType();

    if ((ct == null) || (ct.indexOf("\t") < 0)) {
      return null;
    }

    return ct.substring(ct.indexOf("\t") + 1);
  }

  /** Copy this objects values into the parameter
   *
   * @param val
   */
  public void copyTo(final BwResource val) {
    super.copyTo(val);
    val.setName(getName());
    val.setContentType(getContentType());
    val.setEncoding(getEncoding());
    val.setContentLength(getContentLength());
  }

  /** Update last mod fields
   * @param val
   */
  public void updateLastmod(final Timestamp val) {
    DateTime dt = new DateTime(val);

    setLastmod(new LastModified(dt).getValue());

    setSequence(val.getNanos() / 100000);
  }

  /** Set the resource's previous lastmod - used to allow if none match
   *
   *  @param val     lastmod
   */
  @NoDump
  public void setPrevLastmod(final String val) {
    prevLastmod = val;
  }

  /** Get the resource's previous lastmod - used to allow if none match
   *
   * @return the event's lastmod
   */
  public String getPrevLastmod() {
    return prevLastmod;
  }

  /** Set the event's previous seq - used to allow if none match
   *
   *  @param val     sequence number
   */
  public void setPrevSeq(final int val) {
    prevSeq = val;
  }

  /** Get the event's previous seq - used to allow if none match
   *
   * @return the event's seq
   */
  @NoDump
  public int getPrevSeq() {
    return prevSeq;
  }

  /** Set the content
   *
   *  @param  val   BwResourceContent
   */
  public void setContent(final BwResourceContent val) {
    content = val;
  }

  /** Get the content
   *
   *  @return BwResourceContent     content
   */
  @Dump(compound = true)
  public BwResourceContent getContent() {
    return content;
  }

  /**
   * @return etag for this resource.
   */
  @NoDump
  public String getEtag() {
    return "\"" + getEtagValue() + "\"";
  }

  /**
   * @return unquoted etag for this resource.
   */
  @NoDump
  public String getEtagValue() {
    return getLastmod() + "-" +
        BwEvent.hex4(getSequence());
  }

  /**
   * @return prev tag or null for no values set.
   */
  @NoDump
  public String getPreviousEtag() {
    if (getPrevLastmod() == null) {
      return null;
    }

    return "\"" + getPrevLastmod() + "-" +
        BwEvent.hex4(getPrevSeq()) +
           "\"";
  }

  /** Make this thing a tombstoned resource. Non-reversible
   */
  public void tombstone() {
    if (getTombstoned()) {
      return; // Already tombstoned
    }

    setEncoding(tombstoned);

    // XXX Schema
    /* We have to change the name to avoid conflicts -
     */

    setName(getName() + tombstonedSuffix);
  }

  /** Is this resource tombstoned?
   *
   * @return true/false
   */
  @NoProxy
  @NoDump
  @NoWrap
  public boolean getTombstoned() {
    String f = getEncoding();
    return (f != null) && f.equals(tombstoned);
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int hashCode() {
    return getColPath().hashCode() * getName().hashCode();
  }

  @Override
  public int compareTo(final BwResource that)  {
    if (this == that) {
      return 0;
    }

    int res = Util.cmpObjval(getColPath(), that.getColPath());
    if (res != 0) {
      return res;
    }

    return Util.cmpObjval(getName(), that.getName());
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("name", getName());
    ts.append("getContentType", getContentType());
    ts.append("encoding", getEncoding());
    ts.append("length", getContentLength());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwResource nobj = new BwResource();
    copyTo(nobj);

    return nobj;
  }
}

