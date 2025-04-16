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

import org.bedework.access.CurrentAccess;
import org.bedework.base.exc.BedeworkException;
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
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.filter.SortTerm;
import org.bedework.calfacade.indexing.BwIndexFetcher;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexerParams;
import org.bedework.calfacade.indexing.IndexStatsResponse;
import org.bedework.calfacade.indexing.ReindexResponse;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.convert.RecurUtil;
import org.bedework.convert.RecurUtil.RecurPeriods;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.indexing.ContextInfo;
import org.bedework.util.indexing.IndexException;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.base.response.GetEntitiesResponse;
import org.bedework.base.response.GetEntityResponse;
import org.bedework.base.response.Response;
import org.bedework.util.opensearch.DocBuilderBase.UpdateInfo;
import org.bedework.util.opensearch.EsDocInfo;
import org.bedework.util.opensearch.SearchClient;
import org.bedework.util.timezones.DateTimeUtil;

import net.fortuna.ical4j.model.Period;
import org.opensearch.OpenSearchException;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkProcessor;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.action.search.SearchType;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.GetAliasesResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.cluster.metadata.AliasMetadata;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.VersionType;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.index.query.MatchNoneQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.Operator;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.script.Script;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.bedework.access.PrivilegeDefs.privRead;
import static org.bedework.calcore.indexing.DocBuilder.ItemKind.entity;
import static org.bedework.calfacade.indexing.BwIndexer.IndexedType.unreachableEntities;
import static org.bedework.base.response.Response.Status.failed;
import static org.bedework.base.response.Response.Status.noAccess;
import static org.bedework.base.response.Response.Status.notFound;
import static org.bedework.base.response.Response.Status.ok;
import static org.bedework.base.response.Response.Status.processing;
import static org.bedework.util.opensearch.DocBuilderBase.updateTrackerId;

/** Implementation of indexer for OpenSearch
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
public class BwIndexEsImpl implements Logged, BwIndexer {
  //private int batchMaxSize = 0;
  //private int batchCurSize = 0;

  // private Object batchLock = new Object();

  private final boolean publick;
  private String principalHref;
  private final boolean superUser;

  private final AccessChecker accessCheck;

  private static SearchClient sch;

  private final String docType;

  private String targetIndex;
  private final String[] searchIndexes;

  private static String lastChangeToken;
  private static long lastChangeTokenCheck;
  private final int lastChangeTokenCheckPeriod = 2000;

  final static EntityCaches caches = new EntityCaches();

  private final AuthProperties authpars;
  private final AuthProperties unauthpars;
  private final IndexProperties idxpars;
  private final SystemProperties sysprops;

  /* Indexed by index name */
  private final static Map<String, UpdateInfo> updateInfo = new HashMap<>();

  private final static Set<String> knownTypesLowered =
          new TreeSet<>();

  static {
    for (final String type : allDocTypes) {
      knownTypesLowered.add(type.toLowerCase());
    }
  }

  private final BwIndexerParams params;

  private final BwIndexFetcher indexFetcher;

  /* This is used for testing - we delay searches to give the indexer
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

      if ((debug()) && ((fetches % 100) == 0)) {
        debug(String.format(
                "fetches: %d, nestedFetches: %d, " +
                        "fetchTime: %d, fetcAccessTime: %d",
                fetches, nestedFetches, fetchTime, fetchAccessTime));
      }
    } else if (fetchDepth > 0) {
      fetchDepth--;
    }
  }

  private void fetchAccessEnd() {
    if ((fetchAccessDepth <= 0) && (fetchAccessStart != 0)) {
      fetchAccessTime += (System.currentTimeMillis() - fetchAccessStart);
      fetchAccessStart = 0;
    } else if (fetchAccessDepth > 0) {
      fetchAccessDepth--;
    }
  }

  private class TimedAccessChecker implements AccessChecker {
    private final AccessChecker accessCheck;

    TimedAccessChecker(final AccessChecker accessCheck) {
      this.accessCheck = accessCheck;
    }

    public CurrentAccess checkAccess(final BwShareableDbentity<?> ent,
                                     final int desiredAccess,
                                     final boolean returnResult) {
      try {
        fetchAccessStart();

        return accessCheck
                .checkAccess(ent, desiredAccess, returnResult);
      } finally {
        fetchAccessEnd();
      }
    }

    public CalendarWrapper checkAccess(final BwCalendar val) {
      try {
        fetchAccessStart();

        return accessCheck.checkAccess(val);
      } finally {
        fetchAccessEnd();
      }
    }

    public CalendarWrapper checkAccess(final BwCalendar val,
                                       final int desiredAccess) {
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
  private ReindexResponse currentReindexing;

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
   * @param configs       - the configurations object
   * @param docType       type of entity
   * @param publick       - if false we add an owner term to the
   *                      searches
   * @param principalHref - who is doing the searching - only for
   *                      non-public
   * @param superUser     - true if the principal is a superuser.
   * @param accessCheck   - required - lets us check access
   * @param indexName     - explicitly specified
   */
  public BwIndexEsImpl(final Configurations configs,
                       final String docType,
                       final boolean publick,
                       final String principalHref,
                       final boolean superUser,
                       final AccessChecker accessCheck,
                       final BwIndexFetcher indexFetcher,
                       final String indexName) {
    this.publick = publick;
    this.principalHref = principalHref;
    this.superUser = superUser;
    this.accessCheck = new TimedAccessChecker(accessCheck);
    this.docType = docType;
    this.indexFetcher = indexFetcher;

    params = new BwIndexerParams(configs,
                                 publick,
                                 principalHref,
                                 superUser,
                                 accessCheck);

    idxpars = configs.getIndexProperties();
    authpars = configs.getAuthenticatedAuthProperties();
    unauthpars = configs.getUnauthenticatedAuthProperties();
    sysprops = configs.getSystemProperties();

    sch = new SearchClient(idxpars);

    if (indexName == null) {
      targetIndex = "bw" + docType.toLowerCase();
    } else {
      targetIndex = Util.buildPath(false, indexName.toLowerCase());
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
  public void endBwBatch() {
  }

  @Override
  public void flush() {
  }

  @Override
  public String getDocType() {
    return docType;
  }

  private static class EsSearchResult implements SearchResult {
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
    private QueryBuilder curFilter;
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
  public void markTransaction() {
    final UpdateInfo ui = updateInfo.get(targetIndex);
    if ((ui != null) && !ui.getUpdated()) {
      return;
    }

    final UpdateRequest req = new UpdateRequest(targetIndex,
                                                updateTrackerId);
    req.retryOnConflict(20)
//       .setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL)
       .script(new Script("ctx._source.esUpdateCount += 1"));

    try {
      final UpdateResponse ur = getClient().update(req, RequestOptions.DEFAULT);
    } catch (final IOException ie) {
      throw new BedeworkException(ie);
    }
  }

  @Override
  public String currentChangeToken() {
    UpdateInfo ui;
    try {
      final GetRequest req = new GetRequest(targetIndex,
                                            updateTrackerId).
                      storedFields("esUpdateCount");

      final GetResponse resp = getClient().get(req, RequestOptions.DEFAULT);

      if (!resp.isExists()) {
        return null;
      }

      final EntityBuilder er = getEntityBuilder(resp.getFields());
      ui = er.makeUpdateInfo();

      synchronized (updateInfo) {
        final UpdateInfo fromTbl = updateInfo.get(targetIndex);

        if ((fromTbl == null) ||
                (fromTbl.getCount() < ui.getCount())) {
          updateInfo.put(targetIndex, ui);
        } else {
          ui = fromTbl;
        }
      }

      return ui.getChangeToken();
    } catch (final IOException ie) {
      warn("Exception getting UpdateInfo: " +
                   ie.getLocalizedMessage());
      ui = new UpdateInfo();
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
  public ReindexResponse reindex() {
    if (currentReindexing == null) {
      currentReindexing = new ReindexResponse(docType);
    }

    final ReindexResponse resp = currentReindexing;

    if (resp.getStatus() == processing) {
      return resp;
    }

    // Create a new index.

    final String indexName;

    try {
      indexName = newIndex();
    } catch (final Throwable t) {
      return resp.error(t);
    }

    // Only retrieve masters - we'll query for the overrides
    final QueryBuilder qb =
            getFilters(RecurringRetrievalMode.entityOnly)
                    .getAllForReindex(docType);

    final int timeoutMillis = 60000;  // 1 minute
    final TimeValue tv = new TimeValue(timeoutMillis);
    final int batchSize = 100;

    final var clResp = sch.getClient();

    if (!clResp.isOk()) {
      return resp.fromResponse(clResp);
    }

    final var cl = clResp.getEntity();

    final BulkListener listener = new BulkListener();

    final BulkProcessor.Builder builder = BulkProcessor.builder(
            (request, bulkListener) ->
                    cl.bulkAsync(request, RequestOptions.DEFAULT,
                                 bulkListener),
            listener);

    final BulkProcessor bulkProcessor =
            builder.setBulkActions(batchSize)
                   .setConcurrentRequests(3)
                   .setFlushInterval(tv)
                   .build();

    final SearchSourceBuilder ssb =
            new SearchSourceBuilder()
                    .size(batchSize)
                    .query(qb);

    final SearchRequest sr = new SearchRequest(targetIndex)
            .source(ssb)
            .scroll(tv);

    // Switch to new index
    targetIndex = indexName;

    String scrollId = null;

    try {
      SearchResponse scrollResp = cl.search(sr, RequestOptions.DEFAULT);
      scrollId = scrollResp.getScrollId();

      if (scrollResp.status() != RestStatus.OK) {
        if (debug()) {
          debug("Search returned status " + scrollResp.status());
        }
      }

      //Scroll until no hits are returned
      do {
        for (final SearchHit hit: scrollResp.getHits().getHits()) {
          resp.incProcessed();

          if ((resp.getProcessed() % 250) == 0) {
            info("processed " + docType + ": " + resp.getProcessed());
          }

          resp.getStats().inc(docToType.getOrDefault(docType,
                                                     unreachableEntities));

          final ReindexResponse.Failure hitResp = new ReindexResponse.Failure();
          final Object entity = makeEntity(hitResp, hit, null);

          if (entity == null) {
            warn("Unable to build entity " + hit.getSourceAsString());
            resp.incTotalFailed();
            if (resp.getTotalFailed() < 50) {
              resp.addFailure(hitResp);
            }
            continue;
          }

          if (entity instanceof final BwShareableDbentity<?> ent) {
            principalHref = ent.getOwnerHref();
          }

          if (entity instanceof final EventInfo ei) {
            // This might be a single event or a recurring event.

            final BwEvent ev = ei.getEvent();
            if (ev.getRecurring()) {
              resp.incRecurring();
            }

            if (!reindexEvent(hitResp,
                              indexName,
                              hit,
                              ei,
                              bulkProcessor)) {
              warn("Unable to index event " + hit.getSourceAsString());
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
                    new IndexRequest(indexName);

            request.id(doc.getId());

            request.source(doc.getSource());
            bulkProcessor.add(request);

            if (entity instanceof BwEventProperty) {
              caches.put((BwEventProperty<?>)entity);
            }
          }
        }

        final SearchScrollRequest scrollRequest =
                new SearchScrollRequest(scrollId);
        scrollRequest.scroll(tv);
        scrollResp = getClient().scroll(scrollRequest,
                                        RequestOptions.DEFAULT);
        scrollId = scrollResp.getScrollId();

        //Break condition: No hits are returned
      } while (scrollResp.getHits().getHits().length != 0);

      try {
        bulkProcessor.awaitClose(10, TimeUnit.MINUTES);
      } catch (final InterruptedException e) {
        errorReturn(resp,
                    "Final bulk close was interrupted. Records may be missing",
                    failed);
      }
    } catch (final Throwable t) {
      errorReturn(resp, t);
    } finally {
      closeScroll(scrollId);
    }

    return resp;
  }

  @Override
  public ReindexResponse getReindexStatus(final String indexName) {
    return currentReindexing;
  }

  @Override
  public IndexStatsResponse getIndexStats(final String indexName) {
    final IndexStatsResponse resp = new IndexStatsResponse(indexName);

    if (indexName == null) {
      return errorReturn(resp, "indexName must be provided");
    }

    final QueryBuilder qb = QueryBuilders.matchAllQuery();
    final int timeoutMillis = 60000;  // 1 minute
    final TimeValue tv = new TimeValue(timeoutMillis);
    final int batchSize = 100;

    final var clResp = sch.getClient();

    if (!clResp.isOk()) {
      return resp.fromResponse(clResp);
    }

    final var cl = clResp.getEntity();

    final SearchSourceBuilder ssb =
            new SearchSourceBuilder()
                    .size(batchSize)
                    .query(qb);

    final SearchRequest sr = new SearchRequest(targetIndex)
            .source(ssb)
            .scroll(tv);

    String scrollId = null;

    try {
      SearchResponse scrollResp = getClient()
              .search(sr, RequestOptions.DEFAULT);
      scrollId = scrollResp.getScrollId();

      if (scrollResp.status() != RestStatus.OK) {
        if (debug()) {
          debug("Search returned status " + scrollResp.status());
        }
      }

      //Scroll until no hits are returned
      do {
        for (final SearchHit hit : scrollResp.getHits().getHits()) {
          resp.incProcessed();

          if (docType.equals(docTypeEvent)) {
            final EventInfo entity = (EventInfo)makeEntity(resp, hit,
                                                           null);
            if (entity == null) {
              errorReturn(resp, "Unable to make doc for " + hit
                      .getSourceAsString());
              continue;
            }

            final BwEvent ev = entity.getEvent();

            if ((ev instanceof final BwEventAnnotation ann) &&
                    (ann.testOverride())) {
              resp.incOverrides();
            }

            if (ev.isRecurringEntity()) {
              resp.incRecurring();
            }

            if (ev.getRecurrenceId() == null) {
              resp.incMasters();
            } else {
              resp.incInstances();
            }
          } else {
            resp.getStats().inc(docToType.getOrDefault(docType,
                                                       unreachableEntities));
          }
        }

        final SearchScrollRequest scrollRequest =
                new SearchScrollRequest(scrollId);
        scrollRequest.scroll(tv);
        scrollResp = getClient().scroll(scrollRequest,
                                        RequestOptions.DEFAULT);

        //Break condition: No hits are returned

        scrollId = scrollResp.getScrollId();
      } while (scrollResp.getHits().getHits().length != 0);
    } catch (final Throwable t) {
      errorReturn(resp, t);
    } finally {
      closeScroll(scrollId);
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
                             final RecurringRetrievalMode recurRetrieval) {
    if (sysprops.getTestMode()) {
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
              .matchQuery("all_content", query);

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

    res.curFilter = ef.buildQuery(filter);
    if (res.curFilter instanceof MatchNoneQueryBuilder) {
      res.setFound(0);
      return res;
    }

    res.curFilter = ef.addDateRangeQuery(res.curFilter,
                                         IcalDefs.entityTypeEvent,
                                         start,
                                         end);

    res.curFilter = ef.addLimits(res.curFilter,
                                 defaultFilterContext,
                                 res.delState,
                                 docType.equals(docTypeEvent));
    if (res.curFilter instanceof MatchNoneQueryBuilder) {
      res.setFound(0);
      return res;
    }

    res.requiresSecondaryFetch = ef.requiresSecondaryFetch();
    //res.canPage = ef.canPage();

    res.curSort = sort;

    res.pageStart = 0;
    res.latestStart = ef.getLatestStart();
    res.earliestEnd = ef.getEarliestEnd();

    final SearchResponse resp = executeSearch(res,
                                              0,
                                              0,
                                              false);
    if (resp == null) {
      return res;
    }

    if (debug()) {
      debug("Search: returned status " + resp.status() +
                    " found: " + resp.getHits().getTotalHits());
    }

    final var th = resp.getHits().getTotalHits();
    if (th != null) {
      res.setFound(th.value);
    }

    return res;
  }

  @Override
  public List<SearchResultEntry> getSearchResult(
          final SearchResult sres,
          final Position pos,
          final int desiredAccess) {
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
          final int desiredAccess) {
    try {
      fetchStart();
      if (debug()) {
        debug("offset: " + offset + ", num: " + num);
      }

      final EsSearchResult res = (EsSearchResult)sres;

      res.pageStart = offset;

      final int size;

      if (num < 0) {
        size = (int)sres.getFound();
      } else {
        size = num;
      }

      // TODO - need a configurable absolute max size for fetches

      final List<SearchResultEntry> entities = new ArrayList<>(size);

      final SearchResponse resp = executeSearch(res,
                                                res.pageStart,
                                                size,
                                                false);

      if (resp.status() != RestStatus.OK) {
        if (debug()) {
          debug("Search returned status " + resp.status());
        }
      }

      final SearchHits hitsResp = resp.getHits();

      if ((hitsResp.getHits() == null) ||
              (hitsResp.getHits().length == 0)) {
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

        final String kval = hit.getId();

        if (kval == null) {
          throw new BedeworkException(
                  "org.bedework.index.noitemkey");
        }

        final EntityBuilder eb =
                getEntityBuilder(hit.getSourceAsMap());

        Object entity = null;
        switch (docType) {
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
            final var evrestResp = new Response<>();
            entity = makeEvent(eb, evrestResp, kval,
                               res.recurRetrieval.mode == Rmode.expanded);
            final EventInfo ei = (EventInfo)entity;
            assert ei != null;
            final BwEvent ev = ei.getEvent();

            if (evrestResp.getStatus() != ok) {
              warn("Failed restore of event " + ev.getUid() +
                           ": " + evrestResp);
            }

            final CurrentAccess ca =
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

            if (checkTimeRange && ev.isRecurringEntity()) {
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
                                           docType,
                                           hit.getScore()));
      }

      //<editor-fold desc="Finish off events by setting master, target and overrides">

      for (final EventInfo ei : masters) {
        final BwEvent ev = ei.getEvent();

        if (ev.isRecurringEntity()) {
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
  public void indexEntity(final Object rec) {
    indexEntity(rec, false, false);
  }

  @Override
  public void indexEntity(final Object rec,
                          final boolean waitForIt,
                          final boolean forTouch) {
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

      markUpdated();

      final IndexResponse resp = index(rec, waitForIt, forTouch);

      if (debug()) {
        if (resp == null) {
          debug("IndexResponse: resp=null");
        } else {
          debug("IndexResponse: index=" + resp.getIndex() +
                        " id=" + resp.getId() +
                        " type=" + docType +
                        " version=" + resp.getVersion());
        }
      }
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new BedeworkException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  private boolean addOverrides(final Response<?> resp,
                               final EventInfo ei) {
    try {
      final BwEvent ev = ei.getEvent();
      if (!ev.isRecurringEntity()) {
        return true;
      }

      /* Fetch any overrides. */

      final ESQueryFilter flts = getFilters(null);
      final int batchSize = 100;
      int start = 0;

      while (true) {
        // Search original for overrides

        final SearchSourceBuilder ssb =
                new SearchSourceBuilder()
                        .from(start)
                        .size(batchSize)
                        .postFilter(flts.overridesOnly(ev.getUid()));

        final var clResp = sch.getClient();

        if (!clResp.isOk()) {
          return false;
          //return Response.fromResponse(resp, clResp);
        }

        final var cl = clResp.getEntity();

        final SearchRequest req = new SearchRequest(searchIndexes)
                .searchType(SearchType.QUERY_THEN_FETCH)
                .source(ssb);

        final SearchResponse sres;
        try {
          sres = cl.search(req, RequestOptions.DEFAULT);
        } catch (final Throwable t) {
          errorReturn(resp, t);
          return false;
        }

        if (sres.status() != RestStatus.OK) {
          errorReturn(resp,
                      "Search returned status " + sres.status());

          return false;
        }

        final SearchHit[] hits = sres.getHits().getHits();

        if ((hits == null) ||
                (hits.length == 0)) {
          // No more data - we're done
          break;
        }

        for (final SearchHit hit : hits) {
          final String kval = hit.getId();

          if (kval == null) {
            errorReturn(resp, "org.bedework.index.noitemkey");
            return false;
          }

          final EntityBuilder eb = getEntityBuilder(
                  hit.getSourceAsMap());

          final EventInfo entity;
          if (docTypeEvent.equals(docType)) {
            entity = makeEvent(eb, resp, kval, false);
            assert entity != null;
            final BwEvent oev = entity.getEvent();

            if (oev instanceof final BwEventAnnotation ann) {
              final BwEvent proxy = new BwEventProxy(ann);
              ann.setTarget(ev);
              ann.setMaster(ev);

              ei.addOverride(new EventInfo(proxy));
              continue;
            }
          }

          // Unexpected type
          errorReturn(resp, "Expected override only: " + docType);
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

      if (!ev.isRecurringEntity() && (ev.getRecurrenceId() == null)) {
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
                new IndexRequest(indexName);

        request.id(doc.getId());
        request.source(doc.getSource());
        bulkProcessor.add(request);
        return true;
      }

      if (ev.getRecurrenceId() != null) {
        errorReturn(resp,
                    "Not implemented - index of single override");
        return false;
      }

      if (!addOverrides(resp, ei)) {
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
                  new IndexRequest(indexName);

          request.id(doc.getId());
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
                new IndexRequest(indexName);

        request.id(doc.getId());
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
              new IndexRequest(indexName);

      request.id(doc.getId());
      request.source(doc.getSource());
      bulkProcessor.add(request);
      //</editor-fold>
      return true;
    } catch (final Throwable t) {
      errorReturn(resp, t);
      return false;
    }
  }

  private EsDocInfo makeDoc(final Response<?> resp,
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

  private void markUpdated() {
    UpdateInfo ui = updateInfo.get(targetIndex);

    if (ui == null) {
      currentChangeToken();
    }

    ui = updateInfo.get(targetIndex);

    if (ui == null) {
      throw new BedeworkException("Unable to mark update");
    }

    ui.setUpdated(true);
    caches.clear();
  }

  @Override
  public void unindexContained(final String colPath) {
    try {
      final DeleteByQueryRequest dqr =
              new DeleteByQueryRequest(targetIndex);

      dqr.setConflicts("proceed");
      dqr.setRefresh(true);
      dqr.setQuery(getFilters(null).colPathFilter(null, colPath));

      BulkByScrollResponse bulkResponse =
              getClient().deleteByQuery(dqr, RequestOptions.DEFAULT);

      markUpdated();

      // TODO check response?
    } catch (final OpenSearchException ese) {
      // Failed somehow
      error(ese);
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error(t);
      throw new BedeworkException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  @Override
  public void unindexTombstoned(final String docType,
                                final String href) {
    try {
      final DeleteByQueryRequest dqr =
              new DeleteByQueryRequest(targetIndex);

      dqr.setConflicts("proceed");
      dqr.setRefresh(true);
      final QueryBuilder fb = getFilters(null)
              .singleTombstonedEntityQuery(href,
                                           PropertyInfoIndex.HREF);

      dqr.setQuery(fb);

      final BulkByScrollResponse bulkResponse =
              getClient().deleteByQuery(dqr, RequestOptions.DEFAULT);

      markUpdated();

      // TODO check response?
    } catch (final OpenSearchException ese) {
      // Failed somehow
      error(ese);
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error(t);
      throw new BedeworkException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  @Override
  public void unindexEntity(final BwEventProperty<?> val) {
    unindexEntity(getDocBuilder().getHref(val));
  }

  @Override
  public void unindexEntity(final String href) {
    try {
      final DeleteByQueryRequest dqr =
              new DeleteByQueryRequest(targetIndex);

      final QueryBuilder qb = getFilters(null)
              .singleEntityQuery(href,
                                 PropertyInfoIndex.HREF);

      dqr.setConflicts("proceed");
      dqr.setRefresh(true);
      dqr.setQuery(qb);

      final BulkByScrollResponse bulkResponse =
              getClient().deleteByQuery(dqr, RequestOptions.DEFAULT);

      markUpdated();

      // TODO check response?
    } catch (final OpenSearchException ese) {
      // Failed somehow
      error(ese);
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error(t);
      throw new BedeworkException(t);
    } finally {
      lastIndexTime = System.currentTimeMillis();
    }
  }

  @Override
  public String newIndex() {
    try {
      final String newName = "bw" + docType.toLowerCase() + newIndexSuffix();
      targetIndex = newName;

      final CreateIndexRequest req = new CreateIndexRequest(newName);

      final String mappingStr =
              fileToString(Util.buildPath(false,
                                          idxpars.getIndexerConfig(),
                                          "/",
                                          docType.toLowerCase(),
                                          "/mappings.json"));

      req.source(mappingStr, XContentType.JSON);

      info("Attempt to create index " + newName);

      final CreateIndexResponse resp =
              getClient().indices()
                         .create(req, RequestOptions.DEFAULT);

      info("Index " + newName + " created");

      final DocBuilder db = getDocBuilder();

      indexDoc(db.makeUpdateInfoDoc(docType), true);

      return newName;
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error(t);
      throw new BedeworkException(t);
    }
  }

  @Override
  public Set<IndexInfo> getIndexInfo() {
    final Set<IndexInfo> res = new TreeSet<>();

    try {
      final GetAliasesRequest req = new GetAliasesRequest();

      final GetAliasesResponse resp =
              getClient().indices()
                         .getAlias(req, RequestOptions.DEFAULT);

      final Map<String, Set<AliasMetadata>> aliases = resp.getAliases();

      for (final String inm : aliases.keySet()) {
        final IndexInfo ii = new IndexInfo(inm);
        res.add(ii);

        final Set<AliasMetadata> amds = aliases.get(inm);

        if (amds == null) {
          continue;
        }

        for (final AliasMetadata amd : amds) {
          ii.addAlias(amd.alias());
        }
      }

      return res;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  public List<ContextInfo> getContextInfo() {
    return sch.getContextInfo();
  }

  @Override
  public List<String> purgeIndexes() {
    final Set<IndexInfo> indexes = getIndexInfo();
    final List<String> purged = new ArrayList<>();

    if (Util.isEmpty(indexes)) {
      return purged;
    }

    purge:
    for (final IndexInfo ii : indexes) {
      final String idx = ii.getIndexName();

      if (!idx.startsWith("bw")) {
        continue purge;
      }

      /* Don't delete those pointed to by the current aliases */

      if (!Util.isEmpty(ii.getAliases())) {
        for (final String alias : ii.getAliases()) {
          if (alias.startsWith("bw") && knownTypesLowered
                  .contains(alias.substring(2))) {
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
  public int setAlias(final String index) {
    // Make the alias
    String alias = null;

    for (final String type : knownTypesLowered) {
      if (index.startsWith("bw" + type + "2")) {
        alias = "bw" + type;
        break;
      }
    }

    if (alias == null) {
      throw new BedeworkException("Bad name " + index);
    }

    //IndicesAliasesResponse resp = null;
    try {
      /* Other is the alias name - index is the index we were just indexing into
       */

      final GetAliasesRequest req = new GetAliasesRequest(alias);

      final GetAliasesResponse resp =
              getClient().indices()
                         .getAlias(req, RequestOptions.DEFAULT);

      if (resp.status() == RestStatus.OK) {
        final Map<String, Set<AliasMetadata>> aliases =
                resp.getAliases();
        for (final String inm : aliases.keySet()) {
          final Set<AliasMetadata> amds = aliases.get(inm);

          if (amds == null) {
            continue;
          }

          for (final AliasMetadata amd : amds) {
            final IndicesAliasesRequest ireq = new IndicesAliasesRequest();
            final AliasActions removeAction =
                    new AliasActions(
                            AliasActions.Type.REMOVE)
                            .index(inm)
                            .alias(amd.alias());
            ireq.addAliasAction(removeAction);
            final AcknowledgedResponse ack =
                    getClient().indices().updateAliases(ireq,
                                                        RequestOptions.DEFAULT);
          }
        }
      }

      /* Now add alias */

      final IndicesAliasesRequest ireq = new IndicesAliasesRequest();
      final AliasActions addAction =
              new AliasActions(AliasActions.Type.ADD)
                      .index(index)
                      .alias(alias);
      ireq.addAliasAction(addAction);
      final AcknowledgedResponse ack =
              getClient().indices()
                         .updateAliases(ireq, RequestOptions.DEFAULT);

      return 0;
    } catch (final OpenSearchException ese) {
      // Failed somehow
      error(ese);
      return -1;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private BwSystem cachedBwSystem;

  @Override
  public GetEntityResponse<EventInfo> fetchEvent(final String href) {
    final GetEntityResponse<EventInfo> resp = new GetEntityResponse<>();

    try {
      fetchStart();
      final String recurrenceId;
      final String hrefNorid;

      // Check validity
      final int pos = href.lastIndexOf("/");
      if (pos < 0) {
        throw new RuntimeException("Bad href: " + href);
      }

      final int fragPos = href.lastIndexOf("#");
      final int expectedObjects;

      if (fragPos < pos) {
        hrefNorid = href;
        recurrenceId = null;
        expectedObjects = 1;
      } else {
        hrefNorid = href.substring(0, fragPos);
        recurrenceId = href.substring(fragPos + 1);
        expectedObjects = 2;
      }

      final QueryBuilder qb =
              getFilters(null).singleEventFilter(hrefNorid,
                                                 recurrenceId);

      final SearchHit[] hits =
              fetchEntity(docTypeEvent, qb, expectedObjects);

      if (hits == null) {
        return resp.error(notFound);
      }

      final EventInfo ei;

      if (hits.length == 1) {
        // Master only
        ei = makeEvent(resp, hits[0]);
        if (ei == null) {
          return resp.error(notFound);
        }
      } else {
        // Make events then create proxy.

        final EventInfo ei0 = makeEvent(resp, hits[0]);
        final EventInfo ei1 = makeEvent(resp, hits[1]);
        if ((ei0 == null) || (ei1 == null)) {
          return resp.error(notFound);
        }

        final BwEvent mstr;
        final BwEventAnnotation inst;

        if (ei0.getEvent() instanceof BwEventAnnotation) {
          inst = (BwEventAnnotation)ei0.getEvent();
          mstr = ei1.getEvent();
        } else {
          inst = (BwEventAnnotation)ei1.getEvent();
          mstr = ei0.getEvent();
        }

        final BwEvent proxy = new BwEventProxy(inst);
        inst.setTarget(mstr);
        inst.setMaster(mstr);

        ei = new EventInfo(proxy);
      }

      final BwEvent ev = ei.getEvent();

      final CurrentAccess ca =
              accessCheck.checkAccess(ev, privRead, true);

      if ((ca == null) || !ca.getAccessAllowed()) {
        return resp.error(notFound);
      }

      ei.setCurrentAccess(ca);
      resp.setEntity(ei);

      if (recurrenceId != null) {
        // Single instance
        return resp;
      }

      addOverrides(resp, ei);

      return resp;
    } catch (final Throwable t) {
      return errorReturn(resp, t);
    } finally {
      fetchEnd();
    }
  }

  @Override
  public GetEntitiesResponse<EventInfo> fetchEvent(final String colPath,
                                                   final String guid) {
    final GetEntitiesResponse<EventInfo> resp =
            new GetEntitiesResponse<>();

    try {
      fetchStart();

      final QueryBuilder qb =
              getFilters(null).eventFilterGuid(colPath, guid);

      resp.setEntities(fetchEvents(qb, -1, privRead));
      if (resp.getEntities() == null) {
        resp.setStatus(notFound);
      }

      return resp;
    } catch (final Throwable t) {
      return errorReturn(resp, t);
    } finally {
      fetchEnd();
    }
  }

  @Override
  public List<EventInfo> fetchEvents(final String path,
                                     final String lastmod,
                                     final int lastmodSeq,
                                     final int count) {
    final QueryBuilder qb =
            getFilters(null).syncFilter(path, lastmod,
                                        lastmodSeq,
                                        true);

    return fetchEvents(qb, count, privRead);
  }

  @Override
  public BwCategory fetchCat(final String val,
                             final PropertyInfoIndex... index) {
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
                                                final PropertyInfoIndex... index) {
    final GetEntityResponse<BwCalendar> resp = new GetEntityResponse<>();
    BwCalendar entity;

    if ((val == null) || (val.isEmpty())) {
      return resp.notOk(notFound);
    }

    if ((index.length == 1) &&
            (index[0] == PropertyInfoIndex.HREF)) {
      checkCache();
      entity = getCached(val, desiredAccess, CalendarWrapper.class);

      if (entity != null) {
        if (entity.getTombstoned()) {
          return resp.error(notFound);
        }

        return resp.setEntity(entity).ok();
      }
    }

    if (debug()) {
      final String ival;
      if (index.length == 1) {
        ival = index[0].name();
      } else {
        ival = "multi-ref";
      }
      debug("Fetch collection with " + ival + " = " + val);
    }
    final EntityBuilder eb = fetchEntity(docTypeCollection, val,
                                         index);

    if (eb == null) {
      if (debug()) {
        debug("Not found");
      }
      return resp.error(notFound);
    }

    entity = makeCollection(eb);

    if (desiredAccess < 0) {
      entity = new CalendarWrapper(entity,
                                   accessCheck.getAccessUtil());
      caches.put(entity, desiredAccess);
      resp.setEntity(entity);
      if (debug()) {
        debug("Return ok - any access");
      }

      return resp.ok();
    }

    entity = accessCheck.checkAccess(entity,
                                     desiredAccess);

    if (entity == null) {
      if (debug()) {
        debug("No access");
      }
      return resp.notOk(noAccess);
    }
    caches.put(entity, desiredAccess);
    resp.setEntity(entity);

    if (debug()) {
      debug("Return ok - access ok");
    }
    return resp.ok();
  }

  @Override
  public Collection<BwCalendar> fetchChildren(final String href) {
    return fetchChildren(href, true);
  }

  @Override
  public Collection<BwCalendar> fetchChildren(final String href,
                                              final boolean excludeTombstoned) {
    if (debug()) {
      debug("fetchChildren for " + href);
    }

    final ESQueryFilter filters = getFilters(null);
    QueryBuilder qb = filters.colPathFilter(null, href);

    if (excludeTombstoned) {
      qb = filters.termFilter(qb, PropertyInfoIndex.TOMBSTONED,
                              "false");
    }

    final List<BwCalendar> cols =
            fetchEntities(docTypeCollection,
                          new BuildEntity<>() {
                            @Override
                            BwCalendar make(final EntityBuilder eb,
                                            final String id) {
                              return accessCheck.checkAccess(
                                      makeCollection(eb));
                            }
                          },
                             qb,
                          -1);

    if (Util.isEmpty(cols)) {
      return cols;
    }

    return new TreeSet<>(cols); // Sort the result
  }

  @Override
  public Collection<BwCalendar> fetchChildrenDeep(final String href) {
    if (debug()) {
      debug("fetchChildrenDeep for " + href);
    }

    final Collection<BwCalendar> cols = fetchChildren(href);

    if (Util.isEmpty(cols)) {
      return cols;
    }

    final Collection<BwCalendar> res = new ArrayList<>(cols);

    for (final BwCalendar col: cols) {
      final Collection<BwCalendar> subcols = fetchChildren(col.getHref());

      if (Util.isEmpty(subcols)) {
        continue;
      }

      res.addAll(subcols);
    }

    return new TreeSet<>(res); // Sort the result
  }

  @Override
  public GetEntitiesResponse<BwGroup<?>> fetchGroups(final boolean admin) {
    final QueryBuilder qb = getFilters(null).allGroupsQuery(admin);
    final GetEntitiesResponse<BwGroup<?>> resp = new GetEntitiesResponse<>();

    try {

      return resp.setEntities(
              fetchEntities(docTypePrincipal,
                            new BuildEntity<>() {
                              @Override
                              BwGroup<?> make(final EntityBuilder eb,
                                              final String id) {
                                return (BwGroup<?>)eb.makePrincipal();
                              }
                            },
                            qb,
                            -1)).ok();
    } catch (final Throwable t) {
      return resp.error(t);
    }
  }

  @Override
  public GetEntitiesResponse<BwAdminGroup> fetchAdminGroups() {
    final QueryBuilder qb = getFilters(null).allGroupsQuery(true);
    final GetEntitiesResponse<BwAdminGroup> resp = new GetEntitiesResponse<>();

    try {
      return resp.setEntities(
              fetchEntities(docTypePrincipal,
                            new BuildEntity<>() {
                              @Override
                              BwAdminGroup make(final EntityBuilder eb,
                                                final String id) {
                                return (BwAdminGroup)eb.makePrincipal();
                              }
                            },
                            qb,
                            -1)).ok();
    } catch (final Throwable t) {
      return resp.error(t);
    }
  }

  @Override
  public BwPrincipal<?> fetchPrincipal(final String val) {
    checkCache();
    BwPrincipal<?> entity = getCached(val, BwPrincipal.class);

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

    if (entity instanceof final BwCalSuite cs) {
      if (cs.getGroupHref() != null) {
        cs.setGroup((BwAdminGroup)fetchPrincipal(cs.getGroupHref()));
      }
      return entity;
    }

    if (!(entity instanceof final BwGroup<?> grp)) {
      return entity;
    }

    // Get all member entities
    if (Util.isEmpty(grp.getMemberHrefs())) {
      return entity;
    }

    for (final String href: grp.getMemberHrefs()) {
      final BwPrincipal<?> mbr = fetchPrincipal(href);
      if (mbr == null) {
        warn("Missing member in index: " + href);
        continue;
      }

      grp.addGroupMember(mbr);
    }

    return entity;
  }

  @Override
  public GetEntitiesResponse<BwGroup<?>> fetchGroups(
          final boolean admin,
          final String memberHref) {
    final QueryBuilder qb = getFilters(null).allGroupsQuery(admin,
                                                            memberHref);
    final GetEntitiesResponse<BwGroup<?>> resp =
            new GetEntitiesResponse<>();

    try {
      return resp.setEntities(
              fetchEntities(docTypePrincipal,
                            new BuildEntity<>() {
                              @Override
                              BwGroup<?> make(final EntityBuilder eb,
                                              final String id) {
                                return (BwGroup<?>)eb.makePrincipal();
                              }
                            },
                            qb,
                            -1)).ok();
    } catch (final Throwable t) {
      return resp.error(t);
    }
  }

  @Override
  public GetEntitiesResponse<BwAdminGroup> fetchAdminGroups(final String memberHref) {
    final QueryBuilder qb = getFilters(null).allGroupsQuery(true,
                                                            memberHref);
    final GetEntitiesResponse<BwAdminGroup> resp =
            new GetEntitiesResponse<>();

    try {
      return resp.setEntities(
              fetchEntities(docTypePrincipal,
                            new BuildEntity<>() {
                              @Override
                              BwAdminGroup make(final EntityBuilder eb,
                                                final String id) {
                                return (BwAdminGroup)eb.makePrincipal();
                              }
                            },
                            qb,
                            -1)).ok();
    } catch (final Throwable t) {
      return resp.error(t);
    }
  }

  @Override
  public BwPreferences fetchPreferences(final String href) {
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
  public BwFilterDef fetchFilter(final String href) {
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
                                        final int count) {
    final QueryBuilder q;

    if (fb == null) {
      q = getFilters(null).principalQuery(null);
    } else {
      q = getFilters(null).buildQuery(fb);
    }

    return fetchEntities(docTypeFilter,
                         new BuildEntity<>() {
                           @Override
                           BwFilterDef make(final EntityBuilder eb,
                                            final String id) {
                             return eb.makefilter();
                           }
                         },
                         q,
                         count);
  }

  @Override
  public BwResource fetchResource(final String href) {
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
                                         final int count) {
    final QueryBuilder qb =
            getFilters(null).syncFilter(path, lastmod, lastmodSeq,
                                        false);

    return fetchEntities(docTypeResource,
                         new BuildEntity<>() {
                           @Override
                           BwResource make(final EntityBuilder eb,
                                           final String id) {
                             return eb.makeResource();
                           }
                         },
                         qb,
                         count);
  }

  @Override
  public BwResourceContent fetchResourceContent(final String href) {
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
                                final PropertyInfoIndex... index) {
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
                                  final PropertyInfoIndex... index) {
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
        return resp.error(notFound);
      }

      final EntityBuilder eb = getEntityBuilder(hit.getSourceAsMap());

      final BwLocation loc = eb.makeLocation();

      if (loc == null) {
        return resp.error(notFound);
      }

      resp.setEntity(loc);
      return resp;
    } catch (final BedeworkException be) {
      return errorReturn(resp, be);
    }
  }

  private static final int maxFetchCount = 10000;
  private static final int absoluteMaxTries = 1000;

  @Override
  public List<BwCategory> fetchAllCats() {
    return fetchEntities(docTypeCategory,
                         new BuildEntity<>() {
                           @Override
                           BwCategory make(final EntityBuilder eb,
                                           final String id) {
                             return eb.makeCat();
                           }
                         },
                         -1);
  }

  @Override
  public List<BwContact> fetchAllContacts() {
    return fetchEntities(docTypeContact,
                         new BuildEntity<>() {
                           @Override
                           BwContact make(final EntityBuilder eb,
                                          final String id) {
                             return eb.makeContact();
                           }
                         },
                         -1);
  }

  @Override
  public List<BwLocation> fetchAllLocations() {
    return fetchEntities(docTypeLocation,
                         new BuildEntity<>() {
                           @Override
                           BwLocation make(final EntityBuilder eb,
                                           final String id) {
                             return eb.makeLocation();
                           }
                         }, -1);
  }

  @Override
  public GetEntitiesResponse<BwContact> findContacts(final FilterBase filter,
                                                     final int from,
                                                     final int size) {
    return findEvProperty(filter, from, size, BwContact.class);
  }

  @Override
  public GetEntitiesResponse<BwLocation> findLocations(final FilterBase filter,
                                                       final int from,
                                                       final int size) {
    return findEvProperty(filter, from, size, BwLocation.class);
  }

  @Override
  public GetEntitiesResponse<BwCategory> findCategories(final FilterBase filter,
                                                        final int from,
                                                        final int size) {
    return findEvProperty(filter, from, size, BwCategory.class);
  }

  private <T extends BwEventProperty<?>> GetEntitiesResponse<T>
              findEvProperty(final FilterBase filter,
                             final int from,
                             final int size,
                             final Class<T> cl) {
    final GetEntitiesResponse<T> resp = new GetEntitiesResponse<>();

    try {
      final ESQueryFilter ef = getFilters(null);

      QueryBuilder qb = ef.buildQuery(filter);

      if (!(qb instanceof MatchNoneQueryBuilder)) {
        qb = ef.addLimits(qb,
                          null, DeletedState.noDeleted,
                          false);
      }

      if (qb instanceof MatchNoneQueryBuilder) {
        return resp.error(notFound);
      }

      final EsSearchResult esr = new EsSearchResult(this);

      esr.curQuery = qb;
      esr.pageStart = from;
      esr.pageSize = size;

      final SearchResponse sresp = executeSearch(esr, from, size, false);

      if (debug()) {
        debug("Search: returned status " + sresp.status() +
                      " found: " + sresp.getHits().getTotalHits());
      }

      for (final SearchHit hit : sresp.getHits().getHits()) {
        final BwEventProperty<?> evp =
                (BwEventProperty<?>)makeEntity(resp, hit, null);
        if (!resp.isOk() || (evp == null)) {
          return resp;
        }

        evp.setScore(hit.getScore());

        resp.addEntity((T)evp);
      }

      return resp;
    } catch (final Throwable t) {
      return resp.error(t);
    }
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private boolean checkCache() {
    // 1. Have we only just checked? If so then ok
    final long now = System.currentTimeMillis();

    if ((now - lastChangeTokenCheck) <= lastChangeTokenCheckPeriod) {
      return true;
    }

    lastChangeTokenCheck = now;

    // 2. Has there been a change since we last checked? If not then ok
    final String changeToken = currentChangeToken();

    if ((changeToken == null) ||
            changeToken.equals(lastChangeToken)) {
      return true;
    }

    // 3. There's been a change - flush and return false.
    if (debug()) {
      debug("Change - flush cache");
    }
    lastChangeToken = changeToken;
    caches.clear();

    return false;
  }

  synchronized <T extends BwUnversionedDbentity<?>> T getCached(
          final String href,
          final Class<T> resultType) {
    if (!checkCache()) {
      return null;
    }

    return caches.get(href, resultType);
  }

  synchronized <T extends BwUnversionedDbentity<?>> T getCached(
          final String href,
          final int desiredAccess,
          final Class<T> resultType) {
    if (!checkCache()) {
      return null;
    }

    return caches.get(href, desiredAccess, resultType);
  }

  private SearchResponse executeSearch(final EsSearchResult sresp,
                                       final int pos,
                                       final int pageSize,
                                       final boolean forSecondarySearch) {
    if (sysprops.getTestMode()) {
      final long timeSinceIndex = System.currentTimeMillis() - lastIndexTime;
      final long waitTime = indexerDelay - timeSinceIndex;

      if (waitTime > 0) {
        try {
          Thread.sleep(waitTime);
        } catch (final InterruptedException ignored) {
        }
      }
    }

    final SearchSourceBuilder ssb =
            new SearchSourceBuilder()
                    .from(pos)
                    .size(pageSize)
                    .postFilter(sresp.curFilter);

    if (sresp.curQuery != null) {
      ssb.query(sresp.curQuery);
    } else {
      ssb.query(QueryBuilders.matchAllQuery());
    }

    if (forSecondarySearch) {
      // Limit to href then fetch those
      ssb.docValueField(ESQueryFilter.hrefJname);
    }

    final RestHighLevelClient cl = getClient();

    final SearchRequest req = new SearchRequest(searchIndexes)
            .searchType(SearchType.QUERY_THEN_FETCH)
            .source(ssb);

    if (!Util.isEmpty(sresp.curSort)) {
      SortOrder so;

      for (final SortTerm st : sresp.curSort) {
        if (st.isAscending()) {
          so = SortOrder.ASC;
        } else {
          so = SortOrder.DESC;
        }

        ssb.sort(new FieldSortBuilder(st.getPropertyRef())
                         .order(so));
      }
    }

    if (debug()) {
      debug("Search: latestStart=" + sresp.latestStart +
                    " earliestEnd=" + sresp.earliestEnd +
                    " targetIndex=" + targetIndex +
                    "; req=" + req);
    }

    final SearchResponse resp;
    try {
      resp = cl.search(req, RequestOptions.DEFAULT);
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }

//    if (resp.status() != RestStatus.OK) {
    //TODO
//    }

    if (debug()) {
      debug("Search: returned status " + resp.status() +
                    " found: " + resp.getHits().getTotalHits());
    }

    return resp;
  }

  /*
  private SearchHits multiColFetch(final List<String> hrefs) {
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

    if (debug()) {
      debug("MultiColFetch: targetIndex=" + targetIndex +
                    "; srb=" + srb);
    }

    final SearchResponse resp = srb.execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      if (debug()) {
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
    if (hits.getHits().length == 0) {
      return null;
    }

    return hits;
  }
  */

  private List<SearchHit> multiFetch(final SearchHits hits,
                                     final RecurringRetrievalMode rmode) {
    // Make an ored filter from keys

    final Set<String> hrefs = new TreeSet<>(); // Dedup
    
    /* We may get many more entries than the hits we got for the first search.
       Each href may have many entries that don't match the original search term
       We need to keep fetching until nothing is returned
     */

    for (final SearchHit hit: hits) {
      final String kval = hit.getId();

      if (kval == null) {
        throw new BedeworkException("org.bedework.index.noitemkey");
      }
      final Map<String, Object> map = hit.getSourceAsMap();

      final Object field = map.get(ESQueryFilter.hrefJname);

      if (field == null) {
        warn("Unable to get field " + ESQueryFilter.hrefJname +
                " from " + map);
      } else {
        hrefs.add(field.toString());
      }
    }

    final int batchSize = 1000;
    final List<SearchHit> res = new ArrayList<>();

    final SearchSourceBuilder ssb =
            new SearchSourceBuilder()
                    .size(batchSize)
                    .query(getFilters(null).multiHref(hrefs, rmode));

    final SearchRequest req = new SearchRequest(searchIndexes)
            .searchType(SearchType.QUERY_THEN_FETCH)
            .source(ssb)
            .scroll(TimeValue.timeValueMinutes(1L));

    if (debug()) {
      debug("MultiFetch: targetIndex=" + targetIndex +
                    "; ssb=" + ssb);
    }

    String scrollId = null;

    try {
      SearchResponse resp = getClient().search(req,
                                               RequestOptions.DEFAULT);
      scrollId = resp.getScrollId();

      int tries = 0;

      for (; ; ) {
        if (tries > absoluteMaxTries) {
          // huge count or we screwed up
          warn("Indexer: too many tries");
          break;
        }

        if (resp.status() != RestStatus.OK) {
          if (debug()) {
            debug("Search returned status " + resp.status());
          }

          return null;
        }

        final SearchHit[] hits2 = resp.getHits().getHits();

        if ((hits2 == null) ||
                (hits2.length == 0)) {
          // No more data - we're done
          break;
        }

        res.addAll(Arrays.asList(hits2));

        tries++;

        final SearchScrollRequest scrollRequest =
                new SearchScrollRequest(scrollId);
        scrollRequest.scroll(new TimeValue(60000));
        resp = getClient().scroll(scrollRequest,
                                  RequestOptions.DEFAULT);

        scrollId = resp.getScrollId();
      }

      return res;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    } finally {
      closeScroll(scrollId);
    }
  }

  private void closeScroll(final String scrollId) {
    if (scrollId == null) {
      return;
    }

    try {
      final var closeScroll = new ClearScrollRequest();
      closeScroll.addScrollId(scrollId);
      getClient().clearScroll(closeScroll, RequestOptions.DEFAULT);
    } catch (final Throwable t) {
      error("Close scroll failed", t);
    }
  }

  private void deleteIndexes(final List<String> names) {
    try {
      final DeleteIndexRequest request =
              new DeleteIndexRequest(names.toArray(new String[0]));

      final AcknowledgedResponse deleteIndexResponse =
              getClient().indices().delete(request, RequestOptions.DEFAULT);
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private static abstract class BuildEntity<T> {
    abstract T make(EntityBuilder eb,
                    String id);
  }

  private <T> List<T> fetchEntities(final String docType,
                                    final BuildEntity<T> be,
                                    final int count) {
    return fetchEntities(docType, be,
                         getFilters(null).principalQuery(null),
                         count);
  }

  private <T> List<T> fetchEntities(final String docType,
                                    final BuildEntity<T> be,
                                    final QueryBuilder filter,
                                    final int count) {
    requireDocType(docType);

    int tries = 0;
    final int ourCount;

    if (count < 0) {
      ourCount = maxFetchCount;
    } else {
      ourCount = Math.min(maxFetchCount, count);
    }

    final SearchSourceBuilder ssb =
            new SearchSourceBuilder()
                    .size(ourCount)
                    .query(filter);

    final SearchRequest sr = new SearchRequest(targetIndex)
            .source(ssb)
            .scroll(new TimeValue(60000));

    if (debug()) {
      debug("fetchEntities: " + sr);
    }

    final List<T> res = new ArrayList<>();

    String scrollId = null;

    try {
      SearchResponse scrollResp = getClient()
              .search(sr, RequestOptions.DEFAULT);
      scrollId = scrollResp.getScrollId();

      if (scrollResp.status() != RestStatus.OK) {
        if (debug()) {
          debug("Search returned status " + scrollResp.status());
        }
      }

      for (; ; ) {
        if (tries > absoluteMaxTries) {
          // huge count or we screwed up
          warn("Indexer: too many tries");
          break;
        }

        if (scrollResp.status() != RestStatus.OK) {
          if (debug()) {
            debug("Search returned status " + scrollResp.status());
          }
        }

        final SearchHits hits = scrollResp.getHits();

        //Break condition: No hits are returned
        if (hits.getHits().length == 0) {
          break;
        }

        for (final SearchHit hit : hits) {
          //Handle the hit...
          final T ent = be
                  .make(getEntityBuilder(hit.getSourceAsMap()),
                                         hit.getId());
          if (ent == null) {
            // No access
            continue;
          }
          res.add(ent);
          //ourPos++;
        }

        tries++;

        final SearchScrollRequest scrollRequest =
                new SearchScrollRequest(scrollId);
        scrollRequest.scroll(new TimeValue(60000));
        scrollResp = getClient().scroll(scrollRequest,
                                        RequestOptions.DEFAULT);
        scrollId = scrollResp.getScrollId();
      }
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    } finally {
      closeScroll(scrollId);
    }

    return res;
  }

  private EntityBuilder fetchEntity(final String docType,
                                    final String val,
                                    final PropertyInfoIndex... index) {
    requireDocType(docType);

    try {
      fetchStart();

      if ((!docType.equals(docTypeCollection) && !docType.equals(docTypeEvent)) &&
              (index.length == 1) &&
              (index[0] == PropertyInfoIndex.HREF)) {
        // This path avoids the tombstone check.
        final GetRequest req = new GetRequest(targetIndex,
                                             val);

        final GetResponse gr = getClient().get(req, RequestOptions.DEFAULT);

        if (!gr.isExists()) {
          return null;
        }

        return getEntityBuilder(gr.getSourceAsMap());
      }

      final SearchHit hit =
              fetchEntity(docType,
                          getFilters(null)
                                  .singleEntityQuery(
                                          val,
                                          index));

      if (hit == null) {
        return null;
      }

      return getEntityBuilder(hit.getSourceAsMap());
    } catch (final IOException ie) {
      throw new BedeworkException(ie);
    } finally {
      fetchEnd();
    }
  }

  private SearchHit fetchEntity(final String docType,
                                final QueryBuilder qb) {
    final SearchHit[] res = fetchEntity(docType, qb, 1);

    if (res == null) {
      return null;
    }

    return res[0];
  }

  private SearchHit[] fetchEntity(final String docType,
                                  final QueryBuilder qb,
                                  final int expectedObjects) {
    requireDocType(docType);

    final SearchSourceBuilder ssb =
            new SearchSourceBuilder()
                    .from(0)
                    .size(60)
                    .query(qb);

    final SearchRequest req = new SearchRequest(searchIndexes)
            .searchType(SearchType.QUERY_THEN_FETCH)
            .source(ssb);

    final SearchResponse resp;
    try {
      resp = getClient().search(req, RequestOptions.DEFAULT);
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }

    final SearchHits hits = resp.getHits();
    final SearchHit[] res = hits.getHits();

    if (res.length == 0) {
      // No match
      return null;
    }

    if (res.length != expectedObjects) {
      error(format("Incorrect number of entities of type %s" +
                           " with filter %s. Expected %d got %d",
                   docType, qb, expectedObjects,
                   res.length));
      return null;
    }

    return res;
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

  private void mustBe(final String reqDocType) {
    if (!docType.equals(reqDocType)) {
      throw new BedeworkException("Wrong index type " + docType +
                                           " for expected " + reqDocType);
    }
  }

  /* Return the response after indexing */
  private IndexResponse index(final Object rec,
                              final boolean waitForIt,
                              final boolean forTouch) {
    EsDocInfo di = null;

    try {
      if (rec instanceof EventInfo) {
        mustBe(docTypeEvent);
        return indexEvent((EventInfo)rec, waitForIt);
      }

      final DocBuilder db = getDocBuilder();

      if (rec instanceof BwCalendar) {
        mustBe(docTypeCollection);
        di = db.makeDoc((BwCalendar)rec);
      }

      if (rec instanceof BwCategory) {
        mustBe(docTypeCategory);
        di = db.makeDoc((BwCategory)rec);
      }

      if (rec instanceof BwContact) {
        mustBe(docTypeContact);
        di = db.makeDoc((BwContact)rec);
      }

      if (rec instanceof BwLocation) {
        mustBe(docTypeLocation);
        di = db.makeDoc((BwLocation)rec);
      }

      if (rec instanceof final BwPrincipal<?> pr) {
        mustBe(docTypePrincipal);
        di = db.makeDoc(pr);
      }

      if (rec instanceof BwPreferences) {
        mustBe(docTypePreferences);
        di = db.makeDoc((BwPreferences)rec);
      }

      if (rec instanceof BwResource) {
        mustBe(docTypeResource);
        di = db.makeDoc((BwResource)rec);
      }

      if (rec instanceof BwResourceContent) {
        mustBe(docTypeResourceContent);
        di = db.makeDoc((BwResourceContent)rec);
      }

      if (rec instanceof BwFilterDef) {
        mustBe(docTypeFilter);
        di = db.makeDoc((BwFilterDef)rec);
      }

      if (di != null) {
        return indexDoc(di, waitForIt);
      }

      throw new BedeworkException(
              new IndexException(IndexException.unknownRecordType,
                                 rec.getClass().getName()));
    } catch (final BedeworkException be) {
      throw be;
    } catch (final VersionConflictEngineException vcee) {
      if (forTouch) {
        // Ignore - already touched
        return null;
      }
      error(vcee);
      throw new BedeworkException(vcee);
      /* Can't do this any more
      if (vcee.currentVersion() == vcee.getProvidedVersion()) {
        warn("Failed index with equal version for type " + 
                     di.getType() +
                     " and id " + di.getId());
      }

      return null;
      */
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private EsDocInfo makeDoc(final Response<?> resp,
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

  private IndexResponse indexEvent(final EventInfo ei,
                                   final boolean waitForIt) {
    try {

      /* If it's not recurring or a stand-alone instance index it */

      final BwEvent ev = ei.getEvent();
      if (ev.getTombstoned()) {
        // cleanup.
        deleteEvent(ei);
      }

      if (!ev.isRecurringEntity() && (ev.getRecurrenceId() == null)) {
        return indexEvent(ei,
                          ItemKind.master,
                          ev.getDtstart(),
                          ev.getDtend(),
                          null,
                          waitForIt); //ev.getRecurrenceId(),
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

      final DateLimits dl = new DateLimits();

      /* As a side-effect the call to makeInstances updates dl for use
         later. For tombstoned events we don't index.
       */
      if (!makeEventInstances(ei, dl, ev.getTombstoned())) {
        return null;
      }

      //</editor-fold>

      //<editor-fold desc="Emit the master event with a date range covering the entire period.">

      final String stzid = ev.getDtstart().getTzid();
      final boolean dateOnly = ev.getDtstart().getDateType();

      final BwDateTime start =
              BwDateTime.makeBwDateTime(dateOnly,
                                        dl.minStart, stzid);
      final BwDateTime end =
              BwDateTime.makeBwDateTime(dateOnly,
                                        dl.maxEnd, stzid);
      final IndexResponse iresp = indexEvent(ei,
                                             ItemKind.master,
                                             start,
                                             end,
                                             null,
                                             waitForIt);
      //</editor-fold>

      return iresp;
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private final static long bulkSize = 5 * 1024 * 1024;

  private boolean makeEventInstances(final EventInfo ei,
                                     final DateLimits dl,
                                     final boolean noIndex) {
    final BwEvent ev = ei.getEvent();

    /* Create a list of all instance date/times before overrides. */

    final int maxYears;
    final int maxInstances;

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
      return false;
      //throw new BedeworkException(CalFacadeErrorCode.noRecurrenceInstances);
    }

    int instanceCt = maxInstances;

    final String stzid = ev.getDtstart().getTzid();
    final boolean dateOnly = ev.getDtstart().getDateType();

    /* First build a table of overrides so we can skip these later
     */
    final Map<String, String> overrides = new HashMap<>();

    BulkRequest bulkReq = null;

    if (!noIndex) {
      bulkReq = new BulkRequest();
    }

    if (debug()) {
      debug("Start makeInstances");
    }

    /*
      if (!Util.isEmpty(ei.getOverrideProxies())) {
        for (BwEvent ov: ei.getOverrideProxies()) {
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());
        }
      }
      */
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

        dl.checkMin(rstart);
        dl.checkMax(rend);

        if (bulkReq != null) {
          /*iresp = */
          bulkReq = addToBulk(bulkReq,
                              oei,
                              ItemKind.override,
                              rstart,
                              rend,
                              ov.getRecurrenceId());
        }

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

      dl.checkMin(rstart);
      dl.checkMax(rend);

      if (bulkReq != null) {
        /*iresp = */
        bulkReq = addToBulk(bulkReq,
                            ei,
                            entity,
                            rstart,
                            rend,
                            recurrenceId);
      }

      instanceCt--;
      if (instanceCt == 0) {
        // That's all you're getting from me
        break;
      }
    }

    if ((bulkReq != null) && (bulkReq.estimatedSizeInBytes() > 0)) {
      flushBulkReq(bulkReq);
    }



    return true;
  }

  private boolean deleteEvent(final EventInfo ei) {
    final DeleteByQueryRequest delQreq =
            new DeleteByQueryRequest(targetIndex);

    final var path = ei.getEvent().getColPath();
    final var uid = ei.getEvent().getUid();

    final QueryBuilder qb = getFilters(null)
            .allInstances(path, uid);

    delQreq.setConflicts("proceed");
    delQreq.setRefresh(true);
    delQreq.setQuery(qb);

    boolean ok = true;

    try {
      final BulkByScrollResponse bulkResponse =
              getClient().deleteByQuery(delQreq, RequestOptions.DEFAULT);

      for (final BulkItemResponse.Failure f: bulkResponse.getBulkFailures()) {
        warn(format("Failing shards for delete - " +
                            "path: %s, uid: %s, index: %s",
                    path, uid, f.getIndex()));

        ok = false;
      }
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }

    return ok;
  }

  private BulkRequest addToBulk(final BulkRequest bulkReq,
                                final EventInfo ei,
                                final ItemKind kind,
                                final BwDateTime start,
                                final BwDateTime end,
                                final String recurid) {
    bulkReq.add(makeRequest(ei, kind, start, end, recurid));

    if (bulkReq.estimatedSizeInBytes() <= bulkSize) {
      return bulkReq;
    }

    return flushBulkReq(bulkReq);
  }

  private BulkRequest flushBulkReq(final BulkRequest bulkReq) {
    try {
      final BulkResponse bresp = getClient()
              .bulk(bulkReq, RequestOptions.DEFAULT);

      if (bresp.hasFailures()) {
        throw new BedeworkException("Failed bulk index: " + bresp.buildFailureMessage());
      }

      if (debug()) {
        debug(format("bulk update %s entries, %s size took %s",
                     bulkReq.numberOfActions(),
                     bulkReq.estimatedSizeInBytes(),
                     bresp.getTook().toString()));
      }

      return new BulkRequest();
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private IndexRequest makeRequest(final EventInfo ei,
                                   final ItemKind kind,
                                   final BwDateTime start,
                                   final BwDateTime end,
                                   final String recurid) {
    final DocBuilder db = getDocBuilder();
    final EsDocInfo di = db.makeDoc(ei,
                                    kind,
                                    start,
                                    end,
                                    recurid);

    final IndexRequest req = new IndexRequest(targetIndex);

    req.id(di.getId());
    req.source(di.getSource());

    if (di.getVersion() != 0) {
      req.version(di.getVersion()).versionType(VersionType.EXTERNAL);
    }

    return req;
  }

  private IndexResponse indexEvent(final EventInfo ei,
                                   final ItemKind kind,
                                   final BwDateTime start,
                                   final BwDateTime end,
                                   final String recurid,
                                   final boolean waitForIt) {
    try {
      ei.getEvent().beforeAdd(); // Ensure all up to date
      final DocBuilder db = getDocBuilder();
      final EsDocInfo di = db.makeDoc(ei,
                                      kind,
                                      start,
                                      end,
                                      recurid);

      return indexDoc(di, waitForIt); //ei.getEvent().getTombstoned());
    } catch (final BedeworkException be) {
      throw be;
    } catch (final VersionConflictEngineException vcee) {
      error(vcee);
      error("Event was " + ei.getEvent());
      throw new BedeworkException(vcee);
      /* Can't do this any more
      if (vcee.currentVersion() == vcee.getProvidedVersion()) {
        warn("Failed index with equal version for type " +
                     di.getType() +
                     " and id " + di.getId());
      }

      return null;
      */
    } catch (final OpenSearchException ose) {
      final var dmsg = ose.getDetailedMessage();
      if ((dmsg != null) &&
              dmsg.contains("version_conflict_engine_exception")) {
        // Not too much noise
        error(dmsg);
        error("Event was " + ei.getEvent());
        return null;
      }
      error("Event was " + ei.getEvent());
      throw new BedeworkException(ose);
    } catch (final Throwable t) {
      error("Event was " + ei.getEvent());
      throw new BedeworkException(t);
    }
  }

  private IndexResponse indexDoc(final EsDocInfo di,
                                 final boolean waitForIt) throws Throwable {
    requireDocType(di.getType());

    //batchCurSize++;
    final IndexRequest req = new IndexRequest(targetIndex);

    req.id(di.getId());
    req.source(di.getSource());

    if (di.getVersion() != 0) {
      req.version(di.getVersion()).versionType(VersionType.EXTERNAL);
    }

    if (waitForIt) {
      req.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
    }

    if (debug()) {
      debug("Indexing to index " + targetIndex +
                    " with DocInfo " + di);
    }

    return getClient().index(req, RequestOptions.DEFAULT);
  }

  private EventInfo checkAccess(final EventInfo ei,
                                final int desiredAccess) {
    if (ei == null) {
      return null;
    }

    final BwEvent ev = ei.getEvent();

    final CurrentAccess ca;
    try {
      ca = accessCheck.checkAccess(ev, desiredAccess, true);
    } catch (final BedeworkException ignored) {
      return null;
    }

    if ((ca == null) || !ca.getAccessAllowed()) {
      return null;
    }

    ei.setCurrentAccess(ca);
    return ei;
  }

  private List<EventInfo> fetchEvents(final QueryBuilder qb,
                                      final int count,
                                      final int desiredAccess) {
    final List<EventInfo> eis =
            fetchEntities(docTypeEvent,
                          new BuildEntity<>() {
                            @Override
                            EventInfo make(final EntityBuilder eb,
                                           final String id) {
                              final EventInfo entity = eb.makeEvent(id, false);

                              if (entity == null) {
                                return null;
                              }

                              final var resp = new Response<>();
                              restoreEvent(resp, entity);
                              if (!resp.isOk()) {
                                throw new BedeworkException(resp.toString());
                              }

                              return entity;
                            }
                          },
                          qb,
                          count);

    return new EventGroups(eis, desiredAccess).finalise();
  }

  private static class EventGroup {
    EventInfo master;
    boolean noAccess;
    List<BwEventAnnotation> overrides = new ArrayList<>();
  }

  private class EventGroups extends HashMap<String, EventGroup> {
    EventGroups(final List<EventInfo> eis,
                final int desiredAccess) {
      for (final var ei : eis) {
        final BwEvent ev = ei.getEvent();
        final String href = ev.getHref();

        final EventGroup eg = computeIfAbsent(href,
                                              k -> new EventGroup());

        if (eg.noAccess) {
          continue;
        }

        if (ev.getRecurrenceId() == null) {
          if (eg.master != null) {
            warn("Duplicate hrefs: " + href);
            continue;
          }

          eg.master = checkAccess(ei, desiredAccess);

          if (eg.master == null) {
            eg.noAccess = true;
          }

          continue;
        }

        if (!(ev instanceof BwEventAnnotation)) {
          warn("Expected an annotation for " + href);
          continue;
        }

        eg.overrides.add((BwEventAnnotation)ev);
      }
    }

    List<EventInfo> finalise() {
      final List<EventInfo> res = new ArrayList<>();

      for (final var href: keySet()) {
        final var eg = get(href);

        if (eg.noAccess) {
          continue;
        }

        final var mei = eg.master;

        if (mei == null) {
          warn("Missing master for " + href);
          continue;
        }

        res.add(mei);

        if (Util.isEmpty(eg.overrides)) {
          continue;
        }

        for (final var oev: eg.overrides) {
          oev.setTarget(mei.getEvent());
          oev.setMaster(mei.getEvent());
          oev.setOverride(true);
          oev.setTombstoned(false);

          final BwEvent proxy = new BwEventProxy(oev);

          final EventInfo oei = new EventInfo(proxy);

          mei.addOverride(oei);
        }
      }

      return res;
    }
  }

  public RestHighLevelClient getClient() {
    return sch.getSearchClient();
  }

  /** Return a new filter builder
   *
   * @param recurRetrieval  - value modifies search
   * @return a filter builder
   */
  public ESQueryFilter getFilters(final RecurringRetrievalMode recurRetrieval) {
    return new ESQueryFilter(publick,
                             principalHref,
                             superUser,
                             recurRetrieval,
                             docType);
  }

  private String newIndexSuffix() {
    // ES only allows lower case letters in names (and digits)
    final StringBuilder suffix = new StringBuilder();

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

  private Object makeEntity(final Response<?> resp,
                            final SearchHit hit,
                            final RecurringRetrievalMode rrm) {
    final String kval = hit.getId();

    if (kval == null) {
      errorReturn(resp, "org.bedework.index.noitemkey");
    }

    try {
      final EntityBuilder eb = getEntityBuilder(hit.getSourceAsMap());

      return switch (docType) {
        case docTypeCollection -> makeCollection(eb);
        case docTypeCategory -> eb.makeCat();
        case docTypeContact -> eb.makeContact();
        case docTypeLocation -> eb.makeLocation();
        case docTypeEvent -> makeEvent(eb, resp, kval,
                                       (rrm != null) && (rrm.mode == Rmode.expanded));
        default -> null;
      };
    } catch (final BedeworkException be) {
      errorReturn(resp, be);
      return null;
    }
  }

  private BwCalendar makeCollection(final EntityBuilder eb) {
    final BwCalendar entity = eb.makeCollection();

    if (entity != null) {
      restoreCategories(entity);
    }

    return entity;
  }

  private EventInfo makeEvent(final Response<?> resp,
                              final SearchHit hit) {
    if (hit.getId() == null) {
      throw new RuntimeException("Missing key");
    }

    final EntityBuilder ebmstr = getEntityBuilder(
            hit.getSourceAsMap());
    return makeEvent(ebmstr, resp, hit.getId(), false);
  }

  private EventInfo makeEvent(final EntityBuilder eb,
                              final Response<?> resp,
                              final String kval,
                              final boolean expanded) {
    final EventInfo entity = eb.makeEvent(kval, expanded);

    if (entity == null) {
      return null;
    }

    restoreEvent(resp, entity);

    return entity;
  }

  private void restoreEvent(final Response<?> resp,
                            final Collection<EventInfo> eis) {
    if (Util.isEmpty(eis)) {
      return;
    }

    for (final EventInfo ei: eis) {
      restoreEvent(resp, ei);
    }
  }

  private void restoreEvent(final Response<?> resp,
                            final EventInfo ei) {
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
          final GetEntityResponse<BwContact> geresp =
                  indexFetcher.fetchContact(params, href);

          if (!resp.isOk()) {
            error("Unable to fetch contact " + href +
                          " for event " + ev.getHref());
            errorReturn(resp, "Unable to fetch contact " + href);
            continue;
          }

          ev.addContact(geresp.getEntity());
        }
      }

      final String href = ev.getLocationHref();
      if (href != null) {
        final GetEntityResponse<BwLocation> geresp =
                indexFetcher.fetchLocation(params, href);

        if (!resp.isOk()) {
          error("Unable to fetch location " + href +
                        " for event " + ev.getHref());
          errorReturn(resp, "Unable to fetch location " + href);
        } else {
          ev.setLocation(geresp.getEntity());
        }
      }
    } catch (final Throwable t) {
      error(t);
      errorReturn(resp, t);
    }
  }

  private void restoreCategories(final CategorisedEntity ce) {
    if ((ce == null) || (indexFetcher == null)) {
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
      final GetEntityResponse<BwCategory> resp = indexFetcher.fetchCategory(params, href);

      if (!resp.isOk()) {
        error("Unable to fetch cat " + href);
        continue;
      }

      ce.addCategory(resp.getEntity());
    }
  }

  private EntityBuilder getEntityBuilder(final Map<String, ?> fields) {
    return new EntityBuilder(publick, fields);
  }

  private DocBuilder getDocBuilder() {
    return new DocBuilder();
  }

  private <T extends Response<?>> T errorReturn(
          final T resp,
          final Throwable t) {
    return errorReturn(resp, t, failed);
  }

  private <T extends Response<?>> T errorReturn(
          final T resp,
          final Throwable t,
          final Response.Status st) {
    if (debug()) {
      error(t);
    }
    return errorReturn(resp, t.getMessage(), st);
  }

  private <T extends Response<?>> T errorReturn(
          final T resp,
          final String msg,
          final Response.Status st) {
    resp.setMessage(msg);
    resp.setStatus(st);

    return resp;
  }

  private <T extends Response<?>> T errorReturn(
          final T resp,
          final String msg) {
    if (resp == null) {
      return null;
    }

    if (resp.getMessage() != null) {
      resp.setMessage(resp.getMessage() + "\n" + msg);
    } else {
      resp.setMessage(msg);
    }
    resp.setStatus(failed);

    return resp;
  }

  private String fileToString(final String path) throws IndexException {
    final StringBuilder content = new StringBuilder();
    try (final Stream<String> stream =
                 Files.lines(Paths.get(path),
                             StandardCharsets.UTF_8)) {
      stream.forEach(s -> content.append(s).append("\n"));
    } catch (final Throwable t) {
      throw new IndexException(t);
    }

    return content.toString();
  }

  private void requireDocType(final String docType) {
    if (!this.docType.equals(docType)) {
      throw new BedeworkException("Require doctype: " + docType);
    }
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
