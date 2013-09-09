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

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.BwDateTimeUtil;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.util.calendar.IcalDefs;

import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.property.DtEnd;
import org.xml.sax.Attributes;

import java.util.Date;

/**
 * @author Mike Douglass   douglm@bedework.edu
 * @version 1.0
 */
public class EventRule extends EntityRule {
  private String kind;

  /** Constructor
   *
   * @param globals
   * @param kind "event", "event-annotation" or "override"
   */
  public EventRule(final RestoreGlobals globals, final String kind) {
    super(globals);

    this.kind = kind;
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) throws Exception {
    super.begin(ns, kind, att);

    BwEvent ev = (BwEvent)pop();

    boolean override = (kind.equals(objectOverride));

    if (ev instanceof BwEventAnnotation) {
      BwEventAnnotation ann = (BwEventAnnotation)ev;
      ann.setOverride(override);

      ev = new BwEventProxy(ann);

      if (override) {
        /* The override is embedded in the event so the top() should be the master
         * Annotations will get the master and target set later.
         */
        EventInfo mei = (EventInfo)top();
        ann.setTarget(mei.getEvent());
        ann.setMaster(mei.getEvent());
      } else {
        /* Make up empty event object for us to fill in */
        BwEvent e = new BwEventObj();
        ann.setTarget(e);
        ann.setMaster(e);
      }
    } else if (override) {
      error("Restore error - override but not annotation.");
    }

    EventInfo ei = new EventInfo(ev);

    /* Initialise fields that may be missed when restoring earlier data */
    ev.setNoStart(false);

    push(ei);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    if (!(top() instanceof EventInfo)) {
      warn("Top is not an event");
      warn(top().toString());
      return;
    }

    if (globals.entityError) {
      warn("Not restoring event because of previous error");
      warn(top().toString());
      return;
    }

    EventInfo ei = (EventInfo)top();
    BwEvent entity = ei.getEvent();

    if (entity instanceof BwEventProxy) {
      entity = ((BwEventProxy)entity).getRef();
    }

    boolean override = (entity instanceof BwEventAnnotation) &&
                        (((BwEventAnnotation)entity).getOverride());
    boolean alias = (entity instanceof BwEventAnnotation) && !override;

    globals.counts[globals.events]++;

    if ((globals.counts[globals.events] % 100) == 0) {
      info("Restore event # " + globals.counts[globals.events]);
    }

    if (!override) {
      fixSharableEntity(entity, "Event");
    }

    if ((entity.getEntityType() == IcalDefs.entityTypeTodo) &&
        entity.getNoStart() &&
        (entity.getEndType() == StartEndComponent.endTypeNone)) {
      /* The end date should be sometime in the distant future. If it isn't make
       * it so.
       * A bug was creating essentially one day events.
       */
      Date sdt = BwDateTimeUtil.getDate(entity.getDtstart());
      Date edt = BwDateTimeUtil.getDate(entity.getDtend());
      long years9 = 52L * 7L * 24L * 60L * 60L * 1000L * 9L; // about 9 years of millis

      if ((edt.getTime() - sdt.getTime()) < years9) {
        Dur years10 = new Dur(520); // about 10 years
        net.fortuna.ical4j.model.Date newDt =
          new net.fortuna.ical4j.model.Date(sdt.getTime());
        DtEnd dtEnd = new DtEnd(new net.fortuna.ical4j.model.Date(years10.getTime(newDt)));
        entity.setDtend(BwDateTime.makeBwDateTime(dtEnd));
        warn("Fixed task uid=" + entity.getUid());
      }
    }

    // Out here for debugging
    BwEvent target = null;
    BwEvent master = null;

    try {
      if (override) {
        pop();

        if (!(top() instanceof EventInfo)) {
          warn("Not restoring event because of previous error");
          warn(top().toString());
          return;
        }

        if (debug) {
          trace("Add override to event ");
        }
        EventInfo masterei = (EventInfo)top();
        masterei.addOverride(ei);
        return;
      }

      if (alias) {
        BwEventAnnotation ann = (BwEventAnnotation)entity;

        /* It's an alias, save an entry in the alias table then remove the dummy target.
         * We'll update them all at the end
         */
        // XXX Never did get on table globals.aliasTbl.put(ann);

        target = ann.getTarget();
        BwPrincipal annOwner = globals.getPrincipal(ann.getOwnerHref());
        BwEvent ntarget = globals.rintf.getEvent(annOwner,
                                                 target.getColPath(),
                                                 target.getRecurrenceId(),
                                                 target.getUid());

        if (ntarget == null) {
          error("Unknown target " + target);
        }
        ann.setTarget(ntarget);

        master = ann.getMaster();

        if (master.equals(target)) {
          ann.setMaster(ntarget);
        } else {
          BwEvent nmaster = globals.rintf.getEvent(annOwner,
                                                   master.getColPath(),
                                                   master.getRecurrenceId(),
                                                   master.getUid());

          if (nmaster == null) {
            error("Unknown master " + master);
          }
          ann.setMaster(nmaster);
        }
      }

      boolean ok = true;

      if ((entity.getUid() == null) || (entity.getUid().length() == 0)) {
        error("Unable to save event " + entity + ". Has no guid.");
        ok = false;
      }

      if (entity.getColPath() == null) {
        error("Unable to save event " + entity + ". Has no calendar.");
        ok = false;
      }

      BwEvent ev = ei.getEvent();

      if (ok && (globals.rintf != null) &&
          globals.onlyUsersMap.check(ev)) {
        globals.currentUser = globals.getPrincipal(ev.getOwnerHref());

        globals.rintf.restoreEvent(ei);
      }
    } catch (CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.noRecurrenceInstances)) {
        error("Event has no recurrence instances - not restored." +
              entity.getUid() + "\n" + atLine());
      } else {
        error("Unable to save event " + entity.getUid() + "\n" + atLine());
        cfe.printStackTrace();
      }
    } catch (Throwable t) {
      error("Unable to save event " + entity.getUid() + "\n" + atLine());
      t.printStackTrace();
    }

    pop();
  }
}

