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
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.misc.ToString;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.hibernate.Hibernate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Blob;

/** Represent the content for a resource stored within the system, e.g an attachment or an
 * image. The actual content is stored in a BwResourceContent object to allow us
 * to put off retrieval of content - or maybe even store outside of the db.
 *
 *  @author Mike Douglass   douglm - bedework.edu
 */
@Dump(elementName="resourceContent", keyFields={"colPath", "name", "encodedContent"})
public class BwResourceContent extends BwDbentity<BwResourceContent> {
  /* The collection this belongs to
   */
  private String colPath;

  private String name;

  private Blob value;

  /** Constructor
   *
   */
  public BwResourceContent() {
  }

  /* ====================================================================
   *                      Bean methods
   * ==================================================================== */

  /** Set the object's collection path
   *
   * @param val    String path
   */
  public void setColPath(final String val) {
    colPath = val;
  }

  /** Get the object's collection path
   *
   * @return String   path
   */
  public String getColPath() {
    return colPath;
  }

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

  /** Set the value
   *
   *  @param  val   byte[]
   */
  public void setValue(final Blob val) {
    value = val;
  }

  /** Get the value
   *
   *  @return Blob
   */
  @NoDump
  public Blob getValue() {
    return value;
  }

  /* ====================================================================
   *                   non-db methods
   * ==================================================================== */

  /**
   * @param val
   * @throws CalFacadeException
   */
  @NoDump
  public void setContent(final byte[] val) throws CalFacadeException {
    try {
      setValue(Hibernate.createBlob(val));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Used during dump and restore
   * @param val base64 encoded value
   * @throws CalFacadeException
   */
  public void setEncodedContent(final String val) throws CalFacadeException {
    try {
      setContent(Base64.decodeBase64(val.getBytes()));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @return base64 encoded value
   * @throws CalFacadeException
   */
  public String getEncodedContent() throws CalFacadeException {
    Base64OutputStream b64out = null;

    try {
      Blob b = getValue();
      if (b == null) {
        return null;
      }

      int len = -1;
      int chunkSize = 1024;

      byte buffer[] = new byte[chunkSize];

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      b64out = new Base64OutputStream(baos);
      InputStream str = b.getBinaryStream();

      while((len = str.read(buffer)) != -1) {
        b64out.write(buffer, 0, len);
      }

      return new String(baos.toByteArray());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      try {
        b64out.close();
      } catch (Throwable t) {}
    }
  }

  /**
   * @return String value
   * @throws CalFacadeException
   */
  public String getStringContent() throws CalFacadeException {
    try {
      Blob b = getValue();
      if (b == null) {
        return null;
      }

      int len = -1;
      int chunkSize = 1024;

      byte buffer[] = new byte[chunkSize];

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      InputStream str = b.getBinaryStream();

      while((len = str.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }

      return new String(baos.toByteArray());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Copy this objects values into the parameter
   *
   * @param val
   */
  public void copyTo(final BwResourceContent val) {
    val.setColPath(getColPath());
    val.setName(getName());
    val.setValue(getValue());
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int hashCode() {
    return getValue().hashCode();
  }

  @Override
  public int compareTo(final BwResourceContent that)  {
    if (this == that) {
      return 0;
    }

    return CalFacadeUtil.cmpObjval(getColPath(), that.getColPath());
    /*
    int res = CalFacadeUtil.cmpObjval(getColPath(), that.getColPath());
    if (res != 0) {
      return res;
    }

    byte[] thisone = getValue();
    byte[] thatone = that.getValue();

    if (thisone == null) {
      if (thatone == null) {
        return 0;
      }

      return -1;
    }

    if (thatone == null) {
      return 1;
    }

    if (thisone.length < thatone.length) {
      return -1;
    }

    if (thatone.length < thisone.length) {
      return 1;
    }

    for (int i = 0; i < thisone.length; i++) {
      byte thisbyte = thisone[i];
      byte thatbyte = thatone[i];

      if (thisbyte < thatbyte) {
        return -1;
      }

      if (thatbyte < thisbyte) {
        return 1;
      }
    }

    return 0;
    */
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("path", getColPath());
    ts.append("name", getName());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwResourceContent nobj = new BwResourceContent();
    copyTo(nobj);

    return nobj;
  }
}

