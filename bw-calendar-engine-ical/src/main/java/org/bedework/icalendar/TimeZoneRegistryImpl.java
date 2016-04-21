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
package org.bedework.icalendar;

import org.bedework.util.timezones.Timezones;

import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;

/**
 * The implementation of <code>TimeZoneRegistry</code> for the bedework
 * calendar. This class uses the thread local svci instance to load
 * vtimezone definitions from the database.
 *
 * @author Mike Douglass  (based on the original by Ben Fortuna)
 */
public class TimeZoneRegistryImpl implements TimeZoneRegistry {
  /*
  private static ThreadLocal<IcalCallback> threadCb =
    new ThreadLocal<IcalCallback>();

  /* * Allow static methods to obtain the current cb for the thread.
   *
   * @return IcalCallback
   * @throws CalFacadeException
   * /
  public static IcalCallback getThreadCb() throws CalFacadeException {
    IcalCallback cb = threadCb.get();
    if (cb == null) {
      throw new CalFacadeException("No thread local cb set");
    }

    return cb;
  }

  /* *
   * @param cb
   * /
  public static void setThreadCb(final IcalCallback cb) {
    threadCb.set(cb);
  }
*/
  /* (non-Javadoc)
   * @see net.fortuna.ical4j.model.TimeZoneRegistry#register(net.fortuna.ical4j.model.TimeZone)
   */
  public void register(final TimeZone timezone) {
    try {
      Timezones.registerTz(timezone.getID(), timezone);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public void register(final TimeZone timezone, final boolean update) {
    register(timezone);
  }

  /* (non-Javadoc)
   * @see net.fortuna.ical4j.model.TimeZoneRegistry#clear()
   */
  public void clear() {
    // Cached in CalSvc
  }

  /* (non-Javadoc)
   * @see net.fortuna.ical4j.model.TimeZoneRegistry#getTimeZone(java.lang.String)
   */
  public TimeZone getTimeZone(final String id) {
    try {
      return Timezones.getTz(id);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}

