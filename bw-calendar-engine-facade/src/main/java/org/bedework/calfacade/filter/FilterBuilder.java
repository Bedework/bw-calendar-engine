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
package org.bedework.calfacade.filter;

import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.BooleanFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.EventListEntry;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeSubscriptionLoopException;
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/** This class builds a filter expression given a set of paths.
 * Given one or more paths we create a filter to represent each path.
 * Each path pi will produce an associated filter f-pi.
 *
 * What we return is
 *    f-p1 | f-p2 | ... | f-pn
 *
 * A path pi could point directly at an unfiltered collection Ci. The filter
 * then becomes simply that collection.
 *
 * Alternatively that collection might have a filter f-ci. The result is
 *     Ci & f-ci
 *
 * We can have a chain of filters on aliases with an eventual target of Ci
 * For example
 *     A1 -> A2 -> A3 -> Ci
 * where each A is an alias
 * If each A has a filter f-ai the above gives us
 *     Ci & f-a1 & f-a2 & f-a3
 *
 * For folders we or the resulting filter for each sub-path.
 *
 * The end result is a set of filters applied directly to one or more calendar
 * collections.
 *
 * (C1 & f1) | (C2 & f2) | ... (Cn & fn)
 *
 * It is probable that many of the Ci are the same collection so we should
 * gather all the Ci together. In the one calendar model this would result in
 *
 *  C1 & (f1 | f2 | ... | fn)
 *
 *  What is less likely, though still possible, is a set of common
 *  sub-expressions which can be factored out
 *
 * @author Mike Douglass
 *
 */
public class FilterBuilder implements Logged {
  private HashMap<String, BwCalendar> colCache = new HashMap<>();

  //private HashMap<String, CalFilter> filterCache = new HashMap<String, CalFilter>();

  private static class CalFilter {
    FilterBase filter;
  }

  private static class HrefFilter extends CalFilter {
    String href;
  }

  private static class EntityCalFilter extends CalFilter {
    /* The real calendar collection to which this applies */
    BwCalendar cal;
  }

  private static class CalFilterTerms extends CalFilter {
    Collection<CalFilter> terms = new ArrayList<CalFilter>();
  }

  private static class OrCalFilter extends CalFilterTerms {}

  private static class AndCalFilter extends CalFilterTerms {}

  private SimpleFilterParser parser;

  /**
   * @param parser
   */
  public FilterBuilder(final SimpleFilterParser parser) {
    super();
    this.parser = parser;
  }

  /** Build a filter from the given path. The applyFilter flag only
   * applies to the root of the tree. The filter may already have been
   * processed by the caller.
   *
   * @param path
   * @param applyFilter applies only to root of tree
   * @return FilterBase or null
   */
  public FilterBase buildFilter(final String path,
                                final boolean applyFilter,
                                final boolean explicitSelection) {
    if (path == null) {
      return BooleanFilter.falseFilter;
    }

    BwCalendar col = colCache.get(path);

    if (col == null) {
      try {
        col = parser.getCollection(path);
      } catch (CalFacadeException cfe) {
        error(cfe);
        return BooleanFilter.falseFilter;
      }

      colCache.put(path, col);
    }

    final ArrayList<String> pathElements = new ArrayList<>();
    pathElements.add(path);
    final CalFilter calFilter; 
    
    try {
      calFilter = makeColFilter(col,
                                applyFilter,
                                explicitSelection,
                                pathElements);
    } catch (CalFacadeException cfe) {
      error(cfe);
      return BooleanFilter.falseFilter;
    }

    if (calFilter == null) {
      // No valid matches
      return BooleanFilter.falseFilter;
    }

    /* if we have any OrCalFilters it's because they refer to different
     * calendar collections.
     *
     * Re-express this as BwFilters
     */

    final FilterBase f = makeBwFilter(calFilter);

    if (debug()) {
      debug(" ---------  FilterBuilder result ---------------");
      dump(f, "");

      debug(" ---------  end of FilterBuilder result ---------------");
    }

    return f;
  }

  private FilterBase makeBwFilter(final CalFilter val) {
    boolean conjunction = val instanceof AndCalFilter;

    if (val instanceof CalFilterTerms) {
      CalFilterTerms cft = (CalFilterTerms)val;

      if (cft.terms.size() == 0) {
        // No valid matches
        return BooleanFilter.falseFilter;
      }

      if (cft.terms.size() == 1) {
        return makeBwFilter(cft.terms.iterator().next());
      }

      FilterBase res = null;

      for (CalFilter cf: cft.terms) {
        FilterBase f = makeBwFilter(cf);

        if (f != null) {
          if (conjunction) {
            res = FilterBase.addAndChild(res, f);
          } else {
            res = FilterBase.addOrChild(res, f);
          }
        }
      }

      return res;
    }

    if (val instanceof HrefFilter) {
      BwHrefFilter hf = new BwHrefFilter(null, PropertyInfoIndex.HREF);

      hf.setEntity(((HrefFilter)val).href);

      return hf;
    }

    EntityCalFilter ecf = (EntityCalFilter)val;

    if (ecf.cal == null) {
      return ecf.filter;
    }

    FilterBase f = new BwCollectionFilter(null, ecf.cal);

    return FilterBase.addAndChild(f, ecf.filter);
  }

  /* Create a filter for the supplied collection object.
   *
   * explicitSelection is true if we have a single path and it refers directly
   * to the given collection, e.g. /user/xxx/Inbox
   *
   * This allows us to see the contents of the inbox for example, but not to
   * include it when given paths like /user/xxx
   *
   * pathElement is used to detect loops in the actual path. We fail with an
   * exception if we discover a repeated URI in the list.
   */
  private CalFilter makeColFilter(final BwCalendar cal,
                                  final boolean applyFilter,
                                  final boolean explicitSelection,
                                  final ArrayList<String> pathElements) throws CalFacadeException {
    /* Result of parsing any filter attached to this entity. */
    FilterBase fltr = null;

    if (applyFilter && (cal.getFilterExpr() != null)) {
      fltr = parseExpr(cal);
    }

    if (cal.getCalType() ==  BwCalendar.calTypeEventList) {
      OrCalFilter ocf = new OrCalFilter();

      for (EventListEntry ele: cal.getEventList()) {
        HrefFilter hf = new HrefFilter();

        hf.href = ele.getHref();

        ocf.terms.add(hf);
      }

      return ocf;
    }

    /* This covers most - calendar collection, inbox, outbox, external alias etc */
    if (cal.getCollectionInfo().onlyCalEntities) {
      // Leaf node
      if (!explicitSelection &&
          (cal.getCalType() !=  BwCalendar.calTypeCalendarCollection) &&
          (cal.getCalType() !=  BwCalendar.calTypeExtSub)) {
        return null;
      }

      EntityCalFilter ecalFilter = new EntityCalFilter();
      ecalFilter.cal = cal;
      ecalFilter.filter = fltr;

      //filterCache.put(calPath, ecalFilter);
      return ecalFilter;
    }

    if (cal.getInternalAlias()) {
      BwCalendar target = parser.resolveAlias(cal, false);
      if (target == null) {
        return null;
      }

      String path = target.getPath();

      if (pathElements.contains(path)) {
        throw new CalFacadeSubscriptionLoopException();
      }

      pathElements.add(path);

      return anded(fltr, makeColFilter(target, true, false, pathElements));
    }

    if (cal.getCalType() == BwCalendar.calTypeFolder) {
      return anded(fltr, makeFolderFilter(cal, pathElements));
    }

    return null;
  }

  private CalFilter anded(final FilterBase fltr,
                          final CalFilter calFilter) throws CalFacadeException {
    if (calFilter == null) {
      /* PRobably some sort of error */
      return null;
    }

    if (fltr != null) {
      // We have something to add
      calFilter.filter = FilterBase
              .addAndChild(fltr, calFilter.filter);
    }

    //filterCache.put(calPath, calFilter);
    return calFilter;
  }

  private CalFilter makeFolderFilter(final BwCalendar val,
                                     final ArrayList<String> pathElements) throws CalFacadeException {
    Collection<BwCalendar> cols = parser.getChildren(val);

    OrCalFilter res = new OrCalFilter();

    for (BwCalendar col: cols) {
      if (colCache.get(col.getPath()) == null) {
        colCache.put(col.getPath(), col);
      }

      if (!col.getDisplay()) {
        continue;
      }

      ArrayList<String> pe;
      if (pathElements == null) {
        pe = new ArrayList<>();
      } else {
        pe = new ArrayList<>(pathElements);
      }

      String path = col.getPath();

      if (pe.contains(path)) {
        throw new CalFacadeSubscriptionLoopException();
      }

      pe.add(path);

      CalFilter cf = makeColFilter(col, true, false, pe);

      if (cf == null) {
        continue;
      }

      mergeFilter(res.terms, cf, false);
    }

    if (res.terms.size() == 0) {
      return null;
    }

    if (res.terms.size() == 1) {
      /* Only one filter resulted. Return that rather than the or-filter */
      return res.terms.iterator().next();
    }

    return res;
  }

  @SuppressWarnings("unchecked")
  private void mergeFilter(final Collection<CalFilter> terms,
                           final CalFilter cf,
                           final boolean conjunction) throws CalFacadeException {
    if (!(cf instanceof EntityCalFilter) ||
        (terms.size() == 0)) {
      terms.add(cf);
      return;
    }

    // For the moment don't merge conjunctions
    if (conjunction) {
      terms.add(cf);
      return;
    }

    /* Try to merge this with any other entity filters.
     *
     *  An entity filter is either
     *    (Ci)
     *  or
     *    (Ci, Fi)
     *
     *  where Ci is a calendar and Fi is a filter expression.
     *
     *  A Ci by itself is an unfiltered calendar collection.
     *
     *  So (Ci, F1) | (Ci, F2) = (Ci, (F1 | F2))
     *  whereas
     *     (Ci) | (Ci, F2) = (Ci)
     *  because an unfiltered calendar includes everything.
     */

    EntityCalFilter ecf = (EntityCalFilter)cf;

    for (CalFilter calf: terms) {
      if (!(calf instanceof EntityCalFilter)) {
        continue;
      }

      EntityCalFilter ecalf = (EntityCalFilter)calf;
      if (ecf.cal.equals(ecalf.cal)) {
        if ((ecalf.filter == null) || (ecf.filter == null)) {
          ecalf.filter = null;  // It's unfiltered
          return;
        }

        /* If they are both property filters for a single valued property
         * we may be able to merge the expression to an in (<set>)
         */

        if ((!(ecalf.filter instanceof ObjectFilter)) ||
            (!(ecf.filter instanceof ObjectFilter))) {
          ecalf.filter = FilterBase.addOrChild(ecalf.filter, ecf.filter);
          return;  // Merged
        }

        ObjectFilter ocalf = (ObjectFilter)ecalf.filter;
        ObjectFilter ocf = (ObjectFilter)ecf.filter;

        /* We can match for the same property and exact and
         * entities are <T> or Collection<T>
         */
        /* TODO - can only handle string so far */

        if (!ocalf.getPropertyIndex().equals(ocf.getPropertyIndex()) ||
            !ocalf.getExact() || !ocf.getExact() ||
            BwIcalPropertyInfo.getPinfo(ocalf.getPropertyIndex()).getMultiValued()) {
          ecalf.filter = FilterBase
                  .addOrChild(ecalf.filter, ecf.filter);
          return;  // Merged
        }

        Object o1 = ocalf.getEntity();
        Object o2 = ocf.getEntity();

        Collection c1 = null;
        Collection c2 = null;

        if (o1 instanceof Collection) {
          c1 = (Collection)o1;
          if (c1.size() > 0) {
            o1 = c1.iterator().next();
          } else {
            o1 = null;
          }
        }

        if (o2 instanceof Collection) {
          c2 = (Collection)o2;
          if (c2.size() > 0) {
            o2 = c2.iterator().next();
          } else {
            o2 = null;
          }
        }

        if ((o1 == null) || (o2 == null) ||
            (!o1.getClass().equals(o2.getClass()))) {
          ecalf.filter = FilterBase.addOrChild(ecalf.filter, ecf.filter);
          return;  // Merged
        }

        if (c1 == null) {
          c1 = new ArrayList();

          c1.add(o1);
          ocalf.setEntity(c1);
        }

        if (c2 != null) {
          c1.addAll(c2);
        } else {
          c1.add(o2);
        }

        return;  // Merged
      }
    }

    terms.add(cf);
  }

  private void dump(final FilterBase f, final String curLine) {
    if (f instanceof OrFilter) {
      debug(curLine + "  OR ");
      Iterator<FilterBase> it = f.getChildren().iterator();
      dumpChildren(it, curLine);
      return;
    }

    if (f instanceof AndFilter) {
      debug(curLine + "  AND ");
      Iterator<FilterBase> it = f.getChildren().iterator();
      dumpChildren(it, curLine);
      return;
    }

    if (f instanceof ObjectFilter) {
      ObjectFilter of = (ObjectFilter)f;

      if (of.getEntity() instanceof BwCalendar) {
        final StringBuilder sb = new StringBuilder(curLine);
        sb.append(curLine);
        sb.append("  cal=");
        sb.append(((BwCalendar)of.getEntity()).getPath());

        debug(sb.toString());
      } else {
        debug(curLine + f.toString());
      }
    } else {
      debug(curLine + f.toString());
    }
  }

  private FilterBase parseExpr(final BwCalendar col) throws CalFacadeException {
    SimpleFilterParser sfp = parser.getParser();

    ParseResult pr = sfp.parse(col.getFilterExpr(),
                               false,
                               col.getPath());

    if (!pr.ok) {
      throw pr.cfe;
    }

    return pr.filter;
  }

  private void dumpChildren(final Iterator<FilterBase> it,
                            final String curLine) {
    if (!it.hasNext()) {
      return;
    }

    FilterBase f = it.next();

    if (it.hasNext()) {
      dumpChildren(it, curLine + "   |   ");
    }

    dump(f, curLine);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
