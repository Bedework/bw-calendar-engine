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

import org.bedework.util.indexing.IndexException;

import java.io.CharArrayWriter;

/** Create keys for indexing
 *
 * @author Mike Douglass
 *
 */
public class IndexKeys {
  /* When encoding a key we build it here.
   */
  private CharArrayWriter caw;

  /** Called to make a key value for a record.
   *
   * @param   type of the record
   * @param   href of the record
   * @param   recurid of the record or null
   * @return  String   String which uniquely identifies the record
   * @throws IndexException
   */
  public String makeKeyVal(final String type,
                           final String href,
                           final String recurid) throws IndexException {
    startEncoding();
    encodeString(type);
    encodeString(href);
    encodeString(recurid);

    return getEncodedKey();
  }

  /* ====================================================================
   *                 Encoding methods
   * ==================================================================== */

  /** Get ready to encode
   *
   */
  private void startEncoding() {
    caw = new CharArrayWriter();
  }

  /** Encode a blank terminated, 0 prefixed length.
   *
   * @param len of field
   * @throws IndexException
   */
  private void encodeLength(final int len) throws IndexException {
    try {
      final String slen = String.valueOf(len);
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
   * @param val the string
   * @throws IndexException
   */
  private void encodeString(final String val) throws IndexException {
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

  /** Get the current encoded value
   *
   * @return char[] encoded value
   */
  private String getEncodedKey() {
    return new String(caw.toCharArray());
  }
}
