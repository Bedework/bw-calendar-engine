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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwXproperty;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.Timezones;

import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;

import java.util.Collection;
import java.util.HashMap;

/** Class to help handle events with embedded timezones
 *
 * @author Mike Douglass
 */
public class EventTimeZonesRegistry implements TimeZoneRegistry {
  private IcalTranslator trans;
  private BwEvent ev;

  private HashMap<String, TimeZone> localTzs;

  /** Constructor
   *
   * @param trans
   * @param ev
   */
  public EventTimeZonesRegistry(final IcalTranslator trans, final BwEvent ev) {
    this.trans = trans;
    this.ev = ev;
  }

  /* (non-Javadoc)
   * @see net.fortuna.ical4j.model.TimeZoneRegistry#register(net.fortuna.ical4j.model.TimeZone)
   */
  @Override
  public void register(final TimeZone timezone) {
    // Do nothing
  }

  @Override
  public void register(final TimeZone timezone, final boolean update) {
    register(timezone);
  }

  /* (non-Javadoc)
   * @see net.fortuna.ical4j.model.TimeZoneRegistry#clear()
   */
  @Override
  public void clear() {
    // Do nothing
  }

  /* (non-Javadoc)
   * @see net.fortuna.ical4j.model.TimeZoneRegistry#getTimeZone(java.lang.String)
   */
  @Override
  public TimeZone getTimeZone(final String id) {
    try {
      TimeZone tz = Timezones.getTz(id);
      if (tz != null) {
        return  tz;
      }

      if (localTzs != null) {
        tz = localTzs.get(id);

        if (tz != null) {
          return  tz;
        }
      }

      /* Try to find it in the event */

      String tzSpec = findTzValue(ev, id);

      if (tzSpec == null) {
        return null;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("BEGIN:VCALENDAR\n");
      sb.append(tzSpec);
      sb.append("END:VCALENDAR\n");

      synchronized (trans) {
        tz = trans.tzFromTzdef(sb.toString());
      }

      if (tz != null) {
        if (localTzs == null) {
          localTzs = new HashMap<String, TimeZone>();
        }

        localTzs.put(id, tz);
      }

      return tz;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   * @param ev
   * @param tzId
   * @return tz spec or null.
   */
  @SuppressWarnings("deprecation")
  public static String findTzValue(final BwEvent ev,
                                   final String tzId) {
    if (ev == null) {
      return null;
    }

    Collection<BwXproperty> xps = ev.getXproperties();
    if (Util.isEmpty(xps)) {
      // Try the old way

      xps = ev.getXproperties(BwXproperty.escapeName(BwXproperty.bedeworkTimezonePrefix + tzId));

      if (Util.isEmpty(xps)) {
        return null;
      }

      return xps.iterator().next().getValue();
    }

    for (BwXproperty xp: xps) {
      String val = xp.getValue();

      // value is semicolon escaped name ";" then tzspec
      int pos = BwXproperty.nextSemi(val, 0);

      if (pos < 0) {
        // bad
        continue;
      }

      String id = BwXproperty.unescapeSemi(val.substring(0, pos));

      if (id.equals(tzId)) {
        return val.substring(pos + 1);
      }
    }

    return null;
  }
}
