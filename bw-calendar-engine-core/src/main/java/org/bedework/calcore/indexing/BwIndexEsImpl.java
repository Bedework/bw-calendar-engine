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
package org.bedework.calcore.indexing;

import org.bedework.access.Acl;
import org.bedework.calcore.indexing.DocBuilder.ItemKind;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwDuration;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SortTerm;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.indexing.IndexException;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.DateTimeUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.fortuna.ical4j.model.Period;
import org.apache.log4j.Logger;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequestBuilder;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.alias.get.IndicesGetAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.get.IndicesGetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.status.IndicesStatusRequestBuilder;
import org.elasticsearch.action.admin.indices.status.IndicesStatusResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.deletebyquery.IndexDeleteByQueryResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.calcore.indexing.DocBuilder.DocInfo;

/** Implementation of indexer for ElasticSearch
 *
 * <p>Indexing events is complicated by the need to support CalDAV
 * time-range queries and recurring events. A recurring event will have
 * a MASTER event, INSTANCES and OVERRIDES</p>
 *
 * <p>A timerange query must return the MASTER, and any overrides that
 * fall within the date range - or WOULD HAVE if they weren't overridden</p>
 *
 * <p>So it's possible none of the returned components actually lie in
 * the requested range. However we need to index them so that they are
 * found.</p>
 *
 * <p>First we index a full copy of all event instances expanded. We
 * need a full copy so that filtering can work.</p>
 *
 * <p>We index the MASTER and set the start and end to cover the
 * entire time range. It will always appear</p>
 *
 * <p>OVERRIDEs appear as an instance and as an override. The instance
 * will have the new overridden date/time the override will have the
 * overridden date time.</p>
 *
 * <p>All searching is done on UTC values. We need 2 sets of values -
 * One represents the real dtstart/dtend for the entity, The other
 * is the indexed start/end as outlined above.</p>
 *
 * <p>As well as indexing events/tasks etc we also have to index alarms
 * so that the associated events can be located. This is an alarm object
 * inside an entity with a start date</p>
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class BwIndexEsImpl implements BwIndexer {
  private transient Logger log;

  private boolean debug;

  private int batchMaxSize = 0;
  private int batchCurSize = 0;

  private Object batchLock = new Object();

  private ObjectMapper om;

  private boolean publick;
  private BwPrincipal principal;
  private final boolean superUser;

  private String host;
  private int port = 9300;

  private static Node theNode; /* For embedded use */
  private static Client theClient;
  private static volatile Object clientSyncher = new Object();

  private String targetIndex;
  private int currentMode;

  private AuthProperties authpars;
  private AuthProperties unauthpars;
  private IndexProperties idxpars;
  private BasicSystemProperties basicSysprops;

  /*
  private static Set<String> eventDoctypes;

  static {
    eventDoctypes = new TreeSet<>(IcalDefs.entityTypes);

    for (String s: masterDocTypes) {
      if (s != null) {
        eventDoctypes.add(s);
      }
    }

    for (String s: overrideDocTypes) {
      if (s != null) {
        eventDoctypes.add(s);
      }
    }
  }*/

  /** Constructor
   *
   * @param configs
   * @param publick - if false we add an owner term to the searches
   * @param principal - who we are searching for - only for non-public
   * @param superUser - true if the principal is a superuser.
   * @param currentMode - guest, user,publicAdmin
   * @param indexName - explicitly specified
   * @throws CalFacadeException
   */
  public BwIndexEsImpl(final Configurations configs,
                       final boolean publick,
                       final BwPrincipal principal,
                       final boolean superUser,
                       final int currentMode,
                       final String indexName) throws CalFacadeException {
    debug = getLog().isDebugEnabled();

    this.publick = publick;
    this.principal = principal;
    this.superUser = superUser;
    this.currentMode = currentMode;

    idxpars = configs.getIndexProperties();
    authpars = configs.getAuthProperties(true);
    unauthpars = configs.getAuthProperties(false);
    basicSysprops = configs.getBasicSystemProperties();

    String url = idxpars.getIndexerURL();

    if (url == null) {
      host = "localhost";
    } else {
      int pos = url.indexOf(":");

      if (pos < 0) {
        host = url;
      } else {
        host = url.substring(0, pos);
        if (pos < url.length()) {
          port = Integer.valueOf(url.substring(pos + 1));
        }
      }
    }

    om = new ObjectMapper();

    /* Don't use dates in json - still issues with timezones ironically */
    //DateFormat df = new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'");

    //om.setDateFormat(df);

    om.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    if (indexName == null) {
      if (publick) {
        targetIndex = idxpars.getPublicIndexName();
      } else {
        targetIndex = idxpars.getUserIndexName();
      }
      targetIndex = Util.buildPath(false, targetIndex);
    } else {
      targetIndex = Util.buildPath(false, indexName);
    }
  }

  @Override
  public void setBatchSize(final int val) {
    batchMaxSize = val;
    batchCurSize = 0;

    /* XXX later
    if (batchMaxSize > 1) {
      batch = new XmlEmit();
    }
    */
  }

  @Override
  public void endBwBatch() throws CalFacadeException {
  }

  @Override
  public void flush() throws CalFacadeException {
  }

  private class EsSearchResult implements SearchResult {
    private BwIndexer indexer;

    private long found;

    private int pageStart;
    private int pageSize;

    private boolean requiresSecondaryFetch;

    private boolean canPage;

    /* For paged queries - we need these values */
    private String start;
    private String end;
    private QueryBuilder curQuery;
    private FilterBuilder curFilter;
    private List<SortTerm> curSort;

    private AccessChecker accessCheck;

    private int lastPageStart;

    EsSearchResult(BwIndexer indexer) {
      this.indexer = indexer;
    }

    private void setFound(final long found) {
      this.found = found;
    }

    @Override
    public BwIndexer getIndexer() {
      return indexer;
    }

    @Override
    public long getFound() {
      return found;
    }

    @Override
    public int getPageStart() {
      return pageStart;
    }

    @Override
    public int getPageSize() {
      return pageSize;
    }

    @Override
    public String getStart() {
      return start;
    }

    @Override
    public String getEnd() {
      return end;
    }

    @Override
    public Set<String> getFacetNames() {
      return null;
    }
  }

  @Override
  public SearchResult search(final String query,
                             final FilterBase filter,
                             final List<SortTerm> sort,
                             final String start,
                             final String end,
                             final int pageSize,
                             final AccessChecker accessCheck,
                             final RecurringRetrievalMode recurRetrieval) throws CalFacadeException {
    EsSearchResult res = new EsSearchResult(this);

    res.start = start;
    res.end = end;
    res.pageSize = pageSize;
    res.accessCheck = accessCheck;

    if (query != null) {
      res.curQuery = QueryBuilders.queryString(query);
    }

    ESQueryFilter ef = getFilters(recurRetrieval);

    res.curFilter = ef.buildFilter(filter);

    res.curFilter = ef.addDateRangeFilter(res.curFilter,
                                          start,
                                          end);

    res.curFilter = ef.addLimits(res.curFilter);

    res.requiresSecondaryFetch = ef.requiresSecondaryFetch();
    res.canPage = ef.canPage();

    res.curSort = sort;

    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);
    if (res.curQuery != null) {
      srb.setQuery(res.curQuery);
    }

    srb.setSearchType(SearchType.COUNT)
            .setFilter(res.curFilter)
            .setFrom(0)
            .setSize(0);

    if (!Util.isEmpty(res.curSort)) {
      SortOrder so;

      for (SortTerm st: res.curSort) {
        if (st.isAscending()) {
          so = SortOrder.ASC;
        } else {
          so = SortOrder.DESC;
        }

        srb.addSort(new FieldSortBuilder(
                ESQueryFilter.makePropertyRef(st.getProperties()))
                            .order(so));
      }
    }

    if (debug) {
      debug("Search: targetIndex=" + targetIndex +
                    "; srb=" + srb);
    }

    SearchResponse resp = srb.execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      //TODO
    }

    if (debug) {
      debug("Search: returned status " + resp.status() +
                    " found: " + resp.getHits().getTotalHits());
    }

    res.setFound(resp.getHits().getTotalHits());

    return res;
  }

  @Override
  public List<SearchResultEntry> getSearchResult(final SearchResult sres,
                                                 final Position pos,
                                                 final int desiredAccess)
          throws CalFacadeException {
    EsSearchResult res = (EsSearchResult)sres;

    int offset;

    if (pos == Position.next) {
      offset = sres.getPageStart();
    } else if (pos == Position.previous) {
      // TODO - this is wrong - need to save offsets as we progress.
      offset = Math.max(0,
                        res.lastPageStart - sres.getPageSize());
    } else {
      offset = res.lastPageStart;
    }

    res.lastPageStart = offset;

    return getSearchResult(sres, offset, sres.getPageSize(),
                           desiredAccess);
  }

  @Override
  public List<SearchResultEntry> getSearchResult(final SearchResult sres,
                                                 final int offset,
                                                 final int num,
                                                 final int desiredAccess)
          throws CalFacadeException {
    EsSearchResult res = (EsSearchResult)sres;

    res.pageStart = offset;

    List<SearchResultEntry> entities;
    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);
    if (res.curQuery != null) {
      srb.setQuery(res.curQuery);
    }

    srb.setSearchType(SearchType.QUERY_THEN_FETCH)
            .setFilter(res.curFilter)
            .setFrom(res.pageStart);

    if (num < 0) {
      srb.setSize((int)sres.getFound());
      entities = new ArrayList<>((int)sres.getFound());
    } else {
      srb.setSize(num);
      entities = new ArrayList<>(num);
    }

    if (!Util.isEmpty(res.curSort)) {
      SortOrder so;

      for (SortTerm st: res.curSort) {
        if (st.isAscending()) {
          so = SortOrder.ASC;
        } else {
          so = SortOrder.DESC;
        }

        srb.addSort(new FieldSortBuilder(
                ESQueryFilter.makePropertyRef(st.getProperties()))
                            .order(so));
      }
    }

    if (res.requiresSecondaryFetch) {
      // Limit to href then fetch those
      srb.addField(ESQueryFilter.hrefJname);
    }

    SearchResponse resp = srb.execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + resp.status());
      }
    }

    SearchHits hits = resp.getHits();

    if ((hits.getHits() == null) ||
            (hits.getHits().length == 0)) {
      return entities;
    }

    //Break condition: No hits are returned
    if (hits.hits().length == 0) {
      return entities;
    }

    if (res.requiresSecondaryFetch) {
      hits = multiFetch(hits);

      if (hits == null) {
        return entities;
      }
    }

    Map<String, Collection<BwEventAnnotation>> overrides = new HashMap<>();
    Collection<EventInfo> masters = new TreeSet<>();

    for (SearchHit hit : hits) {
      res.pageStart++;
      String dtype = hit.getType();

      if (dtype == null) {
        throw new CalFacadeException("org.bedework.index.noitemtype");
      }

      String kval = hit.getId();

      if (kval == null) {
        throw new CalFacadeException("org.bedework.index.noitemkey");
      }

      EntityBuilder eb = getEntityBuilder(hit.sourceAsMap());

      Object entity = null;
      if (dtype.equals(docTypeCollection)) {
        entity = eb.makeCollection();
      } else if (dtype.equals(docTypeCategory)) {
        entity = eb.makeCat();
      } else if (dtype.equals(docTypeContact)) {
        entity = eb.makeContact();
      } else if (dtype.equals(docTypeLocation)) {
        entity = eb.makeLocation();
      } else if (dtype.equals(docTypeEvent)) {
        entity = eb.makeEvent();
        EventInfo ei = (EventInfo)entity;
        BwEvent ev = ei.getEvent();

        Acl.CurrentAccess ca = res.accessCheck.checkAccess(ev,
                                                           desiredAccess,
                                                           true);

        if ((ca == null) || !ca.getAccessAllowed()) {
          continue;
        }

        ei.setCurrentAccess(ca);

        if (ev instanceof BwEventAnnotation) {
          // Treat as override
          Collection<BwEventAnnotation> ov = overrides.get(ev.getHref());

          if (ov == null) {
            ov = new TreeSet<>();

            overrides.put(ev.getHref(), ov);
          }

          ov.add((BwEventAnnotation)ev);
          continue;
        }

        masters.add(ei);
      }

      entities.add(new SearchResultEntry(entity,
                                         dtype,
                                         hit.getScore()));
    }

    // Finish off the events

    for (EventInfo ei: masters) {
      BwEvent ev = ei.getEvent();

      if (ev.getRecurring()) {
        Collection<BwEventAnnotation> ov = overrides.get(ev.getHref());

        if (ov != null) {
          for (BwEventAnnotation ann: ov) {
            BwEvent proxy = new BwEventProxy(ann);
            ann.setTarget(ev);
            ann.setMaster(ev);

            EventInfo oei = new EventInfo(proxy);

            ei.addOverride(oei);
          }
        }
      }
    }

    return entities;
  }

  private SearchHits multiFetch(SearchHits hits) throws CalFacadeException {
    // Make an ored filter from keys

    List<String> hrefs = new ArrayList<>();

    for (SearchHit hit : hits) {
      String dtype = hit.getType();

      if (dtype == null) {
        throw new CalFacadeException("org.bedework.index.noitemtype");
      }

      String kval = hit.getId();

      if (kval == null) {
        throw new CalFacadeException("org.bedework.index.noitemkey");
      }

      SearchHitField hrefField = hit.field(ESQueryFilter.hrefJname);


      hrefs.add((String)hrefField.getValue());
    }

    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    srb.setSearchType(SearchType.QUERY_THEN_FETCH)
            .setFilter(getFilters(null).multiHrefFilter(hrefs));
    SearchResponse resp = srb.execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + resp.status());
      }

      return null;
    }

    SearchHits hits2 = resp.getHits();

    if ((hits2.getHits() == null) ||
            (hits2.getHits().length == 0)) {
      return null;
    }

    //Break condition: No hits are returned
    if (hits2.hits().length == 0) {
      return null;
    }

    return hits2;
  }

  @Override
  public void indexEntity(final Object rec) throws CalFacadeException {
    try {
      /* XXX later with batch
      XmlEmit xml;

      if (batchMaxSize > 0) {
        synchronized (batchLock) {

          if (batchCurSize == 0) {
            batch = new XmlEmit();
            xmlWtr = new StringWriter();
            batch.startEmit(xmlWtr);

            batch.openTag(solrTagAdd);
          }

          index(batch, rec);

          if (batchMaxSize == batchCurSize) {
            batch.closeTag(solrTagAdd);
            indexAndCommit(xmlWtr.toString());
            batchCurSize = 0;
          }
        }

        return;
      }
      */

      // Unbatched

      IndexResponse resp = index(rec);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public void unindexEntity(BwEventProperty val) throws CalFacadeException {
    unindexEntity(getDocBuilder().getHref(val));
  }

  @Override
  public void unindexEntity(final String href) throws CalFacadeException {
    try {
      DeleteByQueryRequestBuilder dqrb = getClient().prepareDeleteByQuery(
              targetIndex);

      dqrb.setQuery(QueryBuilders.termQuery(ESQueryFilter.hrefJname, href));

      DeleteByQueryResponse resp = dqrb.execute().actionGet();

      // TODO check response?
    } catch (ElasticSearchException ese) {
      // Failed somehow
      error(ese);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    }
  }

  @Override
  public String newIndex(final String name) throws CalFacadeException {
    CreateIndexResponse resp = null;
    try {
      String newName = name + newIndexSuffix();
      targetIndex = newName;

      IndicesAdminClient idx = getAdminIdx();

      CreateIndexRequestBuilder cirb = idx.prepareCreate(newName);

      File f = new File(idxpars.getIndexerConfig());

      byte[] sbBytes = Streams.copyToByteArray(f);

      cirb.setSource(sbBytes);

      CreateIndexRequest cir = cirb.request();

      ActionFuture<CreateIndexResponse> af = idx.create(cir);

      resp = af.actionGet();

      return newName;
    } catch (ElasticSearchException ese) {
      // Failed somehow
      error(ese);
      return null;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    }
  }

  @Override
  public Set<IndexInfo> getIndexInfo() throws CalFacadeException {
    Set<IndexInfo> res = new TreeSet<>();

    try {
      IndicesAdminClient idx = getAdminIdx();

      IndicesStatusRequestBuilder isrb = idx.prepareStatus(Strings.EMPTY_ARRAY);

      ActionFuture<IndicesStatusResponse> sr = idx.status(
              isrb.request());
      IndicesStatusResponse sresp  = sr.actionGet();

      for (String inm: sresp.getIndices().keySet()) {
        IndexInfo ii = new IndexInfo(inm);
        res.add(ii);

        ClusterStateRequest clusterStateRequest = Requests
                .clusterStateRequest()
                .filterRoutingTable(true)
                .filterNodes(true)
                .filteredIndices(inm);
        ii.setAliases(getAdminCluster().state(clusterStateRequest).
                actionGet().getState().getMetaData().aliases().keySet());
      }

      return res;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public List<String> purgeIndexes() throws CalFacadeException {
    Set<IndexInfo> indexes = getIndexInfo();
    List<String> purged = new ArrayList<>();

    if (Util.isEmpty(indexes)) {
      return purged;
    }

    purge:
    for (IndexInfo ii: indexes) {
      String idx = ii.getIndexName();

      if (!idx.startsWith(idxpars.getPublicIndexName()) &&
              !idx.startsWith(idxpars.getUserIndexName())) {
        continue;
      }

      /* Don't delete those pointed to by the current aliases */

      for (String alias: ii.getAliases()) {
        if (alias.equals(idxpars.getPublicIndexName())) {
          continue purge;
        }

        if (alias.equals(idxpars.getUserIndexName())) {
          continue purge;
        }
      }

      purged.add(idx);
    }

    deleteIndexes(purged);

    return purged;
  }

  private void deleteIndexes(final List<String> names) throws CalFacadeException {
    try {
      IndicesAdminClient idx = getAdminIdx();
      DeleteIndexRequestBuilder dirb = getAdminIdx().prepareDelete(
              names.toArray(new String[0]));

      ActionFuture<DeleteIndexResponse> dr = idx.delete(
              dirb.request());
      DeleteIndexResponse dir  = dr.actionGet();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public int swapIndex(final String index,
                       final String other) throws CalFacadeException {
    IndicesAliasesResponse resp = null;
    try {
      /* Other is the alias name - index is the index we were just indexing into
       */

      IndicesAdminClient idx = getAdminIdx();

      IndicesGetAliasesRequestBuilder igarb = idx.prepareGetAliases(
              other);

      ActionFuture<IndicesGetAliasesResponse> getAliasesAf = idx.getAliases(
              igarb.request());
      IndicesGetAliasesResponse garesp = getAliasesAf.actionGet();

      Map<String, List<AliasMetaData>> aliasesmeta = garesp.getAliases();

      IndicesAliasesRequestBuilder iarb = idx.prepareAliases();

      for (String indexName: aliasesmeta.keySet()) {
        for (AliasMetaData amd: aliasesmeta.get(indexName)) {
          if(amd.getAlias().equals(other)) {
            iarb.removeAlias(indexName, other);
          }
        }
      }

      iarb.addAlias(index, other);

      ActionFuture<IndicesAliasesResponse> af = idx.aliases(iarb.request());

      resp = af.actionGet();

      return 0;
    } catch (ElasticSearchException ese) {
      // Failed somehow
      error(ese);
      return -1;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public BwCategory fetchCat(final String val,
                             final PropertyInfoIndex... index)
          throws CalFacadeException {
    EntityBuilder eb = fetchEntity(docTypeCategory, val, index);

    if (eb == null) {
      return null;
    }

    return eb.makeCat();
  }

  @Override
  public BwContact fetchContact(final String val,
                                final PropertyInfoIndex... index)
          throws CalFacadeException {
    EntityBuilder eb = fetchEntity(docTypeContact, val, index);

    if (eb == null) {
      return null;
    }

    return eb.makeContact();
  }

  @Override
  public BwLocation fetchLocation(final String val,
                                  final PropertyInfoIndex... index)
          throws CalFacadeException {
    EntityBuilder eb = fetchEntity(docTypeLocation, val, index);

    if (eb == null) {
      return null;
    }

    return eb.makeLocation();
  }

  private static final int maxFetchCount = 100;
  private static final int absoluteMaxTries = 1000;

  @Override
  public List<BwCategory> fetchAllCats() throws CalFacadeException {
    return fetchAllEntities(docTypeCategory,
                            new BuildEntity<BwCategory>() {
                              @Override
                              BwCategory make(final EntityBuilder eb)
                                      throws CalFacadeException {
                                return eb.makeCat();
                              }
                            });
  }

  @Override
  public List<BwContact> fetchAllContacts() throws CalFacadeException {
    return fetchAllEntities(docTypeContact,
                            new BuildEntity<BwContact>() {
                              @Override
                              BwContact make(final EntityBuilder eb)
                                      throws CalFacadeException {
                                return eb.makeContact();
                              }
                            });
  }

  @Override
  public List<BwLocation> fetchAllLocations() throws CalFacadeException {
    return fetchAllEntities(docTypeLocation,
                            new BuildEntity<BwLocation>() {
                              @Override
                              BwLocation make(final EntityBuilder eb)
                                      throws CalFacadeException {
                                return eb.makeLocation();
                              }
                            });
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private static abstract class BuildEntity<T> {
    abstract T make(EntityBuilder eb) throws CalFacadeException;
  }

  private <T> List<T> fetchAllEntities(String docType,
                                       BuildEntity<T> be) throws CalFacadeException {
    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    srb.setTypes(docType);

    int tries = 0;
    int ourPos = 0;
    int ourCount = maxFetchCount;

    List<T> res = new ArrayList<>();

    SearchResponse scrollResp = srb.setSearchType(SearchType.SCAN)
            .setScroll(new TimeValue(60000))
            .setFilter(getFilters(null).principalFilter(null))
            .setSize(ourCount).execute().actionGet(); //ourCount hits per shard will be returned for each scroll

    if (scrollResp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + scrollResp.status());
      }
    }

    for (;;) {
      if (tries > absoluteMaxTries) {
        // huge count or we screwed up
        warn("Indexer: too many tries");
        break;
      }

      scrollResp = getClient().prepareSearchScroll(scrollResp.getScrollId())
              .setScroll(new TimeValue(600000)).execute().actionGet();
      if (scrollResp.status() != RestStatus.OK) {
        if (debug) {
          debug("Search returned status " + scrollResp.status());
        }
      }

      SearchHits hits = scrollResp.getHits();

      //Break condition: No hits are returned
      if (hits.hits().length == 0) {
        break;
      }

      for (SearchHit hit : hits) {
        //Handle the hit...
        T ent = be.make(getEntityBuilder(hit.sourceAsMap()));
        res.add(ent);
        ourPos++;
      }

      tries++;
    }

    return res;
  }

  private EntityBuilder fetchEntity(final String docType,
                                    final String val,
                                    final PropertyInfoIndex... index)
          throws CalFacadeException {
    if ((index.length == 1) &&
            (index[0] == PropertyInfoIndex.HREF)) {
      GetRequestBuilder grb = getClient().prepareGet(targetIndex,
                                                     docType,
                                                     val);

      GetResponse gr = grb.execute().actionGet();

      if (!gr.isExists()) {
        return null;
      }

      return getEntityBuilder(gr.getSourceAsMap());
    }

    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    srb.setTypes(docType);

    SearchResponse response = srb.setSearchType(SearchType.QUERY_THEN_FETCH)
            .setFilter(getFilters(null).singleEntityFilter(val, index))
            .setFrom(0).setSize(60).setExplain(true)
            .execute()
            .actionGet();

    SearchHits hits = response.getHits();

    //Break condition: No hits are returned
    if (hits.hits().length == 0) {
      return null;
    }

    if (hits.getTotalHits() != 1) {
      error("Multiple entities of type " + docType +
                    " with field " + ESQueryFilter.makePropertyRef(index) +
                    " value " + val);
      return null;
    }

    return getEntityBuilder(hits.hits()[0].sourceAsMap());
  }

  private static class DateLimits {
    String minStart;
    String maxEnd;
  }

  /* Return the response after indexing */
  private IndexResponse index(final Object rec) throws CalFacadeException {
    try {
      if (rec instanceof EventInfo) {
        return indexEvent((EventInfo)rec);
      }

      DocInfo di = null;

      DocBuilder db = getDocBuilder();

      if (rec instanceof BwCalendar) {
        di = db.makeDoc((BwCalendar)rec);
      }

      if (rec instanceof BwCategory) {
        di = db.makeDoc((BwCategory)rec);
      }

      if (rec instanceof BwContact) {
        di = db.makeDoc((BwContact)rec);
      }

      if (rec instanceof BwLocation) {
        di = db.makeDoc((BwLocation)rec);
      }

      if (di != null) {
        return indexDoc(di);
      }

      throw new CalFacadeException(
              new IndexException(IndexException.unknownRecordType,
                                 rec.getClass().getName()));
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private IndexResponse indexEvent(final EventInfo ei) throws CalFacadeException {
    try {

      /* If it's not recurring or a stand-alone instance index it */

      BwEvent ev = ei.getEvent();

      if (!ev.getRecurring() || (ev.getRecurrenceId() != null)) {
        return indexEvent(ei,
                          ItemKind.entity,
                          ev.getDtstart(),
                          ev.getDtend(),
                          null, //ev.getRecurrenceId(),
                          null);
      }

      /* Delete all instances of this event: we'll do a delete by query
       * We need to find all with the same path and uid.
       */

      /* TODO - do a query for all recurrence ids and delete the ones
          we don't want.
       */

      deleteEvent(ei);

      /* Create a list of all instance date/times before overrides. */

      int maxYears;
      int maxInstances;
      DateLimits dl = new DateLimits();

      if (ev.getPublick()) {
        maxYears = unauthpars.getMaxYears();
        maxInstances = unauthpars.getMaxInstances();
      } else {
        maxYears = authpars.getMaxYears();
        maxInstances = authpars.getMaxInstances();
      }

      RecurPeriods rp = RecurUtil.getPeriods(ev, maxYears, maxInstances);

      if (rp.instances.isEmpty()) {
        // No instances for an alleged recurring event.
        return null;
        //throw new CalFacadeException(CalFacadeException.noRecurrenceInstances);
      }

      String stzid = ev.getDtstart().getTzid();

      int instanceCt = maxInstances;

      boolean dateOnly = ev.getDtstart().getDateType();

      /* First build a table of overrides so we can skip these later
       */
      Map<String, String> overrides = new HashMap<>();

      /*
      if (!Util.isEmpty(ei.getOverrideProxies())) {
        for (BwEvent ov: ei.getOverrideProxies()) {
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());
        }
      }
      */
      IndexResponse iresp = null;

      if (!Util.isEmpty(ei.getOverrides())) {
        for (EventInfo oei: ei.getOverrides()) {
          BwEvent ov = oei.getEvent();
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());

          BwDateTime rstart = BwDateTime.makeBwDateTime(ov.getDtstart().getDateType(),
                                                        ov.getRecurrenceId(),
                                                        stzid);
          BwDateTime rend = rstart.addDuration(BwDuration.makeDuration(ov.getDuration()));

          iresp = indexEvent(oei,
                             ItemKind.override,
                             rstart,
                             rend,
                             ov.getRecurrenceId(),
                             dl);

          instanceCt--;
        }
      }

      /* Emit all instances that aren't overridden. */

      for (Period p: rp.instances) {
        String dtval = p.getStart().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        if (overrides.get(rstart.getDate()) != null) {
          // Overrides indexed separately - skip this instance.
          continue;
        }

        String recurrenceId = rstart.getDate();

        dtval = p.getEnd().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        BwDateTime rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        iresp = indexEvent(ei,
                           ItemKind.entity,
                           rstart,
                           rend,
                           recurrenceId,
                           dl);

        instanceCt--;
        if (instanceCt == 0) {
          // That's all you're getting from me
          break;
        }
      }

      /* Emit the master event with a date range covering the entire
       * period.
       */

      BwDateTime start = BwDateTime.makeBwDateTime(dateOnly,
                                                   dl.minStart, stzid);
      BwDateTime end = BwDateTime.makeBwDateTime(dateOnly,
                                                 dl.maxEnd, stzid);
      iresp = indexEvent(ei,
                         ItemKind.master,
                         start,
                         end,
                         null,
                         null);

      return iresp;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private boolean deleteEvent(final EventInfo ei) throws CalFacadeException {
    BwEvent ev = ei.getEvent();

    DeleteByQueryRequestBuilder delQreq = getClient().prepareDeleteByQuery(
            targetIndex).setTypes(docTypeEvent);

    ESQueryFilter esq= getFilters(null);

    /*
    FilterBuilder fb = esq.addTerm(null, PropertyInfoIndex.COLLECTION,
                                   ev.getColPath());
    fb = esq.addTerm(fb, PropertyInfoIndex.UID, ev.getUid());
    */

    FilterBuilder fb = esq.addTerm(null, PropertyInfoIndex.HREF,
                                   ev.getHref());

    delQreq.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                                                 fb));
    DeleteByQueryResponse delResp = delQreq.execute()
            .actionGet();

    boolean ok = true;

    for (IndexDeleteByQueryResponse idqr: delResp.getIndices().values()) {
      if (idqr.getFailedShards() > 0) {
        warn("Failing shards for recurrence delete uid: " + ev.getUid() +
                     " colPath: " + ev.getColPath() +
                     " index: " + idqr.getIndex());

        ok = false;
      }
    }

    return ok;
  }

  private IndexResponse indexEvent(final EventInfo ei,
                                   final ItemKind kind,
                                   final BwDateTime start,
                                   final BwDateTime end,
                                   final String recurid,
                                   final DateLimits dl) throws CalFacadeException {
    BwEvent ev = ei.getEvent();

    try {
      DocBuilder db = getDocBuilder();
      DocInfo di = db.makeDoc(ei,
                              kind,
                              start,
                              end,
                              recurid);

      if (dl != null) {
        dl.minStart = checkMin(dl.minStart, start);
        dl.maxEnd = checkMax(dl.maxEnd, end);
      }

      return indexDoc(di);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (VersionConflictEngineException vcee) {
      if (vcee.getCurrentVersion() == vcee.getProvidedVersion()) {
        warn("Failed index with equal version for kind " + kind +
                     " and href " + ev.getHref());
      }

      return null;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private String checkMin(final String start,
                          final BwDateTime tm) {
    String val;
    if (tm.getDateType()) {
      val = tm.getDtval();
    } else {
      val = tm.getDate();
    }

    if (start == null) {
      return val;
    }

    if (start.compareTo(val) > 0) {
      return val;
    }

    return start;
  }

  private String checkMax(final String end,
                          final BwDateTime tm) {
    String val;
    if (tm.getDateType()) {
      val = tm.getDtval();
    } else {
      val = tm.getDate();
    }

    if (end == null) {
      return val;
    }

    if (end.compareTo(val) < 0) {
      return val;
    }

    return end;
  }

  private IndexResponse indexDoc(final DocInfo di) throws Throwable {
    batchCurSize++;
    IndexRequestBuilder req = getClient().
            prepareIndex(targetIndex, di.type, di.id);

    req.setSource(di.source);

    if (di.version != 0) {
      req.setVersion(di.version).setVersionType(VersionType.EXTERNAL);
    }

    if (debug) {
      debug("Indexing to index " + targetIndex +
                    " with DocInfo " + di);
    }

    return req.execute().actionGet();
  }

  private Client getClient() throws CalFacadeException {
    if (theClient != null) {
      return theClient;
    }

    synchronized (clientSyncher) {
      if (idxpars.getEmbeddedIndexer()) {
        /* Start up a node and get a client from it.
         */
        ImmutableSettings.Builder settings =
                ImmutableSettings.settingsBuilder();

        if (idxpars.getNodeName() != null) {
          settings.put("node.name", idxpars.getNodeName());
        }

        settings.put("path.data", idxpars.getDataDir());

        if (idxpars.getHttpEnabled()) {
          warn("*************************************************************");
          warn("*************************************************************");
          warn("*************************************************************");
          warn("http is enabled for the indexer. This may be a security risk.");
          warn("*************************************************************");
          warn("*************************************************************");
          warn("*************************************************************");
        }
        settings.put("http.enabled", idxpars.getHttpEnabled());
        NodeBuilder nbld = NodeBuilder.nodeBuilder()
                .settings(settings);

        if (idxpars.getClusterName() != null) {
          nbld.clusterName(idxpars.getClusterName());
        }

        theNode = nbld.data(true).local(true).node();

        theClient = theNode.client();
      } else {
        /* Not embedded - use the URL */
        TransportClient tClient = new TransportClient();

        tClient = tClient.addTransportAddress(
                new InetSocketTransportAddress(host, port));

        theClient = tClient;
      }

      /* Ensure status is at least yellow */

      int tries = 0;
      int yellowTries = 0;

      for (;;) {
        ClusterHealthRequestBuilder chrb = theClient.admin().cluster().prepareHealth();

        ClusterHealthResponse chr = chrb.execute().actionGet();

        if (chr.getStatus() == ClusterHealthStatus.GREEN) {
          break;
        }

        if (chr.getStatus() == ClusterHealthStatus.YELLOW) {
          yellowTries++;

          if (yellowTries > 60) {
            warn("Going ahead anyway on YELLOW status");
          }

          break;
        }

        tries++;

        if (tries % 5 == 0) {
          warn("Cluster status for " + chr.getClusterName() +
                       " is still " + chr.getStatus() +
                       " after " + tries + " tries");
        }

        try {
          Thread.sleep(1000);
        } catch(InterruptedException ex) {
          throw new CalFacadeException("Interrupted out of getClient");
        }
      }

      return theClient;
    }
  }

  private IndicesAdminClient getAdminIdx() throws CalFacadeException {
    return getClient().admin().indices();
  }

  private ClusterAdminClient getAdminCluster() throws CalFacadeException {
    return getClient().admin().cluster();
  }

  /** Return a new filter builder
   *
   * @param recurRetrieval  - value modifies search
   * @return a filter builder
   */
  public ESQueryFilter getFilters(final RecurringRetrievalMode recurRetrieval) {
    return new ESQueryFilter(currentMode, principal, superUser,
                             recurRetrieval);
  }

  private String newIndexSuffix() {
    // ES only allows lower case letters in names (and digits)
    StringBuilder suffix = new StringBuilder("p");

    char[] ch = DateTimeUtil.isoDateTime().toCharArray();

    for (int i = 0; i < 8; i++) {
      suffix.append(ch[i]);
//      suffix.append((char)(ch[i] - '0' + 'a'));
    }

    suffix.append('t');

    for (int i = 9; i < 15; i++) {
      suffix.append(ch[i]);
//      suffix.append((char)(ch[i] - '0' + 'a'));
    }

    return suffix.toString();
  }

  private EntityBuilder getEntityBuilder(final Map<String, Object> fields) {
    return new EntityBuilder(fields);
  }

  private DocBuilder getDocBuilder() throws CalFacadeException {
    return new DocBuilder(principal,
                          authpars, unauthpars, basicSysprops);
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void info(final String msg) {
    getLog().info(msg);
  }

  protected void debug(final String msg) {
    getLog().debug(msg);
  }

  protected void warn(final String msg) {
    getLog().warn(msg);
  }

  protected void error(final String msg) {
    getLog().error(msg);
  }

  protected void error(final Throwable t) {
    getLog().error(this, t);
  }
}
