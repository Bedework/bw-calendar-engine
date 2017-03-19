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
package org.bedework.calfacade.ifs;

import java.io.Serializable;
import java.util.Set;
import java.util.TreeSet;

/** Information about an instance of Svci. We can use this to track
 * processes and watch for those that hang.
 *
 * @author Mike Douglass
 *
 */
public class IfInfo implements Serializable {
  private String logid;
  private String id;
  private String lastStateTime;
  private String state;
  private long seconds;
  private boolean attemptedTermination;
  private Throwable terminationException;
  private Set<String> entityKeys = new TreeSet<>();
  
  /**
   *
   * @return a label identifying this type of service
   */
  public String getLogid() {
    return logid;
  }

  public void setLogid(final String val) {
    logid = val;
  }

  /**
   *
   * @return a label identifying this actual interface
   */
  public String getId() {
    return id;
  }

  public void setId(final String val) {
    id = val;
  }

  /** Updated every time state is changed. Not necessarily an
   * indication of idleness - it depends on state being updated,
   *
   * @return UTC time state was last changed.
   */
  public String getLastStateTime() {
    return lastStateTime;
  }

  public void setLastStateTime(final String val) {
    lastStateTime = val;
  }
  
  /**
   *
   * @return a hopefully informative message
   */
  public String getState() {
    return state;
  }

  public void setState(final String val) {
    state = val;
  }

  /**
   *
   * @return Seconds since transaction started
   */
  public long getSeconds() {
    return seconds;
  }

  public void setSeconds(final long val) {
    seconds = val;
  }

  /**
   * 
   * @return true if we tried to terminate this process.
   */
  public boolean attemptedTermination() {
    return attemptedTermination;
  }

  public void setAttemptedTermination(final boolean val) {
    attemptedTermination = val;
  }

  /** The value reflects the latest attempt.
   * 
   * @return non-null if we got an exception trying to terminate process
   */
  public Throwable terminationException() {
    return terminationException;
  }

  public void setTerminationException(
          final Throwable val) {
    terminationException = val;
  }
  
  /** A list of entities that were updated by this process. If the 
   * process is terminated we need to reindex them after rollback.
   * 
   * <p>This may involve deleting the entity from the index.</p>
   * 
   * @return a list of keys as encoded by IndexKeys
   */
  public Set<String> getEntityKeys() {
    return entityKeys;
  }

  public void addEntityKey(final String val) {
    entityKeys.add(val);
  }
}
