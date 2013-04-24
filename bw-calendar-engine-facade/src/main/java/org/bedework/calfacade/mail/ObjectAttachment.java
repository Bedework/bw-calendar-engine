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

package org.bedework.calfacade.mail;


/** An attachment represented by an object in memory.
 */
public class ObjectAttachment implements Attachment {
  private String mimeType;

  /** file name for object */
  private String originalName;

  private Object val;

  /**
   *
   */
  public ObjectAttachment() {
  }

  /**
   * @param val
   * @param originalName
   * @param mimeType
   */
  public ObjectAttachment(Object val,
                          String originalName,
                          String mimeType) {
    this.val = val;
    this.originalName = originalName;
    this.mimeType = mimeType;
  }

  /**
   * @param val
   */
  public void setVal(Object val) {
    this.val = val;
  }

  /**
   * @return value
   */
  public Object getVal() {
    return val;
  }

  /**
   * @param val
   */
  public void setOriginalName(String val) {
    originalName = val;
  }

  /**
   * @return value
   */
  public String getOriginalName() {
    return originalName;
  }

  /**
   * @param val
   */
  public void setMimeType(String val) {
    mimeType = val;
  }

  /**
   * @return value
   */
  public String getMimeType() {
    return mimeType;
  }

  /** Return a value of the form:
   *   ObjectAttachment[mimeType originalName String.valueOf(val)]
   *
   * @return String    representation of object.
   */
  public String toString() {
    StringBuffer sb = new StringBuffer("ObjectAttachment[");

    try {
      sb.append(mimeType);
      sb.append(" ");
      sb.append(originalName);
      sb.append(" ");
      sb.append(val);
      sb.append("]");
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

    return sb.toString();
  }
}

