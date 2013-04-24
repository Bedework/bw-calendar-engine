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

import edu.rpi.sss.util.ToString;

import java.io.Serializable;

/** Class to represent a reference to an event property.
 *
 * @author Mike Douglass   douglm - rpi.edu
 *
 */
public class EventPropertiesReference implements Serializable {
  /** True for a collection reference
   */
  private boolean collection;

  /** Path to the event or collection
   */
  private String path;

  /** Set for a referencing event
   */
  private String uid;

  /** Constructor for collection
   *
   * @param path
   */
  public EventPropertiesReference(final String path) {
    collection = true;
    this.path = path;
  }

  /** Constructor for event
   *
   * @param path
   * @param uid
   */
  public EventPropertiesReference(final String path, final String uid) {
    collection = false;
    this.path = path;
    this.uid = uid;
  }

  /** True for a collection reference
   *
   * @return boolean true for collection, false for event/task
   */
  public boolean getCollection() {
    return collection;
  }

  /** Path to the event or collection
   *
   * @return String path
   */
  public String getPath() {
    return path;
  }

  /** Set for a referencing event
   */
  /**
   * @return non-null for a referencing event
   */
  public String getUid() {
    return uid;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("collection", getCollection());
    ts.append("path", getPath());

    if (!getCollection()) {
      ts.append("uid", getUid());
    }

    return ts.toString();
  }
}

