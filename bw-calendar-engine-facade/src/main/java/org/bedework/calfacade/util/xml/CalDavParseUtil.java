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
package org.bedework.calfacade.util.xml;

import org.bedework.calfacade.base.BwTimeRange;
import org.bedework.calfacade.exc.CalFacadeBadRequest;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.util.BwDateTimeUtil;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Some utilities for parsing caldav
 *
 * @author Mike Douglass douglm @ bedework.edu
 */
public class CalDavParseUtil {
  /** The given node must be a time-range element
   *  <!ELEMENT time-range EMPTY>
   *
   *  <!ATTLIST time-range start CDATA
   *                       end CDATA>
   *
   * e.g.        <C:time-range start="20040902T000000Z"
   *                           end="20040902T235959Z"/>
   *
   * @param nd
   * @param tzid - timezone to use if specified
   * @return TimeRange
   * @throws CalFacadeException
   */
  public static BwTimeRange parseBwTimeRange(Node nd,
                                         String tzid) throws CalFacadeException {
    BwDateTime start = null;
    BwDateTime end = null;

    NamedNodeMap nnm = nd.getAttributes();

    /* draft 5 has neither attribute required - the intent is that either
       may be absent */

    if ((nnm == null) || (nnm.getLength() == 0)) {
      // Infinite time-range?
      throw new CalFacadeBadRequest("Infinite time range");
    }

    int attrCt = nnm.getLength();

    try {
      Node nmAttr = nnm.getNamedItem("start");

      if (nmAttr != null) {
        attrCt--;
        if (tzid == null) {
          start = BwDateTimeUtil.getDateTimeUTC(nmAttr.getNodeValue());
        } else {
          start = BwDateTimeUtil.getDateTime(nmAttr.getNodeValue(),
                                             false,
                                             false,
                                             tzid);
        }
      }

      nmAttr = nnm.getNamedItem("end");

      if (nmAttr != null) {
        attrCt--;
        if (tzid == null) {
          end = BwDateTimeUtil.getDateTimeUTC(nmAttr.getNodeValue());
        } else {
          end = BwDateTimeUtil.getDateTime(nmAttr.getNodeValue(),
                                           false,
                                           false,
                                           tzid);
        }
      }
    } catch (Throwable t) {
      throw new CalFacadeBadRequest(t);
    }

    if (attrCt != 0) {
      throw new CalFacadeBadRequest();
    }

    return new BwTimeRange(start, end);
  }
}
