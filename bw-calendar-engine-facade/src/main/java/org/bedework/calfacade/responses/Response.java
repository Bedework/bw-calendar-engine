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

import static org.bedework.calfacade.responses.Response.Status.failed;
import static org.bedework.calfacade.responses.Response.Status.ok;
import static org.bedework.calfacade.responses.Response.Status.validationError;

/** Base for service responses
 *
 * @author Mike Douglass douglm - spherical cow
 */
public class Response implements Serializable {
  public enum Status {
    ok,
    
    notFound,

    validationError,

    /** Something is currently running */
    processing,

    /** Something exists that shouldn't */
    exists,

    failed;
  }
  
  private Status status = ok;
  private String message;

  /* Copied from the request */
  private int id;

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
  
  public boolean isOk() {
    return status == ok;
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

  public static Response ok() {
    return ok(new Response(), null);
  }

  public static <T extends Response> T ok(final T resp,
                                          final String msg) {
    resp.setStatus(ok);
    resp.setMessage(msg);

    return resp;
  }

  public static <T extends Response> T notOk(final T resp,
                                             final Status status,
                                             final String msg) {
    resp.setStatus(status);
    resp.setMessage(msg);

    return resp;
  }

  public static <T extends Response> T error(final T resp, 
                                             final String msg) {
    return notOk(resp, failed, msg);
  }

  public static <T extends Response> T error(final T resp,
                                             final Throwable t) {
    return error(resp, t.getLocalizedMessage());
  }

  public static <T extends Response> T invalid(final T resp,
                                               final String msg) {
    return notOk(resp, validationError, msg);
  }

  public void toStringSegment(final ToString ts) {
    ts.append("status", getStatus())
      .append("message", getMessage())
      .append("id", getId());
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);
    
    return ts.toString();
  }
}
