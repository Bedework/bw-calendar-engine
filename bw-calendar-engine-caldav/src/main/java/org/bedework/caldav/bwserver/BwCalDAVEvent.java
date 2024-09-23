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

import org.bedework.access.AccessPrincipal;
import org.bedework.caldav.server.CalDAVEvent;
import org.bedework.caldav.server.Organizer;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.convert.IcalTranslator;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.tagdefs.AppleServerTags;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.ICalTags;
import org.bedework.util.xml.tagdefs.WebdavTags;
import org.bedework.webdav.servlet.shared.WebdavException;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;

/**
 *
 * @author douglm
 *
 */
public class BwCalDAVEvent extends CalDAVEvent<BwCalDAVEvent> {
  private final BwSysIntfImpl intf;

  private EventInfo evi;
  private BwEvent ev;

  /**
   * @param intf system interface
   * @param evi event info
   */
  BwCalDAVEvent(final BwSysIntfImpl intf, final EventInfo evi) {
    this.intf = intf;
    this.evi = evi;

    if (evi != null) {
      ev = evi.getEvent();
    }
  }

  /* ====================================================================
   *                      Abstract method implementations
   * ==================================================================== */

  @Override
  public boolean getCanShare() {
    return false;
  }

  @Override
  public boolean getCanPublish() {
    return false;
  }

  @Override
  public boolean isAlias() {
    return false;
  }

  @Override
  public String getAliasUri() {
    return null;
  }

  @Override
  public BwCalDAVEvent resolveAlias(final boolean resolveSubAlias) {
    return this;
  }

  @Override
  public void setProperty(final QName name, final String val) {
  }

  @Override
  public String getProperty(final QName name) {
    return null;
  }

  @Override
  public boolean getDeleted() {
    return getEv().getDeleted() || getEv().getTombstoned();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVEvent#getScheduleTag()
   */
  @Override
  public String getScheduleTag() {
    return getEv().getStag();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVEvent#getPrevScheduleTag()
   */
  @Override
  public String getPrevScheduleTag() {
    return getEvinfo().getPrevStag();
  }

  @Override
  public boolean getOrganizerSchedulingObject() {
    return getEv().getOrganizerSchedulingObject();
  }

  @Override
  public boolean getAttendeeSchedulingObject() {
    return getEv().getAttendeeSchedulingObject();
  }

  @Override
  public String getSummary() {
    return getEv().getSummary();
  }

  @Override
  public boolean isNew() {
    return getEvinfo().getNewEvent();
  }

  @Override
  public int getEntityType() {
    return getEv().getEntityType();
  }

  @Override
  public void setOrganizer(final Organizer val) {
    final BwOrganizer org = new BwOrganizer();

    org.setCn(val.getCn());
    org.setDir(val.getDir());
    org.setLanguage(val.getLanguage());
    org.setSentBy(val.getSentBy());
    org.setOrganizerUri(val.getOrganizerUri());

    getEv().setOrganizer(org);
  }

  @Override
  public Organizer getOrganizer() {
    final BwOrganizer bworg = getEv().getOrganizer();
    return new Organizer(bworg.getCn(),
                         bworg.getDir(),
                         bworg.getLanguage(),
                         bworg.getSentBy(),
                         bworg.getOrganizerUri());
  }

  @Override
  public void setOriginator(final String val) {
    getEv().setOriginator(val);
  }

  @Override
  public void setRecipients(final Set<String> val) {
    if (getEv().getRecipients() == null) {
      getEv().setRecipients(val);
    } else {
      Util.adjustCollection(val, getEv().getRecipients());
    }
  }

  @Override
  public Set<String> getRecipients() {
    return getEv().getRecipients();
  }

  @Override
  public void addRecipient(final String val) {
    getEv().addRecipient(val);
  }

  @Override
  public Set<String> getAttendeeUris() {
    final Set<String> uris = new TreeSet<>();

    for (final BwAttendee att: getEv().getAttendees()) {
      uris.add(att.getAttendeeUri());
    }

    return uris;
  }

  @Override
  public void setScheduleMethod(final int val) {
    getEv().setScheduleMethod(val);
  }

  @Override
  public int getScheduleMethod() {
    return getEv().getScheduleMethod();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVEvent#getUid()
   */
  @Override
  public String getUid() {
    return getEv().getUid();
  }

  @Override
  public boolean generatePropertyValue(final QName tag,
                                       final XmlEmit xml) {
    final BwEvent ev = getEv();

    /*
      if (tag.equals(CaldavTags.scheduleState)) {
        xml.openTag(tag);
        if (ev.getScheduleState() == BwEvent.scheduleStateNotProcessed) {
          xml.emptyTag(CaldavTags.notProcessed);
        } else {
          xml.emptyTag(CaldavTags.processed);
        }
        xml.closeTag(tag);
        return true;
      }
      */

    if (tag.equals(CaldavTags.scheduleTag)) {
      if (!ev.getOrganizerSchedulingObject() &&
              !ev.getAttendeeSchedulingObject()) {
        return false;
      }

      xml.property(tag, ev.getStag());

      return true;
    }

    if (tag.equals(CaldavTags.originator)) {
      if (ev.getOriginator() != null) {
        xml.openTag(tag);
        xml.property(WebdavTags.href, ev.getOriginator());
        xml.closeTag(tag);
      }
      return true;
    }

    if (tag.equals(CaldavTags.recipient)) {
      final Collection<String> r = ev.getRecipients();
      if ((r == null) || (r.isEmpty())) {
        return true;
      }

      xml.openTag(tag);
      for (final String recip: r) {
        xml.property(WebdavTags.href, recip);
      }
      xml.closeTag(tag);
      return true;
    }

    if (tag.equals(AppleServerTags.scheduleChanges)) {
      final List<BwXproperty> xps =
              ev.getXproperties(BwXproperty.bedeworkChanges);
      if (Util.isEmpty(xps)) {
        return true;
      }

      final BwXproperty xp = xps.get(0);

      final String[] vals = xp.getValue().split(";");

      xml.openTag(tag);

      xml.property(AppleServerTags.dtstamp, vals[0]);
      xml.openTag(AppleServerTags.action);

      final QName actionTag;

      if ("CANCEL".equals(vals[1])) {
        actionTag = AppleServerTags.cancel;
      } else if ("CREATE".equals(vals[1])) {
        actionTag = AppleServerTags.create;
      } else if ("REPLY".equals(vals[1])) {
        actionTag = AppleServerTags.reply;
      } else { // "UPDATE"
        actionTag = AppleServerTags.update;
      }

      xml.openTag(actionTag);

      int i = 2;
      while (i < vals.length) {
        /* Next is master or rid */

        xml.openTag(AppleServerTags.recurrence);

        String val = vals[i];

        if ("MASTER".equals(val)) {
          xml.emptyTag(AppleServerTags.master);
          i++;
        }

        if (val.startsWith("RID=")) {
          xml.openTagNoNewline(AppleServerTags.recurrenceid);
          xml.value(val);
          xml.closeTag(AppleServerTags.recurrenceid);
          i++;
        }

        if ((i < vals.length) && "CHANGES".equals(vals[i])) {
          i++;
          xml.openTag(AppleServerTags.changes);

          while (i < vals.length) {
            val = vals[i];

            if ("MASTER".equals(val) ||
                    "CHANGES".equals(val) ||
                    val.startsWith("RID=")) {
              break;
            }

            xml.startTag(AppleServerTags.changedProperty);
            xml.attribute("name", val);
            xml.endEmptyTag();

            i++;
          }

          xml.closeTag(AppleServerTags.changes);
        }

        xml.closeTag(AppleServerTags.recurrence);
      }


      xml.closeTag(actionTag);
      xml.closeTag(AppleServerTags.action);
      xml.closeTag(tag);

      return true;
    }

    /* =============== ICalTags follow ================= */

    if (tag.equals(ICalTags.action)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.attach)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.attendee)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.categories)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags._class)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.comment)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.completed)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.contact)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.created)) {
      xml.property(tag, ev.getCreated());
      return true;
    }

    if (tag.equals(ICalTags.description)) {
      if (ev.getDescription() != null) {
        xml.property(tag, ev.getDescription());
      }
      return true;
    }

    if (tag.equals(ICalTags.dtend)) {
      xml.property(tag, ev.getDtend().getDate());
      return true;
    }

    if (tag.equals(ICalTags.dtstamp)) {
      xml.property(tag, ev.getDtstamp());
      return true;
    }

    if (tag.equals(ICalTags.dtstart)) {
      xml.property(tag, ev.getDtstart().getDate());
      return true;
    }

    /* TODO
     if (tag.equals(ICalTags.due)) {
     pv.val = ev.
     return pv;
     }
       */

    if (tag.equals(ICalTags.duration)) {
      xml.property(tag, ev.getDuration());
      return true;
    }

    if (tag.equals(ICalTags.exdate)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.exrule)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.freebusy)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.geo)) {
      // PROPTODO
      return true;
    }

    /*
     if (tag.equals(ICalTags.hasRecurrence)) {
     pv.val = ev
     return pv;
     }

     if (tag.equals(ICalTags.hasAlarm)) {
     pv.val = ev
     return pv;
     }

     if (tag.equals(ICalTags.hasAttachment)) {
     pv.val = ev
     return pv;
     }*/

    if (tag.equals(ICalTags.lastModified)) {
      xml.property(tag, ev.getLastmod());
      return true;
    }

    if (tag.equals(ICalTags.location)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.organizer)) {
      final var owner = ev.getParticipants()
                          .getSchedulingOwner()
                          .getCalendarAddress();
      if (owner != null) {
        xml.property(tag, owner);
      }
      return true;
    }

    if (tag.equals(ICalTags.percentComplete)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.priority)) {
      final Integer val = ev.getPriority();
      if ((val != null) && (val != 0)) {
        xml.property(tag, String.valueOf(val));
      }

      return true;
    }

    if (tag.equals(ICalTags.rdate)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.recurrenceId)) {
      if (ev.getRecurrenceId() != null) {
        xml.property(tag, ev.getRecurrenceId());
      }
      return true;
    }

    if (tag.equals(ICalTags.relatedTo)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.repeat)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.resources)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.requestStatus)) {
      // PROPTODO
      /*
      if (ev.getRequestStatus() != null) {
        xml.property(tag, ev.getRequestStatus().strVal());
      }
         */
      return true;
    }

    if (tag.equals(ICalTags.rrule)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.sequence)) {
      xml.property(tag, String.valueOf(ev.getSequence()));

      return true;
    }

    if (tag.equals(ICalTags.status)) {
      xml.property(tag, ev.getStatus());
      return true;
    }

    if (tag.equals(ICalTags.summary)) {
      xml.property(tag, ev.getSummary());
      return true;
    }

    if (tag.equals(ICalTags.transp)) {
      xml.property(tag, ev.getPeruserTransparency(intf.getPrincipal().getPrincipalRef()));
      return true;
    }

    if (tag.equals(ICalTags.trigger)) {
      // PROPTODO
      return true;
    }

    if (tag.equals(ICalTags.uid)) {
      xml.property(tag, ev.getUid());
      return true;
    }

    if (tag.equals(ICalTags.url)) {
      if (ev.getLink() != null) {
        xml.property(tag, ev.getLink());
      }
      return true;
    }

    if (tag.equals(ICalTags.version)) {
      // PROPTODO
      return true;
    }

    return false;
  }

  @Override
  public String toIcalString(final int methodType,
                             final String contentType) {
    try {
      if (contentType.equals("text/calendar")) {
        return IcalTranslator.freebusyToIcalString(methodType, getEv());
      }

      if (contentType.equals("application/calendar+json")) {
        return intf.toJcal(this,
                           methodType != ScheduleMethods.methodTypeNone);
      }

      throw new RuntimeException("Unhandled content type" + contentType);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ====================================================================
   *                      Overrides
   * ==================================================================== */

  @Override
  public void setName(final String val) {
    getEv().setName(val);
  }

  @Override
  public String getName() {
    return getEv().getName();
  }

  @Override
  public void setDisplayName(final String val) {
    // No display name
  }

  @Override
  public String getDisplayName() {
    return getEv().getSummary();
  }

  @Override
  public void setPath(final String val) {
    // Not actually saved
  }

  @Override
  public String getPath() {
    return getEv().getColPath() + "/" + getEv().getName();
  }

  @Override
  public void setParentPath(final String val) {
    getEv().setColPath(val);
  }

  @Override
  public String getParentPath() {
    return getEv().getColPath();
  }

  @Override
  public void setOwner(final AccessPrincipal val) {
    getEv().setOwnerHref(val.getPrincipalRef());
  }

  @Override
  public AccessPrincipal getOwner() {
    try {
      return intf.getPrincipal(getEv().getOwnerHref());
    } catch (final WebdavException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setCreated(final String val) {
    getEv().setCreated(val);
  }

  @Override
  public String getCreated() {
    return getEv().getCreated();
  }

  @Override
  public void setLastmod(final String val) {
    getEv().setLastmod(val);
  }

  @Override
  public String getLastmod() {
    return getEv().getLastmod();
  }

  @Override
  public String getEtag() {
    return "\"" + getEv().getCtoken() +  "\"";
//    return "\"" + getEv().getLastmod() + "-" +
  //         "0" + //  < Should be a sequence - needs to be in event and saved
    //       "\"";
  }

  @Override
  public String getPreviousEtag() {
    return "\"" + getEvinfo().getPrevCtoken() + "\"";
  }

  @Override
  public void setDescription(final String val) {
    getEv().setDescription(val);
  }

  @Override
  public String getDescription() {
    return getEv().getDescription();
  }

  /* ====================================================================
   *                      Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("BwCalDAVEvent{");

    try {
      sb.append(getEv().toString());
    } catch (final Throwable t) {
      sb.append(t);
    }

    sb.append("}");

    return sb.toString();
  }
  /* ====================================================================
   *                      Private methods
   * ==================================================================== */

  EventInfo getEvinfo() {
    if (evi == null) {
      evi = new EventInfo(new BwEventObj());
      ev = evi.getEvent();
    }

    return evi;
  }

  BwEvent getEv() {
    getEvinfo();

    return ev;
  }
}
