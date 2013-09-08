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
package org.bedework.calfacade.filter;

import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.calfacade.BwContact;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

/** A filter that selects events that have a certain contact
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class BwContactFilter extends ObjectFilter<BwContact> {
  /** Match on any of the contacts.
  *
  * @param name - null one will be created
  */
 public BwContactFilter(String name) {
   super(name, PropertyInfoIndex.CONTACT);
 }
}
