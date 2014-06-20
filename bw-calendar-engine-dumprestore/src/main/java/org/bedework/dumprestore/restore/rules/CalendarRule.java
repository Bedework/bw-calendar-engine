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

package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike Douglass   douglm   rpi.edu
 * @version 1.0
 */
public class CalendarRule extends EntityRule {
  /** Constructor
   *
   * @param globals the globals
   */
  public CalendarRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) throws Exception {
    super.begin(ns, name, att);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    final BwCalendar entity = (BwCalendar)pop();

    globals.counts[globals.collections]++;

    if ((globals.counts[globals.collections] % 100) == 0) {
      info("Restore calendar # " + globals.counts[globals.collections]);
    }

    fixSharableEntity(entity, "Calendar");

    if (!entity.getTombstoned()) {
      if (entity.getExternalSub()) {
        globals.counts[globals.externalSubscriptions]++;
        globals.externalSubs.add(
                AliasInfo.getExternalSubInfo(entity.getPath(),
                                             entity.getPublick(),
                                             entity.getOwnerHref()));
      } else if (entity.getInternalAlias() && !entity.getPublick()) {
        final String target = entity.getInternalAliasPath();

        final AliasInfo ai = new AliasInfo(entity.getPath(),
                                           target,
                                           entity.getPublick(),
                                           entity.getOwnerHref());
        List<AliasInfo> ais = globals.aliasInfo.get(target);

        if (ais == null) {
          ais = new ArrayList<>();
          globals.aliasInfo.put(target, ais);
        }
        ais.add(ai);
        globals.counts[globals.aliases]++;
      }
    }

    try {
      if (globals.rintf != null) {
        /* If the parent is null then this should be one of the root calendars,
         */
        final String parentPath = entity.getColPath();
        if (parentPath == null) {
          // Ensure root
          globals.rintf.saveRootCalendar(entity);
        } else {
          globals.rintf.addCalendar(entity);
        }
      }
    } catch (final Throwable t) {
      throw new Exception(t);
    }
  }
}
