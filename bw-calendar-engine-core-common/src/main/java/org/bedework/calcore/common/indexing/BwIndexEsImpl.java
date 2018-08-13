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

import org.bedework.access.Acl;
import org.bedework.calcore.common.indexing.DocBuilder.ItemKind;
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
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SortTerm;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.IndexStatsResponse;
import org.bedework.calfacade.indexing.ReindexResponse;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.responses.GetEntitiesResponse;
import org.bedework.calfacade.responses.GetEntityResponse;
import org.bedework.calfacade.responses.Response;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuitePrincipal;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.elasticsearch.DocBuilderBase.UpdateInfo;
import org.bedework.util.elasticsearch.EsDocInfo;
import org.bedework.util.indexing.IndexException;
import org.bedework.util.misc.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.DateTimeUtil;

import net.fortuna.ical4j.model.Period;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequestBuilder;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.status.IndicesStatusRequestBuilder;
import org.elasticsearch.action.admin.indices.status.IndicesStatusResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.deletebyquery.IndexDeleteByQueryResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.bedework.access.PrivilegeDefs.privRead;
import static org.bedework.calcore.common.indexing.DocBuilder.ItemKind.entity;
import static org.bedework.calfacade.indexing.BwIndexer.IndexedType.unreachableEntities;
import static org.bedework.calfacade.responses.Response.Status.failed;
import static org.bedework.calfacade.responses.Response.Status.noAccess;
import static org.bedework.calfacade.responses.Response.Status.notFound;
import static org.bedework.calfacade.responses.Response.Status.ok;
import static org.bedework.calfacade.responses.Response.Status.processing;
import static org.bedework.util.elasticsearch.DocBuilderBase.docTypeUpdateTracker;
import static org.bedework.util.elasticsearch.DocBuilderBase.updateTrackerId;

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
 * <p>As xxx well as indexing events/tasks etc we also have to index alarms
 * so that the associated events can be located. This is an alarm object
 * inside an entity with a start date</p>
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class BwIndexEsImpl extends Logged implements BwIndexer {
  //private int batchMaxSize = 0;
  //private int batchCurSize = 0;

  // private Object batchLock = new Object();

  private final boolean publick;
  private BwPrincipal principal;
  private final boolean superUser;

  private final AccessChecker accessCheck;

  private final String host;
  private int port = 9300;

  private static Client theClient;
  private static final Object clientSyncher = new Object();

  private String targetIndex;
  private final String[] searchIndexes;
  private final int currentMode;

  private String lastChangeToken;
  private long lastChangeTokenCheck;
  private final int lastChangeTokenCheckPeriod = 2000;

  final static EntityCaches caches = new EntityCaches();

  private final AuthProperties authpars;
  private final AuthProperties unauthpars;
  private final IndexProperties idxpars;
  private final BasicSystemProperties basicSysprops;

  /* Indexed by index name */
  private final static Map<String, UpdateInfo> updateInfo = new HashMap<>();

  /* This is used for testng - we delay searches to give the indexer
   * time to catch up
   */
  private static long lastIndexTime;

  private final static long indexerDelay = 1100;

  // Total
  private static long fetchTime;
  private long fetchStart;
  private int fetchDepth;

  private static long fetchAccessTime;
  private long fetchAccessStart;
  private int fetchAccessDepth;

  private static long fetches;
  private static long nestedFetches;
  private static long nestedAccessFetches;

  private void fetchStart() {
    if (fetchStart != 0) {
      fetchDepth++;
      nestedFetches++;
      return;
    }

    fetches++;
    fetchStart = System.currentTimeMillis();
  }

  private void fetchAccessStart() {
    if (fetchAccessStart != 0) {
      fetchAccessDepth++;
      nestedAccessFetches++;
      return;
    }

    nestedFetches++;
    fetchAccessStart = System.currentTimeMillis();
  }

  private void fetchEnd() {
    fetchEnd(false);
  }

  private void fetchEnd(final boolean closing) {
    if ((closing || (fetchDepth <= 0))
            && (fetchStart != 0)) {
      fetchTime += (System.currentTimeMillis() - fetchStart);
      fetchStart = 0;

      if ((debug) && ((fetches % 100) == 0)) {
        debug("fetches: " + fetches +
                      ", fetchTime: " + fetchTime +
                      ", fetcAccessTime: " + fetchAccessTime);
      }
    } else if (fetchDepth > 0){
      fetchDepth--;
    }
  }

  private void fetchAccessEnd() {
    if ((fetchAccessDepth <= 0) && (fetchAccessStart != 0)){
      fetchAccessTime += (System.currentTimeMillis() - fetchAccessStart);
      fetchAccessStart = 0;
    } else if (fetchAccessDepth > 0){
      fetchAccessDepth--;
    }
  }

  private class TimedAccessChecker implements AccessChecker {
    private AccessChecker accessCheck;

    TimedAccessChecker(final AccessChecker accessCheck) {
      this.accessCheck = accessCheck;
    }

    public Acl.CurrentAccess checkAccess(BwShareableDbentity ent,
                                         int desiredAccess,
                                         boolean returnResult)
            throws CalFacadeException {
      try {
        fetchAccessStart();

        return accessCheck
                .checkAccess(ent, desiredAccess, returnResult);
      } finally {
        fetchAccessEnd();
      }
    }

    public CalendarWrapper checkAccess(final BwCalendar val)
            throws CalFacadeException {
      try {
        fetchAccessStart();

        return accessCheck.checkAccess(val);
      } finally {
        fetchAccessEnd();
      }
    }

    public CalendarWrapper checkAccess(final BwCalendar val,
                                       int desiredAccess)
            throws CalFacadeException {
      try {
        fetchAccessStart();

        return accessCheck.checkAccess(val, desiredAccess);
      } finally {
        fetchAccessEnd();
      }
    }

    @Override
    public AccessUtilI getAccessUtil() {
      return accessCheck.getAccessUtil();
    }
  }

  /**
   * String is name of index
   */
  private Map<String, ReindexResponse> currentReindexing =
          new HashMap<>();

  private final static Map<String, IndexedType> docToType =
          new HashMap<>();

  static {
    for (final IndexedType it : IndexedType.values()) {
      docToType.put(it.getDocType(), it);
    }
  }

  /**
   * Constructor
   *
   * @param configs     - the configurations object
   * @param publick     - if false we add an owner term to the
   *                    searches
   * @param principal   - who is doing the searching - only for
   *                    non-public
   * @param superUser   - true if the principal is a superuser.
   * @param currentMode - guest, user,publicAdmin
   * @param accessCheck - required - lets us check access
   * @param indexName   - explicitly specified
   */
  public BwIndexEsImpl(final Configurations configs,
                       final boolean publick,
                       final BwPrincipal principal,
                       final boolean superUser,
                       final int currentMode,
                       final AccessChecker accessCheck,
                       final String indexName) {
    this.publick = publick;
    this.principal = principal;
    this.superUser = superUser;
    this.currentMode = currentMode;
    this.accessCheck = new TimedAccessChecker(accessCheck);

    idxpars = configs.getIndexProperties();
    authpars = configs.getAuthProperties(true);
    unauthpars = configs.getAuthProperties(false);
    basicSysprops = configs.getBasicSystemProperties();

    final String url = idxpars.getIndexerURL();

    if (url == null) {
      host = "localhost";
    } else {
      final int pos = url.indexOf(":");

      if (pos < 0) {
        host = url;
      } else {
        host = url.substring(0, pos);
        if (pos < url.length()) {
          port = Integer.valueOf(url.substring(pos + 1));
        }
      }
    }

    if (indexName == null) {
      targetIndex = Util.buildPath(false, idxpars.getUserIndexName());
    } else {
      targetIndex = Util.buildPath(false, indexName);
    }
    searchIndexes = new String[]{targetIndex};

    //if (updateInfo.get(targetIndex) == null) {
    // Need to get the info from the index
    //}
  }

  @Override
  public void close() {
    fetchEnd(true);
  }

  @Override
  public void setBatchSize(final int val) {
    //batchMaxSize = val;
    //batchCurSize = 0;

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
    private final BwIndexer indexer;

    private long found;

    private int pageStart;
    private int pageSize;

    private boolean requiresSecondaryFetch;

    //private boolean canPage;

    /* For paged queries - we need these values */
    private String start;
    private String end;
    private QueryBuilder curQuery;
    private FilterBuilder curFilter;
    private List<SortTerm> curSort;
    private DeletedState delState;
    private RecurringRetrievalMode recurRetrieval;

    /* Used for time-ranged queries */
    private String latestStart;
    private String earliestEnd;

    private AccessChecker accessCheck;

    private int lastPageStart;

    EsSearchResult(final BwIndexer indexer) {
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
    public int getLastPageStart() {
      return lastPageStart;
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
  public boolean getPublic() {
    return publick;
  }

  @Override
  public void markTransaction() throws CalFacadeException {
    final UpdateInfo ui = updateInfo.get(targetIndex);
    if ((ui != null) && !ui.isUpdate()) {
      return;
    }

    try {
      final UpdateRequestBuilder urb =
              getClient().prepareUpdate(targetIndex,
                                        docTypeUpdateTracker,
                                        updateTrackerId).
                                 setRetryOnConflict(20).
                                 setRefresh(true);

      urb.setScript("ctx._source.count += 1",
                    ScriptService.ScriptType.INLINE);
      final UpdateResponse ur = urb.execute().actionGet();
    } catch (final ElasticsearchException ese) {
      warn("Exception updating UpdateInfo: " + ese
              .getLocalizedMessage());
      index(new UpdateInfo());
    }
  }

  @Override
  public String currentChangeToken() throws CalFacadeException {
    UpdateInfo ui;
    try {
      final GetRequestBuilder grb = getClient()
              .prepareGet(targetIndex,
                          docTypeUpdateTracker,
                          updateTrackerId).
                      setFields("count", "_timestamp");

      final GetResponse gr = grb.execute().actionGet();

      if (!gr.isExists()) {
        return null;
      }

      final EntityBuilder er = getEntityBuilder(gr.getFields());
      ui = er.makeUpdateInfo();
    } catch (final ElasticsearchException ese) {
      warn("Exception getting UpdateInfo: " + ese
              .getLocalizedMessage());
      ui = new UpdateInfo();
    }

    synchronized (updateInfo) {
      UpdateInfo tui = updateInfo.get(targetIndex);

      if ((tui != null) && (tui.getCount() >= (0111111111 - 1000))) {
        // Reset before we overflow
        tui = null;
      }

      if ((tui == null) || (!tui.getCount().equals(ui.getCount()))) {
        updateInfo.put(targetIndex, ui);
      } else {
        ui = tui;
      }
    }

    return ui.getChangeToken();
  }

  private class BulkListener implements BulkProcessor.Listener {

    @Override
    public void beforeBulk(final long executionId,
                           final BulkRequest request) {
    }

    @Override
    public void afterBulk(final long executionId,
                          final BulkRequest request,
                          final BulkResponse response) {
    }

    @Override
    public void afterBulk(final long executionId,
                          final BulkRequest request,
                          final Throwable failure) {
      error(failure);
    }
  }

  @Override
  public ReindexResponse reindex(final String indexName) {
    final ReindexResponse resp =
            currentReindexing.getOrDefault(indexName,
                                           new ReindexResponse(
                                                   indexName));

    if (resp.getStatus() == processing) {
      return resp;
    }

    reindex(resp, indexName, docTypeCategory);
    if (resp.getStatus() != ok) {
      return resp;
    }
    reindex(resp, indexName, docTypeContact);
    if (resp.getStatus() != ok) {
      return resp;
    }
    reindex(resp, indexName, docTypeLocation);
    if (resp.getStatus() != ok) {
      return resp;
    }
    reindex(resp, indexName, null);

    return resp;
  }

  private void reindex(final ReindexResponse resp,
                       final String indexName,
                       final String docType) {
    // Only retrieve masters - we'll query for the overrides
    final QueryBuilder qb =
            getFilters(RecurringRetrievalMode.entityOnly)
                    .getAllForReindex(docType);
    final int timeoutMillis = 60000;  // 1 minute
    final TimeValue tv = new TimeValue(timeoutMillis);
    final int batchSize = 100;

    final Client cl = getClient(resp);

    if (cl == null) {
      return;
    }

    // Start with default index as source
    targetIndex = Util.buildPath(false, idxpars.getUserIndexName());

    final BulkProcessor bulkProcessor =
            BulkProcessor.builder(cl,
                                  new BulkListener())
                         .setBulkActions(batchSize)
                         .setConcurrentRequests(3)
                         .setFlushInterval(tv)
                         .build();

    SearchResponse scrollResp = cl.prepareSearch(targetIndex)
                                  .setSearchType(SearchType.SCAN)
                                  .setScroll(tv)
                                  .setQuery(qb)
                                  .setSize(batchSize)
                                  .execute()
                                  .actionGet(); //100 hits per shard will be returned for each scroll

    // Switch to new index
    targetIndex = indexName;

    //Scroll until no hits are returned
    while (true) {
      for (final SearchHit hit : scrollResp.getHits().getHits()) {
        final String dtype = hit.getType();

        resp.incProcessed();

        if ((resp.getProcessed() % 250) == 0) {
          info("processed " + docType + ": " + resp.getProcessed());
        }

        if (dtype.equals(docTypeUpdateTracker)) {
          continue;
        }

        resp.getStats().inc(docToType.getOrDefault(dtype,
                                                   unreachableEntities));

        final ReindexResponse.Failure hitResp = new ReindexResponse.Failure();
        final Object entity = makeEntity(hitResp, hit, null);

        if (entity == null) {
          warn("Unable to build entity " + hit.sourceAsString());
          resp.incTotalFailed();
          if (resp.getTotalFailed() < 50) {
            resp.addFailure(hitResp);
          }
          continue;
        }

        if (entity instanceof BwShareableDbentity) {
          final BwShareableDbentity ent = (BwShareableDbentity)entity;

          try {
            principal = BwPrincipal.makePrincipal(ent.getOwnerHref());
          } catch (final CalFacadeException cfe) {
            errorReturn(resp, cfe);
            return;
          }
        }

        if (entity instanceof EventInfo) {
          // This might be a single event or a recurring event.

          final EventInfo ei = (EventInfo)entity;
          final BwEvent ev = ei.getEvent();
          if (ev.getRecurring()) {
            resp.incRecurring();
          }

          if (!reindexEvent(hitResp,
                            indexName,
                            hit,
                            ei,
                            bulkProcessor)) {
            warn("Unable to iondex event " + hit.sourceAsString());
            resp.incTotalFailed();
            if (resp.getTotalFailed() < 50) {
              resp.addFailure(hitResp);
            }
          }
        } else {
          final EsDocInfo doc = makeDoc(resp, entity);

          if (doc == null) {
            if (resp.getStatus() != ok) {
              resp.addFailure(hitResp);
            }

            continue;
          }

          final IndexRequest request =
                  new IndexRequest(indexName, hit.type(),
                                   doc.getId());

          request.source(doc.getSource());
          bulkProcessor.add(request);

          if (entity instanceof BwEventProperty) {
            caches.put((BwEventProperty)entity);
          }
        }
      }
      scrollResp = cl.prepareSearchScroll(scrollResp.getScrollId()).
              setScroll(tv).
                             execute().
                             actionGet();
      //Break condition: No hits are returned
      if (scrollResp.getHits().getHits().length == 0) {
        break;
      }
    }

    try {
      bulkProcessor.awaitClose(10, TimeUnit.MINUTES);
    } catch (final InterruptedException e) {
      errorReturn(resp,
                  "Final bulk close was interrupted. Records may be missing",
                  failed);
    }
  }

  @Override
  public ReindexResponse getReindexStatus(final String indexName) {
    return currentReindexing.get(indexName);
  }

  @Override
  public IndexStatsResponse getIndexStats(final String indexName) {
    final IndexStatsResponse resp = new IndexStatsResponse(indexName);

    if (indexName == null) {
      return errorReturn(resp, "indexName must be provided");
    }

    final QueryBuilder qb = new FilteredQueryBuilder(null,
                                                     FilterBuilders
                                                             .matchAllFilter());
    final int timeoutMillis = 60000;  // 1 minute
    final TimeValue tv = new TimeValue(timeoutMillis);
    final int batchSize = 100;

    final Client cl = getClient(resp);

    if (cl == null) {
      return resp;
    }

    SearchResponse scrollResp = cl.prepareSearch(indexName)
                                  .setSearchType(SearchType.SCAN)
                                  .setScroll(tv)
                                  .setQuery(qb)
                                  .setSize(batchSize)
                                  .execute()
                                  .actionGet(); //100 hits per shard will be returned for each scroll

    //Scroll until no hits are returned
    while (true) {
      for (final SearchHit hit : scrollResp.getHits().getHits()) {
        resp.incProcessed();
        final String dtype = hit.getType();

        if (dtype.equals(docTypeEvent)) {
          final EventInfo entity = (EventInfo)makeEntity(resp, hit,
                                                         null);
          if (entity == null) {
            errorReturn(resp, "Unable to make doc for " + hit
                    .sourceAsString());
            continue;
          }

          final BwEvent ev = entity.getEvent();

          if (ev instanceof BwEventAnnotation) {
            final BwEventAnnotation ann = (BwEventAnnotation)ev;

            if (ann.testOverride()) {
              resp.incOverrides();
            }
          }

          if (ev.getRecurring()) {
            resp.incRecurring();
          }

          if (ev.getRecurrenceId() == null) {
            resp.incMasters();
          } else {
            resp.incInstances();
          }
        } else {
          resp.getStats().inc(docToType.getOrDefault(dtype,
                                                     unreachableEntities));
        }
      }

      scrollResp = cl.prepareSearchScroll(scrollResp.getScrollId()).
              setScroll(tv)
                     .execute()
                     .actionGet();
      //Break condition: No hits are returned
      if (scrollResp.getHits().getHits().length == 0) {
        break;
      }
    }

    return resp;
  }

  @Override
  public SearchResult search(final String query,
                             final boolean relevance,
                             final FilterBase filter,
                             final List<SortTerm> sort,
                             final FilterBase defaultFilterContext,
                             final String start,
                             final String end,
                             final int pageSize,
                             final DeletedState deletedState,
                             final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    if (basicSysprops.getTestMode()) {
      final long timeSinceIndex = System
              .currentTimeMillis() - lastIndexTime;
      final long waitTime = indexerDelay - timeSinceIndex;

      if (waitTime > 0) {
        try {
          Thread.sleep(waitTime);
        } catch (final InterruptedException ignored) {
        }
      }
    }

    final EsSearchResult res = new EsSearchResult(this);

    res.start = start;
    res.end = end;
    res.pageSize = pageSize;
    res.accessCheck = accessCheck;
    res.delState = deletedState;
    res.recurRetrieval = recurRetrieval;

    if (query != null) {
      final MatchQueryBuilder mqb = QueryBuilders
              .matchQuery("_all", query);

      if (!relevance) {
        mqb.operator(Operator.AND);
      } else {
        mqb.fuzziness("1");
        mqb.prefixLength(2);
      }
      res.curQuery = mqb;
//      res.curQuery = QueryBuilders.queryString(query);
    }

    final ESQueryFilter ef = getFilters(recurRetrieval);

    res.curFilter = ef.buildFilter(filter);
    if (res.curFilter instanceof MatchNone) {
      res.setFound(0);
      return res;
    }

    res.curFilter = ef.addDateRangeFilter(res.curFilter,
                                          IcalDefs.entityTypeEvent,
                                          start,
                                          end);

    res.curFilter = ef.addLimits(res.curFilter,
                                 defaultFilterContext,
                                 res.delState);
    if (res.curFilter instanceof MatchNone) {
      res.setFound(0);
      return res;
    }

    res.requiresSecondaryFetch = ef.requiresSecondaryFetch();
    //res.canPage = ef.canPage();

    res.curSort = sort;

    final SearchRequestBuilder srb = getClient()
            .prepareSearch(searchIndexes);
    if (res.curQuery != null) {
      srb.setQuery(res.curQuery);
    }

    srb.setSearchType(SearchType.COUNT)
       .setPostFilter(res.curFilter)
       .setFrom(0)
       .setSize(0);

    if (!Util.isEmpty(res.curSort)) {
      SortOrder so;

      for (final SortTerm st : res.curSort) {
        if (st.isAscending()) {
          so = SortOrder.ASC;
        } else {
          so = SortOrder.DESC;
        }

        srb.addSort(new FieldSortBuilder(
                ESQueryFilter.makePropertyRef(st.getProperties(), null))
                            .order(so));
      }
    }

    res.latestStart = ef.getLatestStart();
    res.earliestEnd = ef.getEarliestEnd();

    if (debug) {
      debug("Search: latestStart=" + res.latestStart +
                    " earliestEnd=" + res.earliestEnd +
                    " targetIndex=" + targetIndex +
                    "; srb=" + srb);
    }

    final SearchResponse resp = srb.execute().actionGet();

//    if (resp.status() != RestStatus.OK) {
    //TODO
//    }

    if (debug) {
      debug("Search: returned status " + resp.status() +
                    " found: " + resp.getHits().getTotalHits());
    }

    res.setFound(resp.getHits().getTotalHits());

    return res;
  }

  @Override
  public List<SearchResultEntry> getSearchResult(
          final SearchResult sres,
          final Position pos,
          final int desiredAccess)
          throws CalFacadeException {
    final EsSearchResult res = (EsSearchResult)sres;

    final int offset;

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
  public List<SearchResultEntry> getSearchResult(
          final SearchResult sres,
          final int offset,
          final int num,
          final int desiredAccess)
          throws CalFacadeException {
    try {
      fetchStart();
      if (debug) {
        debug("offset: " + offset + ", num: " + num);
      }

      final EsSearchResult res = (EsSearchResult)sres;

      res.pageStart = offset;

      final SearchRequestBuilder srb = getClient()
              .prepareSearch(searchIndexes);
      if (res.curQuery != null) {
        srb.setQuery(res.curQuery);
      }

      srb.setSearchType(SearchType.QUERY_THEN_FETCH)
         .setPostFilter(res.curFilter)
         .setFrom(res.pageStart);

      final int size;

      if (num < 0) {
        size = (int)sres.getFound();
      } else {
        size = num;
      }

      // TODO - need a configurable absolute max size for fetches

      srb.setSize(size);
      final List<SearchResultEntry> entities = new ArrayList<>(size);

      if (!Util.isEmpty(res.curSort)) {
        SortOrder so;

        for (final SortTerm st : res.curSort) {
          if (st.isAscending()) {
            so = SortOrder.ASC;
          } else {
            so = SortOrder.DESC;
          }

          srb.addSort(new FieldSortBuilder(
                  ESQueryFilter
                          .makePropertyRef(st.getProperties(), null))
                              .order(so));
        }
      }

      if (res.requiresSecondaryFetch) {
        // Limit to href then fetch those
        srb.addField(ESQueryFilter.hrefJname);
      }

      final SearchResponse resp = srb.execute().actionGet();

      if (resp.status() != RestStatus.OK) {
        if (debug) {
          debug("Search returned status " + resp.status());
        }
      }

      final SearchHits hitsResp = resp.getHits();

      if ((hitsResp.getHits() == null) ||
              (hitsResp.getHits().length == 0)) {
        return entities;
      }

      //Break condition: No hits are returned
      if (hitsResp.hits().length == 0) {
        return entities;
      }

      final List<SearchHit> hits;
      if (res.requiresSecondaryFetch) {
        hits = multiFetch(hitsResp, res.recurRetrieval);

        if (hits == null) {
          return entities;
        }
      } else {
        hits = Arrays.asList(hitsResp.getHits());
      }

      final Map<String, Collection<BwEventAnnotation>> overrides = new HashMap<>();
      final Collection<EventInfo> masters = new TreeSet<>();

      EntityBuilder.checkFlushCache(currentChangeToken());
      
    /* If we are retrieving events with a time range query and we are asking for the 
     * master + overrides then we need to check that the master really has an 
     * instance in the given time range */
      final boolean checkTimeRange =
              (res.recurRetrieval.mode == Rmode.overrides) &&
                      ((res.latestStart != null) ||
                               (res.earliestEnd != null));

      final Set<String> excluded = new TreeSet<>();

      for (final SearchHit hit : hits) {
        res.pageStart++;
        final String dtype = hit.getType();

        if (dtype == null) {
          throw new CalFacadeException(
                  "org.bedework.index.noitemtype");
        }

        final String kval = hit.getId();

        if (kval == null) {
          throw new CalFacadeException(
                  "org.bedework.index.noitemkey");
        }

        final EntityBuilder eb = getEntityBuilder(hit.sourceAsMap());

        Object entity = null;
        switch (dtype) {
          case docTypeCollection:
            entity = makeCollection(eb);
            break;
          case docTypeCategory:
            entity = eb.makeCat();
            break;
          case docTypeContact:
            entity = eb.makeContact();
            break;
          case docTypeLocation:
            entity = eb.makeLocation();
            break;
          case docTypeEvent:
          case docTypePoll:
            final Response evrestResp = new Response();
            entity = makeEvent(eb, evrestResp, kval,
                               res.recurRetrieval.mode == Rmode.expanded);
            final EventInfo ei = (EventInfo)entity;
            final BwEvent ev = ei.getEvent();

            if (evrestResp.getStatus() != ok) {
              warn("Failed restore of event " + ev.getUid() +
                           ": " + evrestResp);
            }

            final Acl.CurrentAccess ca =
                    res.accessCheck.checkAccess(ev,
                                                desiredAccess,
                                                true);

            if ((ca == null) || !ca.getAccessAllowed()) {
              continue;
            }

            ei.setCurrentAccess(ca);

            if (ev instanceof BwEventAnnotation) {
              if (excluded.contains(ev.getUid())) {
                continue;
              }

              // Treat as override
              final Collection<BwEventAnnotation> ov = overrides
                      .computeIfAbsent(
                              ev.getHref(), k -> new TreeSet<>());

              ov.add((BwEventAnnotation)ev);
              continue;
            }

            if (checkTimeRange && dtype.equals(docTypeEvent) && ev
                    .getRecurring()) {
              if (Util.isEmpty(RecurUtil.getPeriods(ev,
                                                    99,
                                                    1,
                                                    res.latestStart,
                                                    res.earliestEnd).instances)) {
                excluded.add(ev.getUid());
                continue;
              }
            }

            masters.add(ei);
            break;
        }

        entities.add(new SearchResultEntry(entity,
                                           dtype,
                                           hit.getScore()));
      }

      //<editor-fold desc="Finish off events by setting master, target and overrides">

      for (final EventInfo ei : masters) {
        final BwEvent ev = ei.getEvent();

        if (ev.getRecurring()) {
          final Collection<BwEventAnnotation> ov = overrides
                  .get(ev.getHref());

          if (ov != null) {
            for (final BwEventAnnotation ann : ov) {
              final BwEvent proxy = new BwEventProxy(ann);
              ann.setTarget(ev);
              ann.setMaster(ev);

              final EventInfo oei = new EventInfo(proxy);

              ei.addOverride(oei);
            }
          }
        }
      }
      //</editor-fold>

      return entities;
    } finally {
      fetchEnd();
    }
  }

  @Override
  public void indexEntity(final Object rec)
          throws CalFacadeException {
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

      if (rec == null) {
        return;
      }

      markUpdated(docTypeFromClass(rec.getClass()));

      final IndexResponse resp = index(rec);

      if (debug) {
        if (resp == null) {
          debug("IndexResponse: resp=null");
        } else {
          debug("IndexResponse: index=" + resp.getIndex() +
                        " id=" + resp.getId() +
                        " type=" + resp.getType() +
                        " version=" + resp.getVersion());
        }
      }
    } catch (final Throwable t) {
      if (debug) {
        error(t);
      }
      throw new CalFacadeException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  private boolean addOverrides(final Response resp,
                               final String indexName,
                               final EventInfo ei) {
    try {
      final BwEvent ev = ei.getEvent();
      if (!ev.testRecurring()) {
        return true;
      }

      /* Fetch any overrides. */

      final ESQueryFilter flts = getFilters(null);
      final int batchSize = 100;
      int start = 0;

      while (true) {
        // Search original for overrides
        final SearchRequestBuilder srb = getClient()
                .prepareSearch(Util.buildPath(false, indexName));

        srb.setSearchType(SearchType.QUERY_THEN_FETCH)
           .setPostFilter(flts.overridesOnly(ev.getUid()));
        srb.setFrom(start);
        srb.setSize(batchSize);

        //if (debug) {
        //  debug("Overrides: targetIndex=" + indexName +
        //                "; srb=" + srb);
        //}

        final SearchResponse sresp = srb.execute().actionGet();

        if (sresp.status() != RestStatus.OK) {
          errorReturn(resp,
                      "Search returned status " + sresp.status());

          return false;
        }

        final SearchHit[] hits = sresp.getHits().getHits();

        if ((hits == null) ||
                (hits.length == 0)) {
          // No more data - we're done
          break;
        }

        for (final SearchHit hit : hits) {
          final String dtype = hit.getType();

          if (dtype == null) {
            errorReturn(resp, "org.bedework.index.noitemtype");
            return false;
          }

          final String kval = hit.getId();

          if (kval == null) {
            errorReturn(resp, "org.bedework.index.noitemkey");
            return false;
          }

          final EntityBuilder eb = getEntityBuilder(
                  hit.sourceAsMap());

          final Object entity;
          switch (dtype) {
            case docTypeEvent:
            case docTypePoll:
              entity = makeEvent(eb, resp, kval, false);
              final EventInfo oei = (EventInfo)entity;
              final BwEvent oev = oei.getEvent();

              if (oev instanceof BwEventAnnotation) {
                final BwEventAnnotation ann = (BwEventAnnotation)oev;
                final BwEvent proxy = new BwEventProxy(ann);
                ann.setTarget(ev);
                ann.setMaster(ev);

                ei.addOverride(new EventInfo(proxy));
                continue;
              }
          }

          // Unexpected type
          errorReturn(resp, "Expected override only: " + dtype);
          return false;
        }

        if (hits.length < batchSize) {
          // All remaining in this batch - we're done
          break;
        }

        start += batchSize;
      }

      return true;
    } catch (final Throwable t) {
      errorReturn(resp, t);
      return false;
    }
  }

  private boolean reindexEvent(final ReindexResponse.Failure resp,
                               final String indexName,
                               final SearchHit sh,
                               final EventInfo ei,
                               final BulkProcessor bulkProcessor) {
    try {
      /* If it's not recurring or a stand-alone instance index it */

      final BwEvent ev = ei.getEvent();

      if (!ev.testRecurring() && (ev.getRecurrenceId() == null)) {
        final EsDocInfo doc = makeDoc(resp,
                                      ei,
                                      ItemKind.master,
                                      ev.getDtstart(),
                                      ev.getDtend(),
                                      null, //ev.getRecurrenceId(),
                                      null);
        if (doc == null) {
          return false;
        }

        final IndexRequest request =
                new IndexRequest(indexName, sh.type(), doc.getId());

        request.source(doc.getSource());
        bulkProcessor.add(request);
        return true;
      }

      if (ev.getRecurrenceId() != null) {
        errorReturn(resp,
                    "Not implemented - index of single override");
        return false;
      }

      if (!addOverrides(resp, idxpars.getUserIndexName(), ei)) {
        return false;
      }

      final int maxYears;
      final int maxInstances;
      final DateLimits dl = new DateLimits();

      if (ev.getPublick()) {
        maxYears = unauthpars.getMaxYears();
        maxInstances = unauthpars.getMaxInstances();
      } else {
        maxYears = authpars.getMaxYears();
        maxInstances = authpars.getMaxInstances();
      }

      final RecurPeriods rp = RecurUtil
              .getPeriods(ev, maxYears, maxInstances);

      if (rp.instances.isEmpty()) {
        errorReturn(resp,
                    "No instances for an alleged recurring event.");
        return false;
      }

      final String stzid = ev.getDtstart().getTzid();

      int instanceCt = maxInstances;

      final boolean dateOnly = ev.getDtstart().getDateType();

      /* First build a table of overrides so we can skip these later
       */
      final Map<String, String> overrides = new HashMap<>();

      /*
      if (!Util.isEmpty(ei.getOverrideProxies())) {
        for (BwEvent ov: ei.getOverrideProxies()) {
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());
        }
      }
      */

      if (!Util.isEmpty(ei.getOverrides())) {
        for (final EventInfo oei : ei.getOverrides()) {
          final BwEvent ov = oei.getEvent();
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());

          final String dtstart;
          if (ov.getDtstart().getDateType()) {
            dtstart = ov.getRecurrenceId().substring(0, 8);
          } else {
            dtstart = ov.getRecurrenceId();
          }
          final BwDateTime rstart =
                  BwDateTime.makeBwDateTime(
                          ov.getDtstart().getDateType(),
                          dtstart,
                          stzid);
          final BwDateTime rend =
                  rstart.addDuration(
                          BwDuration.makeDuration(ov.getDuration()));

          final EsDocInfo doc = makeDoc(resp,
                                        oei,
                                        ItemKind.override,
                                        rstart,
                                        rend,
                                        ov.getRecurrenceId(),
                                        dl);

          if (doc == null) {
            return false;
          }

          final IndexRequest request =
                  new IndexRequest(indexName, sh.type(), doc.getId());

          request.source(doc.getSource());
          bulkProcessor.add(request);

          instanceCt--;
        }
      }

      //<editor-fold desc="Emit all instances that aren't overridden">

      for (final Period p : rp.instances) {
        String dtval = p.getStart().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        final BwDateTime rstart =
                BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        if (overrides.get(rstart.getDate()) != null) {
          // Overrides indexed separately - skip this instance.
          continue;
        }

        final String recurrenceId = rstart.getDate();

        dtval = p.getEnd().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        final BwDateTime rend =
                BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        final EsDocInfo doc = makeDoc(resp,
                                      ei,
                                      entity,
                                      rstart,
                                      rend,
                                      recurrenceId,
                                      dl);

        if (doc == null) {
          return false;
        }

        final IndexRequest request =
                new IndexRequest(indexName, sh.type(), doc.getId());

        request.source(doc.getSource());
        bulkProcessor.add(request);

        instanceCt--;
        if (instanceCt == 0) {
          // That's all you're getting from me
          break;
        }
      }
      //</editor-fold>

      //<editor-fold desc="Emit the master event with a date range covering the entire period.">
      final BwDateTime dtstart =
              BwDateTime.makeBwDateTime(dateOnly,
                                        dl.minStart, stzid);
      final BwDateTime dtend =
              BwDateTime.makeBwDateTime(dateOnly,
                                        dl.maxEnd, stzid);
      final EsDocInfo doc = makeDoc(resp,
                                    ei,
                                    ItemKind.master,
                                    dtstart,
                                    dtend,
                                    null,
                                    null);

      if (doc == null) {
        return false;
      }

      final IndexRequest request =
              new IndexRequest(indexName, sh.type(), doc.getId());

      request.source(doc.getSource());
      bulkProcessor.add(request);
      //</editor-fold>
      return true;
    } catch (final Throwable t) {
      errorReturn(resp, t);
      return false;
    }
  }

  private EsDocInfo makeDoc(final Response resp,
                            final EventInfo ei,
                            final ItemKind kind,
                            final BwDateTime start,
                            final BwDateTime end,
                            final String recurid,
                            final DateLimits dl) {
    try {
      final DocBuilder db = getDocBuilder();
      final EsDocInfo di = db.makeDoc(ei,
                                      kind,
                                      start,
                                      end,
                                      recurid);

      if (dl != null) {
        dl.checkMin(start);
        dl.checkMax(end);
      }

      return di;
    } catch (final Throwable t) {
      errorReturn(resp, t);
      return null;
    }
  }

  private void markUpdated(final String docType)
          throws CalFacadeException {
    UpdateInfo ui = updateInfo.get(targetIndex);

    if (ui == null) {
      currentChangeToken();
    }

    ui = updateInfo.get(targetIndex);

    if (ui == null) {
      throw new CalFacadeException("Unable to set updateInfo");
    }

    caches.clear();

    ui.setUpdate(true);
  }

  @Override
  public void unindexContained(final String colPath)
          throws CalFacadeException {
    try {
      final DeleteByQueryRequestBuilder dqrb = getClient()
              .prepareDeleteByQuery(
                      targetIndex);

      FilterBuilder fb = getFilters(null).colPathFilter(null, colPath);

      dqrb.setQuery(new FilteredQueryBuilder(null, fb));

      /*final DeleteByQueryResponse resp = */
      dqrb.execute().actionGet();

      markUpdated(null);

      // TODO check response?
    } catch (final ElasticsearchException ese) {
      // Failed somehow
      error(ese);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  @Override
  public void unindexTombstoned(final String docType,
                                final String href)
          throws CalFacadeException {
    try {
      final DeleteByQueryRequestBuilder dqrb = getClient()
              .prepareDeleteByQuery(
                      targetIndex);

      FilterBuilder fb = getFilters(null)
              .singleEntityFilter(docType,
                                  href,
                                  PropertyInfoIndex.HREF);

      dqrb.setQuery(new FilteredQueryBuilder(null, fb));

      /*final DeleteByQueryResponse resp = */
      dqrb.execute().actionGet();

      markUpdated(docType);

      // TODO check response?
    } catch (final ElasticsearchException ese) {
      // Failed somehow
      error(ese);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  @Override
  public void unindexEntity(final BwEventProperty val)
          throws CalFacadeException {
    unindexEntity(docTypeFromClass(val.getClass()),
                  getDocBuilder().getHref(val));
  }

  @Override
  public void unindexEntity(final String docType,
                            final String href)
          throws CalFacadeException {
    try {
      final DeleteByQueryRequestBuilder dqrb = getClient()
              .prepareDeleteByQuery(
                      targetIndex);

      FilterBuilder fb = getFilters(null)
              .singleEntityFilter(docType,
                                  href,
                                  PropertyInfoIndex.HREF);

      dqrb.setQuery(new FilteredQueryBuilder(null, fb));

      /*final DeleteByQueryResponse resp = */
      dqrb.execute().actionGet();

      markUpdated(docType);

      // TODO check response?
    } catch (final ElasticsearchException ese) {
      // Failed somehow
      error(ese);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  @Override
  public String newIndex(final String name)
          throws CalFacadeException {
    try {
      final String newName = name + newIndexSuffix();
      targetIndex = newName;

      final IndicesAdminClient idx = getAdminIdx();

      final CreateIndexRequestBuilder cirb = idx
              .prepareCreate(newName);

      final File f = new File(idxpars.getIndexerConfig());

      final byte[] sbBytes = Streams.copyToByteArray(f);

      cirb.setSource(sbBytes);

      final CreateIndexRequest cir = cirb.request();

      final ActionFuture<CreateIndexResponse> af = idx.create(cir);

      /*resp = */
      af.actionGet();

      index(new UpdateInfo());

      info("Index created: change token set to " + currentChangeToken());

      return newName;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    }
  }

  @Override
  public Set<IndexInfo> getIndexInfo() throws CalFacadeException {
    final Set<IndexInfo> res = new TreeSet<>();

    try {
      final IndicesAdminClient idx = getAdminIdx();

      final IndicesStatusRequestBuilder isrb =
              idx.prepareStatus(Strings.EMPTY_ARRAY);

      final ActionFuture<IndicesStatusResponse> sr = idx.status(
              isrb.request());
      final IndicesStatusResponse sresp = sr.actionGet();

      for (final String inm : sresp.getIndices().keySet()) {
        final IndexInfo ii = new IndexInfo(inm);
        res.add(ii);

        final ClusterStateRequest clusterStateRequest = Requests
                .clusterStateRequest()
                .routingTable(true)
                .nodes(true)
                .indices(inm);

        final Iterator<String> it =
                getAdminCluster().state(clusterStateRequest).
                        actionGet().getState().getMetaData().aliases()
                                 .keysIt();
        while (it.hasNext()) {
          ii.addAlias(it.next());
        }
      }

      return res;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public List<String> purgeIndexes() throws CalFacadeException {
    final Set<IndexInfo> indexes = getIndexInfo();
    final List<String> purged = new ArrayList<>();

    if (Util.isEmpty(indexes)) {
      return purged;
    }

    purge:
    for (final IndexInfo ii : indexes) {
      final String idx = ii.getIndexName();

      if (!idx.startsWith(idxpars.getPublicIndexName()) &&
              !idx.startsWith(idxpars.getUserIndexName())) {
        continue purge;
      }

      /* Don't delete those pointed to by the current aliases */

      if (!Util.isEmpty(ii.getAliases())) {
        for (final String alias : ii.getAliases()) {
          if (alias.equals(idxpars.getPublicIndexName())) {
            continue purge;
          }

          if (alias.equals(idxpars.getUserIndexName())) {
            continue purge;
          }
        }
      }

      purged.add(idx);
    }

    deleteIndexes(purged);

    return purged;
  }

  @Override
  public int setAlias(final String index,
                      final String alias) throws CalFacadeException {
    //IndicesAliasesResponse resp = null;
    try {
      /* Other is the alias name - index is the index we were just indexing into
       */

      final IndicesAdminClient idx = getAdminIdx();

      final GetAliasesRequestBuilder igarb = idx.prepareGetAliases(
              alias);

      final ActionFuture<GetAliasesResponse> getAliasesAf =
              idx.getAliases(igarb.request());
      final GetAliasesResponse garesp = getAliasesAf.actionGet();

      final ImmutableOpenMap<String, List<AliasMetaData>> aliasesmeta =
              garesp.getAliases();

      final IndicesAliasesRequestBuilder iarb = idx.prepareAliases();

      final Iterator<String> it = aliasesmeta.keysIt();

      while (it.hasNext()) {
        final String indexName = it.next();

        for (final AliasMetaData amd : aliasesmeta.get(indexName)) {
          if (amd.getAlias().equals(alias)) {
            iarb.removeAlias(indexName, alias);
          }
        }
      }

      iarb.addAlias(index, alias);

      final ActionFuture<IndicesAliasesResponse> af =
              idx.aliases(iarb.request());

      /*resp = */
      af.actionGet();

      return 0;
    } catch (final ElasticsearchException ese) {
      // Failed somehow
      error(ese);
      return -1;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private BwSystem cachedBwSystem;

  @Override
  public GetEntityResponse<EventInfo> fetchEvent(final String href)
          throws CalFacadeException {
    try {
      fetchStart();

      final GetEntityResponse<EventInfo> resp = new GetEntityResponse<>();
      final String recurrenceId;
      final String hrefNorid;

      // Check validity
      final int pos = href.lastIndexOf("/");
      if (pos < 0) {
        throw new RuntimeException("Bad href: " + href);
      }

      final int fragPos = href.lastIndexOf("#");

      if (fragPos < pos) {
        hrefNorid = href;
        recurrenceId = null;
      } else {
        hrefNorid = href.substring(0, fragPos);
        recurrenceId = href.substring(fragPos + 1);
      }

      final FilterBuilder fltr =
              getFilters(null).singleEventFilter(href,
                                                 recurrenceId);

      final SearchHit hit = fetchEntity(docTypeEvent, fltr);

      if (hit == null) {
        return notFound(resp);
      }

      final EntityBuilder eb = getEntityBuilder(hit.sourceAsMap());

      final EventInfo ei = makeEvent(eb, resp, hit.getId(), false);
      if (ei == null) {
        return notFound(resp);
      }

      final BwEvent ev = ei.getEvent();

      final Acl.CurrentAccess ca =
              accessCheck.checkAccess(ev, privRead, true);

      if ((ca == null) || !ca.getAccessAllowed()) {
        return notFound(resp);
      }

      ei.setCurrentAccess(ca);
      resp.setEntity(ei);

      if (ev.getRecurrenceId() != null) {
        // Single instance
        return resp;
      }

      addOverrides(resp, idxpars.getUserIndexName(), ei);

      return resp;
    } finally {
      fetchEnd();
    }
  }

  @Override
  public GetEntityResponse<EventInfo> fetchEvent(String colPath,
                                                 String guid)
          throws CalFacadeException {
    try {
      fetchStart();

      final GetEntityResponse<EventInfo> resp = new GetEntityResponse<>();

      final FilterBuilder fltr =
              getFilters(null).singleEventFilterGuid(colPath, guid);

      final SearchHit hit = fetchEntity(docTypeEvent, fltr);

      if (hit == null) {
        return notFound(resp);
      }

      final EntityBuilder eb = getEntityBuilder(hit.sourceAsMap());

      final EventInfo ei = makeEvent(eb, resp, hit.getId(), false);
      if (ei == null) {
        return notFound(resp);
      }

      final BwEvent ev = ei.getEvent();

      final Acl.CurrentAccess ca =
              accessCheck.checkAccess(ev, privRead, true);

      if ((ca == null) || !ca.getAccessAllowed()) {
        return notFound(resp);
      }

      ei.setCurrentAccess(ca);

      if (ev.getRecurrenceId() != null) {
        // Single instance
        resp.setEntity(ei);
        return resp;
      }

      addOverrides(resp, idxpars.getUserIndexName(), ei);

      return resp;
    } finally {
      fetchEnd();
    }
  }

  @Override
  public BwCategory fetchCat(final String val,
                             final PropertyInfoIndex... index)
          throws CalFacadeException {
    BwCategory entity;

    if ((index.length == 1) &&
            (index[0] == PropertyInfoIndex.HREF)) {
      checkCache();
      entity = getCached(val, BwCategory.class);
      if (entity != null) {
        return entity;
      }
    }

    final EntityBuilder eb = fetchEntity(docTypeCategory, val, index);

    if (eb == null) {
      return null;
    }

    entity = eb.makeCat();
    if (entity == null) {
      return null;
    }

    caches.put(entity);
    return entity;
  }

  @Override
  public GetEntityResponse<BwCalendar> fetchCol(final String val,
                                                final int desiredAccess,
                                                final PropertyInfoIndex... index)
          throws CalFacadeException {
    final GetEntityResponse<BwCalendar> resp = new GetEntityResponse<>();
    BwCalendar entity;

    if ((index.length == 1) &&
            (index[0] == PropertyInfoIndex.HREF)) {
      checkCache();
      entity = getCached(val, desiredAccess, CalendarWrapper.class);

      if (entity != null) {
        resp.setEntity(entity);

        return Response.ok(resp, null);
      }
    }

    final EntityBuilder eb = fetchEntity(docTypeCollection, val,
                                         index);

    if (eb == null) {
      resp.setStatus(notFound);
      return resp;
    }

    entity = makeCollection(eb);

    if (desiredAccess < 0) {
      entity = new CalendarWrapper(entity,
                                   accessCheck.getAccessUtil());
      caches.put(entity, desiredAccess);
      resp.setEntity(entity);

      return Response.ok(resp, null);
    }

    entity = accessCheck.checkAccess(entity,
                                     desiredAccess);

    if (entity == null) {
      return Response.notOk(resp, noAccess, null);
    }
    caches.put(entity, desiredAccess);
    resp.setEntity(entity);

    return Response.ok(resp, null);
  }

  @Override
  public Collection<BwCalendar> fetchChildren(final String href)
          throws CalFacadeException {
    return fetchChildren(href, true);
  }

  @Override
  public Collection<BwCalendar> fetchChildren(final String href,
                                              final boolean excludeTombstoned)
          throws CalFacadeException {
    if (debug) {
      debug("fetchChildren for " + href);
    }

    final ESQueryFilter filters = getFilters(null);
    FilterBuilder fb = filters.colPathFilter(null, href);

    if (excludeTombstoned) {
      fb = filters.termFilter(fb, PropertyInfoIndex.TOMBSTONED,
                              "false");
    }

    final List<BwCalendar> cols =
            fetchEntities(docTypeCollection,
                             new BuildEntity<BwCalendar>() {
                               @Override
                               BwCalendar make(final EntityBuilder eb)
                                       throws CalFacadeException {
                                 return accessCheck.checkAccess(
                                         makeCollection(eb));
                               }
                             },
                             fb,
                          -1);

    if (Util.isEmpty(cols)) {
      return cols;
    }

    return new TreeSet<>(cols); // Sort the result
  }

  @Override
  public Collection<BwCalendar> fetchChildrenDeep(final String href)
          throws CalFacadeException {
    if (debug) {
      debug("fetchChildrenDeep for " + href);
    }

    Collection<BwCalendar> cols = fetchChildren(href);

    if (Util.isEmpty(cols)) {
      return cols;
    }

    Collection<BwCalendar> res = new ArrayList<>(cols);

    for (final BwCalendar col: cols) {
      Collection<BwCalendar> subcols = fetchChildren(col.getHref());

      if (Util.isEmpty(subcols)) {
        return cols;
      }

      res.addAll(subcols);
    }

    return new TreeSet<>(cols); // Sort the result
  }

  @Override
  public BwPrincipal fetchPrincipal(final String val)
          throws CalFacadeException {
    checkCache();
    BwPrincipal entity = getCached(val, BwPrincipal.class);

    if (entity != null) {
      return entity;
    }

    final EntityBuilder eb = fetchEntity(docTypePrincipal, val,
                                         PropertyInfoIndex.HREF);

    if (eb == null) {
      return null;
    }

    entity = eb.makePrincipal();
    if (entity == null) {
      return null;
    }

    caches.put(entity);

    if (entity instanceof BwCalSuitePrincipal) {
      final BwCalSuitePrincipal cs = (BwCalSuitePrincipal)entity;

      if (cs.getGroupHref() != null) {
        cs.setGroup((BwAdminGroup)fetchPrincipal(cs.getGroupHref()));
      }
      return entity;
    }

    if (!(entity instanceof BwGroup)) {
      return entity;
    }

    final BwGroup grp = (BwGroup)entity;

    // Get all member entities
    if (Util.isEmpty(grp.getMemberHrefs())) {
      return entity;
    }

    for (final String href: grp.getMemberHrefs()) {
      final BwPrincipal mbr = fetchPrincipal(href);
      if (mbr == null) {
        warn("Missing member in index: " + href);
        continue;
      }

      grp.addGroupMember(mbr);
    }

    return entity;
  }

  @Override
  public BwPreferences fetchPreferences(String href)
          throws CalFacadeException {
    if (href == null) {
      return null;
    }

    checkCache();
    BwPreferences entity = getCached(href, BwPreferences.class);
    if (entity != null) {
      return entity;
    }

    final EntityBuilder eb = fetchEntity(docTypePreferences, href,
                                         PropertyInfoIndex.HREF);

    if (eb == null) {
      return null;
    }

    entity = eb.makePreferences();
    if (entity == null) {
      return null;
    }

    caches.put(entity);
    return entity;
  }

  @Override
  public BwFilterDef fetchFilter(String href)
          throws CalFacadeException {
    checkCache();
    BwFilterDef entity = getCached(href, BwFilterDef.class);

    if (entity != null) {
      return entity;
    }

    final EntityBuilder eb = fetchEntity(docTypeFilter, href,
                                         PropertyInfoIndex.HREF);

    if (eb == null) {
      return null;
    }

    entity = eb.makefilter();
    if (entity == null) {
      return null;
    }

    caches.put(entity);
    return entity;
  }

  @Override
  public List<BwFilterDef> fetchFilters(final FilterBase fb,
                                        final int count)
          throws CalFacadeException {
    final FilterBuilder f;

    if (fb == null) {
      f = getFilters(null).principalFilter(null);
    } else {
      f = getFilters(null).buildFilter(fb);
    }

    return fetchEntities(docTypeFilter,
                         new BuildEntity<BwFilterDef>() {
                           @Override
                           BwFilterDef make(final EntityBuilder eb)
                                   throws CalFacadeException {
                             return eb.makefilter();
                           }
                         },
                         f,
                         count);
  }

  @Override
  public BwResource fetchResource(String href)
          throws CalFacadeException {
    checkCache();
    BwResource entity = getCached(href, BwResource.class);

    if (entity != null) {
      return entity;
    }

    final EntityBuilder eb = fetchEntity(docTypeResource, href,
                                         PropertyInfoIndex.HREF);

    if (eb == null) {
      return null;
    }

    entity = eb.makeResource();
    if (entity == null) {
      return null;
    }

    caches.put(entity);
    return entity;
  }

  @Override
  public List<BwResource> fetchResources(final String path,
                                         final String lastmod,
                                         final int lastmodSeq,
                                         final int count)
          throws CalFacadeException {
    final FilterBuilder f =
            getFilters(null).resourcesFilter(path, lastmod, lastmodSeq);

    return fetchEntities(docTypeResource,
                         new BuildEntity<BwResource>() {
                           @Override
                           BwResource make(final EntityBuilder eb)
                                   throws CalFacadeException {
                             return eb.makeResource();
                           }
                         },
                         f,
                         count);
  }

  @Override
  public BwResourceContent fetchResourceContent(final String href)
          throws CalFacadeException {
    /* Note - we do not cache these - they are potentially very large */

    final EntityBuilder eb = fetchEntity(docTypeResourceContent, href,
                                         PropertyInfoIndex.HREF);

    if (eb == null) {
      return null;
    }

    final BwResourceContent entity = eb.makeResourceContent();
    if (entity == null) {
      return null;
    }

    return entity;
  }

  @Override
  public BwContact fetchContact(final String val,
                                final PropertyInfoIndex... index)
          throws CalFacadeException {
    BwContact entity;

    if ((index.length == 1) &&
            (index[0] == PropertyInfoIndex.HREF)) {
      checkCache();
      entity = getCached(val, BwContact.class);
      if (entity != null) {
        return entity;
      }
    }

    final EntityBuilder eb = fetchEntity(docTypeContact, val, index);

    if (eb == null) {
      return null;
    }

    entity = eb.makeContact();
    if (entity == null) {
      return null;
    }

    caches.put(entity);
    return entity;
  }

  @Override
  public BwLocation fetchLocation(final String val,
                                  final PropertyInfoIndex... index)
          throws CalFacadeException {
    BwLocation entity;

    if ((index.length == 1) &&
            (index[0] == PropertyInfoIndex.HREF)) {
      checkCache();
      entity = getCached(val, BwLocation.class);
      if (entity != null) {
        return entity;
      }
    }

    final EntityBuilder eb = fetchEntity(docTypeLocation, val, index);

    if (eb == null) {
      return null;
    }

    entity = eb.makeLocation();
    if (entity == null) {
      return null;
    }

    caches.put(entity);
    return entity;
  }

  @Override
  public GetEntityResponse<BwLocation> fetchLocationByKey(final String name,
                                                          final String val) {
    final GetEntityResponse<BwLocation> resp =
            new GetEntityResponse<>();

    try {
      final SearchHit hit =
              fetchEntity(docTypeLocation,
                          getFilters(null)
                                  .locationKeyFilter(name,
                                                     val));

      if (hit == null) {
        return notFound(resp);
      }

      final EntityBuilder eb = getEntityBuilder(hit.sourceAsMap());

      final BwLocation loc = eb.makeLocation();

      if (loc == null) {
        return notFound(resp);
      }

      resp.setEntity(loc);
      return resp;
    } catch (final CalFacadeException cfe) {
      return errorReturn(resp, cfe);
    }
  }

  private static final int maxFetchCount = 100;
  private static final int absoluteMaxTries = 1000;

  @Override
  public List<BwCategory> fetchAllCats() throws CalFacadeException {
    return fetchEntities(docTypeCategory,
                         new BuildEntity<BwCategory>() {
                           @Override
                           BwCategory make(final EntityBuilder eb)
                                   throws CalFacadeException {
                             return eb.makeCat();
                           }
                         },
                         -1);
  }

  @Override
  public List<BwContact> fetchAllContacts()
          throws CalFacadeException {
    return fetchEntities(docTypeContact,
                         new BuildEntity<BwContact>() {
                           @Override
                           BwContact make(final EntityBuilder eb)
                                   throws CalFacadeException {
                             return eb.makeContact();
                           }
                         },
                         -1);
  }

  @Override
  public List<BwLocation> fetchAllLocations()
          throws CalFacadeException {
    return fetchEntities(docTypeLocation,
                         new BuildEntity<BwLocation>() {
                           @Override
                           BwLocation make(final EntityBuilder eb)
                                   throws CalFacadeException {
                             return eb.makeLocation();
                           }
                         }, -1);
  }

  @Override
  public GetEntitiesResponse<BwContact> findContacts(final FilterBase filter,
                                                     final int from,
                                                     final int size) {
    final GetEntitiesResponse<BwContact> resp = new GetEntitiesResponse<>();

    try {
      final ESQueryFilter ef = getFilters(null);

      QueryBuilder qb = ef.buildQuery(filter);

      FilterBuilder curFilter = ef.buildFilter(ef.addTypeFilter(null,
                                                                docTypeContact));

      if (!(curFilter instanceof MatchNone)) {
        curFilter = ef.addLimits(curFilter,
                                 null, DeletedState.noDeleted);
      }

      if (curFilter instanceof MatchNone) {
        resp.setStatus(notFound);
        return resp;
      }

      final SearchRequestBuilder srb = getClient()
              .prepareSearch(searchIndexes);

      srb.setSearchType(SearchType.QUERY_AND_FETCH)
         .setQuery(qb)
         .setPostFilter(curFilter)
         .setFrom(from)
         .setSize(size);

      /*
      if (!Util.isEmpty(res.curSort)) {
        SortOrder so;

        for (final SortTerm st : res.curSort) {
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

*/

      if (debug) {
        debug("Search: targetIndex=" + targetIndex +
                      "; srb=" + srb);
      }

      final SearchResponse sresp = srb.execute().actionGet();

//    if (resp.status() != RestStatus.OK) {
      //TODO
//    }

      if (debug) {
        debug("Search: returned status " + sresp.status() +
                      " found: " + sresp.getHits().getTotalHits());
      }

      for (final SearchHit hit : sresp.getHits().getHits()) {
        final Object entity = makeEntity(resp, hit, null);
        if (!resp.isOk()) {
          return resp;
        }

        if (!(entity instanceof BwContact)) {
          continue;
        }

        final BwEventProperty evp = (BwEventProperty)entity;

        evp.setScore(hit.score());

        resp.addEntity((BwContact)entity);
      }

      return resp;
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }

  @Override
  public GetEntitiesResponse<BwLocation> findLocations(final FilterBase filter,
                                                       final int from,
                                                       final int size) {
    final GetEntitiesResponse<BwLocation> resp = new GetEntitiesResponse<>();

    try {
      final ESQueryFilter ef = getFilters(null);

      QueryBuilder qb = ef.buildQuery(filter);

      FilterBuilder curFilter = ef.buildFilter(ef.addTypeFilter(null,
                                                                docTypeLocation));

      if (!(curFilter instanceof MatchNone)) {
        curFilter = ef.addLimits(curFilter,
                                 null, DeletedState.noDeleted);
      }

      if (curFilter instanceof MatchNone) {
        resp.setStatus(notFound);
        return resp;
      }

      final SearchRequestBuilder srb = getClient()
              .prepareSearch(searchIndexes);

      srb.setSearchType(SearchType.QUERY_AND_FETCH)
         .setQuery(qb)
         .setPostFilter(curFilter)
         .setFrom(from)
         .setSize(size);

      /*
      if (!Util.isEmpty(res.curSort)) {
        SortOrder so;

        for (final SortTerm st : res.curSort) {
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

*/

      if (debug) {
        debug("Search: targetIndex=" + targetIndex +
                      "; srb=" + srb);
      }

      final SearchResponse sresp = srb.execute().actionGet();

//    if (resp.status() != RestStatus.OK) {
      //TODO
//    }

      if (debug) {
        debug("Search: returned status " + sresp.status() +
                      " found: " + sresp.getHits().getTotalHits());
      }

      for (final SearchHit hit : sresp.getHits().getHits()) {
        final Object entity = makeEntity(resp, hit, null);
        if (!resp.isOk()) {
          return resp;
        }

        if (!(entity instanceof BwLocation)) {
          continue;
        }

        final BwEventProperty evp = (BwEventProperty)entity;

        evp.setScore(hit.score());
        resp.addEntity((BwLocation)entity);
      }

      return resp;
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }

  @Override
  public GetEntitiesResponse<BwCategory> findCategories(final FilterBase filter,
                                                        final int from,
                                                        final int size) {
    final GetEntitiesResponse<BwCategory> resp = new GetEntitiesResponse<>();

    try {
      final ESQueryFilter ef = getFilters(null);

      QueryBuilder qb = ef.buildQuery(filter);

      FilterBuilder curFilter = ef.buildFilter(ef.addTypeFilter(null,
                                                                docTypeCategory));

      if (!(curFilter instanceof MatchNone)) {
        curFilter = ef.addLimits(curFilter,
                                 null, DeletedState.noDeleted);
      }

      if (curFilter instanceof MatchNone) {
        resp.setStatus(notFound);
        return resp;
      }

      final SearchRequestBuilder srb = getClient()
              .prepareSearch(searchIndexes);

      srb.setSearchType(SearchType.QUERY_AND_FETCH)
         .setQuery(qb)
         .setPostFilter(curFilter)
         .setFrom(from)
         .setSize(size);

      /*
      if (!Util.isEmpty(res.curSort)) {
        SortOrder so;

        for (final SortTerm st : res.curSort) {
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

*/

      final SearchResponse sresp = srb.execute().actionGet();

//    if (resp.status() != RestStatus.OK) {
      //TODO
//    }

      if (debug) {
        debug("Search: returned status " + sresp.status() +
                      " found: " + sresp.getHits().getTotalHits());
      }

      for (final SearchHit hit : sresp.getHits().getHits()) {
        final Object entity = makeEntity(resp, hit, null);
        if (!resp.isOk()) {
          return resp;
        }

        if (!(entity instanceof BwCategory)) {
          continue;
        }

        final BwEventProperty evp = (BwEventProperty)entity;

        evp.setScore(hit.score());

        resp.addEntity((BwCategory)entity);
      }

      return resp;
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private boolean checkCache() throws CalFacadeException {
    final long now = System.currentTimeMillis();

    if ((now - lastChangeTokenCheck) <= lastChangeTokenCheckPeriod) {
      return true;
    }

    lastChangeTokenCheck = now;
    final String changeToken = currentChangeToken();

    if ((changeToken == null) ||
            changeToken.equals(lastChangeToken)) {
      return true;
    }

    lastChangeToken = changeToken;
    caches.clear();

    return false;
  }

  synchronized <T extends BwUnversionedDbentity> T getCached(final String href,
                                                             final Class<T> resultType)
          throws CalFacadeException {
    if (!checkCache()) {
      return null;
    }

    return caches.get(href, resultType);
  }

  synchronized <T extends BwUnversionedDbentity> T getCached(final String href,
                                                             final int desiredAccess,
                                                             final Class<T> resultType)
          throws CalFacadeException {
    if (!checkCache()) {
      return null;
    }

    return caches.get(href, desiredAccess, resultType);
  }

  private SearchHits multiColFetch(final List<String> hrefs)
          throws CalFacadeException {
    final int batchSize = hrefs.size();

    final SearchRequestBuilder srb = getClient()
            .prepareSearch(searchIndexes);

    final TermsQueryBuilder tqb =
            new TermsQueryBuilder(
                    ESQueryFilter.getJname(PropertyInfoIndex.HREF),
                    hrefs);

    srb.setSearchType(SearchType.QUERY_THEN_FETCH)
       .setQuery(tqb);
    srb.setFrom(0);
    srb.setSize(batchSize);

    if (debug) {
      debug("MultiColFetch: targetIndex=" + targetIndex +
                    "; srb=" + srb);
    }

    final SearchResponse resp = srb.execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + resp.status());
      }

      return null;
    }

    final SearchHits hits = resp.getHits();

    if ((hits.getHits() == null) ||
            (hits.getHits().length == 0)) {
      return null;
    }

    //Break condition: No hits are returned
    if (hits.hits().length == 0) {
      return null;
    }

    return hits;
  }

  private List<SearchHit> multiFetch(final SearchHits hits,
                                     final RecurringRetrievalMode rmode)
          throws CalFacadeException {
    // Make an ored filter from keys

    final Set<String> hrefs = new TreeSet<>(); // Dedup
    
    /* We may get many more entries than the hits we got for the first search.
       Each href may have many entries that don't match the original search term
       We need to keep fetching until nothign is returned
     */

    for (final SearchHit hit : hits) {
      final String dtype = hit.getType();

      if (dtype == null) {
        throw new CalFacadeException("org.bedework.index.noitemtype");
      }

      final String kval = hit.getId();

      if (kval == null) {
        throw new CalFacadeException("org.bedework.index.noitemkey");
      }

      final SearchHitField hrefField = hit
              .field(ESQueryFilter.hrefJname);

      hrefs.add((String)hrefField.getValue());
    }

    final int batchSize = (int)hits.getTotalHits() * 100;
    int start = 0;
    final List<SearchHit> res = new ArrayList<>();

    while (true) {
      final SearchRequestBuilder srb = getClient()
              .prepareSearch(searchIndexes);

      srb.setSearchType(SearchType.QUERY_THEN_FETCH)
         .setPostFilter(getFilters(null).multiHrefFilter(hrefs,
                                                         rmode));
      srb.setFrom(start);
      srb.setSize(batchSize);

      if (debug) {
        debug("MultiFetch: targetIndex=" + targetIndex +
                      "; srb=" + srb);
      }

      final SearchResponse resp = srb.execute().actionGet();

      if (resp.status() != RestStatus.OK) {
        if (debug) {
          debug("Search returned status " + resp.status());
        }

        return null;
      }

      final SearchHit[] hits2 = resp.getHits().getHits();

      if ((hits2 == null) ||
              (hits2.length < 0)) {
        // No more data - we're done
        return res;
      }

      res.addAll(Arrays.asList(hits2));

      if (hits2.length < batchSize) {
        // All remaining in this batch - we're done
        return res;
      }

      start += batchSize;
    }
  }

  private void deleteIndexes(final List<String> names)
          throws CalFacadeException {
    try {
      final IndicesAdminClient idx = getAdminIdx();
      final DeleteIndexRequestBuilder dirb = getAdminIdx()
              .prepareDelete(
                      names.toArray(new String[names.size()]));

      final ActionFuture<DeleteIndexResponse> dr = idx.delete(
              dirb.request());
      /*DeleteIndexResponse dir = */
      dr.actionGet();
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static abstract class BuildEntity<T> {
    abstract T make(EntityBuilder eb) throws CalFacadeException;
  }

  private <T> List<T> fetchEntities(final String docType,
                                    final BuildEntity<T> be,
                                    final int count)
          throws CalFacadeException {
    return fetchEntities(docType, be,
                         getFilters(null).principalFilter(null),
                         count);
  }

  private <T> List<T> fetchEntities(final String docType,
                                    final BuildEntity<T> be,
                                    final FilterBuilder filter,
                                    final int count)
          throws CalFacadeException {
    final SearchRequestBuilder srb = getClient()
            .prepareSearch(targetIndex);

    srb.setTypes(docType);

    if (debug) {
      debug("fetchAllEntities: srb=" + srb);
    }

    int tries = 0;
    final int ourCount;

    if (count < 0) {
      ourCount = maxFetchCount;
    } else {
      ourCount = Math.min(maxFetchCount, count);
    }

    final List<T> res = new ArrayList<>();

    SearchResponse scrollResp = srb.setSearchType(SearchType.SCAN)
                                   .setScroll(new TimeValue(60000))
                                   .setPostFilter(filter)
                                   .setSize(ourCount).execute()
                                   .actionGet(); //ourCount hits per shard will be returned for each scroll

    if (scrollResp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + scrollResp.status());
      }
    }

    for (; ; ) {
      if (tries > absoluteMaxTries) {
        // huge count or we screwed up
        warn("Indexer: too many tries");
        break;
      }

      scrollResp = getClient()
              .prepareSearchScroll(scrollResp.getScrollId())
              .setScroll(new TimeValue(600000)).execute().actionGet();
      if (scrollResp.status() != RestStatus.OK) {
        if (debug) {
          debug("Search returned status " + scrollResp.status());
        }
      }

      final SearchHits hits = scrollResp.getHits();

      //Break condition: No hits are returned
      if (hits.hits().length == 0) {
        break;
      }

      for (final SearchHit hit : hits) {
        //Handle the hit...
        final T ent = be.make(getEntityBuilder(hit.sourceAsMap()));
        if (ent == null) {
          // No access
          continue;
        }
        res.add(ent);
        //ourPos++;
      }

      tries++;
    }

    return res;
  }

  private EntityBuilder fetchEntity(final String docType,
                                    final String val,
                                    final PropertyInfoIndex... index)
          throws CalFacadeException {
    try {
      fetchStart();

      if ((index.length == 1) &&
              (index[0] == PropertyInfoIndex.HREF)) {
        final GetRequestBuilder grb = getClient()
                .prepareGet(targetIndex,
                            docType,
                            val);

        final GetResponse gr = grb.execute().actionGet();

        if (!gr.isExists()) {
          return null;
        }

        return getEntityBuilder(gr.getSourceAsMap());
      }

      final SearchHit hit = fetchEntity(docType,
                                        getFilters(null)
                                                .singleEntityFilter(
                                                        docType, val,
                                                        index));

      if (hit == null) {
        return null;
      }

      return getEntityBuilder(hit.sourceAsMap());
    } finally {
      fetchEnd();
    }
  }

  private SearchHit fetchEntity(final String docType,
                                final FilterBuilder fltr)
          throws CalFacadeException {
    final SearchRequestBuilder srb = getClient()
            .prepareSearch(searchIndexes);

    srb.setTypes(docType);

    final SearchResponse response = srb
            .setSearchType(SearchType.QUERY_THEN_FETCH)
            .setPostFilter(fltr)
            .setFrom(0).setSize(60).setExplain(true)
            .execute()
            .actionGet();

    final SearchHits hits = response.getHits();

    if (hits.hits().length == 0) {
      // No match
      return null;
    }

    if (hits.getTotalHits() != 1) {
      error("Multiple entities of type " + docType +
                    " with filter " + fltr);
      return null;
    }

    return hits.hits()[0];
  }

  private static class DateLimits {
    String minStart;
    String maxEnd;

    void checkMin(final BwDateTime tm) {
      final String val;
      if (tm.getDateType()) {
        val = tm.getDtval();
      } else {
        val = tm.getDate();
      }

      if (minStart == null) {
        minStart = val;
        return;
      }

      if (minStart.compareTo(val) > 0) {
        minStart = val;
      }
    }

    void checkMax(final BwDateTime tm) {
      final String val;
      if (tm.getDateType()) {
        val = tm.getDtval();
      } else {
        val = tm.getDate();
      }

      if (maxEnd == null) {
        maxEnd = val;
        return;
      }

      if (maxEnd.compareTo(val) < 0) {
        maxEnd = val;
      }
    }
  }

  /* Return the response after indexing */
  private IndexResponse index(final Object rec) throws CalFacadeException {
    EsDocInfo di = null;

    try {
      if (rec instanceof EventInfo) {
        return indexEvent((EventInfo)rec);
      }

      final DocBuilder db = getDocBuilder();

      if (rec instanceof UpdateInfo) {
        di = db.makeDoc((UpdateInfo)rec);
      }

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

      if (rec instanceof BwPrincipal) {
        di = db.makeDoc((BwPrincipal)rec);
      }

      if (rec instanceof BwPreferences) {
        di = db.makeDoc((BwPreferences)rec);
      }

      if (rec instanceof BwResource) {
        di = db.makeDoc((BwResource)rec);
      }

      if (rec instanceof BwResourceContent) {
        di = db.makeDoc((BwResourceContent)rec);
      }

      if (rec instanceof BwFilterDef) {
        di = db.makeDoc((BwFilterDef)rec);
      }

      if (di != null) {
        return indexDoc(di);
      }

      throw new CalFacadeException(
              new IndexException(IndexException.unknownRecordType,
                                 rec.getClass().getName()));
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final VersionConflictEngineException vcee) {
      if (vcee.getCurrentVersion() == vcee.getProvidedVersion()) {
        warn("Failed index with equal version for type " + 
                     di.getType() +
                     " and id " + di.getId());
      }

      return null;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private EsDocInfo makeDoc(final Response resp,
                            final Object rec) {
    try {
      if (rec instanceof EventInfo) {
        errorReturn(resp, "Illegal call with event to makeDoc");
        return null;
      }

      final DocBuilder db = getDocBuilder();

      if (rec instanceof UpdateInfo) {
        return db.makeDoc((UpdateInfo)rec);
      }

      if (rec instanceof BwCalendar) {
        return db.makeDoc((BwCalendar)rec);
      }

      if (rec instanceof BwCategory) {
        return db.makeDoc((BwCategory)rec);
      }

      if (rec instanceof BwContact) {
        return db.makeDoc((BwContact)rec);
      }

      if (rec instanceof BwLocation) {
        return db.makeDoc((BwLocation)rec);
      }

      errorReturn(resp, "Unknown record type: " + rec.getClass().getName());
      return null;
    } catch (final Throwable t) {
      errorReturn(resp, t);
    }

    return null;
  }

  private IndexResponse indexEvent(final EventInfo ei) throws CalFacadeException {
    try {

      /* If it's not recurring or a stand-alone instance index it */

      final BwEvent ev = ei.getEvent();

      if (!ev.testRecurring() && (ev.getRecurrenceId() == null)) {
        return indexEvent(ei,
                          ItemKind.master,
                          ev.getDtstart(),
                          ev.getDtend(),
                          null, //ev.getRecurrenceId(),
                          null);
      }

      if (ev.getRecurrenceId() != null) {
        error("Not implemented - index of single override");
        return null;
      }

      /* Delete all instances of this event: we'll do a delete by query
       * We need to find all with the same path and uid.
       */

      /* TODO - do a query for all recurrence ids and delete the ones
          we don't want.
       */

      deleteEvent(ei);

      /* Create a list of all instance date/times before overrides. */

      final int maxYears;
      final int maxInstances;
      final DateLimits dl = new DateLimits();

      if (ev.getPublick()) {
        maxYears = unauthpars.getMaxYears();
        maxInstances = unauthpars.getMaxInstances();
      } else {
        maxYears = authpars.getMaxYears();
        maxInstances = authpars.getMaxInstances();
      }

      final RecurPeriods rp = RecurUtil.getPeriods(ev, maxYears, maxInstances);

      if (rp.instances.isEmpty()) {
        // No instances for an alleged recurring event.
        return null;
        //throw new CalFacadeException(CalFacadeException.noRecurrenceInstances);
      }

      final String stzid = ev.getDtstart().getTzid();

      int instanceCt = maxInstances;

      final boolean dateOnly = ev.getDtstart().getDateType();

      /* First build a table of overrides so we can skip these later
       */
      final Map<String, String> overrides = new HashMap<>();

      /*
      if (!Util.isEmpty(ei.getOverrideProxies())) {
        for (BwEvent ov: ei.getOverrideProxies()) {
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());
        }
      }
      */
      final IndexResponse iresp;

      if (!Util.isEmpty(ei.getOverrides())) {
        for (final EventInfo oei: ei.getOverrides()) {
          final BwEvent ov = oei.getEvent();
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());

          final String start;
          if (ov.getDtstart().getDateType()) {
            start = ov.getRecurrenceId().substring(0, 8);
          } else {
            start = ov.getRecurrenceId();
          }
          final BwDateTime rstart =
                  BwDateTime.makeBwDateTime(ov.getDtstart().getDateType(),
                                            start,
                                            stzid);
          final BwDateTime rend =
                  rstart.addDuration(BwDuration.makeDuration(ov.getDuration()));

          /*iresp = */indexEvent(oei,
                                 ItemKind.override,
                                 rstart,
                                 rend,
                                 ov.getRecurrenceId(),
                                 dl);

          instanceCt--;
        }
      }

      //<editor-fold desc="Emit all instances that aren't overridden">

      for (final Period p: rp.instances) {
        String dtval = p.getStart().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        final BwDateTime rstart =
                BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        if (overrides.get(rstart.getDate()) != null) {
          // Overrides indexed separately - skip this instance.
          continue;
        }

        final String recurrenceId = rstart.getDate();

        dtval = p.getEnd().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        final BwDateTime rend =
                BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        /*iresp = */indexEvent(ei,
                               entity,
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
      //</editor-fold>

      //<editor-fold desc="Emit the master event with a date range covering the entire period.">
      final BwDateTime start =
              BwDateTime.makeBwDateTime(dateOnly,
                                        dl.minStart, stzid);
      final BwDateTime end =
              BwDateTime.makeBwDateTime(dateOnly,
                                        dl.maxEnd, stzid);
      iresp = indexEvent(ei,
                         ItemKind.master,
                         start,
                         end,
                         null,
                         null);
      //</editor-fold>

      return iresp;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private boolean deleteEvent(final EventInfo ei) throws CalFacadeException {
    return deleteEvent(ei.getEvent().getHref());
  }

  private boolean deleteEvent(final String href) throws CalFacadeException {
    final DeleteByQueryRequestBuilder delQreq =
            getClient().prepareDeleteByQuery(targetIndex).
                    setTypes(docTypeEvent,docTypePoll);

    final ESQueryFilter esq= getFilters(null);

    final FilterBuilder fb = esq.addTerm(PropertyInfoIndex.HREF,
                                         href);

    delQreq.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                                                 fb));
    final DeleteByQueryResponse delResp = delQreq.execute()
            .actionGet();

    boolean ok = true;

    for (final IndexDeleteByQueryResponse idqr: delResp.getIndices().values()) {
      if (idqr.getFailedShards() > 0) {
        warn("Failing shards for delete href: " + href +
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
    final BwEvent ev = ei.getEvent();

    try {
      final DocBuilder db = getDocBuilder();
      final EsDocInfo di = db.makeDoc(ei,
                                      kind,
                                      start,
                                      end,
                                      recurid);

      if (dl != null) {
        dl.checkMin(start);
        dl.checkMax(end);
      }

      return indexDoc(di);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final VersionConflictEngineException vcee) {
      if (vcee.getCurrentVersion() == vcee.getProvidedVersion()) {
        warn("Failed index with equal version for kind " + kind +
                     " and href " + ev.getHref());
      }

      return null;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private IndexResponse indexDoc(final EsDocInfo di) throws Throwable {
    //batchCurSize++;
    final IndexRequestBuilder req = getClient().
            prepareIndex(targetIndex, di.getType(), di.getId());

    req.setSource(di.getSource());

    if (di.getVersion() != 0) {
      req.setVersion(di.getVersion()).setVersionType(VersionType.EXTERNAL);
    }

    if (debug) {
      debug("Indexing to index " + targetIndex +
                    " with DocInfo " + di);
    }

    return req.execute().actionGet();
  }

  private Client getClient(final Response resp) {
    try {
      return getClient();
    } catch (final CalFacadeException cfe) {
      resp.setStatus(failed);
      resp.setMessage(cfe.getLocalizedMessage());
      return null;
    }
  }

  private Client getClient() throws CalFacadeException {
    if (theClient != null) {
      return theClient;
    }

    synchronized (clientSyncher) {
      if (theClient != null) {
        return theClient;
      }

      if (idxpars.getEmbeddedIndexer()) {
        /* Start up a node and get a client from it.
         */
        final ImmutableSettings.Builder settings =
                ImmutableSettings.settingsBuilder();

        if (idxpars.getNodeName() != null) {
          settings.put("node.name", idxpars.getNodeName());
        }

        settings.put("path.data", idxpars.getDataDir());

        if (idxpars.getHttpEnabled()) {
          warn("*************************************************************");
          warn("*************************************************************");
          warn("*************************************************************");
          warn("http is enabled for the indexer. This is a security risk.    ");
          warn("Turn it off in the indexer configuration.                    ");
          warn("*************************************************************");
          warn("*************************************************************");
          warn("*************************************************************");
        }
        settings.put("http.enabled", idxpars.getHttpEnabled());
        final NodeBuilder nbld = NodeBuilder.nodeBuilder()
                .settings(settings);

        if (idxpars.getClusterName() != null) {
          nbld.clusterName(idxpars.getClusterName());
        }

        final Node theNode = nbld.data(true).local(true).node();

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
        final ClusterHealthRequestBuilder chrb =
                theClient.admin().cluster().prepareHealth();

        final ClusterHealthResponse chr = chrb.execute().actionGet();

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
        } catch(final InterruptedException ex) {
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
    return new ESQueryFilter(publick,
                             currentMode, principal, superUser,
                             recurRetrieval);
  }

  private String newIndexSuffix() {
    // ES only allows lower case letters in names (and digits)
    final StringBuilder suffix = new StringBuilder("p");

    final char[] ch = DateTimeUtil.isoDateTime().toCharArray();

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

  private static Map<Class, String> classToDoctype = new HashMap<>();
  static {
    classToDoctype.put(BwPrincipal.class, docTypePrincipal);
    classToDoctype.put(BwCalendar.class, docTypeCollection);
    classToDoctype.put(BwEvent.class, docTypeEvent);
    classToDoctype.put(BwCategory.class, docTypeCategory);
    classToDoctype.put(BwContact.class, docTypeContact);
    classToDoctype.put(BwLocation.class, docTypeLocation);
  }

  private String docTypeFromClass(final Class cl) {
    final String docType = classToDoctype.get(cl);

    if (docType != null) {
      return docType;
    }

    return docTypeUnknown;
  }

  private Object makeEntity(final Response resp,
                            final SearchHit hit,
                            final RecurringRetrievalMode rrm) {
    final String dtype = hit.getType();

    if (dtype == null) {
      errorReturn(resp, "org.bedework.index.noitemtype");
      return null;
    }

    final String kval = hit.getId();

    if (kval == null) {
      errorReturn(resp, "org.bedework.index.noitemkey");
    }

    try {
      final EntityBuilder eb = getEntityBuilder(hit.sourceAsMap());

      Object entity = null;
      switch (dtype) {
        case docTypeCollection:
          entity = makeCollection(eb);
          break;
        case docTypeCategory:
          entity = eb.makeCat();
          break;
        case docTypeContact:
          entity = eb.makeContact();
          break;
        case docTypeLocation:
          entity = eb.makeLocation();
          break;
        case docTypeEvent: case docTypePoll:
          entity = makeEvent(eb, resp, kval,
                             (rrm != null) && (rrm.mode == Rmode.expanded));
          break;
      }
      
      return entity;
    } catch (final CalFacadeException cfe) {
      errorReturn(resp, cfe);
      return null;
    }
  }

  private BwCalendar makeCollection(final EntityBuilder eb) throws CalFacadeException {
    final BwCalendar entity = eb.makeCollection();

    if (entity != null) {
      restoreCategories(entity);
    }

    return entity;
  }

  private EventInfo makeEvent(final EntityBuilder eb,
                              final Response resp,
                              final String kval,
                              final boolean expanded) throws CalFacadeException {
    final EventInfo entity = eb.makeEvent(kval, expanded);

    if (entity == null) {
      return null;
    }

    restoreEvent(resp, entity);

    return entity;
  }

  private void restoreEvent(final Response resp,
                            final Collection<EventInfo> eis) throws CalFacadeException {
    if (Util.isEmpty(eis)) {
      return;
    }

    for (final EventInfo ei: eis) {
      restoreEvent(resp, ei);
    }
  }

  private void restoreEvent(final Response resp,
                            final EventInfo ei) throws CalFacadeException {
    if (ei == null) {
      return;
    }

    final BwEvent ev = ei.getEvent();

    restoreCategories(ev);
    restoreEvent(resp, ei.getContainedItems());
    restoreEvent(resp, ei.getOverrides());

    try {
      final Set<String> contactHrefs = ev.getContactHrefs();
      if (!Util.isEmpty(contactHrefs)) {
        for (final String href : contactHrefs) {
          BwContact evprop = fetchContact(href, PropertyInfoIndex.HREF);
          if (evprop == null) {
            warn("Unable to fetch contact " + href +
                         " for event " + ev.getHref());
            errorReturn(resp, "Unable to fetch contact " + href);
            continue;
          }

          ev.addContact(evprop);
        }
      }

      final String href = ev.getLocationHref();
      if (href != null) {
        BwLocation evprop = fetchLocation(href, PropertyInfoIndex.HREF);
        if (evprop == null) {
          warn("Unable to fetch location " + href +
                       " for event " + ev.getHref());
          errorReturn(resp, "Unable to fetch location " + href);
        } else {
          ev.setLocation(evprop);
        }
      }
    } catch (final Throwable t) {
      error(t);
      errorReturn(resp, t);
    }
  }

  private void restoreCategories(final CategorisedEntity ce) throws CalFacadeException {
    if (ce == null) {
      return;
    }

    final Set<String> hrefs = ce.getCategoryHrefs();
    if (Util.isEmpty(hrefs)) {
      return;
    }

    // Check we didn't do this
    final Set<BwCategory> cats = ce.getCategories();
    if (!Util.isEmpty(cats) && (cats.size() == hrefs.size())) {
      return;
    }

    for (final String href: hrefs) {
      final BwCategory cat = fetchCat(href, PropertyInfoIndex.HREF);

      if (cat == null) {
        error("Attempting to store null for cat "
                      + href);
        continue;
      }

      ce.addCategory(cat);
    }
  }

  private EntityBuilder getEntityBuilder(final Map<String, ?> fields) throws CalFacadeException {
    try {
      return new EntityBuilder(publick, currentMode, fields);
    } catch (final IndexException ie) {
      throw new CalFacadeException(ie);
    }
  }

  private DocBuilder getDocBuilder() throws CalFacadeException {
    try {
      return new DocBuilder(principal, basicSysprops);
    } catch (final IndexException ie) {
      throw new CalFacadeException(ie);
    }
  }

  private <T extends Response> T errorReturn(final T resp,
                                             final Throwable t) {
    return errorReturn(resp, t, failed);
  }

  private <T extends Response> T errorReturn(final T resp,
                                             final Throwable t,
                                             final Response.Status st) {
    if (debug) {
      error(t);
    }
    return errorReturn(resp, t.getMessage(), st);
  }

  private <T extends Response> T errorReturn(final T resp,
                                             final String msg,
                                             final Response.Status st) {
    resp.setMessage(msg);
    resp.setStatus(st);

    return resp;
  }

  private <T extends Response> T notFound(final T resp) {
    resp.setStatus(notFound);

    return resp;
  }

  private <T extends Response> T errorReturn(final T resp,
                                             final String msg) {
    if (resp == null) {
      return resp;
    }

    if (resp.getMessage() != null) {
      resp.setMessage(resp.getMessage() + "\n" + msg);
    } else {
      resp.setMessage(msg);
    }
    resp.setStatus(failed);

    return resp;
  }
}
