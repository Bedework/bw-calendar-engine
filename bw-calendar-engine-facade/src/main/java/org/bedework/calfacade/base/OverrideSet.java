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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;

import java.util.Set;
import java.util.TreeSet;

/** An override collection which is a Set of T.
 *
 * @author Mike Douglass douglm - bedework.edu
 * @param <T>
 */
public abstract class OverrideSet <T> extends OverrideCollection <T,
                                                                  Set<T>>
      implements Set<T> {
  /**
   * @param fieldIndex
   * @param ann
   * @param cf
   */
  public OverrideSet(BwEvent.ProxiedFieldIndex fieldIndex,
                     BwEventAnnotation ann,
                     ChangeFlag cf) {
    super(fieldIndex, ann, cf);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.OverrideCollection#getEmptyOverrideCollection()
   */
  public Set<T> getEmptyOverrideCollection() {
    return new TreeSet<T>();
  }
}
