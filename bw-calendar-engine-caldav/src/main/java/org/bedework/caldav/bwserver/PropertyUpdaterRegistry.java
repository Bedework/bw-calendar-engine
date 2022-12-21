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

import org.bedework.caldav.bwserver.stdupdaters.AttendeePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.CategoryPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.ClassPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.CommentPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.CompletedPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.ContactPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.DescriptionPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.DtEndDuePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.DtStartPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.DurationPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.ExdatePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.GeoPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.IgnorePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.ImmutablePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.LocationPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.PercentCompletePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.PriorityPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.RdatePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.RelatedToPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.RepeatPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.ResourcesPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.SequencePropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.StatusPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.SummaryPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.TranspPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.TriggerPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.UrlPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.XbwCategoryPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.XbwContactPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.XbwLocPropUpdater;
import org.bedework.caldav.bwserver.stdupdaters.XbwWrapperPropUpdater;

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
import ietf.params.xml.ns.icalendar_2.SequencePropType;
import ietf.params.xml.ns.icalendar_2.StatusPropType;
import ietf.params.xml.ns.icalendar_2.SummaryPropType;
import ietf.params.xml.ns.icalendar_2.TranspPropType;
import ietf.params.xml.ns.icalendar_2.TriggerPropType;
import ietf.params.xml.ns.icalendar_2.UidPropType;
import ietf.params.xml.ns.icalendar_2.UrlPropType;
import ietf.params.xml.ns.icalendar_2.VersionPropType;
import ietf.params.xml.ns.icalendar_2.XBedeworkWrapperPropType;
import ietf.params.xml.ns.icalendar_2.XBwCategoriesPropType;
import ietf.params.xml.ns.icalendar_2.XBwContactPropType;
import ietf.params.xml.ns.icalendar_2.XBwLocationPropType;

import java.util.HashMap;
import java.util.Map;

/** This allows us to register updaters for properties making it relatively
 * easy to add new updaters as properties appear.
 *
 * <p>Standard updaters are registered statically. nonStandard updaters are
 * registered dynamically.
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

  private static final Map<Class<?>, UpdaterEntry> standardUpdaters =
      new HashMap<>();

  private Map<Class<?>, UpdaterEntry> nonStandardUpdaters;

  /** Register a non-standard updater. This can override standard updaters.
   *
   * @param cl - property class for which this is an updater
   * @param updCl - class name of updater
   */
  public void registerUpdater(final Class<?> cl,
                              final String updCl) {
    if (nonStandardUpdaters == null) {
      nonStandardUpdaters = new HashMap<>();
    }

    nonStandardUpdaters.put(cl, new UpdaterEntry(updCl));
  }

  static void registerStandardUpdater(final Class<?> cl,
                                      final String updCl) {
    standardUpdaters.put(cl, new UpdaterEntry(updCl));
  }

  PropertyUpdater getUpdater(final Object o) {
    final PropertyUpdater pu;
    final Class<?> cl = o.getClass();

    if (nonStandardUpdaters != null) {
      pu = findUpdater(cl, nonStandardUpdaters);

      if (pu != null) {
        return pu;
      }
    }

    return findUpdater(cl, standardUpdaters);
  }

  static PropertyUpdater findUpdater(final Class<?> cl,
                                     final Map<Class<?>, UpdaterEntry> updaters) {
    Class<?> lcl = cl;

    while (lcl != null) {
      final UpdaterEntry ue = updaters.get(lcl);

      if (ue != null) {
        if (ue.updater != null) {
          return ue.updater;
        }

        final Object o;
        try {
          o = Class.forName(ue.className).newInstance();
        } catch (final Throwable t) {
          throw new RuntimeException("PropertyUpdater class " +
                                     ue.className +
                                     " cannot be instantiated");
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

  private static void standardPropUpdater(final Class<?> cl,
                                          final Class<?> updCl) {
    registerStandardUpdater(cl, updCl.getName());
  }

  private static void immutableProp(final Class<?> cl) {
    standardPropUpdater(cl, ImmutablePropUpdater.class);
  }

  private static void ignoreProp(final Class<?> cl) {
    standardPropUpdater(cl, IgnorePropUpdater.class);
  }

  static {
    /* ======================================================================
     *          Register property updaters
     * ====================================================================== */

    standardPropUpdater(AttendeePropType.class, AttendeePropUpdater.class);
    standardPropUpdater(CategoriesPropType.class, CategoryPropUpdater.class);
    standardPropUpdater(ClassPropType.class, ClassPropUpdater.class);
    standardPropUpdater(CommentPropType.class, CommentPropUpdater.class);
    standardPropUpdater(CompletedPropType.class, CompletedPropUpdater.class);
    standardPropUpdater(ContactPropType.class, ContactPropUpdater.class);

    ignoreProp(CreatedPropType.class);
    standardPropUpdater(SequencePropType.class, SequencePropUpdater.class);

    standardPropUpdater(DescriptionPropType.class, DescriptionPropUpdater.class);

    ignoreProp(DtstampPropType.class);

    standardPropUpdater(DtendPropType.class, DtEndDuePropUpdater.class);
    standardPropUpdater(DtstartPropType.class, DtStartPropUpdater.class);
    standardPropUpdater(DuePropType.class, DtEndDuePropUpdater.class);
    standardPropUpdater(DurationPropType.class, DurationPropUpdater.class);
    standardPropUpdater(ExdatePropType.class, ExdatePropUpdater.class);
    standardPropUpdater(GeoPropType.class, GeoPropUpdater.class);

    ignoreProp(LastModifiedPropType.class);

    standardPropUpdater(LocationPropType.class, LocationPropUpdater.class);

    ignoreProp(ProdidPropType.class);

    standardPropUpdater(PercentCompletePropType.class, PercentCompletePropUpdater.class);
    standardPropUpdater(PriorityPropType.class, PriorityPropUpdater.class);
    standardPropUpdater(RdatePropType.class, RdatePropUpdater.class);

    immutableProp(RecurrenceIdPropType.class);

    standardPropUpdater(RelatedToPropType.class, RelatedToPropUpdater.class);
    standardPropUpdater(ResourcesPropType.class, ResourcesPropUpdater.class);
    standardPropUpdater(StatusPropType.class, StatusPropUpdater.class);
    standardPropUpdater(SummaryPropType.class, SummaryPropUpdater.class);
    standardPropUpdater(TranspPropType.class, TranspPropUpdater.class);

    immutableProp(UidPropType.class);
    standardPropUpdater(UrlPropType.class, UrlPropUpdater.class);

    immutableProp(VersionPropType.class);

    /* ==== vcalendar properties ==== */
    immutableProp(CalscalePropType.class);

    /* ==== valarm only properties ==== */
    immutableProp(ActionPropType.class);
    standardPropUpdater(RepeatPropType.class, RepeatPropUpdater.class);
    standardPropUpdater(TriggerPropType.class, TriggerPropUpdater.class);

    /* ==== public event synch x-properties ==== */
    standardPropUpdater(XBwCategoriesPropType.class, XbwCategoryPropUpdater.class);
    standardPropUpdater(XBwContactPropType.class, XbwContactPropUpdater.class);
    standardPropUpdater(XBwLocationPropType.class, XbwLocPropUpdater.class);

    standardPropUpdater(XBedeworkWrapperPropType.class,
                        XbwWrapperPropUpdater.class);

    /* All classes
    standardPropUpdater(AttachPropType.class, AttachPropUpdater.class);
    standardPropUpdater(ExrulePropType.class, ExrulePropUpdater.class);
    standardPropUpdater(LinkPropType.class, LinkPropUpdater.class);
    standardPropUpdater(OrganizerPropType.class, OrganizerPropUpdater.class);
    standardPropUpdater(RequestStatusPropType.class, RequestStatusPropUpdater.class);
    standardPropUpdater(RrulePropType.class, RrulePropUpdater.class);
    standardPropUpdater(TolerancePropType.class, TolerancePropUpdater.class);
    standardPropUpdater(XBedeworkCostPropType.class, XBedeworkCostPropUpdater.class);
    standardPropUpdater(XBedeworkExsynchEndtzidPropType.class, XBedeworkExsynchEndtzidPropUpdater.class);
    standardPropUpdater(XBedeworkExsynchLastmodPropType.class, XBedeworkExsynchLastmodPropUpdater.class);
    standardPropUpdater(XBedeworkExsynchStarttzidPropType.class, XBedeworkExsynchStarttzidPropUpdater.class);
    standardPropUpdater(XMicrosoftCdoBusystatusPropType.class, XMicrosoftCdoBusystatusPropUpdater.class);
    standardPropUpdater(XMicrosoftCdoIntendedstatusPropType.class, XMicrosoftCdoIntendedstatusPropUpdater.class);

    ==== vcalendar properties ====
    standardPropUpdater(MethodPropType.class, MethodPropUpdater.class);

    ==== freebusy properties ====
    standardPropUpdater(BusytypePropType.class, BusytypePropUpdater.class);
    standardPropUpdater(FreebusyPropType.class, FreebusyPropUpdater.class);

    ==== vtimezone properties ====
    standardPropUpdater(TzidPropType.class, TzidPropUpdater.class);
    standardPropUpdater(TznamePropType.class, TznamePropUpdater.class);
    standardPropUpdater(TzoffsetfromPropType.class, TzoffsetfromPropUpdater.class);
    standardPropUpdater(TzoffsettoPropType.class, TzoffsettoPropUpdater.class);
    standardPropUpdater(TzurlPropType.class, TzurlPropUpdater.class);
     */
  }

}
