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
package org.bedework.calsvc.scheduling.hosts;

import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.http.BasicHttpClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.http.HttpServletResponse;

/**
 * @author Mike Douglass
 *
 */
public class Response implements Serializable {
  private HostInfo hostInfo;

  private int responseCode;

  private boolean noResponse;

  private Throwable exception;

  private BasicHttpClient client;

  private int redirects;

  /**
   */
  public static class ResponseElement {
    private String recipient;
    private String reqStatus;
    private EventInfo calData;

    private String davError;

    /**Collection<Element>
     * @param val
     */
    public void setRecipient(final String val) {
      recipient = val;
    }

    /**
     * @return recipient
     */
    public String getRecipient() {
      return recipient;
    }

    /**
     * @param val
     */
    public void setReqStatus(final String val) {
      reqStatus = val;
    }

    /**
     * @return reqStatus
     */
    public String getReqStatus() {
      return reqStatus;
    }

    /**
     * @param val
     */
    public void setCalData(final EventInfo val) {
      calData = val;
    }

    /**
     * @return reqStatus
     */
    public EventInfo getCalData() {
      return calData;
    }

    /**
     * @param val
     */
    public void setDavError(final String val) {
      davError = val;
    }

    /**
     * @return String
     */
    public String getDavError() {
      return davError;
    }
  }

  private Collection<ResponseElement> responses = new ArrayList<ResponseElement>();

  /**
   * @param val HostInfo
   */
  public void setHostInfo(final HostInfo val) {
    hostInfo = val;
  }

  /**
   * @return HostInfo
   */
  public HostInfo getHostInfo() {
    return hostInfo;
  }

  /**
   * @param val int
   */
  public void setResponseCode(final int val) {
    responseCode = val;
  }

  /**
   * @return int
   */
  public int getResponseCode() {
    return responseCode;
  }

  /**
   * @param val boolean
   */
  public void setNoResponse(final boolean val) {
    noResponse = val;
  }

  /**
   * @return boolean
   */
  public boolean getNoResponse() {
    return noResponse;
  }

  /**
   * @param val Throwable
   */
  public void setException(final Throwable val) {
    exception = val;
  }

  /**
   * @return Throwable
   */
  public Throwable getException() {
    return exception;
  }

  /** set client handling the interactions
   *
   * @param val
   */
  public void setClient(final BasicHttpClient val) {
    client = val;
  }

  /**
   * @return BasicHttpClient
   */
  public BasicHttpClient getClient() {
    return client;
  }

  /**
   * @param val
   */
  public void setRedirects(final int val) {
    redirects = val;
  }

  /**
   * @return int
   */
  public int getRedirects() {
    return redirects;
  }

  /**
   * @return Collection<FbResponseElement>
   */
  public Collection<ResponseElement> getResponses() {
    return responses;
  }

  /**
   * @param val
   */
  public void addResponse(final ResponseElement val) {
    responses.add(val);
  }

  /**
   * @return boolean
   */
  public boolean okResponse() {
    return (getResponseCode() == HttpServletResponse.SC_OK) &&
           !getNoResponse() && (getException() == null);
  }
}
