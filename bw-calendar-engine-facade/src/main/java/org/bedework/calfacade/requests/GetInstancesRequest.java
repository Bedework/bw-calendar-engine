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

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.responses.Response;
import org.bedework.util.misc.ToString;

import java.util.Collection;

import static org.bedework.calfacade.responses.Response.invalid;

/** Request instances for a given recurrence rule and start date.
 * Exdates, Rdates may be provided. Additionally the result may be
 * limited by a begin and/or an end date.
 *
 * <p>If no rrule is provided then rdates MUST be supplied</p>
 * 
 * User: douglm: spherical cow group
 */
public class GetInstancesRequest extends RequestBase {
  private String rrule;

  private BwDateTime startDt;

  private BwDateTime endDt;

  private Collection<String> exdates;

  private Collection<String> rdates;

  private String begin;

  private String end;

  public GetInstancesRequest() {
    setAction(getInstancesAction);
  }

  public GetInstancesRequest(final String rrule,
                             final BwDateTime startDt,
                             final BwDateTime endDt) {
    this();

    this.rrule = rrule;
    this.startDt = startDt;
    this.endDt = endDt;
  }

  /**
   *
   * @return rrule or null
   */
  public String getRrule() {
    return rrule;
  }

  /**
   *
   * @return start date
   */
  public BwDateTime getStartDt() {
    return startDt;
  }

  /**
   *
   * @return end date
   */
  public BwDateTime getEndDt() {
    return endDt;
  }

  /**
   *
   * @param val collection of exdates
   */
  public void setExdates(final Collection<String> val) {
    exdates = val;
  }

  /**
   * @return collection of exdates
   */
  public Collection<String> getExdates() {
    return exdates;
  }

  /**
   *
   * @param val collection of rdates
   */
  public void setRdates(final Collection<String> val) {
    rdates = val;
  }

  /**
   * @return collection of rdates
   */
  public Collection<String> getRdates() {
    return rdates;
  }

  /**
   *
   * @param val beginning of window or null
   */
  public void setBegin(final String val) {
    begin = val;
  }

  /**
   *
   * @return beginning of window or null
   */
  public String getBegin() {
    return begin;
  }

  /**
   *
   * @param val end of window or null
   */
  public void setEnd(final String val) {
    end = val;
  }

  /**
   *
   * @return end of window or null
   */
  public String getEnd() {
    return end;
  }

  @SuppressWarnings("RedundantIfStatement")
  @Override
  public boolean validate(final Response resp) {
    if (getStartDt() == null) {
      invalid(resp, "Missing start date/time");
      return false;
    }

    if (getRrule() != null) {
      return true;
    }

    if (getRdates() == null) {
      invalid(resp, "Missing rdates when no rrule");
      return false;
    }

    return true;
  }

  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);
    ts.append("rrule", getRrule());
    ts.append("startDt", getStartDt());
    ts.append("endDt", getEndDt());
    ts.append("exdates", getExdates());
    ts.append("rdates", getRdates());
    ts.append("begin", getBegin());
    ts.append("end", getEnd());
  }
}
