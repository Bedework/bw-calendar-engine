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
package org.bedework.calsvc.client;

import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.BooleanFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCalendar.EventListEntry;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeSubscriptionLoopException;
import org.bedework.calfacade.filter.BwCollectionFilter;
import org.bedework.calfacade.filter.BwHrefFilter;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.calsvci.CalSvcI;

import edu.rpi.cmt.calendar.PropertyIndex.PropertyInfoIndex;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

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
public class FilterBuilder {
  private CalSvcI svci;

  private boolean debug;

  private transient Logger log;

  /* Built as we find the calendar collections. */
  private ColorMap colorMap;

  private HashMap<String, BwCalendar> colCache = new HashMap<String, BwCalendar>();

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
   * @param svci
   * @param colorMap
   */
  public FilterBuilder(final CalSvc svci,
                       final ColorMap colorMap) {
    this.svci = svci;
    this.colorMap = colorMap;
    debug = getLogger().isDebugEnabled();
  }

  /** Build a filter from the paths. If the paths were explicity selected paths
   * (clicking on a collection etc), explicit will be marked as true. Otherwise
   * the paths come from a view.
   *
   * @param paths
   * @param conjunction - true for logical AND of paths
   * @param filterStr - supplied filter - from vpath
   * @param explicit
   * @return BwFilter or null
   * @throws CalFacadeException
   */
  public FilterBase buildFilter(final List<String> paths,
                                final boolean conjunction,
                                final String filterStr,
                                final boolean explicit) throws CalFacadeException {
    FilterBase globalFilter = null;

    if (filterStr != null) {
      globalFilter = parseExpr(filterStr);
    }

    if (paths == null) {
      // No filters

      return globalFilter;
    }

    CalFilterTerms cft;

    if (conjunction) {
      cft = new AndCalFilter();
    } else {
      cft = new OrCalFilter();
    }

    for (String path: paths) {
      BwCalendar col = colCache.get(path);

      if (col == null) {
        try {
          col = svci.getCalendarsHandler().get(path);
        } catch (CalFacadeAccessException cae) {
        }

        if (col == null) {
          // Presumably inacccessible or possibly deleted

          continue;
        }

        colCache.put(path, col);
      }

      if (!explicit && !col.getDisplay()) {
        continue;
      }

      ArrayList<String> pathElements = new ArrayList<String>();
      pathElements.add(path);
      CalFilter calFilter = makeColFilter(col,
                                          null,
                                          paths.size() == 1,
                                          pathElements);

      if (calFilter != null) {
        mergeFilter(cft.terms, calFilter, conjunction);
      }
    }

    if (cft.terms.isEmpty()) {
      // No valid matches
      return BooleanFilter.falseFilter;
    }

    /* if we have any OrCalFilters it's because they refer to different
     * calendar collections.
     *
     * Re-express this as BwFilters
     */

    FilterBase f = makeBwFilter(cft);

    if (globalFilter != null) {
      FilterBase and = new AndFilter();

      and.addChild(globalFilter);
      and.addChild(f);

      f = and;
    }

    if (debug) {
      dmsg(" ---------  FilterBuilder result ---------------");
      dump(f, "");

      if (colorMap != null) {
        dump(colorMap);
      }
      dmsg(" ---------  end of FilterBuilder result ---------------");
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
                                  final ColorInfo parCi,
                                  final boolean explicitSelection,
                                  final ArrayList<String> pathElements) throws CalFacadeException {
    /* Result of parsing any filter attached to this entity. */
    FilterBase fltr = null;

    if (cal.getFilterExpr() != null) {
      fltr = parseExpr(cal.getFilterExpr());
    }

    ColorInfo ci = updateColorInfo(cal, fltr, parCi);

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
    if (cal.getCollectionInfo().entitiesAllowed) {
      // Leaf node
      if (!explicitSelection &&
          (cal.getCalType() !=  BwCalendar.calTypeCalendarCollection) &&
          (cal.getCalType() !=  BwCalendar.calTypePoll) &&
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
      BwCalendar target = svci.getCalendarsHandler().resolveAlias(cal, false, false);
      if (target == null) {
        return null;
      }

      String path = target.getPath();

      if (pathElements.contains(path)) {
        throw new CalFacadeSubscriptionLoopException();
      }

      pathElements.add(path);

      return anded(fltr, makeColFilter(target, ci, false, pathElements));
    }

    if (cal.getCalType() == BwCalendar.calTypeFolder) {
      return anded(fltr, makeFolderFilter(cal, ci, pathElements));
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
      calFilter.filter = FilterBase.addAndChild(fltr, calFilter.filter);
    }

    //filterCache.put(calPath, calFilter);
    return calFilter;
  }

  private CalFilter makeFolderFilter(final BwCalendar val,
                                     final ColorInfo parCi,
                                     final ArrayList<String> pathElements) throws CalFacadeException {
    Collection<BwCalendar> cols = svci.getCalendarsHandler().getChildren(val);

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
        pe = new ArrayList<String>();
      } else {
        pe = new ArrayList<String>(pathElements);
      }

      String path = col.getPath();

      if (pe.contains(path)) {
        throw new CalFacadeSubscriptionLoopException();
      }

      pe.add(path);

      CalFilter cf = makeColFilter(col, parCi, false, pe);

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
          ecalf.filter = FilterBase.addOrChild(ecalf.filter, ecf.filter);
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

  private static class ColorInfo {
    /* This is the leaf collection or the first alias */
    BwCalendar col;

    /* This is the last non-null color we saw heading down this path. */
    String color;

    /* This is the target path */
    String path;

    /* This is the combined filter for this path. */
    FilterBase filter;

    ColorInfo() {}

    ColorInfo(final BwCalendar col,
              final String color,
              final String path,
              final FilterBase filter) {
      this.col = col;
      this.color = color;
      this.path = path;
      this.filter = filter;
    }

    void setCol(final BwCalendar val) {
      col = val;

      if ((color == null) || (col.getColor() != null)) {
        color = col.getColor();
      }
    }

    /* We know the color if we have reached the leaf or the first alias
     */
    boolean colorKnown() {
      return col != null;
    }

    void updateFilter(final FilterBase val) {
      if (filter == null) {
        filter = val;
        return;
      }

      filter = FilterBase.addAndChild(filter, val);
    }

    @Override
    protected Object clone() {
      return new ColorInfo(col, color, color, filter);
    }
  }

  /* As we work down the current subtree we build coloring information.
   * This consists of an accumulated filter per virtual path, the end path and
   * the last color set by the current users collections. For example
   *
   * For:
   * /user/fred/calendar(red)
   *   the resulting path is /user/fred/calendar
   *   the color is red
   *
   * /user/fred/dance(blue) -->
   *      /user/suite-a/dance(red) -->
   *          /public/MainCal filter=cat=Dance
   * That is fred has an alias to a calendar suite alias which in turn points to
   * a public alias
   *   the resulting path is /public/MainCal - the path in the events
   *   the color is blue - that's what Fred set
   *   the filter is cat=Dance, only filter we saw
   *
   * /user/fred/dance(green) -->
   *      /user/suite-b/dance(puce) cat=suite-b-events -->
   *          /public/MainCal filter=cat=Dance
   *   the resulting path is /public/MainCal - the path in the events
   *   the color is green - that's what Fred set
   *   the filter is cat=suite-b-events && cat=Dance, aggregated filters we saw
   *
   * These are built while we are doing a recursive descent of the supplied
   * initial paths. The parameter is the current calendar object and the state
   * we are at in the descent. The result is the new state for that point in the
   * tree.
   *
   * If the supplied collection object is a leaf node then the color map gets
   * updated with the accumulated information.
   *
   * @param fltr Result of parsing any filter attached to this entity.
   */
  private ColorInfo updateColorInfo(final BwCalendar col,
                                    final FilterBase fltr,
                                    final ColorInfo parCi) throws CalFacadeException {
    ColorInfo ci;

    if (parCi == null) {
      ci = new ColorInfo();
    } else {
      ci = (ColorInfo)parCi.clone();
    }

    String path = col.getPath();
    boolean leaf = col.getCollectionInfo().entitiesAllowed;

    ci.updateFilter(fltr);

    if (leaf) {
      if (ci.col == null) {
        ci.setCol(col);
      }

      /* If there's no color set don't put it in the table */
      if (ci.color != null) {
        ci.path = path;

        colorMap.put(path, new ClientCollectionInfo(path, ci.color,
                                                    col.getCalType(),
                                                    ci.filter));
      }
    } else if (col.getInternalAlias()) {
      if (ci.col == null) {
        // It's the first alias we've seen
        ci.setCol(col);
      }
    }

    return ci;
  }

  private void dump(final FilterBase f, final String curLine) {
    if (f instanceof OrFilter) {
      dmsg(curLine + "  OR ");
      Iterator<FilterBase> it = f.getChildren().iterator();
      dumpChildren(it, curLine);
      return;
    }

    if (f instanceof AndFilter) {
      dmsg(curLine + "  AND ");
      Iterator<FilterBase> it = f.getChildren().iterator();
      dumpChildren(it, curLine);
      return;
    }

    if (f instanceof ObjectFilter) {
      ObjectFilter of = (ObjectFilter)f;

      if (of.getEntity() instanceof BwCalendar) {
        StringBuilder sb = new StringBuilder(curLine);
        sb.append(curLine);
        sb.append("  cal=");
        sb.append(((BwCalendar)of.getEntity()).getPath());

        dmsg(sb.toString());
      } else {
        dmsg(curLine + f.toString());
      }
    } else {
      dmsg(curLine + f.toString());
    }
  }

  private void dump(final ColorMap cmap) {
    if (colorMap == null) {
      dmsg("*** No color map ***");
      return;
    }

    dmsg("------- color map ------");

    for (String path: cmap.keySet()) {
      dmsg("path: " + path);
      for (ClientCollectionInfo cci: colorMap.get(path)) {
        dmsg("  cci.path: " + cci.getPath());
        dmsg("  cci.color: " + cci.getColor());
        dmsg("  cci.filter: " + cci.getFilter());
      }
    }

    dmsg("------- end color map ------");
  }

  private FilterBase parseExpr(final String expr) throws CalFacadeException {
    if (parser == null) {
      parser = svci.getFilterParser();
    }

    ParseResult pr = parser.parse(expr);

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

  /* Get a logger for messages
   */
  private Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  private void dmsg(final String msg) {
    getLogger().debug(msg);
  }

}
