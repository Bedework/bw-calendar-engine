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

import net.fortuna.ical4j.model.Period;

import java.util.Collection;

/** Container for fetching instances.
 *
 * @author Mike Douglass douglm - spherical cow
 */
public class InstancesResponse extends Response {
  private Collection<Period> instances;

  private boolean truncated;

  /**
   *
   * @param val collection of instances
   */
  public void setInstances(final Collection<Period> val) {
    instances = val;
  }

  /**
   * @return collection of instances
   */
  public Collection<Period> getInstances() {
    return instances;
  }

  /** The result may be truncated if there are too many instances or
   * they are too far in the past or future.
   *
   * @param val true if result truncated
   */
  public void setTruncated(final boolean val) {
    truncated = val;
  }

  /**
   * @return true if result truncated
   */
  public boolean getTruncated() {
    return truncated;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("instances", getInstances());
    ts.append("truncated", getTruncated());
  }
}
