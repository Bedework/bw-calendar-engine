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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/** This class allows us to apply a color to an event in a result set.
 *
 * <p>If the filter is null then a match on the path is sufficient. Otherwise
 * an event must be passed through the filter to see if it matches.
 *
 * <p>We need this as queries to the server are an optimised and combined form
 * of the paths and filters represented by aliases and real collections. The
 * result is that without effectively replaying the defined filters any given
 * event can be uassignable to a path and thence a color.
 *
 * <p>This is especially true of public events in the single calendar model
 * where all events come from a single path and some categories are common to
 * many of the virtual paths, e.g. Arts
 *
 * <p>Each entry is indexed by the path. If there is only one ClientCollectionInfo
 * in the result then that color is applied. If there is more than one then the
 * event must be matched to the filter.
 *
 * @author Mike Douglass
 *
 */
public class ColorMap extends HashMap<String,
                                      List<ClientCollectionInfo>> {
  /* These maintained by setColor */
  private String lastPath;
  private List<ClientCollectionInfo> theList;

  /** Either put a new entry or add to an existing path entry.
   *
   * @param path
   * @param cci
   */
  public void put(final String path, final ClientCollectionInfo cci) {
    List<ClientCollectionInfo> ccis = get(path);

    if (ccis == null) {
      ccis = new ArrayList<ClientCollectionInfo>();
      put(path, ccis);
    }

    if ((ccis.size() == 0) || (cci.getFilter() == null)) {
      /* Put it at the end so that subsets can be colored first */
      ccis.add(cci);
    } else {
      /* Put at the front */
      ccis.add(0, cci);
    }
  }

  /** Attempt to set the color for the given event. If there is no appropriate
   * mapping the event color will be set to null.
   *
   * @param ei
   * @param userHref
   */
  public void setColor(final EventInfo ei, final String userHref) {
    BwEvent ev = ei.getEvent();

    List<ClientCollectionInfo> ccis = null;
    String path = ev.getColPath();

    if (path.equals(lastPath)) {
      ccis = theList;
    } else {
      ccis = get(path);
      lastPath = path;
      theList = ccis;
    }

    if (Util.isEmpty(ccis)) {
      // This event doesn't appear in the map. Set color to null.
      ev.setColor(null);
      return;  // Done
    }

    for (ClientCollectionInfo cci: ccis) {
      try {
        if ((cci.getFilter() == null) || cci.getFilter().match(ev,
                                                               userHref)) {
          ev.setColor(cci.getColor());
          return;  // Done
        }
      } catch (Throwable t) {
        // Assume a failed match
      }
    }

    // No match
    ev.setColor(null);
  }

  /** Attempt to set the color for the given events. If there is no appropriate
   * mapping the event color will be set to null.
   *
   * @param eis
   * @param userHref
   */
  public void setColor(final Collection<EventInfo> eis,
                       final String userHref) {
    for (EventInfo ei: eis) {
      setColor(ei, userHref);
    }
  }
}
