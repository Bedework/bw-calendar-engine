package org.bedework.caldav.bwserver;

import org.bedework.caldav.server.CalDAVCollection;
import org.bedework.caldav.server.CalDAVEvent;
import org.bedework.caldav.server.Organizer;
import org.bedework.caldav.server.SysiIcalendar;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.convert.Icalendar;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.webdav.servlet.shared.WdEntity;
import org.bedework.webdav.servlet.shared.WebdavException;
import org.bedework.webdav.servlet.shared.WebdavForbidden;

import net.fortuna.ical4j.model.TimeZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

class BwSysiIcalendar extends SysiIcalendar implements Logged {
  private final Icalendar ic;
  private final CalDAVCollection<?> col;
  private final BwSysIntfImpl sysi;

  private Iterator<EventInfo> icIterator;

  BwSysiIcalendar(final BwSysIntfImpl sysi,
                  final CalDAVCollection<?> col,
                  final Icalendar ic) {
    this.sysi = sysi;
    this.col = col;
    this.ic = ic;
  }

  @Override
  public String getProdid() {
    return ic.getProdid();
  }

  @Override
  public String getVersion() {
    return ic.getVersion();
  }

  @Override
  public String getCalscale() {
    return ic.getCalscale();
  }

  @Override
  public void assertNoMethod(final String operation) {
    if (getMethod() != null) {
      throw new WebdavForbidden(CaldavTags.validCalendarObjectResource,
                                "No method on " + operation);
    }

  }

  @Override
  public String getMethod() {
    return ic.getMethod();
  }

  @Override
  public Collection<TimeZone> getTimeZones() {
    final Collection<TimeZone> tzs = new ArrayList<>();

    for (final Icalendar.TimeZoneInfo tzi: ic.getTimeZones()) {
      tzs.add(tzi.tz);
    }

    return tzs;
  }

  @Override
  public Collection<?> getComponents() {
    return ic.getComponents();
  }

  @Override
  public IcalDefs.IcalComponentType getComponentType() {
    return ic.getComponentType();
  }

  @Override
  public int getMethodType() {
    return ic.getMethodType();
  }

  @Override
  public int getMethodType(final String val) {
    return Icalendar.getMethodType(val);
  }

  @Override
  public String getMethodName(final int mt) {
    return Icalendar.getMethodName(mt);
  }

  @Override
  public Organizer getOrganizer() {
    final var owner = ic.getOrganizer();

    if (owner.noOwner()) {
      return null;
    }

    return new Organizer(owner.getName(),
                         null, //owner.getDir(),
                         owner.getLanguage(),
                         owner.getInvitedBy(),
                         owner.getCalendarAddress());
  }

  @Override
  public CalDAVEvent<?> getEvent() {
    return (CalDAVEvent<?>)iterator().next();
  }

  @Override
  public CalDAVEvent<?> getOnlyEvent() {
    if (size() != 1) {
      throw new WebdavException("Expect only 1 event");
    }
    //if (getComponentType() != IcalDefs.IcalComponentType.event) {
    //  throw new WebdavException("org.bedework.icalendar.component.not.event");
    //}

    return (CalDAVEvent<?>)iterator().next();
  }

  @Override
  public Iterator<WdEntity<?>> iterator() {
    return this;
  }

  @Override
  public int size() {
    return ic.size();
  }

  @Override
  public boolean validItipMethodType() {
    return validItipMethodType(getMethodType());
  }

  @Override
  public boolean requestMethodType() {
    return itipRequestMethodType(getMethodType());
  }

  @Override
  public boolean replyMethodType() {
    return itipReplyMethodType(getMethodType());
  }

  @Override
  public boolean itipRequestMethodType(final int mt) {
    return Icalendar.itipRequestMethodType(mt);
  }

  @Override
  public boolean itipReplyMethodType(final int mt) {
    return Icalendar.itipReplyMethodType(mt);
  }

  @Override
  public boolean validItipMethodType(final int val) {
    return Icalendar.validItipMethodType(val);
  }

  /* ====================================================================
   *                        Iterator methods
   * ==================================================================== */

  @Override
  public boolean hasNext() {
    return getIcIterator().hasNext();
  }

  @Override
  public WdEntity<?> next() {
    final EventInfo ei = getIcIterator().next();

    if (ei == null) {
      return null;
    }

    final var ev = new BwCalDAVEvent(sysi, ei);
    if (col != null) {
      ev.setParentPath(col.getPath());
    }

    if (debug()) {
      debug("putContent: next() has event with uid %s " +
                    " and summary %s " +
                    " and new event %b ",
            ev.getUid(),
            ev.getSummary(),
            ev.isNew());
    }
    return ev;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private Iterator<EventInfo> getIcIterator() {
    if (icIterator == null) {
      icIterator = (Iterator<EventInfo>)ic.iterator();
    }

    return icIterator;
  }

  /* ==============================================================
   *                   Logged methods
   * ============================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}

