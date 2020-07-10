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
package org.bedework.convert.jscal;

import org.bedework.calfacade.BwAttachment;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.convert.DifferResult;
import org.bedework.jsforj.impl.JSFactory;
import org.bedework.jsforj.impl.values.dataTypes.JSLocalDateTimeImpl;
import org.bedework.jsforj.impl.values.dataTypes.JSUnsignedIntegerImpl;
import org.bedework.jsforj.model.JSCalendarObject;
import org.bedework.jsforj.model.JSProperty;
import org.bedework.jsforj.model.JSPropertyNames;
import org.bedework.jsforj.model.JSTypes;
import org.bedework.jsforj.model.values.JSLink;
import org.bedework.jsforj.model.values.JSLocation;
import org.bedework.jsforj.model.values.JSOverride;
import org.bedework.jsforj.model.values.JSParticipant;
import org.bedework.jsforj.model.values.JSRecurrenceRule;
import org.bedework.jsforj.model.values.JSRelation;
import org.bedework.jsforj.model.values.JSValue;
import org.bedework.jsforj.model.values.collections.JSLinks;
import org.bedework.jsforj.model.values.collections.JSList;
import org.bedework.jsforj.model.values.collections.JSLocations;
import org.bedework.jsforj.model.values.collections.JSParticipants;
import org.bedework.jsforj.model.values.collections.JSRecurrenceOverrides;
import org.bedework.jsforj.model.values.dataTypes.JSLocalDateTime;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;
import org.bedework.util.timezones.DateTimeUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.FreeBusy;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.RRule;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.bedework.convert.BwDiffer.differs;
import static org.bedework.util.misc.response.Response.Status.failed;

/** Class to provide utility methods for translating to VEvent ical4j classes
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public class BwEvent2JsCal {
  final static JSFactory factory = JSFactory.getFactory();

  private final static BwLogger logger =
          new BwLogger().setLoggedClass(BwEvent2JsCal.class);

  /** Make a Jscalendar object from a BwEvent object.
   *
   * @param ei the event or override we are converting
   * @param master - if non-null ei is an override
   * @param jsCalMaster - must be non-null if master is non-null
   * @param tzreg - timezone registry
   * @param currentPrincipal - href for current authenticated user
   * @return Response with status and EventInfo object representing new entry or updated entry
   */
  public static GetEntityResponse<JSCalendarObject> convert(
          final EventInfo ei,
          final EventInfo master,
          final JSCalendarObject jsCalMaster,
          final TimeZoneRegistry tzreg,
          final String currentPrincipal) {
    final var resp = new GetEntityResponse<JSCalendarObject>();

    if ((ei == null) || (ei.getEvent() == null)) {
      return Response.notOk(resp, failed, "No entity supplied");
    }

    final BwEvent val = ei.getEvent();

    boolean isInstance = false;

    try {
      /*
      Component xcomp = null;
      Calendar cal = null;

      final List<BwXproperty> xcompProps = val.getXproperties(BwXproperty.bedeworkIcal);
      if (!Util.isEmpty(xcompProps)) {
        final BwXproperty xcompProp = xcompProps.get(0);
        final String xcompPropVal = xcompProp.getValue();

        if (xcompPropVal != null) {
          final StringBuilder sb = new StringBuilder();
          final Icalendar ic = new Icalendar();

          try {
            sb.append("BEGIN:VCALENDAR\n");
            sb.append(Version.VERSION_2_0.toString());
            sb.append("\n");
            sb.append(xcompPropVal);
            if (!xcompPropVal.endsWith("\n")) {
              sb.append("\n");
            }
            sb.append("END:VCALENDAR\n");

            CalendarBuilder bldr = new CalendarBuilder(new CalendarParserImpl(), ic);

            UnfoldingReader ufrdr = new UnfoldingReader(new StringReader(sb.toString()),
                                                        true);

            cal = bldr.build(ufrdr);
          } catch (Throwable t) {
            logger.error(t);
            logger.error("Trying to parse:\n" + xcompPropVal);
          }
        }
      }
      */

      boolean freeBusy = false;
      boolean vavail = false;
      boolean todo = false;
      boolean event = false;
      boolean vpoll = false;
      final String jstype;

      switch (val.getEntityType()) {
        case IcalDefs.entityTypeEvent:
          jstype = JSTypes.typeJSEvent;
          event = true;
          break;

        case IcalDefs.entityTypeTodo:
          jstype = JSTypes.typeJSTask;
          todo = true;
          break;
/*
        case IcalDefs.entityTypeJournal:
          jstype = JSTypes.typeJSJournal;
          break;

        case IcalDefs.entityTypeFreeAndBusy:
          jstype = JSTypes.typeJSFreeBusy;
          freeBusy = true;
          break;

        case IcalDefs.entityTypeVavailability:
          jstype = JSTypes.typeJSAvailability;
          vavail = true;
          break;

        case IcalDefs.entityTypeAvailable:
          jstype = JSTypes.typeJSAvailable;
          break;
*/

        case IcalDefs.entityTypeVpoll:
          jstype = JSTypes.typeJSVPoll;
          vpoll = true;
          break;

        default:
          return Response.error(resp, "org.bedework.invalid.entity.type: " +
                  val.getEntityType());
      }

      JSCalendarObject jsval = null;
      final JSRecurrenceOverrides ovs;

      if (master == null) {
        jsval = (JSCalendarObject)factory.newValue(jstype);
        ovs = null;
      } else {
        ovs = jsCalMaster.getOverrides(true);
      }

      /* ------------------- RecurrenceID --------------------
       * Done early to verify if this is an instance.
       */

      String strval = val.getRecurrenceId();
      JSLocalDateTime rid = null;
      if ((strval != null) && (strval.length() > 0)) {
        rid = new JSLocalDateTimeImpl(
                jsonDate(timeInZone(strval,
                                    findZone(val.getDtstart(),
                                             val.getDtend()),
                                    tzreg)));
        isInstance = true;

        if (master == null) {
          // A standalone recurrence instance
          jsval.setRecurrenceId(rid);
        } else {
          /* See if the override exists in the jscalendar version.
             It may do so if we have rdates or exdates.
             If it does exist and is an excluded date then this is an error.

             If it doesn't exist then make one.
           */
          final JSOverride ov =
                  ovs.makeEntry(rid).getValue();

          if (ov.getExcluded()) {
            throw new RuntimeException("Cannot have override for exdate");
          }
          jsval = ov;
          //jsval.setRecurrenceId(rid);
        }
      }

      /* At this point jsval should be non-null. Otherwise we have a
         badly formatted event.
       */

      if (jsval == null) {
        return Response.error(resp, "Badly formatted component");
      }

      /* ------------------- Alarms -------------------- */
      //VAlarmUtil.processEventAlarm(val, comp, currentPrincipal);

      /* ------------------- Attachments -------------------- */

      final Set<BwAttachment> atts = val.getAttachments();
      final DifferResult<BwAttachment, ?> attDiff =
              differs(BwAttachment.class,
                      PropertyInfoIndex.ATTACH,
                      atts, master);
      if (attDiff.differs) {
        if ((master == null) || attDiff.addAll) {
          // Just add to js
          final var links = jsval.getLinks(true);
          for (final BwAttachment att: atts) {
            makeAttachment(links, att);
          }
        } else if (attDiff.removeAll) {
          // Remove all ref="enclosure" from links
          final var masterLinks = jsCalMaster.getLinks(false);

          if (masterLinks != null) {
            for (final var linkp: masterLinks.get()) {
              final var link = linkp.getValue();
              if ("enclosure".equals(link.getRel())) {
                jsval.setNull(JSPropertyNames.links,
                              linkp.getName());
              }
            }
          }
        } else {
          if (!Util.isEmpty(attDiff.removed)) {
            for (final BwAttachment att: attDiff.removed) {
              final var masterLinks = jsCalMaster.getLinks(false);

              for (final var linkp: masterLinks.get()) {
                final var link = linkp.getValue();
                if (compareAttachment(att, linkp)) {
                  jsval.setNull(JSPropertyNames.links,
                                linkp.getName());
                }
              }
            }
          }
          if (!Util.isEmpty(attDiff.added)) {
            for (final BwAttachment att: attDiff.added) {
              makeAttachmentOverride(jsval, att);
            }
          }
        }
      }

      /* ------------------- Attendees -------------------- */
      if (!vpoll && (val.getNumAttendees() > 0)) {
        final Set<BwAttendee> attendees = val.getAttendees();
        final DifferResult<BwAttendee, Set<BwAttendee>> partDiff =
                differs(BwAttendee.class,
                        PropertyInfoIndex.ATTENDEE,
                        attendees, master);
        if (partDiff.differs) {
          if ((master == null) || partDiff.addAll) {
            // Just add to js
            makeAttendees(jsval,
                          jsCalMaster,
                          jsval.getParticipants(true),
                          attendees);
          } else if (partDiff.removeAll) {
            // Remove all from participants - use sendTo to identify an attendee
            final var masterPart = jsCalMaster.getParticipants(false);

            if (masterPart != null) {
              for (final var partp: masterPart.get()) {
                final var part = partp.getValue();
                final var sendTo = part.getSendTo(false);
                if ((sendTo != null) && (!Util.isEmpty(sendTo.get()))) {
                  // Is an attendee
                  jsval.setNull(JSPropertyNames.participants,
                                partp.getName());
                }
              }
            }
          } else {
            if (!Util.isEmpty(partDiff.removed)) {
              final var masterPart = jsCalMaster.getParticipants(false);

              if (masterPart != null) {
                for (final BwAttendee att: partDiff.removed) {
                  final var partp = masterPart.findParticipant(
                          att.getAttendeeUri());
                  if (partp != null) {
                    jsval.setNull(JSPropertyNames.participants,
                                  partp.getName());
                  }
                }
              }
            }
            if (!Util.isEmpty(partDiff.added)) {
              makeAttendees(jsval,
                            jsCalMaster,
                            jsval.getParticipants(true),
                            partDiff.added);
            }
            if (!Util.isEmpty(partDiff.differ)) {
              final var parts = jsval.getParticipants(true);

              for (final BwAttendee att: partDiff.differ) {
                // Find the attendee in the master and output the difference
                for (final BwAttendee matt: master.getEvent().getAttendees()) {
                  if (!"group".equalsIgnoreCase(att.getCuType())) {
                    continue;
                  }
                  if (att.equals(matt)) {
                    makeAttendeeOverride(jsval,
                                         jsCalMaster,
                                         att, matt);
                    break;
                  }
                }
                for (final BwAttendee matt: master.getEvent().getAttendees()) {
                  if ("group".equalsIgnoreCase(att.getCuType())) {
                    continue;
                  }
                  if (att.equals(matt)) {
                    makeAttendeeOverride(jsval,
                                         jsCalMaster,
                                         att, matt);
                    break;
                  }
                }
              }
            }
          }
        }
      }

      /* ------------------- Categories -------------------- */

      final Set<BwCategory> cats = val.getCategories();
      final DifferResult<BwCategory, Set<BwCategory>> catDiff =
              differs(BwCategory.class,
                      PropertyInfoIndex.CATEGORIES,
                      cats, master);
      if (catDiff.differs) {
        if ((master == null) || catDiff.addAll) {
          // Just add to js
          final JSList<String> jscats = jsval.getKeywords(true);
          for (final BwCategory cat: val.getCategories()) {
            jscats.add(cat.getWord().getValue());
          }
        } else if (catDiff.removeAll) {
          jsval.setNull(JSPropertyNames.keywords);
        } else if (!Util.isEmpty(catDiff.removed)) {
          for (final BwCategory cat: catDiff.removed) {
            jsval.setNull(JSPropertyNames.keywords + "/" +
                    cat.getWord().getValue());
          }
        } else {
          if (Util.isEmpty(catDiff.added)) {
            return Response.error(resp, "Bad return from differ -" +
                    " expected non-null added");
          }

          for (final BwCategory cat: catDiff.added) {
            jsval.addProperty(JSPropertyNames.keywords + "/" +
                                      cat.getWord().getValue(), true);
          }
        }
      }

      /* ------------------- Class -------------------- */

      final String clazz = val.getClassification();
      final DifferResult<String, ?> classDiff =
              differs(String.class,
                      PropertyIndex.PropertyInfoIndex.CLASS,
                      clazz, master);
      if (classDiff.differs) {
        if (clazz.equalsIgnoreCase("confidential")) {
          jsval.setProperty(JSPropertyNames.privacy, "secret");
        } else {
          jsval.setProperty(JSPropertyNames.privacy,
                            clazz.toLowerCase());
        }
      }

      /* ------------------- Comments -------------------- */

      final var comments = val.getComments();
      final DifferResult<BwString, Set<BwString>> commDiff =
              differs(BwString.class,
                      PropertyInfoIndex.COMMENT,
                      comments, master);
      if (commDiff.differs) {
        for (final BwString str: val.getComments()) {
          jsval.addComment(str.getValue());
        }
      }

      /* ------------------- Completed -------------------- */
      if (todo || vpoll) {
        final var completed = val.getCompleted();
        final DifferResult<BwString, ?> compDiff =
                differs(BwString.class,
                        PropertyInfoIndex.COMPLETED,
                        completed, master);
        if (compDiff.differs) {
          jsval.setProperty(JSPropertyNames.progress, "completed");
          jsval.setProperty(JSPropertyNames.progressUpdated,
                            jsonDate(completed));
        }
      }
      /* ------------------- Contact -------------------- */

      final var contacts = val.getContacts();
      final DifferResult<BwContact, ?> contDiff =
              differs(BwContact.class,
                      PropertyInfoIndex.CONTACT,
                      contacts, master);
      if (contDiff.differs) {
        for (final BwContact c: contacts) {
          final var parts = jsval.getParticipants(true);
          final var jsContact =
                  parts.makeEntry(c.getUid()).getValue();

          final var roles = jsContact.getRoles(true);
          roles.add("contact");

          jsContact.setDescription(c.getCn().getValue());
          addLinkId(jsval, jsCalMaster, jsContact, c.getLink());
        }
      }

      /* ------------------- Cost -------------------- */

      if (val.getCost() != null) {
        throw new RuntimeException("Not done");
//        addXproperty(jsval, master, BwXproperty.bedeworkCost,
  //                   null, val.getCost());
      }

      /* ------------------- Created -------------------- */

      if ((master == null) && // Suppressed for overrides
          val.getCreated() != null) {
        jsval.setProperty(JSPropertyNames.created,
                          jsonDate(val.getCreated()));
      }

      /* ------------------- Deleted -------------------- */

      if (val.getDeleted()) {
        throw new RuntimeException("Not done");
//        addXproperty(jsval, master, BwXproperty.bedeworkDeleted,
  //                   null, String.valueOf(val.getDeleted()));
      }

      /* ------------------- Description -------------------- */

      final String descVal = val.getDescription();
      final DifferResult<String, ?> descDiff =
              differs(String.class,
                      PropertyInfoIndex.DESCRIPTION,
                      descVal, master);
      if (descDiff.differs) {
        jsval.setProperty(JSPropertyNames.description, descVal);
      }

      /* ------------------- DtStamp -------------------- */
      /* ------------------- LastModified -------------------- */

      if (master == null) {
        final var dtstamp = val.getDtstamp();
        final var lastMod = val.getLastmod();
        final String updatedVal;

        final var updCmp = Util.cmpObjval(dtstamp, lastMod);
        if (updCmp <= 0) {
          updatedVal = dtstamp;
        } else {
          updatedVal = lastMod;
        }

        if (updatedVal != null) {
          jsval.setProperty(JSPropertyNames.created,
                            jsonDate(updatedVal));
        }
      }

      /* ------------------- DtStart -------------------- */

      String startTimezone = null;
      if (!val.getNoStart()) {
        final BwDateTime bdt = val.getDtstart();
        startTimezone = jscalTzid(bdt, true, master);
        final String jsStart = jscalDt(bdt);

        /* We only emit start if it differs from the recurrence id
         */

        if ((rid == null) ||
                !jsStart.equals(rid.getStringValue())) {
          jsval.setProperty(JSPropertyNames.start,
                            jsStart);

          if (bdt.getDateType() && (master == null)) {
            // Don't add to override
            jsval.addProperty(JSPropertyNames.showWithoutTime,
                              true);
          }

          if (startTimezone != null) {
            jsval.setProperty(JSPropertyNames.timeZone,
                              startTimezone);
          }
        }
      }

      /* ------------------- Due/DtEnd/Duration --------------------
      */

      if (val.getEndType() == StartEndComponent.endTypeDate) {
        final BwDateTime bdt = val.getDtend();
        final var tzid = jscalTzid(bdt, false, master);

        if (val.getNoStart()) {
          // Have to set tz from end
          if (tzid != null) {
            jsval.setProperty(JSPropertyNames.timeZone,
                              tzid);
          }
        }

        if (todo) {
          // TODO - adjust due if different tz
          // TODO - test to see if this shoudl be output
          sameZone(val.getDtstart(), bdt);
          jsval.setProperty(JSPropertyNames.due,
                            jscalDt(bdt));
        } else {
          if (Util.cmpObjval(tzid, startTimezone) != 0) {
            // Add a location for end tz
            final var locs = jsval.getLocations(true);
            final JSLocation loc =
                    locs.makeLocation().getValue();

            loc.setTimeZone(tzid);
            loc.setRelativeTo("end");
          }

          final String durVal =
                  BwDateTime.makeDuration(val.getDtstart(),
                                          bdt).toString();
          final DifferResult<String, ?> durDiff =
                  differs(String.class,
                          PropertyInfoIndex.DURATION,
                          durVal, master);
          if (durDiff.differs) {
            jsval.setProperty(JSPropertyNames.duration,
                              durVal);
          }
        }
      } else if (val.getEndType() == StartEndComponent.endTypeDuration) {
        final var durVal = val.getDuration();
        final DifferResult<String, ?> durDiff =
                differs(String.class,
                        PropertyInfoIndex.DURATION,
                        durVal, master);
        if (durDiff.differs) {
          jsval.setProperty(JSPropertyNames.duration, durVal);
        }
      }

      /* ------------------- ExDate --below------------ */
      /* ------------------- ExRule --below------------- */

      if (freeBusy) {
        final Collection<BwFreeBusyComponent> fbps = val.getFreeBusyPeriods();

        if (fbps != null) {
          for (final BwFreeBusyComponent fbc: fbps) {
            final FreeBusy fb = new FreeBusy();

            throw new RuntimeException("Not done");
/*            int type = fbc.getType();
            if (type == BwFreeBusyComponent.typeBusy) {
              addParameter(fb, FbType.BUSY);
            } else if (type == BwFreeBusyComponent.typeFree) {
              addParameter(fb, FbType.FREE);
            } else if (type == BwFreeBusyComponent.typeBusyUnavailable) {
              addParameter(fb, FbType.BUSY_UNAVAILABLE);
            } else if (type == BwFreeBusyComponent.typeBusyTentative) {
              addParameter(fb, FbType.BUSY_TENTATIVE);
            } else {
              throw new CalFacadeException("Bad free-busy type " + type);
            }

            PeriodList pdl =  fb.getPeriods();

            for (Period p: fbc.getPeriods()) {
              // XXX inverse.ca plugin cannot handle durations.
              Period np = new Period(p.getStart(), p.getEnd());
              pdl.add(np);
            }

            pl.add(fb);
 */
          }
        }

      }

      /* ------------------- Geo -------------------- */

      if (!vpoll) {
        final BwGeo bwgeo = val.getGeo();
        if (bwgeo != null) {
          final Geo geo = new Geo(bwgeo.getLatitude(), bwgeo.getLongitude());
          throw new RuntimeException("Not done");
          //pl.add(geo);
        }
      }

      /* ------------------- Location -------------------- */

      if (!vpoll) {
        final BwLocation loc = val.getLocation();
        final DifferResult<BwLocation, ?> locDiff =
                differs(BwLocation.class,
                        PropertyInfoIndex.LOCATION,
                        loc, master);
        if (locDiff.differs) {
          final JSLocations locs = jsval.getLocations(true);
          final JSLocation jsloc =
                  locs.makeEntry(loc.getUid()).getValue();

          jsloc.setName(loc.getAddressField());
          jsloc.setDescription(loc.getCombinedValues());

          /*
          pl.add(langProp(uidProp(prop, loc.getUid()), loc.getAddress()));

          addXproperty(pl, BwXproperty.xBedeworkLocationAddr,
                       null, loc.getAddressField());
          addXproperty(pl, BwXproperty.xBedeworkLocationRoom,
                       null, loc.getRoomField());
          addXproperty(pl, BwXproperty.xBedeworkLocationAccessible,
                       null, String.valueOf(loc.getAccessible()));
          addXproperty(pl, BwXproperty.xBedeworkLocationSfield1,
                       null, loc.getSubField1());
          addXproperty(pl, BwXproperty.xBedeworkLocationSfield2,
                       null, loc.getSubField2());
          addXproperty(pl, BwXproperty.xBedeworkLocationGeo,
                       null, loc.getGeouri());
          addXproperty(pl, BwXproperty.xBedeworkLocationStreet,
                       null, loc.getStreet());
          addXproperty(pl, BwXproperty.xBedeworkLocationCity,
                       null, loc.getCity());
          addXproperty(pl, BwXproperty.xBedeworkLocationState,
                       null, loc.getState());
          addXproperty(pl, BwXproperty.xBedeworkLocationZip,
                       null, loc.getZip());
          addXproperty(pl, BwXproperty.xBedeworkLocationLink,
                       null, loc.getLink());
*/
        }
      }

      /* ------------------- Organizer -------------------- */

      final BwOrganizer org = val.getOrganizer();
      if (org != null) {
        final var orgUri = org.getOrganizerUri();

        final DifferResult<BwOrganizer, ?> orgDiff =
                differs(BwOrganizer.class,
                        PropertyInfoIndex.ORGANIZER,
                        org, master);
        if (orgDiff.differs) {
          final var parts = jsval.getParticipants(true);

          var jsOrg = parts.findParticipant(orgUri);
          if (jsOrg == null) {
            jsOrg = parts.makeParticipant();
          }
          /* For the master always add a replyTo for iMip and the
             organizer.

             For an override - only if the reply to has changed
           */

          if (master == null) {
            jsval.getReplyTo(true).makeReplyTo("imip", orgUri);
          } else {
            final var morg = master.getEvent().getOrganizer();
            if ((morg == null) ||
                    !morg.getOrganizerUri().equals(orgUri)) {
              jsval.getReplyTo(true).makeReplyTo("imip", orgUri);
            }
          }

          makeOrganizer(jsval, jsCalMaster, jsOrg.getValue(), org);
        }
      }

      /* ------------------- PercentComplete -------------------- */

      if (todo) {
        final Integer pc = val.getPercentComplete();
        if (pc != null) {
          throw new RuntimeException("Not done");
          //pl.add(new PercentComplete(pc));
        }
      }

      /* ------------------- Priority -------------------- */

      final Integer prio = val.getPriority();
      final DifferResult<Integer, ?> prioDiff =
              differs(Integer.class,
                      PropertyInfoIndex.PRIORITY,
                      prio, master);
      if (prioDiff.differs) {
        jsval.setProperty(JSPropertyNames.priority,
                          new JSUnsignedIntegerImpl(prio));
      }

      /* ------------------- RDate -below------------------- */

      /* ------------------- RelatedTo -------------------- */

      /* We encode related to (maybe) as triples -
            reltype, value-type, value

         I believe we use the x-property because we are only have a
         single related to value in the schema.

         We also apparently have a value type parameter. This is not
         covered in the spec.

         We'll ignore that for the moment.
       */

      String[] info = null;

      final BwRelatedTo relto = val.getRelatedTo();
      if (relto != null) {
        info = new String[3];

        info[0] = relto.getRelType();
        info[1] = ""; // default
        info[2] = relto.getValue();
      } else {
        final String relx = val.getXproperty(BwXproperty.bedeworkRelatedTo);

        if (relx != null) {
          info = Util.decodeArray(relx);
        }
      }

      if (info != null) {
        final var relations = jsval.getRelatedTo(true);

        int i = 0;

        while (i < info.length) {
          String reltype = info[i];
          //String valtype = info[i + 1];
          final String relval = info[i + 2];

          final var rel = relations.makeEntry(relval);
          final JSRelation relVal = rel.getValue();
          final JSList<String> rs = relVal.getRelations(true);
          if (reltype == null) {
            reltype = "parent";
          }
          switch (reltype.toLowerCase()) {
            case "parent":
              rs.add("parent");
              break;
            case "child":
              rs.add("child");
              break;
            case "sibling":
              rs.add("next");
          }

          i += 3;
        }
      }

      /* ------------------- Resources -------------------- */

      if (val.getNumResources() > 0) {
        /* This event has a resource */

        //prop = new Resources();
        //TextList rl = ((Resources)prop).getResources();

        for (final BwString str: val.getResources()) {
          // LANG
          //rl.add(str.getValue());
        }

        throw new RuntimeException("Not done");
        //pl.add(prop);
      }

      /* ------------------- RRule -below------------------- */

      /* ------------------- Sequence -------------------- */

      final int seq = val.getSequence();
      final DifferResult<Integer, ?> seqDiff =
              differs(Integer.class,
                      PropertyInfoIndex.SEQUENCE,
                      seq, master);
      // Don't add a defaulted sequence
      if (seqDiff.differs &&
              ((master != null) || (seq != 0))) {
        jsval.setProperty(JSPropertyNames.sequence,
                          new JSUnsignedIntegerImpl(seq));
      }

      /* ------------------- Status -------------------- */

      final String status = val.getStatus();
      if ((status != null) && !status.equals(BwEvent.statusMasterSuppressed)) {
        final DifferResult<String, ?> statusDiff =
                differs(String.class,
                        PropertyInfoIndex.STATUS,
                        status, master);
        if (statusDiff.differs) {
          if (event) {
            jsval.setProperty(JSPropertyNames.status,
                              status.toLowerCase());
          } else {
            jsval.setProperty(JSPropertyNames.progress,
                              status.toLowerCase());
          }
        }
      }

      /* ------------------- Summary -------------------- */

      final var summary = val.getSummary();
      final DifferResult<String, ?> sumDiff =
              differs(String.class,
                      PropertyInfoIndex.SUMMARY,
                      summary, master);
      if (sumDiff.differs) {
        jsval.setProperty(JSPropertyNames.title, summary);
      }

      /* ------------------- Transp -------------------- */

      if (!todo && !vpoll) {
        strval = val.getPeruserTransparency(currentPrincipal);

        final DifferResult<String, ?> transpDiff =
                differs(String.class,
                        PropertyInfoIndex.TRANSP,
                        strval, master);
        if (transpDiff.differs) {
          if (strval.equalsIgnoreCase("opaque")) {
            jsval.setProperty(JSPropertyNames.freeBusyStatus, "busy");
          } else {
            jsval.setProperty(JSPropertyNames.freeBusyStatus,
                              "free");
          }
        }
      }

      /* ------------------- Uid -------------------- */

      if (master == null) {
        jsval.setUid(val.getUid());
      }

      /* ------------------- Url -------------------- */

      strval = val.getLink();

      if (strval != null) {
        // Possibly drop this if we do it on input and check all data
        strval = strval.trim();
      }

      final DifferResult<String, ?> urlDiff =
              differs(String.class,
                      PropertyInfoIndex.URL,
                      strval, master);
      if (urlDiff.differs) {
        final URI uri = Util.validURI(strval);
        if (uri != null) {
          throw new RuntimeException("Not done");
          //pl.add(new Url(uri));
        }
      }

      /* ------------------- X-PROPS -------------------- */

      if (val.getNumXproperties() > 0) {
        /* This event has x-props */

        try {
//          throw new RuntimeException("Not done");
          //xpropertiesToIcal(pl, val.getXproperties());
        } catch (Throwable t) {
          // XXX For the moment swallow these.
          logger.error(t);
        }
      }

      /* ------------------- Overrides -------------------- */

      if (!vpoll && !isInstance && val.testRecurring()) {
        doRecurring(val, jsval, tzreg);
      }

      /* ------------------- Available -------------------- */

      if (vavail) {
        throw new RuntimeException("Not done");
/*        if (ei.getNumContainedItems() > 0) {
          final VAvailability va = (VAvailability)comp;
          for (final EventInfo aei: ei.getContainedItems()) {
            va.getAvailable().add((Available)toIcalComponent(aei, false, tzreg,
                                                  currentPrincipal));
          }
        }

        /* ----------- Vavailability - busyType ----------------- */

/*
        String s = val.getBusyTypeString();
        if (s != null) {
          throw new RuntimeException("Not done");
          //pl.add(new BusyType(s));
        }

 */
      }

      /* ------------------- Vpoll -------------------- */

      //if (!vpoll && (val.getPollItemId() != null)) {
      //  pl.add(new PollItemId(val.getPollItemId()));
     // }

      final List<BwXproperty> xlocs =
              val.getXproperties(BwXproperty.xBedeworkLocation);

      /*
      throw new RuntimeException("Not done");
      if (!Util.isEmpty(xlocs) &&
              (comp.getProperty(Property.LOCATION) == null)) {
        // Create a location from the x-property
        final BwXproperty xloc = xlocs.get(0);

        final Location loc = new Location(xloc.getValue());

        comp.getProperties().add(loc);
      }

      if (vpoll) {
        final Integer ival = val.getPollWinner();

        if (ival != null) {
          pl.add(new PollWinner(ival));
        }

        strval = val.getPollAcceptResponse();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new AcceptResponse(strval));
        }

        strval = val.getPollMode();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new PollMode(strval));
        }

        strval = val.getPollProperties();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new PollProperties(strval));
        }

        final Map<String, Participant> voters = parseVpollVoters(val);

        for (final Participant v: voters.values()) {
          ((VPoll)comp).getVoters().add(v);
        }

        final Map<Integer, Component> comps = parseVpollCandidates(val);

        for (final Component candidate: comps.values()) {
          ((VPoll)comp).getCandidates().add(candidate);
        }
      }
       */

//      throw new RuntimeException("Not done");
//    } catch (final CalFacadeException cfe) {
//      throw cfe;
      resp.setEntity(jsval);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  //  throw new RuntimeException("Not done");
    return resp;
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private static String jscalDt(final BwDateTime bdt) {
    if (bdt.isUTC()) {
      return jsonDate(bdt.getDtval()
                         .substring(0,
                                    bdt.getDtval().length() - 1));
    }

    if (bdt.getDateType()) {
      return jsonDate(bdt.getDtval() + "T000000");
    }

    return jsonDate(bdt.getDtval());
  }

  private static String jscalTzid(final BwDateTime bdt,
                                  final boolean start,
                                  final EventInfo master) {
    if (bdt.isUTC()) {
      return "Etc/UTC";
    }

    if (bdt.getDateType()) {
      return null;
    }

    // Don't set tzid if override and same as master
    if (master != null) {
      final BwDateTime mbdt;

      if (start) {
        mbdt = master.getEvent().getDtstart();
      } else {
        mbdt = master.getEvent().getDtend();
      }

      if (mbdt != null) {
        if (Util.cmpObjval(bdt.getTzid(), mbdt.getTzid()) == 0) {
          return null;
        }
      }
    }

    return bdt.getTzid();
  }

  private static String findZone(final BwDateTime start,
                                 final BwDateTime end) {
    if (start != null) {
      return start.getTzid();
    }

    return end.getTzid();
  }

  private static void sameZone(final BwDateTime start,
                               final BwDateTime due) {
    if (start == null) {
      return;
    }

    if ((due.isUTC() != start.isUTC()) ||
            (due.getDateType() != start.getDateType()) ||
            (Util.cmpObjval(due.getTzid(), start.getTzid()) != 0)) {
      throw new RuntimeException("Start zone and due zone must be equal");
    }
  }

  /*
  private static void mergeXparams(final Property p,
                                   final Component c) {
    if (c == null) {
      return;
    }

    PropertyList pl = c.getProperties(p.getName());

    if (Util.isEmpty(pl)) {
      return;
    }

    String pval = p.getValue();

    if (p instanceof Attach) {
      // We just saved the hash for binary content
      Value v = (Value)p.getParameter(Parameter.VALUE);

      if (v != null) {
        pval = String.valueOf(pval.hashCode());
      }
    }

    Property from = null;

    if (pl.size() == 1) {
      from = pl.get(0);
    } else {
      // Look for value?
      for (final Object aPl : pl) {
        from = (Property)aPl;
        if (from.getValue().equals(pval)) {
          break;
        }
      }
    }

    if (from == null) {
      return;
    }

    ParameterList params = from.getParameters();

    final Iterator<Parameter> parit = params.iterator();
    while (parit.hasNext()) {
      Parameter param = parit.next();

      if (!(param instanceof XParameter)) {
        continue;
      }

      XParameter xpar = (XParameter)param;

      if (xpar.getName().toUpperCase().equals(BwXproperty.xparUid)) {
        continue;
      }

      p.getParameters().add(xpar);
    }
  }

  private static Property uidProp(final Property prop, final String uid) {
    Parameter par = new XParameter(BwXproperty.xparUid, uid);

    prop.getParameters().add(par);

    return prop;
  }

  private static Property langProp(final Property prop, final BwStringBase s) {
    Parameter par = s.getLangPar();

    if (par != null) {
      prop.getParameters().add(par);
    }

    return prop;
  }

   */

  /** Build recurring properties from event.
   *
   * @param val event
   * @param jsval JSCalendar object
   * @param tzreg for timezone lookups
   * @throws RuntimeException for bad date values
   */
  private static void doRecurring(final BwEvent val,
                                  final JSCalendarObject jsval,
                                  final TimeZoneRegistry tzreg) {
    if (val.hasRrules()) {
      for (final String rl: val.getRrules()) {
        makeRule(val, jsval, rl, tzreg);
      }
    }

    if (val.hasExrules()) {
      for(final String rl: val.getExrules()) {
        final var rrule = makeRule(val, jsval, rl, tzreg);
        rrule.addProperty(JSPropertyNames.excluded,
                          true);
      }
    }

    makeRexdates(val, false, val.getRdates(), jsval, tzreg);

    makeRexdates(val, true, val.getExdates(), jsval, tzreg);
  }

  private static JSRecurrenceRule makeRule(final BwEvent val,
                                           final JSCalendarObject jsval,
                                           final String iCalRule,
                                           final TimeZoneRegistry tzreg) {
    final RRule rule = new RRule();
    try {
      rule.setValue(iCalRule);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }

    final var rrules = jsval.getRecurrenceRules(true);

    final JSRecurrenceRule rrule = rrules.makeRecurrenceRule();

    final var recur = rule.getRecur();

    // FREQ - > frequency
    if (recur.getFrequency() != null) {
      rrule.setFrequency(recur.getFrequency().toLowerCase());
    }

    // INTERVAL -> interval
    if (recur.getInterval() != -1) {
      rrule.setInterval(
              new JSUnsignedIntegerImpl(recur.getInterval()));
    }

    // TODO Rscale
    // TODO Skip
    // TODO FirstDayOfWeek

    // BYDAY -> byday
    if (!Util.isEmpty(recur.getDayList())) {
      for (final var d: recur.getDayList()) {
        rrule.addByDayValue(d.getDay().name().toLowerCase(),
                            d.getOffset());
      }
    }

    // BYMONTHDAY -> byMonthDay
    if (!Util.isEmpty(recur.getMonthDayList())) {
      final var mdl = rrule.getByMonthDay(true);

      for (final var md: recur.getMonthDayList()) {
        mdl.add(md);
      }
    }

    // BYMONTH -> byMonth
    // This will need changing for rscale.
    if (!Util.isEmpty(recur.getMonthList())) {
      final var mdl = rrule.getByMonth(true);

      for (final var md: recur.getMonthList()) {
        mdl.add(String.valueOf(md));
      }
    }

    // BYYEARDAY -> byYearDay
    if (!Util.isEmpty(recur.getYearDayList())) {
      final var ydl = rrule.getByYearDay(true);

      for (final var yd: recur.getYearDayList()) {
        ydl.add(yd);
      }
    }

    // BYWEEKNO -> byWeekNo
    if (!Util.isEmpty(recur.getWeekNoList())) {
      final var wnl = rrule.getByWeekNo(true);

      for (final var wn: recur.getWeekNoList()) {
        wnl.add(wn);
      }
    }

    // BYHOUR -> byHour
    if (!Util.isEmpty(recur.getHourList())) {
      final var hl = rrule.getByHour(true);

      for (final var h: recur.getHourList()) {
        hl.add(new JSUnsignedIntegerImpl(h));
      }
    }

    // BYMINUTE -> byMinute
    if (!Util.isEmpty(recur.getMinuteList())) {
      final var ml = rrule.getByMinute(true);

      for (final var m: recur.getMinuteList()) {
        ml.add(new JSUnsignedIntegerImpl(m));
      }
    }

    // BYSECOND -> bySecond
    if (!Util.isEmpty(recur.getSecondList())) {
      final var sl = rrule.getBySecond(true);

      for (final var s: recur.getSecondList()) {
        sl.add(new JSUnsignedIntegerImpl(s));
      }
    }

    // BYSETPOS -> bySetPosition
    if (!Util.isEmpty(recur.getSetPosList())) {
      final var spl = rrule.getBySetPosition(true);

      for (final var s: recur.getSetPosList()) {
        spl.add(s);
      }
    }

    if (recur.getCount() > 0) {
      rrule.setCount(new JSUnsignedIntegerImpl(recur.getCount()));
    }

    if (recur.getUntil() != null) {
      final var until = new JSLocalDateTimeImpl(
              jsonDate(timeInZone(recur.getUntil().toString(),
                                  findZone(val.getDtstart(),
                                           val.getDtend()),
                                  tzreg)));
      rrule.setUntil(until);
    }

    return rrule;
  }

  private static void makeRexdates(final BwEvent val,
                                   final boolean exdt,
                                   final Collection<BwDateTime> dts,
                                   final JSCalendarObject jsval,
                                   final TimeZoneRegistry tzreg) {
    if ((dts == null) || (dts.isEmpty())) {
      return;
    }

    String tzid = null;
    if (!val.getForceUTC()) {
      final BwDateTime dtstart = val.getDtstart();

      if ((dtstart != null) && !dtstart.isUTC()) {
        final DtStart ds = dtstart.makeDtStart();
        tzid = ds.getTimeZone().getID();
      }
    }

    /* An rdate is generated as an empty override.
       An exdate is an override withe the property
       "excluded": true
     */

    final var overrides = jsval.getOverrides(true);

    for (final BwDateTime dt: dts) {
      final var rid = new JSLocalDateTimeImpl(
              jsonDate(timeInZone(dt.getDtval(), tzid, tzreg)));
      final var ov = overrides.makeEntry(rid);

      if (exdt) {
        ov.getValue().markExcluded();
      }
    }
  }

  private static final String dataUriPrefix =
          "data:text/plain;base64,";
  private static final int dataUriPrefixLen =
          dataUriPrefix.length();

  private static void makeAttachment(final JSLinks links,
                                     final BwAttachment att) {
    setLink(links.makeLink().getValue(), att);
  }

  private static void makeAttachmentOverride(final JSCalendarObject jsval,
                                             final BwAttachment att) {
    final JSProperty<JSLink> linkp =
            jsval.makeProperty(
                    new TypeReference<>() {},
                    makePath(JSPropertyNames.links,
                             UUID.randomUUID().toString()),
                    JSTypes.typeLink);
    setLink(linkp.getValue(), att);
  }

  private static void setLink(final JSLink link,
                              final BwAttachment att) {
    link.setRel("enclosure");

    final String temp = att.getFmtType();
    if (temp != null) {
      link.setContentType(temp);
    }

    if (att.getEncoding() == null) {
      // uri type
      link.setHref(att.getUri());
    } else {
      // Binary - make a data uri
      link.setHref(dataUriPrefix + att.getValue());
    }
  }

  public static boolean compareAttachment(
          final BwAttachment att,
          final JSProperty<JSLink> linkp) {
    final var link = linkp.getValue();

    if (!"enclosure".equals(link.getRel())) {
      return false;
    }

    if (Util.cmpObjval(att.getFmtType(),
                       link.getContentType()) != 0) {
      return false;
    }

    if (att.getEncoding() == null) {
      return Util.cmpObjval(att.getUri(),
                            link.getHref()) == 0;
    }

    if (att.getValue() == null) {
      return false;
    }

    return att.getValue().regionMatches(
            0, link.getHref(),
            dataUriPrefixLen,
            link.getHref().length());
  }

  private static void makeOrganizer(final JSCalendarObject jsval,
                                    final JSCalendarObject master,
                                    final JSParticipant jsOrg,
                                    final BwOrganizer org) {
    // We may already have a sendTo if this is also an attendee
    final var sendTos = jsOrg.getSendTo(true);
    if (sendTos.get("imip") == null) {
      sendTos.makeSendTo("imip",
                         org.getOrganizerUri());
    }

    jsOrg.getRoles(true).add("owner");

    String temp = org.getCn();
    if (temp != null) {
      jsOrg.setName(temp);
    }

    addLinkId(jsval, master, jsOrg, org.getDir());

    temp = org.getLanguage();
    if (temp != null) {
      jsOrg.setLanguage(temp);
    }

    temp = org.getScheduleStatus();
    if (temp != null) {
      logger.warn("Do this - scheduleStatus");
      //pars.add(new ScheduleStatus(temp));
    }

    temp = org.getSentBy();
    if (temp != null) {
      jsOrg.setInvitedBy(temp);
    }
  }

  private static void makeAttendees(final JSCalendarObject jsval,
                                    final JSCalendarObject master,
                                    final JSParticipants participants,
                                    final Set<BwAttendee> attendees) {
    /* Note below we add participants in two passes - groups first
       then the rest. This is because an attendee with a MEMBER
       parameter needs to refer to a group participant by the id
       which we don't know till we create the group.
     */
    for (final BwAttendee att: attendees) {
      if (!"group".equalsIgnoreCase(att.getCuType())) {
        continue;
      }
      makeAttendee(jsval, master, participants, att);
    }

    for (final BwAttendee att: attendees) {
      if ("group".equalsIgnoreCase(att.getCuType())) {
        continue;
      }
      makeAttendee(jsval, master, participants, att);
    }
  }

  /**
   *
   * @param participants to add to
   * @param master - needed to locate group participants
   * @param att attendee
   */
  private static void makeAttendee(final JSCalendarObject jsval,
                                   final JSCalendarObject master,
                                   final JSParticipants participants,
                                   final BwAttendee att) {
    final var part = participants.makeParticipant().getValue();

    // We do attendee before organizer so this should be fine.
    part.getSendTo(true).makeSendTo("imip",
                                    att.getAttendeeUri());

    final String partStat = att.getPartstat();

    if ((partStat != null) &&
            !partStat.equals(IcalDefs.partstatValNeedsAction)) {
      // Not default value.
      part.setParticipationStatus(partStat.toLowerCase());
    }

    if (att.getRsvp()) {
      part.setExpectReply(true);
    }

    String temp = att.getCn();
    if (temp != null) {
      part.setName(temp);
    }

    temp = jsCalCutype(att.getCuType());
    if (temp != null) {
      part.setKind(temp);
    }

    temp = att.getDelegatedFrom();
    if (temp != null) {
      // Could be a list of cal-address
      final var split = temp.split(",");
      for (final var s: split) {
        part.getDelegatedFrom(true).add(s);
      }
    }

    temp = att.getDelegatedTo();
    if (temp != null) {
      // Could be a list of cal-address
      final var split = temp.split(",");
      for (final var s: split) {
        part.getDelegatedTo(true).add(s);
      }
    }

    addLinkId(jsval, master, part, att.getDir());

    temp = att.getLanguage();
    if (temp != null) {
      part.setLanguage(temp);
    }

    temp = att.getMember();
    if (temp != null) {
      /* Locate the associated group participant which may be in this
         participants object or that of the master
       */
      var groupPart = participants.findParticipant(temp);
      if ((groupPart == null) && (master != null)) {
        final var mparts = master.getParticipants(false);
        if (mparts != null) {
          groupPart = mparts.findParticipant(temp);
        }
      }

      if (groupPart != null) {
        part.getMemberOf(true).add(groupPart.getName());
      }
    }

    jsCalRole(part.getRoles(true), att.getRole());

    temp = scheduleAgent(att.getScheduleAgent());
    if (temp != null) {
      part.setScheduleAgent(temp);
    }

    temp = att.getScheduleStatus();
    if (temp != null) {
      part.setScheduleStatus(temp);
    }

    temp = att.getSentBy();
    if (temp != null) {
      part.setInvitedBy(temp);
    }
  }

  private static void makeAttendeeOverride(
          final JSCalendarObject jsval,
          final JSCalendarObject master,
          final BwAttendee attendee,
          final BwAttendee masterAttendee) {
    /* Called because the attendee is present in the master but has
       changed in some way
     */
    final var jsMAtt = master.getParticipants(false).findParticipant(
            masterAttendee.getAttendeeUri());
    if (jsMAtt == null) {
      throw new RuntimeException("Missing master attendee " +
                                         masterAttendee.getAttendeeUri());
    }

    final var partId = jsMAtt.getName();

    // sendTo - may have been added or removed

    // partstat
    makeAttendeeOverrideVal(jsval,
                            lower(attendee.getPartstat()),
                            lower(masterAttendee.getPartstat()),
                            partId,
                            JSPropertyNames.participationStatus);

    // RSVP
    if (attendee.getRsvp() != masterAttendee.getRsvp()) {
      jsval.setProperty(makePath(JSPropertyNames.participants,
                                 partId,
                                 JSPropertyNames.expectReply),
                        attendee.getRsvp());
    }

    makeAttendeeOverrideVal(jsval,
                            attendee.getCn(),
                            masterAttendee.getCn(),
                            partId,
                            JSPropertyNames.name);

    makeAttendeeOverrideVal(jsval,
                            jsCalCutype(attendee.getCuType()),
                            jsCalCutype(masterAttendee.getCuType()),
                            partId,
                            JSPropertyNames.kind);

    /* TODO
    temp = att.getDelegatedFrom();
    if (temp != null) {
      // Could be a list of cal-address
      final var split = temp.split(",");
      for (final var s: split) {
        part.getDelegatedFrom(true).add(s);
      }
    }

    temp = att.getDelegatedTo();
    if (temp != null) {
      // Could be a list of cal-address
      final var split = temp.split(",");
      for (final var s: split) {
        part.getDelegatedTo(true).add(s);
      }
    }
    */

    final var dir = attendee.getDir();
    final var mdir = masterAttendee.getDir();
    if (dir == null) {
      if (mdir != null) {
        final var links = master.getLinks(false);
        if (links != null) {
          final var linkp = links.findLink(mdir);
          if (linkp != null) {
            jsval.setNull(JSPropertyNames.participants,
                          partId,
                          JSPropertyNames.linkIds,
                          linkp.getName());
          }
        }
      }
    } else if (!dir.equalsIgnoreCase(mdir)) {
      // First an override link
      final var linkp = jsval.getLinks(true).makeLink();
      final var link = linkp.getValue();
      link.setRel("alternate");
      link.setHref(dir);

      jsval.setProperty(makePath(JSPropertyNames.participants,
                                 partId,
                                 JSPropertyNames.linkIds),
                                 linkp.getName());
    }

    makeAttendeeOverrideVal(jsval,
                            attendee.getLanguage(),
                            masterAttendee.getLanguage(),
                            partId,
                            JSPropertyNames.language);

    /* TODO
    temp = att.getMember();
    if (temp != null) {
      /* Locate the associated group participant which may be in this
         participants object or that of the master
       * /
      var groupPart = participants.findParticipant(temp);
      if ((groupPart == null) && (master != null)) {
        final var mparts = master.getParticipants(false);
        if (mparts != null) {
          groupPart = mparts.findParticipant(temp);
        }
      }

      if (groupPart != null) {
        part.getMemberOf(true).add(groupPart.getName());
      }
    }
     */

    final String attVal = icalRole(attendee.getRole());
    final String mattVal = icalRole(masterAttendee.getRole());
    if (!attVal.equalsIgnoreCase(mattVal)) {
      final JSProperty<JSList<String>> roles =
              jsval.makeProperty(new TypeReference<>() {},
              makePath(JSPropertyNames.participants,
                       partId,
                       JSPropertyNames.roles),
                                 JSTypes.typeStrings);
      jsCalRole(roles.getValue(), attVal);
    }

    makeAttendeeOverrideVal(jsval,
                            scheduleAgent(attendee.getScheduleAgent()),
                            scheduleAgent(masterAttendee.getScheduleAgent()),
                            partId,
                            JSPropertyNames.scheduleAgent);

    makeAttendeeOverrideVal(jsval,
                            attendee.getScheduleStatus(),
                            masterAttendee.getScheduleStatus(),
                            partId,
                            JSPropertyNames.scheduleStatus);

    makeAttendeeOverrideVal(jsval,
                            attendee.getSentBy(),
                            masterAttendee.getSentBy(),
                            partId,
                            JSPropertyNames.kind);
  }

  private static String scheduleAgent(final int sagent) {
    if (sagent == IcalDefs.scheduleAgentServer) {
      return null;
    }

    return "client";
  }

  private static JSProperty<JSLink> findLink(final JSCalendarObject jsval,
                                             final JSCalendarObject master,
                                             final String href) {
    var links = jsval.getLinks(false);

    if (links != null) {
      final var prop = links.findLink(href);
      if (prop != null) {
        return prop;
      }
    }

    if (master == null) {
      return null;
    }

    links = jsval.getLinks(false);

    if (links == null) {
      return null;
    }

    return links.findLink(href);
  }

  private static void addLinkId(final JSCalendarObject jsval,
                                final JSCalendarObject master,
                                final JSParticipant part,
                                final String href) {
    if (href == null) {
      return;
    }

    var linkp = findLink(jsval, master, href);
    if (linkp == null) {
      final var links = jsval.getLinks(true);
      linkp = links.makeLink();
      final var link = linkp.getValue();

      link.setHref(href);
      link.setRel("alternate");
    }

    part.getLinkIds(true).add(linkp.getName());
  }
  private static String lower(final String val) {
    if (val == null) {
      return null;
    }

    return val.toLowerCase();
  }

  private static void makeAttendeeOverrideVal(
          final JSCalendarObject jsval,
          final String attVal,
          final String mattVal,
          final String partId,
          final String jstype) {
    if (attVal == null) {
      if (mattVal != null) {
        jsval.setNull(JSPropertyNames.participants,
                      partId,
                      jstype);
      }
    } else if (!attVal.equalsIgnoreCase(mattVal)) {
      jsval.setProperty(makePath(JSPropertyNames.participants,
                                 partId,
                                 jstype),
                        attVal);
    }
  }

  private static String jsCalCutype(final String icalCutype) {
    if (icalCutype == null) {
      return null;
    }

    if ("room".equalsIgnoreCase(icalCutype)) {
      return "location";
    }

    if ("unknown".equalsIgnoreCase(icalCutype)) {
      return null;
    }

    return icalCutype.toLowerCase();
  }

  private static String icalRole(final String role) {
    if (role == null) {
      return "REQ-PARTICIPANT";
    }

    return role;
  }

  private static void jsCalRole(final JSList<String> roles,
                                  final String icalRole) {
    if (icalRole == null) {
      roles.add("attendee");
      return;
    }

    /*
      "CHAIR" -> "chair"
      "REQ-PARTICIPANT" -> "attendee"
      "OPT-PARTICIPANT" -> "attendee", "optional"
      "NON-PARTICIPANT" -> "attendee", "informational"
     */

    if (icalRole.equalsIgnoreCase("chair")) {
      roles.add("attendee");
      roles.add("chair");
    } else if (icalRole.equalsIgnoreCase("REQ-PARTICIPANT")) {
      roles.add("attendee");
    } else if (icalRole.equalsIgnoreCase("OPT-PARTICIPANT")) {
      roles.add("attendee");
      roles.add("optional");
    } else if (icalRole.equalsIgnoreCase("NON-PARTICIPANT")) {
      roles.add("attendee");
      roles.add("informational");
    } else {
      roles.add(icalRole.toLowerCase());
    }
  }

  private static String makePath(final String... name) {
    if ((name == null) || (name.length == 0)) {
      throw new RuntimeException("Empty path");
    }
    final var pathsb = new StringBuilder();
    for (final var n : name) {
      if (pathsb.length() != 0) {
        pathsb.append("/");
      }
      pathsb.append(n);
    }

    return pathsb.toString();
  }

     /*
  private static Date makeZonedDt(final BwEvent val,
                                  final String dtval) throws Throwable {
    final BwDateTime dtstart = val.getDtstart();

    final DateTime dt = new DateTime(dtval);

    if (dtstart.getDateType()) {
      // RECUR - fix all day recurrences sometime
      if (dtval.length() > 8) {
        // Try to fix up bad all day recurrence ids. - assume a local timezone
        dt.setTimeZone(null);
        return new Date(dt.toString().substring(0, 8));
      }

      return dt;
    }

    if (val.getForceUTC()) {
      return dt;
    }

    if (!dtstart.isUTC()) {
      final DtStart ds = dtstart.makeDtStart();
      dt.setTimeZone(ds.getTimeZone());
    }

    return dt;
  }

  private String makeContactString(BwSponsor sp) {
    if (pars.simpleContact) {
      return sp.getName();
    }

    StringBuilder sb = new StringBuilder(sp.getName());
    addNonNull(defaultDelim, Resources.PHONENBR, sp.getPhone(), sb);
    addNonNull(defaultDelim, Resources.EMAIL, sp.getEmail(), sb);
    addNonNull(urlDelim, Resources.URL, sp.getLink(), sb);

    if (sb.length() == 0) {
      return null;
    }

    return sb.toString();
  }

  /* * Build a location string value from the location.
   *
   * <p>We try to build something we can parse later.
   * /
  private String makeLocationString(BwLocation loc) {
    if (pars.simpleLocation) {
      return loc.getAddress();
    }

    StringBuilder sb = new StringBuilder(loc.getAddress());
    addNonNull(defaultDelim, Resources.SUBADDRESS, loc.getSubaddress(), sb);
    addNonNull(urlDelim, Resources.URL, loc.getLink(), sb);

    if (sb.length() == 0) {
      return null;
    }

    return sb.toString();
  }
  */

  private static String jsonDate(final String val) {
    return XcalUtil.getXmlFormatDateTime(val);
  }

  /**
   * @param jscal current entity
   * @param master non-null if jscal is an override
   * @param name of xprop
   * @param pars List of Xpar
   * @param val new value
   */
  public static void addXproperty(final JSValue jscal,
                                  final EventInfo master,
                                  final String name,
                                  final List<BwXproperty.Xpar> pars,
                                  final String val) {
    if (val == null) {
      return;
    }
    throw new RuntimeException("Not done");
//    pl.add(new XProperty(name, makeXparlist(pars), val));
  }

  private static String timeInZone(final String dateTime,
                                   final String tzid,
                                   final TimeZoneRegistry tzreg) {
    try {
      final java.util.Date date;
      if (dateTime.endsWith("Z") || (tzid == null)) {
        date = DateTimeUtil.fromISODateTimeUTC(dateTime);
      } else {
        date = DateTimeUtil.fromISODateTime(dateTime,
                                            tzreg.getTimeZone(tzid));
      }

      return DateTimeUtil.isoDateTime(date);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }
}

