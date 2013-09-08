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
package org.bedework.calsvc.indexing;

import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.EventsI;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.indexing.Index;
import org.bedework.util.indexing.IndexException;

import java.io.CharArrayWriter;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class BwIndexKey extends Index.Key {
  private String key1; // calendar:path
  private String key2; // event:guid
  private String key3; // event:recurrenceid

  /** An event key is stored as a concatenated set of Strings,
   * calendar:path + guid + (recurrenceid | null).
   *
   * <p>We set it here and use the decode methods to split it up.
   */
  private char[] encoded;

  private String itemType;

  /** Constructor
   *
   */
  public BwIndexKey() {
  }

  /** Constructor for a collection key
   *
   * @param path
   */
  public BwIndexKey(final String path) {
    this(BwIndexDefs.itemTypeCalendar, path);
  }

  /** Constructor for an event key
   *
   * @param path
   * @param uid
   * @param rid
   */
  public BwIndexKey(final String path, final String uid, final String rid) {
    itemType = BwIndexDefs.itemTypeEvent;
    key1 = path;
    key2 = uid;
    key3 = rid;
  }

  /** Constructor for a single valued key
   *
   * @param itemType - type of indexed item
   * @param k the key
   */
  public BwIndexKey(final String itemType,
                    final String k) {
    this.itemType = itemType;
    key1 = k;
  }

  /** Constructor for an event key
   *
   * @param itemType
   * @param path
   * @param uid
   * @param rid
   */
  public BwIndexKey(final String itemType,
                    final String path, final String uid, final String rid) {
    this.itemType = itemType;
    key1 = path;
    key2 = uid;
    key3 = rid;
  }

  /** Constructor
   *
   * @param score
   */
  public BwIndexKey(final float score) {
    this.score = score;
  }

  /** Set the item type with a value defined in BwIndexDefs
   *
   * @param val
   */
  public void setItemType(final String val) {
    itemType = val;
  }

  /**
   * @return String item type as defined in BwIndexDefs
   */
  public String getItemType() {
    return itemType;
  }

  /** Set the score
   *
   * @param val
   */
  public void setScore(final float val) {
    score = val;
  }

  /** Set the key for a calendar (the path) or category (the uid)
   *
   * @param key1
   */
  public void setKey1(final String key1) {
    this.key1 = key1;
  }

  /** Set the key for an event (encode ca+guid+recurid)
   *
   * @param key
   * @throws IndexException
   */
  public void setEventKey(final String key) throws IndexException {
    encoded = key.toCharArray();
    pos = 0;

    key1 = getKeyString();
    key2 = getKeyString();
    key3 = getKeyString();
  }

  /**
   * @return String key value.
   * @throws IndexException
   */
  public String getKey() throws IndexException {
    if (itemType.equals(BwIndexDefs.itemTypeCalendar)) {
      return key1;  // Path
    }

    if (itemType.equals(BwIndexDefs.itemTypeCategory)) {
      return makeCategoryKey(key1);
    }

    return makeEventKey(key1, key2, key3);
  }

  public String makeCategoryKey(final String key1) {
    // Key is just the uid
    return key1;
  }

  /** Encode an event key
   *
   * @param key1
   * @param key2
   * @param key3
   * @return Strign encoded key
   * @throws IndexException
   */
  public String makeEventKey(final String key1, final String key2,
                             final String key3) throws IndexException {
    startEncoding();
    encodeString(key1);
    encodeString(key2);
    encodeString(key3);

    return getEncodedKey();
  }

  /** Will return either a BwCalendar or a Collection of EventInfo.
   *
   * @see edu.rpi.cct.misc.indexing.Index.Key#getRecord()
   */
  @Override
  public Object getRecord() throws IndexException {
    throw new RuntimeException("org.bedework.wrong.method");
  }

  /** Will return either a BwCalendar or a Collection of EventInfo.
   *
   * @param svci
   * @param dtStart
   * @param dtEnd
   * @return BwCalendar or a Collection of EventInfo
   * @throws IndexException
   */
  public Object getRecord(final CalSvcI svci,
                          final BwDateTime dtStart,
                          final BwDateTime dtEnd) throws IndexException {
    try {
      if (itemType == null) {
        throw new IndexException("org.bedework.index.nullkeyitemtype");
      }

      if (itemType.equals(BwIndexDefs.itemTypeCalendar)) {
        return svci.getCalendarsHandler().get(key1);
      }

      if (itemType.equals(BwIndexDefs.itemTypeCategory)) {
        return svci.getCategoriesHandler().get(key1);
      }

      if (itemType.equals(BwIndexDefs.itemTypeEvent)) {
        BwCalendar cal = svci.getCalendarsHandler().get(key1);
        if (cal == null) {
          return null;
        }

        EventsI evhandler = svci.getEventsHandler();
        if (((dtStart == null) && (dtEnd == null)) ||
            (key3 != null)) {
          Collection<EventInfo> evis = evhandler.get(key1, key2, key3,
                                                     new RecurringRetrievalMode(),
                                                     false);

          if (key3 == null) {
            return evis;
          }

          if ((dtStart == null) && (dtEnd == null)) {
            return evis;
          }

          Collection<EventInfo> resEvis = new ArrayList<EventInfo>();
          String start = null;
          String end = null;

          if (dtStart != null) {
            start = dtStart.getDate();
          }

          if (dtEnd != null) {
            end = dtEnd.getDate();
          }

          for (EventInfo ei: evis) {
            if (ei.getEvent().inDateTimeRange(start, end)) {
              resEvis.add(ei);
            }
          }

          return resEvis;
        }

        ObjectFilter<String> filter = new ObjectFilter<String>(null,
            PropertyIndex.PropertyInfoIndex.UID);
        filter.setEntity(key2);
        filter.setExact(true);
        filter.setCaseless(false);

        Collection<EventInfo> evis = evhandler.getEvents(cal, filter,
                                                         dtStart, dtEnd,
                                                         null, // retrieveList
                                           new RecurringRetrievalMode());

        /* Filter out the overridden events as they are indexed separately.
         * We should really have a flag for this - for the moment check the
         * annotation for any instance.
         */

        Collection<EventInfo> resEvis = new ArrayList<EventInfo>();

        for (EventInfo ei: evis) {
          BwEvent ev = ei.getEvent();

          if (!(ev instanceof BwEventProxy)) {
            resEvis.add(ei);
            continue;
          }

          BwEventAnnotation ann = ((BwEventProxy)ev).getRef();

          if (ann.unsaved()) {
            // This is an instance that is not overrriden
            resEvis.add(ei);
          }
        }

        return resEvis;
      }

      throw new IndexException(IndexException.unknownRecordType,
                               itemType);
    } catch (IndexException ie) {
      throw ie;
    } catch (CalFacadeAccessException cae) {
      return null;
    } catch (Throwable t) {
      throw new IndexException(t);
    }
  }

  /* ====================================================================
   *                 Key decoding methods
   * ==================================================================== */

  /* Current position in the key */
  private int pos;

  /* When encoding a key we build it here.
   */
  private CharArrayWriter caw;

  /** Get next char from encoded value. Return < 0 for no more
   *
   * @return char value
   */
  private char getChar() {
    if ((encoded == null) || (pos == encoded.length)) {
      return (char)-1;
    }

    char c = encoded[pos];
    pos++;

    return c;
  }

  /** Back off one char
   *
   * @throws IndexException
   */
  private void back() throws IndexException {
    back(1);
  }

  /** Back off n chars
   *
   * @param n   int number of chars
   * @throws IndexException
   */
  private void back(final int n) throws IndexException {
    if ((pos - n) < 0) {
      throw new IndexException("org.bedework.index.badKeyRewind");
    }

    pos -= n;
  }

  /* * Get current position
   *
   * @return int position
   * /
  public int getPos() {
    return pos;
  }

  /* * Set current position
   *
   * @param val  int position
   * /
  public void setPos(final int val) {
    pos = val;
  }

  /* * Get number of chars remaining
   *
   * @return int number of chars remaining
   * /
  public int remaining() {
    if (encoded == null) {
      return 0;
    }
    return encoded.length - pos;
  }

  /* * Test for more
   *
   * @return boolean true for more
   * /
  public boolean hasMore() {
    return remaining() > 0;
  }

  /* * Test for no more
   *
   * @return boolean true for no more
   * /
  public boolean empty() {
    return (encoded == null) || (encoded.length == 0);
  }

  /* * Rewind to the start
   * /
  private void rewind() {
    pos = 0;
  }*/

  /** Return the value of a blank terminated length. On success current pos
   * has been incremented.
   *
   * @return int length
   * @throws IndexException
   */
  private int getLength() throws IndexException {
    int res = 0;

    for (;;) {
      char c = getChar();
      if (c == ' ') {
        break;
      }

      if (c < 0) {
        throw new IndexException("org.bedework.index.badKeyLength");
      }

      if ((c < '0') || (c > '9')) {
        throw new IndexException("org.bedework.index.badkeychar");
      }

      res = (res * 10) + Character.digit(c, 10);
    }

    return res;
  }

  /** Get a String from the encoded acl at the current position.
   *
   * @return String decoded String value
   * @throws IndexException
   */
  private String getKeyString() throws IndexException {
    if (getChar() == 'N') {
      return null;
    }
    back();
    int len = getLength();

    if ((encoded.length - pos) < len) {
      throw new IndexException("org.bedework.index.badKeyLength");
    }

    String s = new String(encoded, pos, len);
    pos += len;

    return s;
  }

  /* * Skip a String from the encoded acl at the current position.
   *
   * @throws IndexException
   * /
  public void skipString() throws IndexException {
    if (getChar() == 'N') {
      return;
    }

    back();
    int len = getLength();
    pos += len;
  }*/

  /* ====================================================================
   *                 Encoding methods
   * ==================================================================== */

  /** Get ready to encode
   *
   */
  private void startEncoding() {
    caw = new CharArrayWriter();
  }

  /** Encode a blank terminated 0 prefixed length.
   *
   * @param len
   * @throws IndexException
   */
  private void encodeLength(final int len) throws IndexException {
    try {
      String slen = String.valueOf(len);
      caw.write('0');
      caw.write(slen, 0, slen.length());
      caw.write(' ');
    } catch (Throwable t) {
      throw new IndexException(t);
    }
  }

  /** Encode a String with length prefix. String is encoded as <ul>
   * <li>One byte 'N' for null string or</li>
   * <li>length {@link #encodeLength(int)} followed by</li>
   * <li>String value.</li>
   * </ul>
   *
   * @param val
   * @throws IndexException
   */
  public void encodeString(final String val) throws IndexException {
    try {
      if (val == null) {
        caw.write('N'); // flag null
      } else {
        encodeLength(val.length());
        caw.write(val, 0, val.length());
      }
    } catch (IndexException ie) {
      throw ie;
    } catch (Throwable t) {
      throw new IndexException(t);
    }
  }

  /** Add a character
   *
   * @param c char
   * @throws IndexException
   */
  public void addChar(final char c) throws IndexException {
    try {
      caw.write(c);
    } catch (Throwable t) {
      throw new IndexException(t);
    }
  }

  /** Get the current encoed value
   *
   * @return char[] encoded value
   */
  public String getEncodedKey() {
    return new String(caw.toCharArray());
  }
}
