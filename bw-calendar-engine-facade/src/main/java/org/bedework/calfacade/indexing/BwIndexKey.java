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
package org.bedework.calfacade.indexing;

import org.bedework.util.indexing.Index;
import org.bedework.util.indexing.IndexException;

import java.io.CharArrayWriter;

/**
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class BwIndexKey extends Index.Key {
  private String key1; // calendar:path
  private String key2; // event:recurrenceid

  /** An event key is stored as a concatenated pair of Strings,
   * calendar:href + (recurrenceid | null).
   */

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
    itemType = BwIndexer.docTypeCollection;
    key1 = path;
  }

  /** Constructor for an event key
   *
   * @param itemType
   * @param href
   * @param recurrenceid
   */
  public BwIndexKey(final String itemType,
                    final String href,
                    final String recurrenceid) {
    this.itemType = itemType;
    key1 = href;
    key2 = recurrenceid;
  }

  /** Constructor
   *
   * @param score
   */
  public BwIndexKey(final float score) {
    this.score = score;
  }

  /**
   * @return String item type as defined in BwIndexDefs
   */
  public String getItemType() {
    return itemType;
  }

  /**
   * @return String key value.
   * @throws IndexException
   */
  public String getKey() throws IndexException {
    if (itemType.equals(BwIndexer.docTypeCollection)) {
      return key1;  // Path
    }

    if (itemType.equals(BwIndexer.docTypeCategory)) {
      return makeCategoryKey(key1);
    }

    return makeEventKey(key1, key2);
  }

  public String makeCategoryKey(final String key1) {
    // Key is just the uid
    return key1;
  }

  public String makeContactKey(final String key1) {
    // Key is just the uid
    return key1;
  }

  public String makeLocationKey(final String key1) {
    // Key is just the uid
    return key1;
  }

  /** Encode an event key
   *
   * @param href
   * @param recurrenceid
   * @return String encoded key
   * @throws IndexException
   */
  public String makeEventKey(final String href,
                             final String recurrenceid) throws IndexException {
    startEncoding();
    encodeString(href);
    encodeString(recurrenceid);

    return getEncodedKey();
  }

  /** Will return either a BwCalendar or a Collection of EventInfo.
   *
   */
  @Override
  public Object getRecord() throws IndexException {
    throw new RuntimeException("org.bedework.wrong.method");
  }

  /* ====================================================================
   *                 Key decoding methods
   * ==================================================================== */

  /* Current position in the key */
  private int pos;

  /* When encoding a key we build it here.
   */
  private CharArrayWriter caw;

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

  /** Get the current encoed value
   *
   * @return char[] encoded value
   */
  public String getEncodedKey() {
    return new String(caw.toCharArray());
  }
}
