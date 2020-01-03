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
package org.bedework.calfacade.requests;

import org.bedework.util.misc.ToString;
import org.bedework.util.misc.response.Response;

/** Request base object. 
 * 
 * <p>The action is used by the json routines to enable deserialization
 * of the json into a Java object.</p>
 * 
 * <p>The id is not required but clients may use it to identify a
 * response. The id from the request will be copied into responses.</p>
 *
 * <p>The validate method may be overridden and will be called by
 * the api to validate fields.</p>
 * 
 * douglm: spherical cow group
 */
public class RequestBase {
  /** get instances
   */
  public final static String getInstancesAction = "get-instances";

  private String action;

  /* Copied into the response */
  private int id;

  /**
   * @param val the action.
   */
  public void setAction(final String val) {
    action = val;
  }

  /**
   * @return the action
   */
  public String getAction() {
    return action;
  }

  /**
   * @param val an id to identify the request
   */
  public void setId(final int val) {
    id = val;
  }

  /**
   * @return an id to identify the request
   */
  public int getId() {
    return id;
  }

  /** May clean up the data in the request.
   *
   * @param resp for failed status and message
   * @return true for ok request
   */
  public boolean validate(final Response resp) {
    return true;
  }

  /** Add information to the ToString builder
   * 
   * @param ts ToString builder
   */
  public void toStringSegment(final ToString ts) {
    ts.append("action", getAction());
    ts.append("id", getId());
  }
  
  public String toString() {
    final ToString ts = new ToString(this);
    
    toStringSegment(ts);
    
    return ts.toString();
  }
}
