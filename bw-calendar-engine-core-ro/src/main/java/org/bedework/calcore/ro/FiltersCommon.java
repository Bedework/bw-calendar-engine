/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.ro;

import org.bedework.base.exc.BedeworkException;
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
import org.bedework.calfacade.filter.BwCategoryFilter;
import org.bedework.calfacade.filter.BwObjectFilter;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.logging.BwLogger;
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

  public FiltersCommon(final FilterBase filter) {
    /* Reconstruct the filter and create a date only filter for overrides. */

    fullFilter = reconstruct(filter, false);
    overrideFilter = reconstruct(filter, true);
  }

  @Override
  public boolean postFilter(final BwEvent ev,
                            final String userHref) {
    return match(fullFilter, ev, userHref);
  }

  /* Return null if there are no terms to test for - otherwise return the
   * modified filter.
   *
   * Mostly we replace ObjectFilter with its wrapped form.
   */
  private FilterBase reconstruct(final FilterBase f,
                                 final boolean forOverrides) {
    if (f == null) {
      return null;
    }

    if ((f instanceof AndFilter) || (f instanceof OrFilter)) {
      final boolean itsAnd = (f instanceof AndFilter);

      final List<FilterBase> fs = new ArrayList<>();

      for (final FilterBase flt: f.getChildren()) {
        final FilterBase chf = reconstruct(flt, forOverrides);

        if (chf != null) {
          fs.add(chf);
        }
      }

      if (fs.isEmpty()) {
        return null;
      }

      if (fs.size() == 1) {
        return fs.get(0);
      }

      final FilterBase res;

      if (itsAnd) {
        res = new AndFilter();
      } else {
        res = new OrFilter();
      }

      for (final FilterBase flt: fs) {
        res.addChild(flt);
      }

      return res;
    }

    /* Not AND/OR */

    if (!(f instanceof final PropertyFilter pf)) {
      throw new BedeworkException("org.bedework.filters.unknownfilter",
                                  String.valueOf(f));
    }

    if (pf.getPropertyIndex() == PropertyInfoIndex.HREF) {
      // Special case this
      return f;
    }

    if (BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex()) == null) {
      throw new BedeworkException("org.bedework.filters.unknownproperty",
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
        if (pf instanceof final EntityTypeFilter etf) {

          if ((etf.getEntity() == IcalDefs.entityTypeVavailability) ||
                  (etf.getEntity() == IcalDefs.entityTypeAvailable)) {
            // Ensure we get or exclude both
            final boolean not = etf.getNot();
            final FilterBase both;

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
            } catch (final Throwable t) {
              throw new BedeworkException(t);
            }

            return both;
          }
        }
        return new BwObjectFilter(null, (ObjectFilter<?>)pf);
      }

      /* An override - filter on collection as well */

      if (pf.getPropertyIndex() != PropertyInfoIndex.COLLECTION) {
        termsDropped = true;
        return null;
      }

      return new BwObjectFilter(null, (ObjectFilter<?>)pf);
    } else {
      /* We assume we can't handle this one as a query.
       */
      throw new BedeworkException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }
  }

  private boolean match(final FilterBase f,
                        final BwEvent ev,
                        final String userHref) {
    if (f == null) {
      return true;
    }

    if (debug()) {
      debug("match " + f);
    }

    if ((f instanceof AndFilter) || (f instanceof OrFilter)) {
      final boolean itsAnd = (f instanceof AndFilter);

      for (final FilterBase flt: f.getChildren()) {
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

    if (!(f instanceof final PropertyFilter pf)) {
      /* We assume we can't handle this one as a query.
       */
      throw new BedeworkException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }

    final BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

    if (pi == null) {
      throw new BedeworkException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    String fieldName = pi.getDbFieldName();
    final boolean param = pi.getParam();

    if (param) {
      final BwIcalPropertyInfoEntry parentPi =
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
    } catch (final WebdavException wde) {
      throw new BedeworkException(wde);
    }

    throw new BedeworkException("org.bedework.filters.unknownfilter",
                                 String.valueOf(f));
  }

  private boolean traceMatch(final boolean val) {
    if (debug()) {
      debug("match " + val);
    }

    return val;
  }

  private boolean match(final TimeRangeFilter f,
                        final BwEvent ev) {

    return switch (f.getPropertyIndex()) {
      case COMPLETED -> (ev.getCompleted() != null) &&
              matchTimeRange(f, ev.getCompleted());
      case DTSTAMP -> matchTimeRange(f, ev.getDtstamp());
      case LAST_MODIFIED -> matchTimeRange(f, ev.getLastmod());
      case VALARM -> {
        for (final BwAlarm a: ev.getAlarms()) {
          if (matchTimeRange(f, a.getTrigger())) {
            yield true;
          }
        }

        yield false;
      }
      default -> false;
    };
  }

  private boolean matchTimeRange(final TimeRangeFilter trf,
                                 final String fld) {
    final TimeRange tr = trf.getEntity();

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
                                final String userHref) {

    switch (pi) {
      case CLASS:
        return ev.getClassification() != null;

      case CREATED:
        return true;

      case DESCRIPTION:
        return !ev.getDescriptions().isEmpty();

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
        return !ev.getSchedulingInfo().getSchedulingOwner().noOwner();

      case PRIORITY:
        return ev.getPriority() != null;

      case RECURRENCE_ID:
        return ev.getRecurrenceId() != null;

      case SEQUENCE:
        return true;

      case STATUS:
        return ev.getStatus() != null;

      case SUMMARY:
        return !ev.getSummaries().isEmpty();

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
        return !ev.getCategories().isEmpty();

      case COMMENT:
        return !ev.getComments().isEmpty();

      case CONTACT:
        return !ev.getContacts().isEmpty();

      case EXDATE:
        return !ev.getExdates().isEmpty();

      case EXRULE :
        return !ev.getExrules().isEmpty();

      case REQUEST_STATUS:
        return !ev.getRequestStatuses().isEmpty();

      case RELATED_TO:
        return ev.getRelatedTo() != null;

      case RESOURCES:
        return !ev.getResources().isEmpty();

      case RDATE:
        return !ev.getRdates().isEmpty();

      case RRULE :
        return !ev.getRrules().isEmpty();

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

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
