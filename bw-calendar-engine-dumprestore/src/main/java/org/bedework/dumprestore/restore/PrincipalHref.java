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
package org.bedework.dumprestore.restore;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.util.misc.ToString;

/**
 * @author Mike Douglass
 *
 */
public class PrincipalHref {
  /** */
  public String href;
  /** */
  public String account;
  /** */
  public int kind;
  /** */
  public String prefix;

  /** No-arg Constructor
   */
  public PrincipalHref() {
  }

  /** Constructor
   *
   * @param href
   * @param kind
   */
  public PrincipalHref(final String href, final int kind) {
    this.href = href;
    this.kind = kind;
  }

  /**
   * @param val
   */
  public void setHref(final String val) {
    href = val;
  }

  /**
   * @return  String account name
   */
  public String getHref() {
    return href;
  }

  /**
   * @param val
   */
  public void setAccount(final String val) {
    account = val;
  }

  /**
   * @return  String account name
   */
  public String getAccount() {
    return account;
  }

  /**
   * @param val int
   */
  public void setKind(final int val) {
    kind = val;
  }

  /**
   * @return int kind
   */
  public int getKind() {
    return kind;
  }

  /** Make a key to the principal.
   *
   * @param p
   * @return OwnerInfo
   */
  public static PrincipalHref makeOwnerHref(final BwPrincipal p) {
    PrincipalHref pr = new PrincipalHref(p.getPrincipalRef(), p.getKind());

    pr.prefix = RestoreGlobals.getPrincipalHrefPrefix(p);

    return pr;
  }

  /**
   * @param o
   * @return int
   */
  public int compareTo(final Object o) {
    if (o == null) {
      return -1;
    }

    if (!(o instanceof PrincipalHref)) {
      return -1;
    }

    PrincipalHref that = (PrincipalHref)o;

    if (kind < that.kind) {
      return -1;
    }

    if (kind > that.kind) {
      return 1;
    }

    return href.compareTo(that.href);
  }

  @Override
  public int hashCode() {
    return href.hashCode() * (kind + 1);
  }

  /* We always use the compareTo method
   */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    return compareTo(obj) == 0;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("href", href);
    ts.append("kind", kind);

    return ts.toString();
  }

}
