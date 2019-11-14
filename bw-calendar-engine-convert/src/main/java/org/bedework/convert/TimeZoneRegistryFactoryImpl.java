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

package org.bedework.convert;

import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;

/**
 * The implementation of <code>TimeZoneRegistry</code> for  the bedework
 * calendar. This class uses the thread local svci instance to load
 * vtimezone definitions from the database.
 *
 * @author Mike Douglass  (based on the original by Ben Fortuna)
 */
public class TimeZoneRegistryFactoryImpl extends TimeZoneRegistryFactory {
  public TimeZoneRegistry createRegistry() {
    return new TimeZoneRegistryImpl();
  }
}

