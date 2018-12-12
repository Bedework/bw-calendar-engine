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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeDupNameException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;

/** Move entities
 * <pre>
 *   move events [addcats] [setname] [alias=qval]* &lt;from-cal&gt; &lt;to-cal&gt; &lt;cats&gt;
 *   
 *   where:
 *      &lt;x-cal&gt; is a work or a qval giving the path
 *      &lt;cats&gt; zero or more categories to apply in move
 *      qval is a quoted value
 * </pre>
 * 
 * @author douglm
 *
 */
public class ProcessMove extends CmdUtilHelper {
  private final int batchSize = 200;

  ProcessMove(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      addInfo("move events [addcats] [setname] \\\n" +
                      "(alias=\"path\")* <from-cal> \\\n" +
                      "<to-cal> (\"category\")* \\\n" +
                      "   Move events optionally dding categories");

      return true;
    }

    if ("events".equals(wd)) {
      return moveEvents();
    }

    return false;
  }

  @Override
  String command() {
    return "move";
  }

  @Override
  String description() {
    return "Move events optionally dding categories";
  }

  private boolean moveEvents() throws Throwable {
    final String toPath;
    final String toOwner;
    final String fromPath;

    /* Expect from (possibly quoted)
     *        to  (possibly quoted)
     *        0 or more categories
     */
    try {
      final boolean addcats = testToken("addcats");

      final boolean setname = testToken("setname");

      final Collection<String> aliases = new ArrayList<>();

      while (testToken("alias")) {
        assertToken('=');

        aliases.add(quotedVal());
      }

      open();

      final BwCalendar from = getCal();

      if (from == null) {
        if (debug()) {
          debug("No from cal");
        }
        return false;
      }

      fromPath = from.getPath();

      final BwCalendar to = getCal();

      if (to == null) {
        if (debug()) {
          debug("No to cal");
        }
        return false;
      }

      toOwner = to.getOwnerHref();
      toPath = to.getPath();

      close();

      /* Get the list of categories */

      final Collection<String> catVals = new ArrayList<>();

      while (!cmdEnd()) {
        final String catVal = wordOrQuotedVal();

        if (catVal == null) {
          error("Expected a category");
          return false;
        }

        catVals.add(catVal);
      }

      /* Now we need to move the stuff in batches */
      for (;;) {
        open();

        final Collection<BwCategory> cats = getCats(catVals,
                                                    toOwner,
                                                    addcats,
                                                    fromPath);
        if (cats == null) {
          return false;
        }

        final Collection<String> names = 
                getSvci().getAdminHandler().getChildEntities(fromPath,
                                                             0,
                                                             batchSize);

        if (Util.isEmpty(names)) {
          break;
        }

        info("Move " + names.size() +
             " events from " + fromPath + " to " + toPath);
        final long start = System.currentTimeMillis();

        for (final String name: names) {
          moveEvent(fromPath,
                    toPath,
                    name,
                    cats,
                    aliases,
                    setname);
        }

        final long elapsed = System.currentTimeMillis() - start;

        info("Move of " + names.size() +
             " events took " + elapsed / 1000 +
             "seconds at " + elapsed / names.size() +
             "millisecs per event");

        close();
      }

      return true;
    } finally {
      close();
    }
  }

  private void moveEvent(final String fromPath,
                         final String toPath,
                         final String name,
                         final Collection<BwCategory> cats,
                         final Collection<String> aliases,
                         final boolean setname) throws Throwable {
    final EventInfo ei = getEvent(fromPath, name);

    final BwEvent ev = ei.getEvent();

    if (debug()) {
      final StringBuilder sb = new StringBuilder("Moving event ");
      sb.append(ev.getUid());
      if (ev.getRecurring()) {
        sb.append(" recurring");
      }

      sb.append(" from ");
      sb.append(fromPath);
      sb.append(" to ");
      sb.append(toPath);

      debug(sb.toString());
    }

    ev.setColPath(toPath);

    for (final BwCategory cat: cats) {
      ev.addCategory(cat);
    }

    for (final String alias: aliases) {
      final BwXproperty x = new BwXproperty();
      x.setName(BwXproperty.bedeworkAlias);
      x.setValue(alias);

      ev.addXproperty(x);
    }

    if (setname) {
      final String nm = ev.getName();
      final String nnm = ev.getUid() + ".ics";

      if (!nm.equals(nnm)) {
        ev.setName(nnm);
      }
    }

    if (ei.getOverrideProxies() != null) {
      for (final BwEvent oev: ei.getOverrideProxies()) {
        oev.setColPath(toPath);
      }
    }

    try {
      getSvci().getEventsHandler().update(ei, false, null);
    } catch (final CalFacadeDupNameException cdne) {
      pstate.addError("Duplicate name " + ev.getName() +
                           " uid: " + ev.getUid() +
                           " from: " + fromPath);
    }

  }

  private Collection<BwCategory> getCats(final Collection<String> catVals,
                                         final String toOwner,
                                         final boolean addcats,
                                         final String fromPath) throws Throwable {
    final Collection<BwCategory> cats = new ArrayList<>();

    for (final String catVal: catVals) {
      final BwCategory cat = getCat(toOwner, catVal);

      if (cat == null) {
        if (debug()) {
          debug("No cat ");
        }
        return null;
      }

      cats.add(cat);
    }

    if (addcats) {
      final String[] pathEls = fromPath.split("/");

      // First element should be "" for the leading "/"
      // Second element is the public root.

      if (pathEls.length > 2) {
        for (int i = 2; i < pathEls.length; i++) {
          final String catVal = pathEls[i];
          final BwCategory cat = getCat(toOwner, catVal);

          if (cat == null) {
            return null;
          }

          cats.add(cat);
        }
      }
    }

    return cats;
  }
}
