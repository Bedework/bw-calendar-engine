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

package org.bedework.caldav.bwserver;

import ietf.params.xml.ns.icalendar_2.ActionPropType;
import ietf.params.xml.ns.icalendar_2.AttendeePropType;
import ietf.params.xml.ns.icalendar_2.CalscalePropType;
import ietf.params.xml.ns.icalendar_2.CategoriesPropType;
import ietf.params.xml.ns.icalendar_2.ClassPropType;
import ietf.params.xml.ns.icalendar_2.CommentPropType;
import ietf.params.xml.ns.icalendar_2.CompletedPropType;
import ietf.params.xml.ns.icalendar_2.ContactPropType;
import ietf.params.xml.ns.icalendar_2.CreatedPropType;
import ietf.params.xml.ns.icalendar_2.DescriptionPropType;
import ietf.params.xml.ns.icalendar_2.DtendPropType;
import ietf.params.xml.ns.icalendar_2.DtstampPropType;
import ietf.params.xml.ns.icalendar_2.DtstartPropType;
import ietf.params.xml.ns.icalendar_2.DuePropType;
import ietf.params.xml.ns.icalendar_2.DurationPropType;
import ietf.params.xml.ns.icalendar_2.ExdatePropType;
import ietf.params.xml.ns.icalendar_2.GeoPropType;
import ietf.params.xml.ns.icalendar_2.LastModifiedPropType;
import ietf.params.xml.ns.icalendar_2.LocationPropType;
import ietf.params.xml.ns.icalendar_2.PercentCompletePropType;
import ietf.params.xml.ns.icalendar_2.PriorityPropType;
import ietf.params.xml.ns.icalendar_2.ProdidPropType;
import ietf.params.xml.ns.icalendar_2.RdatePropType;
import ietf.params.xml.ns.icalendar_2.RecurrenceIdPropType;
import ietf.params.xml.ns.icalendar_2.RelatedToPropType;
import ietf.params.xml.ns.icalendar_2.RepeatPropType;
import ietf.params.xml.ns.icalendar_2.ResourcesPropType;
import ietf.params.xml.ns.icalendar_2.StatusPropType;
import ietf.params.xml.ns.icalendar_2.SummaryPropType;
import ietf.params.xml.ns.icalendar_2.TranspPropType;
import ietf.params.xml.ns.icalendar_2.TriggerPropType;
import ietf.params.xml.ns.icalendar_2.UidPropType;
import ietf.params.xml.ns.icalendar_2.UrlPropType;
import ietf.params.xml.ns.icalendar_2.VersionPropType;

import java.util.HashMap;
import java.util.Map;

/** This allows us to register updaters for properties making it relatively
 * easy to add new updaters as properties appear.
 *
 * Standard updaters are registered statically. nonStandard updaters are
 * registered dynamically.
 *
 *
 * @author douglm
 */
public class PropertyUpdaterRegistry {
  private static class UpdaterEntry {
    String className;
    PropertyUpdater updater;

    UpdaterEntry(final String className) {
      this.className = className;
    }
  }

  private static Map<Class, UpdaterEntry> standardUpdaters =
      new HashMap<Class, UpdaterEntry>();

  private Map<Class, UpdaterEntry> nonStandardUpdaters;

  /** Register a non-standard updater. This can override standard updaters.
   *
   * @param cl - property class for which this is an updater
   * @param updCl - class name of updater
   */
  public void registerUpdater(final Class cl,
                              final String updCl) {
    if (nonStandardUpdaters != null) {
      nonStandardUpdaters = new HashMap<Class, UpdaterEntry>();
    }

    nonStandardUpdaters.put(cl, new UpdaterEntry(updCl));
  }

  static void registerStandardUpdater(final Class cl,
                                      final String updCl) {
    standardUpdaters.put(cl, new UpdaterEntry(updCl));
  }

  PropertyUpdater getUpdater(final Object o) {
    PropertyUpdater pu;
    Class cl = o.getClass();

    if (nonStandardUpdaters != null) {
      pu = findUpdater(cl, nonStandardUpdaters);

      if (pu != null) {
        return pu;
      }
    }

    return findUpdater(cl, standardUpdaters);
  }

  static PropertyUpdater findUpdater(final Class cl,
                                     final Map<Class, UpdaterEntry> updaters) {
    Class lcl = cl;

    while (lcl != null) {
      UpdaterEntry ue = updaters.get(lcl);

      if (ue != null) {
        if (ue.updater != null) {
          return ue.updater;
        }

        Object o;
        try {
          o = Class.forName(ue.className).newInstance();
        } catch (Throwable t) {
          throw new RuntimeException("PropertyUpdater class " +
                                     ue.className +
                                     " cannot be instantiated");
        }

        if (o == null) {
          throw new RuntimeException("PropertyUpdater class " +
                                     ue.className + " not found");
        }

        if (!(o instanceof PropertyUpdater)) {
          throw new RuntimeException("Class " + ue.className +
                                    " is not a subclass of " +
                                    PropertyUpdater.class.getName());
        }

        ue.updater = (PropertyUpdater)o;

        return ue.updater;
      }

      lcl = lcl.getSuperclass();
    }

    return null;
  }

  private static void standardPropUpdater(final Class cl,
                                          final String updCl) {
    registerStandardUpdater(cl,
                            "org.bedework.caldav.bwserver.stdupdaters." + updCl);
  }

  private static void immutableProp(final Class cl) {
    standardPropUpdater(cl, "ImmutablePropUpdater");
  }

  private static void ignoreProp(final Class cl) {
    standardPropUpdater(cl, "IgnorePropUpdater");
  }

  static {
    /* ======================================================================
     *          Register property updaters
     * ====================================================================== */

    standardPropUpdater(AttendeePropType.class, "AttendeePropUpdater");
    standardPropUpdater(CategoriesPropType.class, "CategoryPropUpdater");
    standardPropUpdater(ClassPropType.class, "ClassPropUpdater");
    standardPropUpdater(CommentPropType.class, "CommentPropUpdater");
    standardPropUpdater(CompletedPropType.class, "CompletedPropUpdater");
    standardPropUpdater(ContactPropType.class, "ContactPropUpdater");

    ignoreProp(CreatedPropType.class);

    standardPropUpdater(DescriptionPropType.class, "DescriptionPropUpdater");

    ignoreProp(DtstampPropType.class);

    standardPropUpdater(DtendPropType.class, "DtEndDuePropUpdater");
    standardPropUpdater(DtstartPropType.class, "DtStartPropUpdater");
    standardPropUpdater(DuePropType.class, "DtEndDuePropUpdater");
    standardPropUpdater(DurationPropType.class, "DurationPropUpdater");
    standardPropUpdater(ExdatePropType.class, "ExdatePropUpdater");
    standardPropUpdater(GeoPropType.class, "GeoPropUpdater");

    ignoreProp(LastModifiedPropType.class);

    standardPropUpdater(LocationPropType.class, "LocationPropUpdater");

    ignoreProp(ProdidPropType.class);

    standardPropUpdater(PercentCompletePropType.class, "PercentCompletePropUpdater");
    standardPropUpdater(PriorityPropType.class, "PriorityPropUpdater");
    standardPropUpdater(RdatePropType.class, "RdatePropUpdater");

    immutableProp(RecurrenceIdPropType.class);

    standardPropUpdater(RelatedToPropType.class, "RelatedToPropUpdater");
    standardPropUpdater(ResourcesPropType.class, "ResourcesPropUpdater");
    standardPropUpdater(StatusPropType.class, "StatusPropUpdater");
    standardPropUpdater(SummaryPropType.class, "SummaryPropUpdater");
    standardPropUpdater(TranspPropType.class, "TranspPropUpdater");

    immutableProp(UidPropType.class);
    standardPropUpdater(UrlPropType.class, "UrlPropUpdater");

    immutableProp(VersionPropType.class);

    /* ==== vcalendar properties ==== */
    immutableProp(CalscalePropType.class);

    /* ==== valarm only properties ==== */
    immutableProp(ActionPropType.class);
    standardPropUpdater(RepeatPropType.class, "RepeatPropUpdater");
    standardPropUpdater(TriggerPropType.class, "TriggerPropUpdater");

    /* All classes
    standardPropUpdater(AttachPropType.class, "AttachPropUpdater");
    standardPropUpdater(ExrulePropType.class, "ExrulePropUpdater");
    standardPropUpdater(LinkPropType.class, "LinkPropUpdater");
    standardPropUpdater(OrganizerPropType.class, "OrganizerPropUpdater");
    standardPropUpdater(RequestStatusPropType.class, "RequestStatusPropUpdater");
    standardPropUpdater(RrulePropType.class, "RrulePropUpdater");
    standardPropUpdater(SequencePropType.class, "SequencePropUpdater");
    standardPropUpdater(TolerancePropType.class, "TolerancePropUpdater");
    standardPropUpdater(XBedeworkCostPropType.class, "XBedeworkCostPropUpdater");
    standardPropUpdater(XBedeworkExsynchEndtzidPropType.class, "XBedeworkExsynchEndtzidPropUpdater");
    standardPropUpdater(XBedeworkExsynchLastmodPropType.class, "XBedeworkExsynchLastmodPropUpdater");
    standardPropUpdater(XBedeworkExsynchStarttzidPropType.class, "XBedeworkExsynchStarttzidPropUpdater");
    standardPropUpdater(XMicrosoftCdoBusystatusPropType.class, "XMicrosoftCdoBusystatusPropUpdater");
    standardPropUpdater(XMicrosoftCdoIntendedstatusPropType.class, "XMicrosoftCdoIntendedstatusPropUpdater");

    ==== vcalendar properties ====
    standardPropUpdater(MethodPropType.class, "MethodPropUpdater");

    ==== freebusy properties ====
    standardPropUpdater(BusytypePropType.class, "BusytypePropUpdater");
    standardPropUpdater(FreebusyPropType.class, "FreebusyPropUpdater");

    ==== vtimezone properties ====
    standardPropUpdater(TzidPropType.class, "TzidPropUpdater");
    standardPropUpdater(TznamePropType.class, "TznamePropUpdater");
    standardPropUpdater(TzoffsetfromPropType.class, "TzoffsetfromPropUpdater");
    standardPropUpdater(TzoffsettoPropType.class, "TzoffsettoPropUpdater");
    standardPropUpdater(TzurlPropType.class, "TzurlPropUpdater");
     */
  }

}
