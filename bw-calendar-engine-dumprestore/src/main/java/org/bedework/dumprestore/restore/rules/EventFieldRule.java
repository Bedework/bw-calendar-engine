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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass
 * @version 1.0
 */
public class EventFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<String>();

    skippedNames.add("alarms");
    skippedNames.add("attachments");
    skippedNames.add("attendee");
    skippedNames.add("attendees");
    skippedNames.add("contacts");
    skippedNames.add("categories");
    skippedNames.add("descriptions");
    skippedNames.add("exdates");
    skippedNames.add("exrules");
    skippedNames.add("location");
    skippedNames.add("master");
    skippedNames.add("override");    // Set on object creation
    skippedNames.add("overrides");
    skippedNames.add("rdates");
    skippedNames.add("recipients");
    skippedNames.add("rrules");
    skippedNames.add("summaries");
    skippedNames.add("target");
    skippedNames.add("xproperties");
  }

  EventFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Exception {
    if (skippedNames.contains(name)) {
      return;
    }

    DateTimeValues dtv = null;

    if (top() instanceof DateTimeValues) {
      dtv = (DateTimeValues)pop();
    }

    EventInfo ei = (EventInfo)getTop(EventInfo.class, name);
    BwEventAnnotation ann = null;

    BwEvent e = ei.getEvent();
    if (e instanceof BwEventProxy) {
      ann = ((BwEventProxy)e).getRef();
      //e = ann;
    }

    if (shareableContainedEntityTags(e, name)) {
      return;
    }

    try {
      if (name.equals("emptyFlags")) {
        char[] flags = stringFld().toCharArray();

        for (char c: flags) {
          if ((c != 'T' ) && (c != 'F')) {
            error("Bad empty flags '" + stringFld() + "' for event " + ann);
          }
        }

        ann.setEmptyFlags(new String(flags));

        /* ------------------- Start/end --------------------------- */
      } else if (name.equals("noStart")) {
        if (ann != null) {
          ann.setNoStart(booleanFld());
        } else {
          e.setNoStart(booleanFld());
        }

      } else if (name.equals("dtstart")) {
        if (ann != null) {
          ann.setDtstart(dateTimeFld(dtv));
        } else {
          e.setDtstart(dateTimeFld(dtv));
        }

      } else if (name.equals("dtend")) {
        if (ann != null) {
          ann.setDtend(dateTimeFld(dtv));
        } else {
          e.setDtend(dateTimeFld(dtv));
        }

      } else if (name.equals("duration")) {
        // XXX Fix bad duration value due to old bug
        String dur = stringFld();
        char endType = e.getEndType();
        if ("PT1S".equals(dur) && (endType == StartEndComponent.endTypeNone)) {
          dur = "PT0S";
        }
        if (ann != null) {
          ann.setDuration(dur);
        } else {
          e.setDuration(dur);
        }
      } else if (name.equals("endType")) {
        if (ann != null) {
          ann.setEndType(charFld());
        } else {
          e.setEndType(charFld());
        }

      } else if (name.equals("entityType")) {
        e.setEntityType(intFld());
      } else if (name.equals("name")) {
        if (ann != null) {
          ann.setName(stringFld());
        } else {
          e.setName(stringFld());
        }
      } else if (name.equals("uid")) {
        if (ann != null) {
          ann.setUid(stringFld());
        } else {
          e.setUid(stringFld());
        }
      } else if (name.equals("classification")) {
        e.setClassification(stringFld());

      } else if (name.equals("link")) {
        e.setLink(stringFld());

      } else if (name.equals("geo-latitude")) {
        BwGeo geo = e.getGeo();
        if (geo == null) {
          geo = new BwGeo();
          e.setGeo(geo);
        }
        //geo.setLatitude(bigDecimalFld());
        geo.setLatitude(bigDecimalFld());
      } else if (name.equals("geo-longitude")) {
        BwGeo geo = e.getGeo();
        if (geo == null) {
          geo = new BwGeo();
          e.setGeo(geo);
        }
        //geo.setLongitude(bigDecimalFld());
        geo.setLongitude(bigDecimalFld());

      } else if (name.equals("status")) {
        String status = stringFld();
        if ((status != null) &&
            (!status.equals("F"))) {       // 2.3.2
          e.setStatus(status);
        }
      } else if (name.equals("cost")) {
        e.setCost(stringFld());
      } else if (name.equals("deleted")) {
        e.setDeleted(booleanFld());
      } else if (name.equals("tombstoned")) {
        e.setTombstoned(booleanFld());

      } else if (name.equals("dtstamp")) {
        e.setDtstamp(stringFld());
      } else if (name.equals("lastmod")) {
        e.setLastmod(stringFld());
      } else if (name.equals("created")) {
        e.setCreated(stringFld());

      } else if (name.equals("byteSize")) {
        e.setByteSize(intFld());

      } else if (name.equals("priority")) {
        e.setPriority(integerFld());

      } else if (name.equals("transparency")) {
        e.setTransparency(stringFld());

      } else if (name.equals("relatedTo")) {
//        e.setRelatedTo(stringFld());

      } else if (name.equals("percentComplete")) {
        e.setPercentComplete(integerFld());
      } else if (name.equals("completed")) {
        e.setCompleted(stringFld());

      } else if (name.equals("ctoken")) {
        e.setCtoken(stringFld());

        /* --------------- Recurrence fields ---------------------- */
      } else if (name.equals("recurring")) {
        e.setRecurring(new Boolean(booleanFld()));

      } else if (name.equals("rrule")) {
        e.addRrule(stringFld());
      } else if (name.equals("exrule")) {
        e.addExrule(stringFld());

      } else if (name.equals("rdate")) {
        e.addRdate(dateTimeFld(dtv));
      } else if (name.equals("exdate")) {
        e.addExdate(dateTimeFld(dtv));

      } else if (name.equals("recurrenceId")) {
        e.setRecurrenceId(stringFld());
      } else if (name.equals("latestDate")) {
        e.setCtoken(stringFld());

        /* --------------- Scheduling fields ---------------------- */

      } else if (name.equals("organizer")) {
        // pre 3.5

      } else if (name.equals("sequence")) {
        e.setSequence(intFld());
      } else if (name.equals("scheduleMethod")) {
        e.setScheduleMethod(intFld());
      } else if (name.equals("originator")) {
        e.setOriginator(stringFld());
      } else if (name.equals("recipient")) {
        e.addRecipient(stringFld());
      } else if (name.equals("scheduleState")) {
        e.setScheduleState(intFld());
      } else if (name.equals("organizerSchedulingObject")) {
        e.setOrganizerSchedulingObject(booleanFld());
      } else if (name.equals("attendeeSchedulingObject")) {
        e.setAttendeeSchedulingObject(booleanFld());
      } else if (name.equals("stag")) {
        e.setStag(stringFld());

        /* ------------------- vavailability --------------------------- */
      } else if (name.equals("busyType")) {
        e.setBusyType(intFld());
      } else {
        unknownTag(name);
      }
    } catch (Exception ex) {
      error("Error processing event uid " + e.getUid(), ex);
      globals.entityError = true;
    }
  }
}

