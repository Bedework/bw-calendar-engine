/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.common;

import org.bedework.calcorei.FiltersCommonI;
import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.EntityTimeRangeFilter;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.PropertyFilter;
import org.bedework.caldav.util.filter.TimeRangeFilter;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.BwCategoryFilter;
import org.bedework.calfacade.filter.BwObjectFilter;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.logging.Logged;
import org.bedework.webdav.servlet.shared.WebdavException;

import java.util.ArrayList;
import java.util.List;

/**
 * User: mike Date: 6/18/18 Time: 23:57
 */
public class FiltersCommon implements Logged, FiltersCommonI {
  /* The complete filter with some filter elements replaced by bedework specific
   * classes
   */
  protected FilterBase fullFilter;

  /* A copy of the filter modified for hsql generation for overrides (and annotations
   * These can only include date and collection path terms.
   *
   * If null the unmodified filter is fine and no post-processing is needed
   */
  protected FilterBase overrideFilter;

  /* Set true during reconstruction of the filter for overrides if any terms
   * are dropped
   */
  protected boolean termsDropped;

  public FiltersCommon(final FilterBase filter) throws CalFacadeException {
    /* Reconstruct the filter and create a date only filter for overrides. */

    fullFilter = reconstruct(filter, false);
    overrideFilter = reconstruct(filter, true);
  }

  @Override
  public boolean postFilter(final BwEvent ev,
                            final String userHref) throws CalFacadeException {
    return match(fullFilter, ev, userHref);
  }

  /* Return null if there are no terms to test for - otherwise return the
   * modified filter.
   *
   * Mostly we replace ObjectFilter with its wrapped form.
   */
  private FilterBase reconstruct(final FilterBase f,
                                 final boolean forOverrides) throws CalFacadeException {
    if (f == null) {
      return null;
    }

    if ((f instanceof AndFilter) || (f instanceof OrFilter)) {
      boolean itsAnd = (f instanceof AndFilter);

      List<FilterBase> fs = new ArrayList<FilterBase>();

      for (FilterBase flt: f.getChildren()) {
        FilterBase chf = reconstruct(flt, forOverrides);

        if (chf != null) {
          fs.add(chf);
        }
      }

      if (fs.size() == 0) {
        return null;
      }

      if (fs.size() == 1) {
        return fs.get(0);
      }

      FilterBase res;

      if (itsAnd) {
        res = new AndFilter();
      } else {
        res = new OrFilter();
      }

      for (FilterBase flt: fs) {
        res.addChild(flt);
      }

      return res;
    }

    /* Not AND/OR */

    if (!(f instanceof PropertyFilter)) {
      throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }

    PropertyFilter pf = (PropertyFilter)f;
    if (pf.getPropertyIndex() == PropertyInfoIndex.HREF) {
      // Special case this
      return f;
    }

    if (BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex()) == null) {
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    if (f instanceof PresenceFilter) {
      if (!forOverrides) {
        return f;
      }

      if ((pf.getPropertyIndex() == PropertyInfoIndex.COLLECTION) ||
              (pf.getPropertyIndex() == PropertyInfoIndex.DTEND) ||
              (pf.getPropertyIndex() == PropertyInfoIndex.DTSTART) ||
              (pf.getPropertyIndex() == PropertyInfoIndex.DURATION)) {
        return f;
      }

      termsDropped = true;
      return null;
    } else if (pf instanceof EntityTimeRangeFilter) {
      return f;
    } else if (pf instanceof TimeRangeFilter) {
      if (!forOverrides) {
        return f;
      }

      termsDropped = true;
      return null;
    } else if (pf instanceof BwCategoryFilter) {
      if (!forOverrides) {
        return f;
      }

      termsDropped = true;
      return null;
    } else if (pf instanceof ObjectFilter) {
      if (!forOverrides) {
        if (pf instanceof EntityTypeFilter) {
          EntityTypeFilter etf = (EntityTypeFilter)pf;

          if ((etf.getEntity() == IcalDefs.entityTypeVavailability) ||
                  (etf.getEntity() == IcalDefs.entityTypeVavailability)) {
            // Ensure we get or exclude both
            boolean not = etf.getNot();
            FilterBase both;

            if (not) {
              both = new AndFilter();
            } else {
              both = new OrFilter();
            }

            try {
              both.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                                  "vavailability",
                                                                  false));
              both.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                                  "available",
                                                                  false));
            } catch (Throwable t) {
              throw new CalFacadeException(t);
            }

            return both;
          }
        }
        return new BwObjectFilter(null, (ObjectFilter)pf);
      }

      /* An override - filter on collection as well */

      if (pf.getPropertyIndex() != PropertyInfoIndex.COLLECTION) {
        termsDropped = true;
        return null;
      }

      return new BwObjectFilter(null, (ObjectFilter)pf);
    } else {
      /* We assume we can't handle this one as a query.
       */
      throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }
  }

  private boolean match(final FilterBase f,
                        final BwEvent ev,
                        final String userHref) throws CalFacadeException {
    if (f == null) {
      return true;
    }

    if (debug()) {
      debug("match " + f);
    }

    if ((f instanceof AndFilter) || (f instanceof OrFilter)) {
      boolean itsAnd = (f instanceof AndFilter);

      for (FilterBase flt: f.getChildren()) {
        if (match(flt, ev, userHref)) {
          if (!itsAnd) {
            // Success for OR
            if (debug()) {
              debug("match true");
            }

            return true;
          }
        } else if (itsAnd) {
          debug("match true");
          return false;
        }
      }

      // For AND all matched, for OR nothing matched
      debug("match " + itsAnd);
      return itsAnd;
    }

    if (f instanceof EntityTimeRangeFilter) {
      return true; // Matched in db query
    }

    if (!(f instanceof PropertyFilter)) {
      /* We assume we can't handle this one as a query.
       */
      throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }

    PropertyFilter pf = (PropertyFilter)f;
    BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

    if (pi == null) {
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    String fieldName = pi.getDbFieldName();
    boolean param = pi.getParam();

    if (param) {
      BwIcalPropertyInfoEntry parentPi =
              BwIcalPropertyInfo.getPinfo(pf.getParentPropertyIndex());

      fieldName = parentPi.getDbFieldName() + "." + fieldName;
    }

    try {
      if (pf instanceof BwCategoryFilter) {
        return traceMatch(pf.match(ev, userHref)) ;
      }

      if (f instanceof PresenceFilter) {
        return traceMatch(matchPresence(pf.getPropertyIndex(), ev, userHref));
      }

      if (pf instanceof TimeRangeFilter) {
        return traceMatch(match((TimeRangeFilter)pf, ev));
      }

      if (pf instanceof BwObjectFilter) {
        return traceMatch(pf.match(ev, userHref));
      }
    } catch (WebdavException wde) {
      throw new CalFacadeException(wde);
    }

    throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                 String.valueOf(f));
  }

  private boolean traceMatch(final boolean val) {
    if (debug()) {
      debug("match " + val);
    }

    return val;
  }

  private boolean match(final TimeRangeFilter f,
                        final BwEvent ev) throws CalFacadeException {

    switch (f.getPropertyIndex()) {
      case COMPLETED:
        return (ev.getCompleted() != null) &&
                matchTimeRange(f, ev.getCompleted());

      case DTSTAMP:
        return matchTimeRange(f, ev.getDtstamp());

      case LAST_MODIFIED:
        return matchTimeRange(f, ev.getLastmod());

      case VALARM:
        for (BwAlarm a: ev.getAlarms()) {
          if (matchTimeRange(f, a.getTrigger())) {
            return true;
          }
        }

        return false;

      default:
        return false;
    }
  }

  private boolean matchTimeRange(final TimeRangeFilter trf,
                                 final String fld) {
    TimeRange tr = trf.getEntity();

    if (tr.getStart() != null) {
      if (fld.compareTo(tr.getStart().toString()) < 0) {
        return false;
      }
    }

    if (tr.getEnd() != null) {
      if (fld.compareTo(tr.getEnd().toString()) >= 0) {
        return false;
      }
    }

    return true;
  }

  private boolean matchPresence(final PropertyInfoIndex pi,
                                final BwEvent ev,
                                final String userHref) throws CalFacadeException {

    switch (pi) {
      case CLASS:
        return ev.getClassification() != null;

      case CREATED:
        return true;

      case DESCRIPTION:
        return ev.getDescriptions().size() > 0;

      case DTSTAMP:
        return true;

      case DTSTART:
        return !ev.getNoStart();

      case DURATION:
        return ev.getEndType() == StartEndComponent.endTypeDuration;

      case GEO:
        return ev.getGeo() != null;

      case LAST_MODIFIED:
        return true;

      case LOCATION:
        return ev.getLocation() != null;

      case ORGANIZER:
        return ev.getOrganizer() != null;

      case PRIORITY:
        return ev.getPriority() != null;

      case RECURRENCE_ID:
        return ev.getRecurrenceId() != null;

      case SEQUENCE:
        return true;

      case STATUS:
        return ev.getStatus() != null;

      case SUMMARY:
        return ev.getSummaries().size() > 0;

      case UID:
        return true;

      case URL:
        return ev.getLink() != null;

    /* Event only */

      case DTEND:
        return ev.getEndType() == StartEndComponent.endTypeDate;

      case TRANSP:
        return ev.getPeruserTransparency(userHref) != null;

    /* Todo only */

      case COMPLETED:
        return ev.getCompleted() != null;

      case DUE:
        return ev.getEndType() == StartEndComponent.endTypeDate;

      case PERCENT_COMPLETE:
        return ev.getPercentComplete() != null;

    /* ---------------------------- Multi valued --------------- */

    /* Event and Todo */

      case ATTACH:
        break;

      case ATTENDEE :
        break;

      case CATEGORIES:
        return ev.getCategories().size() > 0;

      case COMMENT:
        return ev.getComments().size() > 0;

      case CONTACT:
        return ev.getContacts().size() > 0;

      case EXDATE:
        return ev.getExdates().size() > 0;

      case EXRULE :
        return ev.getExrules().size() > 0;

      case REQUEST_STATUS:
        return ev.getRequestStatuses().size() > 0;

      case RELATED_TO:
        return ev.getRelatedTo() != null;

      case RESOURCES:
        return ev.getResources().size() > 0;

      case RDATE:
        return ev.getRdates().size() > 0;

      case RRULE :
        return ev.getRrules().size() > 0;

    /* -------------- Other non-event: non-todo ---------------- */

      case FREEBUSY:
        break;

      case TZID:
        break;

      case TZNAME:
        break;

      case TZOFFSETFROM:
        break;

      case TZOFFSETTO:
        break;

      case TZURL:
        break;

      case ACTION:
        break;

      case REPEAT:
        break;

      case TRIGGER:
        break;

      case CREATOR:
        return true;

      case OWNER:
        return true;

      case ENTITY_TYPE:
        break;

    }

    return false;
  }
}
