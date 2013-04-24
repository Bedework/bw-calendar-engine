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
package org.bedework.calfacade.base;

/** Interface which defines an implementing class as being differeable.
 *
 * <p>This is to distinguish it from Comparable. The issue here is that hibernate
 * requires that equals return true for entities which refer to the same db
 * object. Thus objects with the same key values will return equals == true
 * even if other fields differ.
 *
 * <p>The Comparable interface declaration states
 * <pre>It is strongly recommended (though not required) that natural orderings
 * be consistent with equals. </pre>
 *
 * <p>This means we need some other way to determine that two entities, though
 * equal are in fact different in some respect. For example, we may have two
 * attendee objects for the same person but with a different partstat.
 *
 * @author Mike Douglass
 * @version 1.0
 *
 * @param <T>
 */
public interface Differable<T> {
  /** Return true if the entity differs in any way.
   *
   * @param val
   * @return boolean
   */
  public boolean differsFrom(T val);
}
