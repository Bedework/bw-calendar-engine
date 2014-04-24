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

import org.bedework.util.misc.ToString;

import java.io.Serializable;

/** For a VPOLL there may be a set of these with distinct values -
 * these are only added and not updated. They are for transporting the response.
 * For the candidates this gives the poll item id for that candidate.
 *
 * <p>We encode these as Response":"public-comment":"value</p>
 *
 * @author douglm
 */
public class PollItmId implements Comparable<PollItmId>, Serializable {
  private Integer response;
  private String publicComment;
  private Integer id;
  private boolean expanded;

  private String val;


  /**
   * @param id - the item id
   */
  public PollItmId(final Integer id) {
    this.id = id;
    expanded = true;
  }

  /** REPLY and STATUS only
   *
   * @param response from voter
   * @param publicComment text
   * @param id item id being responded to
   */
  public PollItmId(final Integer response,
                   final String publicComment,
                   final Integer id) {
    this.response = response;
    if (publicComment != null) {
      this.publicComment = publicComment.replace(':', ' ');
    }
    this.id = id;
    expanded = true;
  }

  /**
   * @param val - the internally encoded value
   */
  public PollItmId(final String val) {
    this.val = val;
    expand();
  }

  /**
   * @return item id
   */
  public Integer getId() {
    return id;
  }

  /**
   * @return response
   */
  public Integer getResponse() {
    return response;
  }

  public String getPublicComment() {
    return publicComment;
  }

  /**
   * @return value
   */
  public String getVal() {
    if (val != null) {
      return val;
    }

    final StringBuilder sb = new StringBuilder();

    if (getResponse() != null) {
      sb.append(getResponse());
    }
    sb.append(":");
    if (getPublicComment() != null) {
      sb.append(getPublicComment());
    }
    sb.append(":");
    sb.append(getId());

    val = sb.toString();

    return val;
  }

  private void expand() {
    if (expanded) {
      return;
    }

    if (val == null) {
      expanded = true;
      return;
    }

    final String[] split = val.split(":");

    if (split.length != 3) {
      expanded = true;
      return;
    }

    if (!empty(split[0])) {
      response = Integer.valueOf(split[0]);
    }
    if (!empty(split[1])) {
      publicComment = split[1];
    }
    id = Integer.valueOf(split[2]);

    expanded = true;
  }

  private boolean empty(final String s) {
    return (s == null) || (s.length() == 0);
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public int compareTo(final PollItmId  o) {
    return getId().compareTo(o.getId());
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    if (getResponse() != null) {
      ts.append("response", getResponse());
      ts.append("publicComment", getPublicComment());
    }

    ts.append("id", getId());

    return ts.toString();
  }
}
