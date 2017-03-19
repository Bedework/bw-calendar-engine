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
package org.bedework.calfacade.responses;

import org.bedework.util.misc.ToString;

import java.io.Serializable;

/** Base for web service responses
 *
 * @author Mike Douglass douglm - spherical cow
 */
public class Response implements Serializable {
  public enum Status {
    ok,
    
    failed;
  }
  
  private Status status;
  private String message;

  /**
   *
   * @param val status
   */
  public void setStatus(final Status val) {
    status = val;
  }

  /**
   * @return status
   */
  public Status getStatus() {
    return status;
  }

  /**
   *
   * @param val a message
   */
  public void setMessage(final String val) {
    message = val;
  }

  /**
   * @return a message or null
   */
  public String getMessage() {
    return message;
  }
  
  public void toStringSegment(final ToString ts) {
    ts.append("status", getStatus())
      .append("message", getMessage());
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);
    
    return ts.toString();
  }
}
