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

/** Until we have a synch mechanism in place this provides a partial
 * mechanism. The returned token is equivalent to the Apple ctag and, for the
 * time being, is also the etag value.
 *
 * @author douglm
 *
 */
public class CollectionSynchInfo {
  /** Opaque value for Synch requests - usable as etag or ctag
   *
   */
  public String token;

  /** True if the targeted Collection object or resources changed.
   *
   */
  public boolean changed;
}
