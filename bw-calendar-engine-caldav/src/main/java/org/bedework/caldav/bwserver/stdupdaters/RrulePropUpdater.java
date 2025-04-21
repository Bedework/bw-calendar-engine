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

package org.bedework.caldav.bwserver.stdupdaters;

import org.bedework.base.exc.BedeworkException;
import org.bedework.caldav.bwserver.PropertyUpdater;
import org.bedework.caldav.server.sysinterface.SysIntf.UpdateResult;
import org.bedework.util.calendar.WsXMLTranslator;

import ietf.params.xml.ns.icalendar_2.RrulePropType;

import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public class RrulePropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) {
    final var ev = ui.getEvent();
    final var cte = ui.getCte();

    final var evRules = ev.getRrules();
    final RrulePropType rule = (RrulePropType)ui.getProp();

    /* For the moment we'll cheat and assume only one rule
     */
    if (evRules.size() > 1) {
      throw new BedeworkException(
              "Multiple Rrule properties are not supported");
    }

    if (ui.isRemove()) {
      ev.setRrules(new TreeSet<>());

      return UpdateResult.getOkResult();
    }

    final var rrule = WsXMLTranslator.fromRecurProperty(rule);

    if (ui.isAdd()) {
      ev.addRrule(rrule);

      return UpdateResult.getOkResult();
    }

    ev.getRrules().clear();
    ev.getRrules().add(rrule);

    return UpdateResult.getOkResult();
  }
}
