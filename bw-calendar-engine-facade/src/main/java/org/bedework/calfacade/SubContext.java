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

import org.bedework.calfacade.util.CalFacadeUtil;

import edu.rpi.sss.util.ToString;

/** Provide information about a sub-context of the public calendar.
 *
 * @author Mike Douglass       douglm@rpi.edu
 */
public class SubContext implements Comparable<SubContext> {
  private BwProperty prop;

  private String contextName;
  private String calSuite;
  private boolean defaultContext;

  /**
   * @param prop
   */
  public SubContext(final BwProperty prop) {
    this.prop = prop;
  }

  /**
   * @param contextName
   * @param calSuite
   * @param defaultContext
   */
  public SubContext(final String contextName,
                    final String calSuite,
                    final boolean defaultContext) {
    this.contextName = contextName;
    this.calSuite = calSuite;
    this.defaultContext = defaultContext;
  }

  /**
   * @param encoded - comma delimited internal form.
   */
  public SubContext(final String encoded) {
    setPvals(encoded);
  }

  /**
   * @return BwProperty representing this context.
   */
  public BwProperty getProp() {
    if (prop != null) {
      return prop;
    }

    String val = contextName + "," + calSuite;

    if (defaultContext) {
      val += ",true";
    }

    prop = new BwProperty(BwSystem.bedeworkContextsPname, val);
    return prop;
  }

  /** Get the context name.
   *
   * @return String   context name
   */
  public String getContextName() {
    setPvals();
    return contextName;
  }

  /** Get the calSuite name.
   *
   * @return String   calSuite name
   */
  public String getCalSuite() {
    setPvals();
    return calSuite;
  }

  /**
   *
   * @return true for default context
   */
  public boolean getDefaultContext() {
    setPvals();

    return defaultContext;
  }

  static String extractContextName(final String encoded) {
    return encoded.split(",")[0];
  }

  private void setPvals() {
    if (contextName != null) {
      return;
    }

    if (prop == null) {
      return;
    }

    setPvals(prop.getValue());
  }

  private void setPvals(final String val) {
    String[] pvals = val.split(",");
    contextName = pvals[0];
    calSuite = pvals[1];
    defaultContext = pvals.length == 3;
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final SubContext that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    return CalFacadeUtil.cmpObjval(getContextName(), that.getContextName());
  }

  @Override
  public int hashCode() {
    return 7 * getContextName().hashCode();
  }

  protected ToString toStringSegment(final ToString ts) {
    return ts.append("contextName", getContextName()).
        append("calSuite", getCalSuite());
  }

  @Override
  public String toString() {
    return toStringSegment(new ToString(this)).toString();
  }
}
