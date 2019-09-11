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
package org.bedework.calcore.common.indexing;

import org.bedework.calcore.common.indexing.TermOrTermsQuery.AndQB;
import org.bedework.calcore.common.indexing.TermOrTermsQuery.NotQB;
import org.bedework.calcore.common.indexing.TermOrTermsQuery.OrQB;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.BooleanFilter;
import org.bedework.caldav.util.filter.EntityTimeRangeFilter;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.PropertyFilter;
import org.bedework.caldav.util.filter.TimeRangeFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.BwCollectionFilter;
import org.bedework.calfacade.filter.BwHrefFilter;
import org.bedework.calfacade.filter.BwViewFilter;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer.DeletedState;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.elasticsearch.ESQueryFilterBase;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchNoneQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.bedework.calfacade.indexing.BwIndexer.DeletedState.includeDeleted;
import static org.bedework.calfacade.indexing.BwIndexer.DeletedState.onlyDeleted;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeEvent;
import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/** Build filters for ES searching
 *
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class ESQueryFilter extends ESQueryFilterBase
        implements CalintfDefs, Logged {
  private final boolean publick;
  private final int currentMode;
  private final String ownerHref;
  private final boolean superUser;
  private final String docType;

  private RecurringRetrievalMode recurRetrieval;

  /* True if we have a limitation to owner or collection */
  private boolean queryLimited;

  /* True if we have a filter on a property which is not owner or
     collection.

     These can only be satisfied by searching the instances */
  private boolean queryFiltered;
  
  private String latestStart;
  private String earliestEnd;

  private static final String entityTypeJname =
          getJname(PropertyInfoIndex.ENTITY_TYPE);

  public static final String colpathJname =
          getJname(PropertyInfoIndex.COLLECTION);

  private final static String lastModJname =
          getJname(PropertyInfoIndex.LAST_MODIFIED);

  private final static String lastModSeqJname =
          getJname(PropertyInfoIndex.LASTMODSEQ);

  //private static final String dtendJname = getJname(PropertyInfoIndex.DTEND);
  //private static final String dtstartJname = getJname(PropertyInfoIndex.DTSTART);

  /** */
  public static final String hrefJname =
          getJname(PropertyInfoIndex.HREF);

  private static final String ownerJname =
          getJname(PropertyInfoIndex.OWNER);

  private static final String publicJname =
          getJname(PropertyInfoIndex.PUBLIC);

  //public static final String recurrenceidJname =
  //        getJname(PropertyInfoIndex.RECURRENCE_ID);

  //private static String indexEndJname = getJname(PropertyInfoIndex.INDEX_END);
  //private static String indexStartJname = getJname(PropertyInfoIndex.INDEX_START);

  private final static String hrefRef =
          makePropertyRef(PropertyInfoIndex.HREF);

  private final static String colPathRef =
          makePropertyRef(PropertyInfoIndex.COLPATH);

  private final static String uidRef =
          makePropertyRef(PropertyInfoIndex.UID);

  private final static String dtEndUTCRef =
          makePropertyRef(PropertyInfoIndex.DTEND,
                          PropertyInfoIndex.UTC);

  private final static String dtStartUTCRef = makePropertyRef(
          PropertyInfoIndex.DTSTART,
          PropertyInfoIndex.UTC);

  private final static String indexEndUTCRef =
          makePropertyRef(PropertyInfoIndex.INDEX_END,
                          PropertyInfoIndex.UTC);

  private final static String indexStartUTCRef = makePropertyRef(
          PropertyInfoIndex.INDEX_START,
          PropertyInfoIndex.UTC);

  private final static String nextTriggerRef = makePropertyRef(
          PropertyInfoIndex.VALARM,
          PropertyInfoIndex.NEXT_TRIGGER_DATE_TIME);
          
  /**
   *
   * @param publick true for a public indexer
   * @param currentMode - guest, user,publicAdmin
   * @param ownerHref - only used to add a filter for non-public
   * @param superUser - true if the principal is a superuser.
   * @param recurRetrieval  - value modifies search
   */
  public ESQueryFilter(final boolean publick,
                       final int currentMode,
                       final String ownerHref,
                       final boolean superUser,
                       final RecurringRetrievalMode recurRetrieval,
                       final String docType) {
    this.publick = publick;
    this.currentMode = currentMode;
    this.ownerHref = ownerHref;
    this.superUser = superUser;
    this.recurRetrieval = recurRetrieval;
    if (recurRetrieval == null) {
      this.recurRetrieval = RecurringRetrievalMode.expanded;
    }
    this.docType = docType;
  }

  /** Build a query for a single entity identified by the property
   * reference and value. The search will be limited by the current
   * mode.
   *
   * @param val the value to match
   * @param index list of terms for dot reference
   * @return a query builder
   */
  public QueryBuilder singleEntityQuery(final String val,
                                        final PropertyInfoIndex... index) {
    return termQuery(makePropertyRef(index), val);
  }

  /** Build a query for a single tombstoned entity identified by the property
   * reference and value. The search will be limited by the current
   * mode.
   *
   * @param val the value to match
   * @param index list of terms for dot reference
   * @return a query builder
   */
  public QueryBuilder singleTombstonedEntityQuery(final String val,
                                        final PropertyInfoIndex... index) {
    return and(termQuery(makePropertyRef(index), val),
               termQuery(makePropertyRef(PropertyInfoIndex.TOMBSTONED),
                         true),
               null);
  }

  /** Build a filter for a single event identified by the href
   * and recurrenceid. If recurrenceId is null we target a master only
   * otherwise we target an instance only.
   *
   * @param href  of event
   * @param recurrenceId of instance
   * @return a filter builder
   */
  public QueryBuilder singleEventFilter(final String href,
                                          final String recurrenceId) {
    QueryBuilder anded = termQuery(hrefRef, href);

    if (recurrenceId == null) {
      anded = and(anded, addTerm(PropertyInfoIndex.MASTER, "true"), null);
    } else {
      anded = and(anded, addTerm(PropertyInfoIndex.INSTANCE, "true"),
                  null);
      anded = and(anded, addTerm(PropertyInfoIndex.RECURRENCE_ID,
                                 recurrenceId),
                  null);
    }

    return anded;
  }

  /** Build a filter for a single event identified by the colPath
   * and uid.
   *
   * @param colPath to event
   * @param guid of event
   * @return a filter builder
   */
  public QueryBuilder singleEventFilterGuid(final String colPath,
                                             final String guid) {
    QueryBuilder anded = termQuery(colPathRef, colPath);

    anded = and(anded, termQuery(uidRef, guid),
                null);
    anded = and(anded, addTerm(PropertyInfoIndex.MASTER, "true"), null);

    return anded;
  }

  /** Build a filter for a single location identified by the key with
   * the given name and value.
   *
   * @param name - of key field
   * @param val - expected full value
   * @return a filter builder
   */
  public QueryBuilder locationKeyFilter(final String name,
                                         final String val) throws CalFacadeException {
    final ObjectFilter<String> kf =
            new ObjectFilter<>(null,
                               PropertyInfoIndex.LOC_KEYS_FLD,
                               null,
                               name);
    kf.setEntity(val);
    kf.setExact(true);
    kf.setCaseless(false);

    return buildQuery(kf);
  }

  /** Build a filter for resources in a collection possibly limited by
   * lastmod + seq
   *
   * @param path - to resources
   * @param lastmod - if non-null use for sync check
   * @param lastmodSeq - if lastmod non-null use for sync check
   * @return a filter builder
   */
  public QueryBuilder resources(final String path,
                                final String lastmod,
                                final int lastmodSeq) throws CalFacadeException {
    final BwCollectionFilter cf =
            new BwCollectionFilter(null, path);

    QueryBuilder fb = buildQuery(cf);

    if (lastmod == null) {
      return fb;
    }

    /* filter is
       fb AND (lastmod>"lastmod" or
                (lastmod="lastmod" and sequence>seq))

     */

    QueryBuilder lmeq =
            and(termQuery(lastModJname, lastmod),
                new RangeQueryBuilder(lastModSeqJname).gt(lastmodSeq),
                null);

    QueryBuilder lmor =
            or(new RangeQueryBuilder(lastModJname).gt(lastmod),
               lmeq);

    return principalQuery(and(fb, lmor, "lmor"));
  }

  public FilterBase addTypeFilter(final FilterBase f,
                                  final String docType) {
    final ObjectFilter<String> ef =
            new ObjectFilter<>(null,
                               PropertyInfoIndex.DOCTYPE,
                               null,
                               null);
    ef.setEntity(docType);
    ef.setExact(true);
    ef.setCaseless(false);

    final AndFilter and = new AndFilter();

    and.addChild(ef);
    and.addChild(f);

    return and;
  }

  public QueryBuilder buildQuery(final FilterBase f) throws CalFacadeException {
    QueryBuilder qb = makeQuery(f);

    if (qb instanceof TermOrTermsQuery) {
      qb = ((TermOrTermsQuery)qb).makeQb();
    }

    return qb;
  }

  /* TODO we need to provide a chain of filters when we have deep paths,
       e.g. entity[key1].entity[key2].value = "something"
   */
  public QueryBuilder makeQuery(final List<PropertyInfoIndex> pis,
                                final Object val,
                                final Integer intKey,
                                final String strKey,
                                final OperationType opType,
                                final boolean negate) throws CalFacadeException {
    /* Work backwards through the property list building a path.
       When the head of the path is a nested type:
         If it's the first we found:
            generate a match or term query based on the leaf
         otherwise:
            we already have a nested query to push inside a new one
    
       If the top entry has a keyindex we expect a String or Numeric 
       key value we generate a bool query with 2 must match terms.
     */

    QueryBuilder qb = null;
    QueryBuilder nqb = null; // current nested level
    PropertyInfoIndex leafPii = null;

    /* See if we need to build a nested query */
    final BwIcalPropertyInfoEntry rootPie = 
            BwIcalPropertyInfo.getPinfo(pis.get(0));
    final boolean isNested = rootPie.getNested();

    for (int plistIndex = pis.size() - 1; plistIndex >= 0; plistIndex--) {
      final PropertyInfoIndex pii = pis.get(plistIndex);

      if (leafPii == null) {
        leafPii = pii;
      }
      
      final BwIcalPropertyInfoEntry bwPie = BwIcalPropertyInfo.getPinfo(pii);

      final String fullTermPath;
      if (bwPie.getTermsField() == null) {
        fullTermPath = null;
      } else {
        fullTermPath = makePropertyRef(pis, plistIndex,
                                       bwPie.getTermsField());
      }

      if (isNested) {
        final QueryBuilder nested;
        String path = makePropertyRef(pis, plistIndex, null);

        if (nqb != null) {
          if (plistIndex == 0) {
            // TODO Temp fix this
            path = "event." + path;
          }
          nested = new NestedQueryBuilder(path, nqb,
                                          ScoreMode.Avg);
        } else {
          qb = makeQuery(leafPii,
                         makePropertyRef(pis, null),
                         fullTermPath,
                         val, opType,
                         1, false);

          /* Is the parent indexed? */
          final BwIcalPropertyInfoEntry parentPie;
          if (plistIndex == 0) {
            // No parent
            parentPie = null;
          } else {
            parentPie = BwIcalPropertyInfo.getPinfo(pis.get(plistIndex - 1));
          }
          
          if ((parentPie != null) && 
                  (parentPie.getKeyindex() != PropertyInfoIndex.UNKNOWN_PROPERTY)) {
            final BoolQueryBuilder bqb = new BoolQueryBuilder();

            if (qb == null) {
              error("No nested query for " + pii);
              return null;
            }
            bqb.must(qb);
            
            final List<PropertyInfoIndex> indexPis = new ArrayList<>();
            indexPis.add(pis.get(plistIndex - 1));
            indexPis.add(parentPie.getKeyindex());
            final String indexPath = makePropertyRef(indexPis, null);

            if (intKey != null) {
              bqb.must(new TermQueryBuilder(indexPath,
                                            intKey));
            } else if (strKey != null) {
              bqb.must(new TermQueryBuilder(indexPath,
                                            strKey));
            } else {
              error("Missing key for index for " + pii);
              return null;
            }
            
            qb = bqb;
          }
           
          nested = qb;
        }

        nqb = nested;
      } else if (plistIndex == 0) {
        // No nested types found
        qb = makeQuery(leafPii, makePropertyRef(pis, null),
                       fullTermPath,
                       val, opType,
                       1,
                       false);
      }
    }

    if (nqb != null) {
      qb = nqb;
    }

    if (negate) {
      return not(qb);
    }

    return qb;
  }

  public QueryBuilder multiHref(final Set<String> hrefs,
                                final RecurringRetrievalMode rmode) throws CalFacadeException {
    QueryBuilder qb = null;

    for (final String href: hrefs) {
      qb = or(qb, termQuery(hrefJname, href));
    }

    if ((rmode.mode == Rmode.entityOnly) && docType.equals(docTypeEvent)) {
      return and(qb,
                 addTerm(PropertyInfoIndex.MASTER, "true"), null);
    }

    if (rmode.mode == Rmode.overrides) {
      final QueryBuilder limit =
              or(addTerm(PropertyInfoIndex.MASTER, "true"),
                 addTerm(PropertyInfoIndex.OVERRIDE, "true"));

      return and(qb, limit, null);
    }

    return qb;
  }

  private static class NamedQueryBuilder {
    final String name;
    final QueryBuilder qb;

    public NamedQueryBuilder(final String name,
                             final QueryBuilder qb) {
      this.name = name;
      this.qb = qb;
    }
  } 
  
  /** This method adds extra limits to the search if they are necessary.
   * If the search is already limited to one or more collections or 
   * specific href(s) then we don't need to add anything.
   * 
   * <p>Otherwise we apply a default search context (if any). If the 
   * result is still not limited we limit to entities owned by the 
   * current principal</p>
   * 
   * @param q current query
   * @param defaultFilterContext set if we have one
   * @param forEvents true if this is an events index we are searching
   * @return augmented filter
   * @throws CalFacadeException on error
   */
  public QueryBuilder addLimits(final QueryBuilder q,
                                 final FilterBase defaultFilterContext,
                                 final DeletedState delState,
                                 final boolean forEvents) throws CalFacadeException {
    if ((q != null) && (q instanceof MatchNoneQueryBuilder)) {
      return q;
    }
    
    final List<NamedQueryBuilder> nfbs = new ArrayList<>();

    if (!queryLimited) {
      if (defaultFilterContext != null) {
        if (defaultFilterContext instanceof BwViewFilter) {
          /* Treat this specially. Create a named query for each 
             child filter. The name will be the filter name which
             itself is derived from the collection href.
          */

          final FilterBase fb = 
                  ((BwViewFilter)defaultFilterContext).getFilter();
          
          if ((fb != null) && (fb.getChildren() != null)) {
            for (final FilterBase vfb : fb.getChildren()) {
              nfbs.add(new NamedQueryBuilder(vfb.getName(),
                                              and(buildQuery(vfb), q,
                                                  vfb.getName())));
            }
          }
        } else {
          final QueryBuilder limQb = buildQuery(defaultFilterContext);
          nfbs.add(new NamedQueryBuilder(null, and(q, limQb, null)));
        }
      }

      if (!queryLimited) {
        if (q == null) {
          nfbs.add(new NamedQueryBuilder(null, new MatchAllQueryBuilder()));
        } else {
          nfbs.add(new NamedQueryBuilder(null, principalQuery(q)));
        }
      }
    } else if (q == null) {
      nfbs.add(new NamedQueryBuilder(null, new MatchAllQueryBuilder()));
    } else {
      nfbs.add(new NamedQueryBuilder(null, q));
    }

    QueryBuilder recurQb;

    if (forEvents) {
      recurQb = recurTerms();
    } else {
      recurQb = null;
    }

    QueryBuilder qb;

    if (nfbs.size() == 1) {
      qb = nfbs.get(0).qb;

      if (recurQb != null) {
        qb = and(qb, recurQb, null);
      }
    } else {
      qb = null;
      if (recurQb == null) {
        recurQb = new MatchAllQueryBuilder();
      }

      for (final NamedQueryBuilder nfb : nfbs) {
        qb = or(qb, and(nfb.qb, recurQb, nfb.name));
      }
    }

    // Always exclude tombstoned here

    qb = termFilter(qb, PropertyInfoIndex.TOMBSTONED,
                    "false");

    if (delState == includeDeleted) {
      return qb;
    }

    return and(qb,
               addTerm(PropertyInfoIndex.DELETED,
                       String.valueOf(delState == onlyDeleted)),
               null);

  }
  
  final QueryBuilder overridesOnly(final String uid) {
    QueryBuilder qb = addTerm(PropertyInfoIndex.UID, uid);
    return and(qb, addTerm(PropertyInfoIndex.OVERRIDE, "true"), null);
  }
  
  final QueryBuilder recurTerms() throws CalFacadeException {
    /* If the search is for expanded events we want instances or
       overrides or non-recurring masters only.

       For non-expanded, if it's filtered we don't limit -
       the filter needs to check the instances and maybe the overrides.
       However, we will return only the hrefs and do a secondary
       retrieval of the events.

       For non-expanded and non-filtered we want the master and
       overrides only.
     */

    if (recurRetrieval.mode == Rmode.expanded) {
      // Limit events to instances only //
      QueryBuilder limit =
              addTerm(PropertyInfoIndex.INSTANCE, "true");
      limit = or(limit, addTerm(PropertyInfoIndex.OVERRIDE, "true"));

      limit = or(limit,
                 and(addTerm(PropertyInfoIndex.MASTER, "true"),
                     addTerm(PropertyInfoIndex.RECURRING, "false"),
                     null));

      return limit;
    }

    if ((recurRetrieval.mode == Rmode.entityOnly) &&
            docType.equals(docTypeEvent)) {
      return addTerm(PropertyInfoIndex.MASTER, "true");
    }

    /* if the query is not filtered we can limit to the master and
       overrides only
     */

    if (queryFiltered) {
      return null;
    }

    QueryBuilder limit = addTerm(PropertyInfoIndex.MASTER, "true");

    limit = or(limit, addTerm(PropertyInfoIndex.OVERRIDE, "true"));

//    queryFiltered = false; // Reset it.

    return limit;
  }
  
  public QueryBuilder getAllForReindex(final String docType) {
    QueryBuilder limit;
    if (docType.equals(docTypeEvent)) {
      return addTerm(PropertyInfoIndex.MASTER, "true");
    }

    return matchAllQuery();
  }

  public QueryBuilder getAllForReindexStats() {
    if (docType.equals(docTypeEvent)) {
      return or(addTerm(PropertyInfoIndex.MASTER, "true"),
                addTerm(PropertyInfoIndex.OVERRIDE, "true"));
    }

    return matchAllQuery();
  }

  /**
   * 
   * @return non null if we have a start date/time limitation
   */
  public String getLatestStart() {
    return latestStart;
  }

  /**
   *
   * @return non null if we have an end date/time limitation
   */
  public String getEarliestEnd() {
    return earliestEnd;
  }

  /** If we have a filtered query the search result will only contain
   * the master and/or overrides that match the query.
   *
   * <p> If we are not returning expansions we need to return the
   * entire event - master and all overrides. FOr non-expanded we
   * have to do a fetch of the entire event so we don't want parts of
   * it returned for the search. Instead we'll just ask for the matching
   * hrefs and then do a fetch for those.
   *</p>
   *
   * @return true if we are not fetching expanded and we have a filtered
   *          query which examines the instances. In this case we
   *          fetch only the href and do a secondary fetch for master
   *          and overrides.
   */
  public boolean requiresSecondaryFetch() {
    return (recurRetrieval.mode != Rmode.expanded) &&
            queryFiltered;
  }

  /**
   *
   * @return true if we are fetching expanded.
   */
  public boolean canPage() {
    return recurRetrieval.mode == Rmode.expanded;
  }

  /** Add date range terms to query. The actual terms depend on
   * how we want recurring events returned - expanded or as master
   * and overrides.
   *
   * <p>For expanded we just search on the actual dtstart and
   * end. This will find all the entities that fall in the desired
   * range</p>
   *
   * <p>For master + overrides we need to return any override that
   * overrides an instance in the range but itself is outside the
   * range. For these we search on the index start/end</p>
   *
   * @param query to add to
   * @param entityType to limit
   * @param start of range
   * @param end of range
   * @return the query or a query with anded date range
   * @throws CalFacadeException on error
   */
  public QueryBuilder addDateRangeQuery(final QueryBuilder query,
                                          final int entityType,
                                          final String start,
                                          final String end) throws CalFacadeException {
    if ((start == null) && (end == null)) {
      return query;
    }

    queryFiltered = true;
    
    final String startRef;
    final String endRef;

    if (entityType == IcalDefs.entityTypeAlarm) {
      return and(query, range(nextTriggerRef, start, end), null);
    }      
     
    if (recurRetrieval.mode == Rmode.expanded) {
      startRef = dtStartUTCRef;
      endRef = dtEndUTCRef;
    } else {
      startRef = indexStartUTCRef;
      endRef = indexEndUTCRef;
    }

    QueryBuilder qb = query;

    if (end != null) {
      // Start of events must be before the end of the range
      final RangeQueryBuilder rqb = new RangeQueryBuilder(startRef);

      rqb.lt(dateTimeUTC(end));

      qb = and(qb, rqb, null);
    }

    if (start != null) {
      // End of events must be before the end of the range
      final RangeQueryBuilder rqb = new RangeQueryBuilder(endRef);

      rqb.gte(dateTimeUTC(start));
      rqb.includeLower(false);

      qb = and(qb, rqb, null);
    }
    
    adjustTimeLimits(start, end);

    return qb;
  }

  /** Add a filter for the given href.
   *
   * @param q - or null
   * @param href to match
   * @return a filter
   * @throws CalFacadeException on fatal error
   */
  public QueryBuilder hrefFilter(final QueryBuilder q,
                                  final String href) throws CalFacadeException {
    return and(q,
               termQuery(hrefJname, href),
               null);
  }

  /** Add a query for the collection path.
   *
   * @param q - or null
   * @param colPath to match
   * @return a filter
   * @throws CalFacadeException on error
   */
  public QueryBuilder colPathFilter(final QueryBuilder q,
                                     final String colPath) throws CalFacadeException {
    return and(q,
               termQuery(colpathJname, colPath),
               null);
  }

  /** Add a query for the term.
   *
   * @param q - or null
   * @param pi of field
   * @param val to match
   * @return a filter
   * @throws CalFacadeException on error
   */
  public QueryBuilder termFilter(final QueryBuilder q,
                                  final PropertyInfoIndex pi,
                                  final String val)
          throws CalFacadeException {
    return and(q,
               termQuery(getJname(pi), val),
               null);
  }

  /** Add query for the current principal - or public - to limit search
   * to entities owned by the current principal.
   *
   * @param q - or null
   * @return a filter
   * @throws CalFacadeException on error
   */
  public QueryBuilder principalQuery(final QueryBuilder q) throws CalFacadeException {
    final boolean publicEvents = publick ||
            (currentMode == guestMode) ||
            (currentMode == publicAdminMode);

    //boolean all = (currentMode == guestMode) || ignoreCreator;

    if (publicEvents) {
      return and(q,
                 termQuery(publicJname, String.valueOf(true)),
                 null);
    }

    if (superUser) {
      return q;
    }

    return and(q, termQuery(ownerJname, ownerHref),
               null);
  }

  /**
   *
   * @param pi property info index
   * @param val value
   * @return TermQuery
   */
  public QueryBuilder addTerm(final PropertyInfoIndex pi,
                               final String val) {
    if ((pi != PropertyInfoIndex.HREF) &&
            (pi != PropertyInfoIndex.COLLECTION)) {
      queryFiltered = true;
    }
    return termQuery(getJname(pi), val);
  }

  /**
   *
   * @param pis the refs
   * @return dot delimited property reference
   */
  public static String makePropertyRef(final List<PropertyInfoIndex> pis,
                                       final String lastElJname) {
    return makePropertyRef(pis, pis.size() - 1, lastElJname);
  }

  /**
   *
   * @param pis the refs
   * @return dot delimited property reference
   */
  public static String makePropertyRef(final List<PropertyInfoIndex> pis,
                                       final int numElements,
                                       final String lastElJname) {
    String delim = "";
    final int last = numElements - 1;

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i <= numElements; i++) {
      sb.append(delim);
      delim = ".";

      if ((i < last) || (lastElJname == null)) {
        final PropertyInfoIndex pi = pis.get(i);
        sb.append(getJname(pi));
      } else {
        sb.append(lastElJname);
      }
    }

    return sb.toString();
  }

  /**
   *
   * @param pis list of indexes
   * @return dot delimited property reference
   */
  public static String makePropertyRef(final PropertyInfoIndex... pis) {
    String delim = "";

    final StringBuilder sb = new StringBuilder();

    for (final PropertyInfoIndex pi: pis) {
      sb.append(delim);
      sb.append(getJname(pi));
      delim = ".";
    }

    return sb.toString();
  }

  private QueryBuilder makeQuery(final PropertyInfoIndex pii,
                                 final String path,
                                 final String fullTermPath,
                                 final Object val,
                                 final OperationType opType,
                                 final float boost,
                                 final boolean negate) throws CalFacadeException {
    final BwIcalPropertyInfoEntry bwPie = BwIcalPropertyInfo.getPinfo(pii);
    QueryBuilder qb;

    switch (opType) {
      case compare:
        if (bwPie.getAnalyzed()) {
          if (val instanceof Collection) {
            final String[] vals;

            try {
              final Collection valsC = (Collection)val;
              vals = (String[]) (valsC).toArray(new String[valsC.size()]);
            } catch (final Throwable t) {
              throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                           "Invalid query. Multi match only allowed on strings");
            }
            qb = new MultiMatchQueryBuilder(path, vals);
            break;
          }

          if (bwPie.getTermsField() == null) {
            qb = new MatchQueryBuilder(path, val).operator(
                    Operator.AND);
            break;
          }

          /* Build something like this
              "query" : {
                "function_score" : {
                  "query" : {
                    "match" : {
                      "loc_all" : {
                        "query" : "hill",
                        "operator" : "AND"
                      }
                    }
                  },
                  "functions" : [{
                    "weight": 10,
                    "filter": {
                      "term" : {
                        "loc_all_terms" : "hill"
                      }
                    }
                  }]
                }
              },...
           */
          qb = new MatchQueryBuilder(path, val).operator(
                  Operator.AND);
          if (boost != 1) {
            qb.boost(boost);
          }
          break;
        }

        if(val instanceof Collection) {
          qb = new TermsQueryBuilder(path, (Collection)val);
        } else {
          qb = new TermQueryBuilder(path, val);
        }
        if (boost != 1) {
          qb.boost(boost);
        }
        break;

      case prefix:
        qb = QueryBuilders.prefixQuery(path, (String)val);
        break;

      case timeRange:
        final RangeQueryBuilder rqb = QueryBuilders.rangeQuery(path);
        qb = rqb;

        final TimeRange tr = (TimeRange)val;
        if (tr.getEnd() == null) {
          rqb.gte(tr.getStart().toString());

          break;
        }

        if (tr.getStart() == null) {
          rqb.lt(tr.getEnd().toString());

          break;
        }

        rqb.from(tr.getStart().toString());
        rqb.to(tr.getEnd().toString());
        rqb.includeLower(true);
        rqb.includeUpper(false);

        break;

      case absence:
        qb = not(QueryBuilders.existsQuery(path));
        break;

      case presence:
        qb = QueryBuilders.existsQuery(path);
        break;

      default:
        throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                     opType.toString());
    }

    if (negate) {
      qb = not(qb);
    }

    return qb;
  }

  private QueryBuilder not(final QueryBuilder q) {
    return new NotQB(q);
  }

  private QueryBuilder and(final QueryBuilder q,
                            final QueryBuilder newQ,
                            final String name) {
    if (q == null) {
      return newQ;
    }

    if (q instanceof AndQB) {
      ((AndQB)q).add(newQ);

      return q;
    }

    final AndQB afb = new AndQB(q);

    afb.add(newQ);
    
    if (name != null) {
      afb.queryName(name);
    }

    return afb;
  }

  private QueryBuilder or(final QueryBuilder q,
                           final QueryBuilder newQ) {
    if (q == null) {
      return newQ;
    }

    if (q instanceof OrQB) {
      ((OrQB)q).add(newQ);

      return q;
    }

    OrQB ofb = new OrQB(q);

    ofb.add(newQ);

    return ofb;
  }

  static String getJname(final PropertyInfoIndex pi) {
    final BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

    if (ipie == null) {
      return null;
    }

    return ipie.getJname();
  }

  private String dateTimeUTC(final String dt) {
    if (dt == null) {
      return null;
    }
    
    if (dt.length() == 16) {
      return dt;
    }

    if (dt.length() == 15) {
      return dt + "Z";
    }

    if (dt.length() == 8) {
      return dt + "T000000Z";
    }

    return dt; // It's probably a bad date
  }
  
  private QueryBuilder range(final String fld,
                             final String start,
                             final String end) {
    if ((start == null) && (end == null)) {
      return null;
    }
    
    final RangeQueryBuilder rfb = rangeQuery(fld);
    
    if (end == null) {
      rfb.gte(start);
    } else if (start == null) {
      rfb.lt(end);
    } else {
      rfb.from(start);
      rfb.to(end);
    }

    rfb.includeLower(true);
    rfb.includeUpper(false);

    adjustTimeLimits(start, end);

    return rfb;
  }
  
  private void adjustTimeLimits(final String start, final String end) {
    final String s = dateTimeUTC(start);
    final String e = dateTimeUTC(end);
    
    if (s != null) {
      if ((latestStart == null) ||
              (s.compareTo(latestStart) > 0)) {
        latestStart = s;
      }
    }

    if (e != null) {
      if ((earliestEnd == null) ||
              (e.compareTo(earliestEnd) < 0)) {
        earliestEnd = e;
      }
    }
  }

  /*
  private FilterBuilder makeFilter(final TermOrTermsQuery tort) throws CalFacadeException {
    final boolean hrefOrPath = tort.fldName.equals(hrefJname) ||
            tort.fldName.equals(colpathJname);
    
    queryFiltered |= !hrefOrPath;

    if (!tort.not) {
      queryLimited |= hrefOrPath;
    }
    
    return tort.makeFb();
  }
  */

  private QueryBuilder makeQuery(final FilterBase f) throws CalFacadeException {
    if (f == null) {
      return null;
    }

    if (f instanceof BooleanFilter) {
      final BooleanFilter bf = (BooleanFilter)f;
      if (!bf.getValue()) {
        return new MatchNoneQueryBuilder();
      } else {
        return new MatchAllQueryBuilder();
      }
    }

    if (f instanceof AndFilter) {
      final List<QueryBuilder> qbs = makeQueries(f.getChildren(), true);

      if (qbs.size() == 1) {
        return qbs.get(0);
      }

      final BoolQueryBuilder bqb = new BoolQueryBuilder();

      for (final QueryBuilder qb: qbs) {
        if (qb instanceof TermOrTermsQuery) {
          bqb.must(((TermOrTermsQuery)qb).makeQb());
        } else {
          bqb.must(qb);
        }
      }

      return bqb;
    }

    if (f instanceof OrFilter) {
      final List<QueryBuilder> qbs = makeQueries(f.getChildren(), false);

      if (qbs.size() == 1) {
        return qbs.get(0);
      }

      final BoolQueryBuilder bqb = new BoolQueryBuilder().minimumShouldMatch("1");

      for (final QueryBuilder qb: qbs) {
        if (qb instanceof TermOrTermsQuery) {
          bqb.should(((TermOrTermsQuery)qb).makeQb());
        } else {
          bqb.should(qb);
        }
      }

      return bqb;
    }

    if (f instanceof BwViewFilter) {
      return makeQuery(((BwViewFilter)f).getFilter());
    }

    if (f instanceof BwHrefFilter) {
      queryLimited = true;

      return termQuery(hrefJname,
                                     ((BwHrefFilter)f).getHref());
    }

    if (!(f instanceof PropertyFilter)) {
      return null;
    }

    final PropertyFilter pf = (PropertyFilter)f;

    if (pf instanceof EntityTypeFilter) {
      final EntityTypeFilter etf = (EntityTypeFilter)pf;

      return new MatchAllQueryBuilder();
      //return new TermOrTermsQuery("_type",
      //                            etf.getEntity(),
      //                            pf.getNot());
    }

    if (f instanceof PresenceFilter) {
      final PresenceFilter prf = (PresenceFilter)f;

      if (prf.getTestPresent()) {
        return makeQuery(pf.getPropertyIndex(),
                         makePropertyRef(pf.getPropertyIndexes(), null),
                         null,
                         null, OperationType.presence, 1, false);
      }

      return makeQuery(pf.getPropertyIndex(),
                       makePropertyRef(pf.getPropertyIndexes(), null),
                       null,
                       null, OperationType.absence, 1, false);
    }

    if (pf instanceof TimeRangeFilter) {
      return makeQuery(pf.getPropertyIndex(),
                       makePropertyRef(pf.getPropertyIndexes(), null),
                       null,
                       ((TimeRangeFilter)pf).getEntity(),
                       OperationType.timeRange,
                       1, pf.getNot());
    }

    if (pf instanceof BwCollectionFilter) {
      if (!pf.getNot()) {
        queryLimited = true;
      }

      final String path = ((BwCollectionFilter)pf).getPath();

      return new TermOrTermsQuery(colpathJname,
                             path,
                             pf.getNot(),
                             f.getName());
    }

    if (pf instanceof EntityTimeRangeFilter) {
      final EntityTimeRangeFilter etrf = (EntityTimeRangeFilter)pf;

      final TimeRange tr = etrf.getEntity();
      String start = null;
      String end = null;

      if (tr.getStart() != null) {
        start = tr.getStart().toString();
      }

      if (tr.getEnd() != null) {
        end = tr.getEnd().toString();
      }

      return addDateRangeQuery(null, etrf.getEntityType(),
                               start, end);
    }

    if (pf.getPropertyIndex() == PropertyInfoIndex.COLLECTION) {
      if (!pf.getNot()) {
        queryLimited = true;
      }
    }

    if (pf instanceof ObjectFilter) {
      final BwIcalPropertyInfoEntry bwPie = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

      final String fullTermsPath;
      if (bwPie.getTermsField() == null) {
        fullTermsPath = null;
      } else {
        fullTermsPath = makePropertyRef(pf.getPropertyIndexes(),
                                        bwPie.getTermsField());
      }
      return makeQuery(pf.getPropertyIndex(),
                       makePropertyRef(pf.getPropertyIndexes(), null),
                       fullTermsPath,
                       getValue((ObjectFilter)pf),
                       OperationType.compare,
                       1, pf.getNot());
    }

    return null;
  }

  private List<QueryBuilder> makeQueries(final List<FilterBase> fs,
                                         final boolean anding) throws CalFacadeException {
    final List<QueryBuilder> qbs = new ArrayList<>();

        /* We'll try to compact the queries - if we have a whole bunch of
          "term" {"category_uid": "abcd"} for example we can turn it into
          "terms" {"category_uid": ["abcd", "pqrs"]}
        */
    TermOrTermsQuery lastQb = null;

    for (final FilterBase f: fs) {
      final QueryBuilder qb = makeQuery(f);

      if (qb == null) {
        continue;
      }

      if (lastQb == null) {
        if (!(qb instanceof TermOrTermsQuery)) {
          qbs.add(qb);
          continue;
        }

        lastQb = (TermOrTermsQuery) qb;
      } else if (!(qb instanceof TermOrTermsQuery)) {
        qbs.add(qb);
      } else {
                /* Can we combine them? */
        final TermOrTermsQuery thisQb = (TermOrTermsQuery)qb;

        if (thisQb.dontMerge ||
                !lastQb.fldName.equals(thisQb.fldName) ||
                (lastQb.not != thisQb.not)) {
          qbs.add(lastQb);
          lastQb = thisQb;
        } else {
          lastQb = lastQb.anding(anding);

          if (thisQb.isTerms) {
            for (final Object o: (Collection)thisQb.value) {
              lastQb.addValue(o);
            }
          } else {
            lastQb.addValue(thisQb.value);
          }
        }
      }
    }

    if (lastQb != null) {
      qbs.add(lastQb);
    }

    return qbs;
  }

  private Object getValue(final ObjectFilter of) throws CalFacadeException {
    Object o = of.getEntity();
    Collection c = null;

    boolean isString = o instanceof String;

    if (!isString) {
      if (o instanceof Collection) {
        c = (Collection)o;
        if (c.size() > 0) {
          o = c.iterator().next();
          isString = o instanceof String;
        }
      }
    }

    boolean doCaseless = isString && of.getCaseless();

    if (c != null) {
      // TODO - Assuming String collection
      List res = new ArrayList();

      for (Object co: c) {
        String s = (String)co;
        if (doCaseless) {
          s = s.toLowerCase();
        }

        res.add(s);
      }

      return res;
    }

    if (o instanceof BwCalendar) {
      BwCalendar cal = (BwCalendar)o;
      return cal.getPath();
    }

    if (o instanceof BwPrincipal) {
      return ((BwPrincipal)o).getPrincipalRef();
    }

    if (o instanceof BwDbentity) {
      warn("Got db entity in search " + o);
      return "";
    }

    if (of.getExact()) {
      if (doCaseless) {
        o = ((String)o).toLowerCase();
      }

      return o;
    }

    if (of.getEntity() instanceof String) {
      String s = o.toString();

      if (doCaseless) {
        s = s.toLowerCase();
      }

      //warn("Fuzzy search for " + of);
      return s;
      //sess.setString(parPrefix + qi, "%" + s + "%");
    }

    //sess.setString(parPrefix + qi, "%" + o + "%");

    warn("Fuzzy search for " + of);
    return "";
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
