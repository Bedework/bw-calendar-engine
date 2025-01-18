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
package org.bedework.tools.cmdutil;

import org.bedework.access.PrivilegeDefs;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.RealiasResult;
import org.bedework.util.misc.Util;

import java.util.List;
import java.util.Set;

import static org.bedework.calfacade.indexing.BwIndexer.DeletedState.includeDeleted;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeEvent;
import static org.bedework.base.response.Response.Status.ok;

/** Reakias events
 * <pre>
 *   realias events &lt;alias&gt; replace|add &lt;alias&gt;
 *      to replace or add an alias to events with the first alias
 *   realias events &lt;alias&gt; remove
 *      to remove the alias from all events.
 *   
 *   where:
 *      &lt;alias&gt; is a qval giving the alias href
 *      qval is a quoted value
 * </pre>
 * 
 * @author douglm
 *
 */
public class ProcessRealias extends CmdUtilHelper {
  private final int batchSize = 200;

  ProcessRealias(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      pstate.addInfo("realias events \"<from-path>\" remove\n" +
                             "     remove topical area from events\n" +
                             "realias events \"<from-path>\" (add | replace) \"<to-path>\"\n" +
                             "     add or replace topical area");

      return true;
    }

    if ("events".equals(wd)) {
      return realiasEvents();
    }

    return false;
  }

  @Override
  String command() {
    return "realias";
  }

  @Override
  String description() {
    return "Realias events";
  }

  private boolean realiasEvents() throws Throwable {
    boolean add = false;
    boolean remove = false;
    BwXproperty xp = null;
    
    /* Expect from (possibly quoted)
     *        to  (possibly quoted)
     */
    try {
      open();

      final String fromHref = getAliasPath();

      if (fromHref == null) {
        return false;
      }

      final String wd = word();

      if (wd == null) {
        return false;
      }

      if ("remove".equals(wd)) {
        remove = true;
      } else {
        if ("add".equals(wd)) {
          add = true;
        } else if ("replace".equals(wd)) {
          remove = true;
        } else {
          error("Expect add | remove | replace");
          return false;
        }
        
        final BwCalendar col = getCal();

        if (col == null) {
          return false;
        }

        /* At the moment an alias is represented by an x-property with 
           the form:
           X-BEDEWORK-ALIAS; \
             X-BEDEWORK-PARAM-DISPLAYNAME=Jobs;\
             X-BEDEWORK-PARAM-PATH=/public/aliases/Browse By Topic/Jobs;\
             X-BEDEWORK-PARAM-ALIASPATH=/public/cals/MainCal:\
             /user/agrp_calsuite-MainCampus/Browse By Topic/Jobs"
        
           That is - it appears the displayname comes from the top level
           the path is what it points to
           the aliaspath is the path of the final target 
           the value is the path of the alias itself.
         */

        final BwCalendar aliasTarget = getAliasTarget(col);
        xp = new BwXproperty();

        xp.setName("X-BEDEWORK-ALIAS");
        xp.setPars("X-BEDEWORK-PARAM-DISPLAYNAME=" + col.getName() +
                           ";X-BEDEWORK-PARAM-PATH=" + 
                           col.getAliasUri().
                                   substring(BwCalendar.internalAliasUriPrefix.length()) +
                           ";X-BEDEWORK-PARAM-ALIASPATH=" + aliasTarget.getPath());
        
        xp.setValue(col.getPath());
      }
      
      final FilterBase fltr = parseQuery("topical_area=\"\t" + fromHref + "\"");
      if (fltr == null) {
        return false;
      }

      close();

      /* Now we need to process the stuff in batches */
      
      open();

      final BwIndexer idx = getIndexer(docTypeEvent);
      final SearchResult sr = idx.search(null,
                                         false,
                                         fltr,
                                         null,
                                         null,
                                         null,
                                         null,
                                         batchSize,
                                         includeDeleted,
                                         RecurringRetrievalMode.entityOnly);
      if (sr.getFound() == 0) {
        warn("No events found");
        return false;
      }
      
      for (;;) {
        final List<SearchResultEntry> sres = 
                idx.getSearchResult(sr,
                                    BwIndexer.Position.next,
                                    PrivilegeDefs.privAny);
        if (Util.isEmpty(sres)) {
          break;
        }
        
        int updated = 0;
        
        for (final SearchResultEntry sre: sres) {
          final Object o = sre.getEntity();
          
          if (!(o instanceof EventInfo)) {
            warn("Unhandled entity " + o.getClass());
            continue;
          }
          
          EventInfo ei = (EventInfo)o;
          
          /* Fetch the persistent version
           */
          final String colPath = ei.getEvent().getColPath();
          final String name = ei.getEvent().getName();
          ei = getEvents().get(colPath,
                               name);
          
          if (ei == null) {
            warn("Unable to retrieve persistent copy of " + colPath +
                         " " + name);
            continue;
          }

          updated += doRealias(ei, fromHref, xp, add, remove);
          if ((updated % 10) == 0) {
            info("done " + updated);
          }
        }
        
        info("Total updated: " + updated);
      }

      return true;
    } finally {
      close();
    }
  }
  
  private int doRealias(final EventInfo ei,
                        final String fromHref,
                        final BwXproperty xp,
                        final boolean add,
                        final boolean remove) throws Throwable {
    final BwEvent ev = ei.getEvent();
    int updated = 0;
    boolean changed = false;

    if (!add) {
      // We need to remove the from alias
      boolean found = false;
      for (final BwXproperty xpi: ev.getXproperties(BwXproperty.bedeworkAlias)) {
        if (xpi.getValue().equals(fromHref)) {
          ev.removeXproperty(xpi);
          found = true;
          changed = true;
          break;
        }
      }

      if (!found) {
        warn("Unable to find " + fromHref);
      }
    }

    if (!remove) {
      // Need to add a new one.

      ev.addXproperty(xp);
      changed = true;
    }

    final RealiasResult resp =
            getSvci().getEventsHandler().reAlias(ei.getEvent());

    if (resp.getStatus() != ok) {
      warn("Status from reAlias was " + resp.getStatus() +
                   " message was " + resp.getMessage());
      return 0;
    }
          
    /* Adjusting the categories may not work too well as we don't
       have any idea what categories appear as a result of the
       topical areas.

       It might be worth simplifying this and searching on
       topical areas alone.
     */

    if (!add) {
      // Remove all the categories for the from alias            
      final Set<BwCategory> fromCats = getCols().getCategorySet(fromHref);

      if (!Util.isEmpty(fromCats) &&
              !Util.isEmpty(ev.getCategories())) {
        for (final BwCategory cat: fromCats) {
          if (ev.getCategories().remove(cat)){
            changed = true;
          }
        }
      }
    }
          
    /* Now we need to adjust for the new alias
     */
    final Set<BwCategory> cats = resp.getCats();

    if (!Util.isEmpty(cats)) {
      for (final BwCategory cat: cats) {
        ev.addCategory(cat);
      }
      changed = true;
    }
    
    ei.clearChangeset();

    final EventInfo.UpdateResult ur =
            getSvci().getEventsHandler().update(ei, true, null,
                                                false); // autocreate
    
    if (ur.hasChanged) {
      updated++;
    }

    if (!Util.isEmpty(ei.getOverrides())) {
      for (final EventInfo ovei: ei.getOverrides()) {
        updated += doRealias(ovei, fromHref, xp, add, remove);
      }
    }
    
    return updated;
  }
}
