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

import org.bedework.access.Ace;
import org.bedework.access.Acl;
import org.bedework.access.PrivilegeDefs;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SortTerm;
import org.bedework.calfacade.indexing.BwIndexKey;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.indexing.Index;
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
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.alias.get.IndicesGetAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.get.IndicesGetAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.status.IndicesStatusRequestBuilder;
import org.elasticsearch.action.admin.indices.status.IndicesStatusResponse;
import org.elasticsearch.action.delete.DeleteResponse;
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
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.bedework.calcore.indexing.DocBuilder.DocInfo;
import static org.bedework.calcore.indexing.DocBuilder.TypeId;

/** Implementation of indexer for ElasticSearch
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class BwIndexEsImpl /*extends CalSvcDb*/ implements BwIndexer {
  private transient Logger log;

  private boolean debug;

  private BwIndexKey keyConverter = new BwIndexKey();

  private int batchMaxSize = 0;
  private int batchCurSize = 0;

  private Object batchLock = new Object();

  private ObjectMapper om;

  private boolean publick;
  private BwPrincipal principal;

  private String host;
  private int port = 9300;

  private static Node theNode; /* For embedded use */
  private static Client theClient;
  private static volatile Object clientSyncher = new Object();

  private String targetIndex;
  private boolean writeable;

  private AuthProperties authpars;
  private AuthProperties unauthpars;
  private IndexProperties idxpars;
  private BasicSystemProperties basicSysprops;

  private EntityBuilder entityBuilder;

  private DocBuilder docBuilder;

  /** Constructor
   *
   * @param configs
   * @param publick - if false we add an owner term to the searches
   * @param principal - who we are searching for
   * @param writeable - true for an updatable index
   * @param indexName - explicitly specified
   * @throws CalFacadeException
   */
  public BwIndexEsImpl(final Configurations configs,
                       final boolean publick,
                       final BwPrincipal principal,
                       final boolean writeable,
                       final String indexName) throws CalFacadeException {
    debug = getLog().isDebugEnabled();

    this.publick = publick;
    this.principal = principal;
    this.writeable = writeable;

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
                             final AccessChecker accessCheck) throws CalFacadeException {
    EsSearchResult res = new EsSearchResult(this);

    res.start = start;
    res.end = end;
    res.pageSize = pageSize;
    res.accessCheck = accessCheck;

    if (query != null) {
      res.curQuery = QueryBuilders.queryString(query);
    }

    ESQueryFilter ef = getFilters();

    res.curFilter = ef.buildFilter(filter);

    res.curFilter = ef.addDateRangeFilter(res.curFilter, start, end);

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
      debug("search-srb=" + srb);
    }

    SearchResponse resp = srb.execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + resp.status());
      }
    }

    res.setFound(resp.getHits().getTotalHits());

    return res;
  }

  @Override
  public List<SearchResultEntry> getSearchResult(final SearchResult sres,
                                                 final Position pos)
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

    return getSearchResult(sres, offset, sres.getPageSize());
  }

  @Override
  public List<SearchResultEntry> getSearchResult(final SearchResult sres,
                                                 final int offset,
                                                 final int num)
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
      } else if (IcalDefs.entityTypes.contains(dtype)) {
        entity = eb.makeEvent();
        EventInfo ei = (EventInfo)entity;
        Acl.CurrentAccess ca = res.accessCheck.checkAccess(ei.getEvent(),
                                                         PrivilegeDefs.privAny, true);

        if ((ca == null) || !ca.getAccessAllowed()) {
          continue;
        }

        ei.setCurrentAccess(ca);
      }

      entities.add(new SearchResultEntry(entity,
                                         dtype,
                                         hit.getScore()));
    }

    return entities;
  }

  @Override
  public long getKeys(final SearchResult sres,
                      final long n,
                      final Index.Key[] keys) throws CalFacadeException {
    EsSearchResult res = (EsSearchResult)sres;
    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    SearchResponse resp = srb.setSearchType(SearchType.QUERY_AND_FETCH)
//            .setScroll(new TimeValue(60000))
            .setQuery(res.curQuery)
            .setFilter(res.curFilter).execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + resp.status());
      }
    }

    SearchHits hits = resp.getHits();

    if ((hits.getHits() == null) ||
            (hits.getHits().length == 0)) {
      return 0;
    }

    int num = hits.getHits().length;
    if (keys.length < num) {
      // Bad result?
      num = keys.length;
    }

    for (int i = 0; i < num; i++) {
      SearchHit hit = hits.getAt(i);

      keys[i] = makeKey(keys[i], hit);
    }

    return num;
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
  public void unindexEntity(final Object rec) throws CalFacadeException {
    //try {
    // Always unbatched

    List<TypeId> tids = getDocBuilder().makeKeys(rec);

    // XXX Should use a batch for large number
    for (TypeId tid: tids) {
      DeleteResponse resp = getClient().prepareDelete(targetIndex,
                                                      tid.type,
                                                      tid.id).execute().actionGet();

      // XXX process response
    }
    //} catch (IOException e) {
    //throw new CalFacadeException(e);
    //}
  }

  @Override
  public void setCleanLocks(final boolean val) {
    throw new RuntimeException("unimplemented");
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
  public List<String> listIndexes() throws CalFacadeException {
    List<String> res = new ArrayList<>();

    try {
      IndicesAdminClient idx = getAdminIdx();

      IndicesStatusRequestBuilder isrb= idx.prepareStatus(Strings.EMPTY_ARRAY);

      ActionFuture<IndicesStatusResponse> sr = idx.status(
              isrb.request());
      IndicesStatusResponse sresp  = sr.actionGet();

      return new ArrayList<>(sresp.getIndices().keySet());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public List<String> purgeIndexes(final List<String> preserve) throws CalFacadeException {
      List<String> indexes = listIndexes();
      List<String> purged = new ArrayList<>();

      if (Util.isEmpty(indexes)) {
        return purged;
      }

      for (String idx: indexes) {
        if (preserve.contains(idx)) {
          continue;
        }

        unloadIndex(idx);

        purged.add(idx);
      }

      return purged;
  }

  private void unloadIndex(final String name) throws CalFacadeException {
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
  public boolean isFetchEnabled() throws CalFacadeException {
    return true;
  }

  @Override
  public BwCategory fetchCat(final String field, final String val)
          throws CalFacadeException {
    EntityBuilder eb = fetchEntity(docTypeCategory, field,val);

    if (eb == null) {
      return null;
    }

    return eb.makeCat();
  }

  @Override
  public BwContact fetchContact(final String field, final String val)
          throws CalFacadeException {
    EntityBuilder eb = fetchEntity(docTypeContact, field,val);

    if (eb == null) {
      return null;
    }

    return eb.makeContact();
  }

  @Override
  public BwLocation fetchLocation(final String field, final String val)
          throws CalFacadeException {
    EntityBuilder eb = fetchEntity(docTypeLocation, field,val);

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
            .setFilter(getFilters().buildFilter(null))
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
                                    final String field,
                                    final String val)
          throws CalFacadeException {
    if (field.equals(PropertyInfoIndex.UID.getJname())) {
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
            .setFilter(getFilters().buildFilter(field, val))
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
                    " with field " + field +
                    " value " + val);
      return null;
    }

    return getEntityBuilder(hits.hits()[0].sourceAsMap());
  }

  /** Called to make or fill in a Key object.
   *
   * @param key   Possible Index.Key object for reuse
   * @param hit    The retrieved document
   * @return Index.Key  new or reused object
   * @throws CalFacadeException
   */
  private Index.Key makeKey(final Index.Key key,
                            final SearchHit hit) throws CalFacadeException {
    BwIndexKey bwkey;

    if ((key == null) || (!(key instanceof BwIndexKey))) {
      bwkey = new BwIndexKey();
    } else {
      bwkey = (BwIndexKey)key;
    }

    Float score = hit.getScore();

    if (score != null) {
      bwkey.setScore(score);
    }

    String dtype = hit.getType();

    if (dtype == null) {
      throw new CalFacadeException("org.bedework.index.noitemtype");
    }

    String kval = hit.getId();

    if (kval == null) {
      throw new CalFacadeException("org.bedework.index.noitemkey");
    }

    bwkey.setItemType(dtype);

    if (dtype.equals(docTypeCollection)) {
      bwkey.setKey1(kval);
    } else if (dtype.equals(docTypeCategory)) {
      bwkey.setKey1(kval);
    } else if (dtype.equals(docTypeContact)) {
      bwkey.setKey1(kval);
    } else if (dtype.equals(docTypeLocation)) {
      bwkey.setKey1(kval);
    } else if (IcalDefs.entityTypes.contains(dtype)) {
      try {
        bwkey.setEventKey(kval);
      } catch (IndexException ie) {
        throw new CalFacadeException(ie);
      }
    } else {
      throw new CalFacadeException(IndexException.unknownRecordType,
                                   dtype);
    }

    return bwkey;
  }

  /* Return the response after indexing */
  private IndexResponse index(final Object rec) throws CalFacadeException {
    try {
      DocInfo di = null;
      XContentBuilder builder = newBuilder();

      builder.startObject();

      if (rec instanceof BwCalendar) {
        di = getDocBuilder().makeDoc(builder, rec, null, null, null);
      }

      if (rec instanceof BwCategory) {
        di = getDocBuilder().makeDoc(builder, (BwCategory)rec);
      }

      if (rec instanceof BwContact) {
        di = getDocBuilder().makeDoc(builder, (BwContact)rec);
      }

      if (rec instanceof BwLocation) {
        di = getDocBuilder().makeDoc(builder, (BwLocation)rec);
      }

      builder.endObject();

      if (di != null) {
        return indexDoc(builder, di);
      }

      if (!(rec instanceof EventInfo)) {
        throw new CalFacadeException(
                new IndexException(IndexException.unknownRecordType,
                                   rec.getClass().getName()));
      }

      /* If it's not recurring or an override index it */

      EventInfo ei = (EventInfo)rec;
      BwEvent ev = ei.getEvent();

      if (!ev.getRecurring() || (ev.getRecurrenceId() != null)) {
        builder = newBuilder();

        builder.startObject();
        di = getDocBuilder().makeDoc(builder,
                                     rec,
                                     ev.getDtstart(),
                                     ev.getDtend(),
                                     ev.getRecurrenceId());

        builder.endObject();

        return indexDoc(builder, di);
      }

      /* Delete all instances of this event: we'll do a delete by query
       * We need to find all with the same path and uid.
       */

      /* TODO - do a query for all recurrence ids and delete the ones
          we don't want.
       */

      String itemType = IcalDefs.fromEntityType(ev.getEntityType());

      DeleteByQueryRequestBuilder delQreq = getClient().prepareDeleteByQuery(
              targetIndex).setTypes(itemType);

      ESQueryFilter esq= getFilters();
      FilterBuilder fb = esq.addTerm(null, "colPath", ev.getColPath());
      fb = esq.addTerm(fb, "uid", ev.getUid());
      delQreq.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                                                   fb));
      DeleteByQueryResponse delResp = delQreq.execute()
              .actionGet();

      for (IndexDeleteByQueryResponse idqr: delResp.getIndices().values()) {
        if (idqr.getFailedShards() > 0) {
          warn("Failing shards for recurrence delete uid: " + ev.getUid() +
               " colPath: " + ev.getColPath() +
               " index: " + idqr.getIndex());
        }
      }

      /* Emit all instances that aren't overridden. */

      int maxYears;
      int maxInstances;

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
          builder = newBuilder();

          builder.startObject();
          di = getDocBuilder().makeDoc(builder,
                                       oei,
                                       ov.getDtstart(),
                                       ov.getDtend(),
                                       ov.getRecurrenceId());

          builder.endObject();

          iresp = indexDoc(builder, di);
        }
      }

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

        builder = newBuilder();

        builder.startObject();
        di = getDocBuilder().makeDoc(builder,
                                     rec,
                                     rstart,
                                     rend,
                                     recurrenceId);

        builder.endObject();

        di.id = keyConverter.makeEventKey(ev.getColPath(),
                                          ev.getUid(),
                                          recurrenceId);
        iresp = indexDoc(builder, di);

        instanceCt--;
        if (instanceCt == 0) {
          // That's all you're getting from me
          break;
        }
      }

      return iresp;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private IndexResponse indexDoc(final XContentBuilder builder,
                                  final DocInfo di) throws Throwable {
    batchCurSize++;
    IndexRequestBuilder req = getClient().
            prepareIndex(targetIndex, di.type, di.id);

    req.setSource(builder);

    if (di.version != 0) {
      req.setVersion(di.version).setVersionType(VersionType.EXTERNAL);
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

  private XContentBuilder newBuilder() throws CalFacadeException {
    try {
      XContentBuilder builder = XContentFactory.jsonBuilder();

      if (debug) {
        builder = builder.prettyPrint();
      }

      return builder;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private ESQueryFilter getFilters() {
    return new ESQueryFilter(publick, principal.getPrincipalRef());
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

  private DocBuilder getDocBuilder() {
    if (docBuilder == null) {
      docBuilder = new DocBuilder(principal,
                                  authpars, unauthpars, basicSysprops);
    }

    return docBuilder;
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
