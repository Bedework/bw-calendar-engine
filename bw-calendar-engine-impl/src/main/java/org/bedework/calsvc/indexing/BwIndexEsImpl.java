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
package org.bedework.calsvc.indexing;

import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.PropertyFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.BwCategoryFilter;
import org.bedework.calfacade.filter.BwCollectionFilter;
import org.bedework.calfacade.filter.BwCreatorFilter;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;

import edu.rpi.cct.misc.indexing.Index;
import edu.rpi.cct.misc.indexing.IndexException;
import edu.rpi.cct.misc.indexing.SearchLimits;
import edu.rpi.cmt.access.Acl;
import edu.rpi.cmt.access.PrivilegeDefs;
import edu.rpi.cmt.calendar.IcalDefs;
import edu.rpi.sss.util.DateTimeUtil;
import edu.rpi.sss.util.Util;

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
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.action.update.UpdateResponse;
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
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.xml.ws.Holder;

import static org.bedework.calsvc.indexing.BwIndexDefs.itemTypeCalendar;
import static org.bedework.calsvc.indexing.BwIndexDefs.itemTypeCategory;
import static org.bedework.calsvc.indexing.BwIndexDefs.itemTypeEvent;

/** Implementation of indexer for ElasticSearch
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class BwIndexEsImpl implements BwIndexer {
  private transient Logger log;

  private boolean debug;

  private BwIndexKey keyConverter = new BwIndexKey();

  /* For paged queries */
  private QueryBuilder curQuery;
  private FilterBuilder curFilter;

  private int batchMaxSize = 0;
  private int batchCurSize = 0;

  private Object batchLock = new Object();

  // Types of entity we index
  private static final String docTypeEvent = "event";
  private static final String docTypeCollection = "collection";
  private static final String docTypeCategory = "category";

  private ObjectMapper om;

  private boolean publick;
  private String principal;

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

  /* Used to batch index */

  /** Constructor
   *
   * @param publick - if false we add an owner term to the searches
   * @param principal - who we are searching for
   * @param writeable - true for an updatable index
   * @param authpars - for authenticated limits
   * @param unauthpars - for public limits
   * @param idxpars - for system config info
   * @param noAdmin - for no administration
   * @param indexName - explicitly specified
   * @throws IndexException
   */
  public BwIndexEsImpl(final boolean publick,
                       final String principal,
                       final boolean writeable,
                       final AuthProperties authpars,
                       final AuthProperties unauthpars,
                       final IndexProperties idxpars,
                       final boolean noAdmin,
                       final String indexName) throws IndexException {
    debug = getLog().isDebugEnabled();

    this.publick = publick;
    this.principal = principal;
    this.idxpars = idxpars;
    this.authpars = authpars;
    this.unauthpars = unauthpars;
    this.writeable = writeable;

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

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#setBatchSize(int)
   */
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

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#endBwBatch()
   */
  @Override
  public void endBwBatch() throws CalFacadeException {
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#flush()
   */
  @Override
  public void flush() throws CalFacadeException {
  }

  @Override
  public long getKeys(final long n,
                      final Index.Key[] keys) throws CalFacadeException {
    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    SearchResponse resp = srb.setSearchType(SearchType.COUNT)
            .setScroll(new TimeValue(60000))
            .setQuery(curQuery)
            .setFilter(curFilter).execute().actionGet();

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

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#indexEntity(java.lang.Object)
   */
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

      XContentBuilder builder = newBuilder();

      builder.startObject();

      index(builder, rec);

      builder.endObject();

      UpdateRequestBuilder req = getClient().prepareUpdate(targetIndex,
                                                    getType(rec),
                                                    makeKeyVal(rec));
      UpdateResponse resp = req.setDoc(builder)
              .setDocAsUpsert(true)
              .execute().actionGet();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#unindexEntity(java.lang.Object)
   */
  @Override
  public void unindexEntity(final Object rec) throws CalFacadeException {
    //try {
      // Always unbatched

      List<TypeId> tids = makeKeys(rec);

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
  public long search(final String query,
                     final SearchLimits limits) throws CalFacadeException {
    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    curQuery = QueryBuilders.queryString(query);
    srb.setQuery(curQuery);

    curFilter = addPrincipal(null);

    curFilter = addDateRangeFilter(curFilter, limits);

    SearchResponse resp = srb.setSearchType(SearchType.COUNT)
            .setScroll(new TimeValue(60000))
            .setFilter(curFilter).execute().actionGet();

    if (resp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + resp.status());
      }
    }

    return resp.getHits().getTotalHits();
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#setCleanLocks(boolean)
   */
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
    List<String> res = new ArrayList<String>();

    try {
      IndicesAdminClient idx = getAdminIdx();

      IndicesStatusRequestBuilder isrb= idx.prepareStatus(Strings.EMPTY_ARRAY);

      ActionFuture<IndicesStatusResponse> sr = idx.status(isrb.request());
      IndicesStatusResponse sresp  = sr.actionGet();

      return new ArrayList<String>(sresp.getIndices().keySet());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public List<String> purgeIndexes(final List<String> preserve) throws CalFacadeException {
      List<String> indexes = listIndexes();
      List<String> purged = new ArrayList<String>();

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

      List<String> indices = listIndexes();

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
  public BwCategory fetchCat(final String field,
                             final String val)
          throws CalFacadeException {
    if (field.equals("uid")) {
      GetRequestBuilder grb = getClient().prepareGet(targetIndex,
                                                     docTypeCategory,
                                                     val);

      GetResponse gr = grb.execute().actionGet();

      if (!gr.isExists()) {
        return null;
      }

      return makeCat(gr.getSourceAsMap());
    }

    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    FilterBuilder fb = addPrincipal(addTerm(null, field, val));

    srb.setTypes(docTypeCategory);

    SearchResponse response = srb.setSearchType(SearchType.QUERY_THEN_FETCH)
            .setFilter(fb)
            .setFrom(0).setSize(60).setExplain(true)
            .execute()
            .actionGet();

    SearchHits hits = response.getHits();

    //Break condition: No hits are returned
    if (hits.hits().length == 0) {
      return null;
    }

    if (hits.getTotalHits() != 1) {
      error("Multiple categories with field " + field +
                    " value " + val);
      return null;
    }

    return makeCat(hits.hits()[0]);
  }

  @Override
  public List<BwCategory> fetchAllCats() throws CalFacadeException {
    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    srb.setTypes(docTypeCategory);

    FilterBuilder fb = addPrincipal(null);

    int tries = 0;
    int ourPos = 0;
    int ourCount = maxFetchCount;

    List<BwCategory> res = new ArrayList<>();

    SearchResponse scrollResp = srb.setSearchType(SearchType.SCAN)
            .setScroll(new TimeValue(60000))
            .setFilter(fb)
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
        BwCategory cat = makeCat(hit);
        res.add(cat);
        ourPos++;
      }

      tries++;
    }

    return res;
  }

  private static final int maxFetchCount = 100;
  private static final int absoluteMaxTries = 1000;

  @Override
  public Set<EventInfo> fetch(final FilterBase filter,
                              final String start,
                              final String end,
                              final Holder<Long> found,
                              final long pos,
                              final int count,
                              final AccessChecker accessCheck) throws CalFacadeException {
    long ourPos = pos;
    int ourCount = count;

    if ((ourCount < 0) | (ourCount > maxFetchCount)) {
      ourCount = maxFetchCount;
    }

    int fetched = 0;
    int tries = 0;
    Set<EventInfo> res = new ConcurrentSkipListSet<>();

    SearchRequestBuilder srb = getClient().prepareSearch(targetIndex);

    srb.setTypes(docTypeEvent);

    FilterBuilder fb = null;

    if (filter != null) {
      fb = makeFilter(filter);
    }

    fb = addPrincipal(fb);

    long toFetch = count;

    SearchResponse scrollResp = srb.setSearchType(SearchType.SCAN)
            .setScroll(new TimeValue(60000))
            .setFilter(fb)
            .setSize(ourCount).execute().actionGet(); //ourCount hits per shard will be returned for each scroll

    if (scrollResp.status() != RestStatus.OK) {
      if (debug) {
        debug("Search returned status " + scrollResp.status());
      }
    }

    //Scroll until no hits are returned

    while ((toFetch < 0) || (fetched < toFetch)) {
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

      if (toFetch < 0) {
        toFetch = hits.getTotalHits();
      }

      if (found != null) {
        found.value = toFetch;
      }

      //Break condition: No hits are returned
      if (hits.hits().length == 0) {
        break;
      }

      for (SearchHit hit : hits) {
        //Handle the hit...
        EventInfo ei = makeEvent(hit);
        res.add(ei);
        fetched++;
        ourPos++;
      }

      tries++;
    }

    Set<EventInfo> checked = new ConcurrentSkipListSet<>();

    for (EventInfo ei: res) {
      Acl.CurrentAccess ca = accessCheck.checkAccess(ei.getEvent(),
                                     PrivilegeDefs.privAny, true);

      if ((ca == null) || !ca.getAccessAllowed()) {
        continue;
      }

      checked.add(ei);
    }

    return checked;
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private FilterBuilder makeFilter(final FilterBase f) throws CalFacadeException {
    if (f instanceof AndFilter) {
      AndFilterBuilder fb = new AndFilterBuilder();

      for (FilterBase flt: f.getChildren()) {
        fb.add(makeFilter(flt));
      }

      return fb;
    }

    if (f instanceof OrFilter) {
      OrFilterBuilder fb = new OrFilterBuilder();

      for (FilterBase flt: f.getChildren()) {
        fb.add(makeFilter(flt));
      }

      return fb;
    }

    if (!(f instanceof PropertyFilter)) {
      return null;
    }

    if (f instanceof PresenceFilter) {
      return null;
    }

    if (f instanceof BwCreatorFilter) {
      return FilterBuilders.termFilter("creator",
                                       ((BwCreatorFilter)f)
                                               .getEntity());
    }

    if (f instanceof BwCategoryFilter) {
      BwCategory cat = ((BwCategoryFilter)f).getEntity();
      return FilterBuilders.termFilter("category_uid",
                                       cat.getUid());
    }

    if (f instanceof BwCollectionFilter) {
      BwCalendar col = ((BwCollectionFilter)f).getEntity();

      return FilterBuilders.termFilter("path",
                                       col.getPath());
    }

    return null;

    /*
    if (f instanceof TimeRangeFilter) {
      addThisJoin(pi);
      return;
    }

    if (f instanceof BwObjectFilter) {
      addThisJoin(pi);
      return;
    }*/
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

    if (dtype.equals(docTypeCollection)) {
      bwkey.setItemType(itemTypeCalendar);
      bwkey.setKey1(kval);
    } else if (dtype.equals(docTypeCategory)) {
      bwkey.setItemType(itemTypeCategory);
      bwkey.setKey1(kval);
    } else if (dtype.equals(docTypeEvent)) {
      bwkey.setItemType(itemTypeEvent);
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

  private BwCategory makeCat(final SearchHit hit) throws CalFacadeException {
    Map<String, Object> fields = hit.sourceAsMap();

    return makeCat(fields);
  }

  private BwCategory makeCat(final Map<String, Object> fields) throws CalFacadeException {
    BwCategory cat = new BwCategory();

    cat.setWord(new BwString(null,
                             getString(fields, "category")));
    cat.setDescription(new BwString(null,
                                    getString(fields,
                                              "description")));
    cat.setCreatorHref(getString(fields, "creator"));
    cat.setOwnerHref(getString(fields, "owner"));
    cat.setUid(getString(fields, "uid"));

    return cat;
  }

  private EventInfo makeEvent(final SearchHit hit) throws CalFacadeException {
    BwEvent ev = new BwEventObj();
    EventInfo ei = new  EventInfo(ev);

    Map<String, Object> fields = hit.sourceAsMap();

    /*
    Float score = (Float)sd.getFirstValue("score");

    if (score != null) {
      bwkey.setScore(score);
    }
    */

    ev.setName(getString(fields, "name"));
    ev.setColPath(getString(fields, "path"));

    Collection<Object> vals = getFieldValues(fields, "category_uid");
    if (!Util.isEmpty(vals)) {
      Set<String> catUids = new TreeSet<>();

      for (Object o: vals) {
        catUids.add((String)o);
      }

      ev.setCategoryUids(catUids);
    }

    ev.setCreated(getString(fields, "created"));

    ev.setLastmod(getString(fields, "last_modified"));
    ev.setDtstamp(ev.getLastmod());
    ev.setCreatorHref(getString(fields, "creator"));
    ev.setOwnerHref(getString(fields, "owner"));
    ev.setAccess(getString(fields, "acl"));
    ev.setSummary(getString(fields, "summary"));
    ev.setDescription(getString(fields, "description"));

    /* comment */
    /* contact */
    /* location - lat/long */
    /* resources */

    ev.setDtstart(unindexDate(fields, "start_"));
    ev.setDtend(unindexDate(fields, "end_"));

    ev.setDuration(getString(fields, "duration"));
    ev.setNoStart(Boolean.parseBoolean(getString(fields, "start_present")));
    ev.setEndType(getString(fields, "end_type").charAt(0));

    ev.setUid(getString(fields, "uid"));

    ev.setRecurrenceId(getString(fields, "recurrenceid"));

    ev.setEntityType(makeEntityType(getString(fields, "eventType")));

    ev.setStatus(getString(fields, "status"));

    ev.setLocationUid(getString(fields, "location_uid"));

    Set<String> xpnames = interestingXprops.keySet();

    if (!Util.isEmpty(xpnames)) {
      for (String xpname: xpnames) {
        @SuppressWarnings("unchecked")
        Collection<String> xvals = (Collection)getFieldValues(fields, interestingXprops.get(xpname));

        if (!Util.isEmpty(xvals)) {
          for (String xval: xvals) {
            int pos = xval.indexOf("\t");
            String pars = null;

            if (pos > 0) {
              pars = xval.substring(0, pos);
            }

            BwXproperty xp = new BwXproperty(xpname, pars, xval.substring(pos + 1));
            ev.addXproperty(xp);
          }
        }
      }
    }

    return ei;
  }

  private List<Object> getFieldValues(final Map<String, Object> fields,
                                      final String name) {
    Object val = fields.get(name);

    if (val == null) {
      return null;
    }

    if (val instanceof List) {
      return (List)val;
    }

    List<Object> vals = new ArrayList<>();
    vals.add(val);

    return vals;
  }

  private Object getFirstValue(final Map<String, Object> fields,
                               final String name) {
    Object val = fields.get(name);

    if (val == null) {
      return null;
    }

    if (!(val instanceof List)) {
      return val;
    }

    List vals = (List)val;
    if (Util.isEmpty(vals)) {
      return null;
    }

    return vals.get(0);
  }

  private String getString(final Map<String, Object> fields,
                           final String name) {
    return (String)getFirstValue(fields, name);
  }

  private static Map<String, Integer> entitytypeMap =
      new HashMap<String, Integer>();

  static {
    entitytypeMap.put("event", IcalDefs.entityTypeEvent);
    entitytypeMap.put("alarm", IcalDefs.entityTypeAlarm);
    entitytypeMap.put("todo", IcalDefs.entityTypeTodo);
    entitytypeMap.put("journal", IcalDefs.entityTypeJournal);
    entitytypeMap.put("freeAndBusy", IcalDefs.entityTypeFreeAndBusy);
    entitytypeMap.put("vavailability", IcalDefs.entityTypeVavailability);
    entitytypeMap.put("available", IcalDefs.entityTypeAvailable);
  }

  private int makeEntityType(final String val) throws CalFacadeException {
    Integer i = entitytypeMap.get(val);

    if (i == null) {
      return IcalDefs.entityTypeEvent;
    }

    return i;
  }

  private BwDateTime unindexDate(final Map<String, Object> fields,
                                 final String prefix) throws CalFacadeException {
    String utc = getString(fields, prefix + "utc");
    String local = getString(fields, prefix + "local");
    String tzid = getString(fields, prefix + "tzid");
    boolean floating = Boolean.parseBoolean(getString(fields,
                                                      prefix + "floating"));

    boolean dateType = (local != null) && (local.length() == 8);

    return BwDateTime.makeBwDateTime(dateType, local, utc, tzid,
                                     floating);
  }

  private FilterBuilder addDateRangeFilter(final FilterBuilder filter,
                                           final SearchLimits limits) throws CalFacadeException {
    if (limits == null) {
      return filter;
    }

    return addDateRangeFilter(filter, limits.fromDate, limits.toDate);
  }

  private FilterBuilder addDateRangeFilter(final FilterBuilder filter,
                                           final String start,
                                           final String end) throws CalFacadeException {
    if ((start == null) && (end == null)) {
      return filter;
    }

    AndFilterBuilder afb = new AndFilterBuilder(filter);

    if (start != null) {
      // End of events must be on or after the start of the range
      RangeFilterBuilder rfb = new RangeFilterBuilder("end_utc");

      rfb.gte(start);
      afb.add(rfb);
    }

    if (end != null) {
      // Start of events must be before the end of the range
      RangeFilterBuilder rfb = new RangeFilterBuilder("start_utc");

      rfb.lt(end);
      afb.add(rfb);
    }

    return afb;
  }

  private FilterBuilder addTerm(final FilterBuilder filter,
                                final String name,
                                final String val) throws CalFacadeException {
    FilterBuilder fb = FilterBuilders.termFilter(name, val);

    if (filter == null) {
      return fb;
    }

    AndFilterBuilder afb = new AndFilterBuilder(filter);

    afb.add(fb);

    return afb;
  }

  private FilterBuilder addPrincipal(final FilterBuilder filter) throws CalFacadeException {
    if (publick) {
      return filter;
    }

    FilterBuilder fb = FilterBuilders.termFilter("owner", principal);

    if (filter == null) {
      return fb;
    }

    AndFilterBuilder afb = new AndFilterBuilder(filter);
    afb.add(fb);

    return afb;
  }

  /** Called to make a key value for a record.
   *
   * @param   rec      The record
   * @return  String   String which uniquely identifies the record
   * @throws IndexException
   */
  private String makeKeyVal(final Object rec) throws IndexException {
    if (rec instanceof BwCalendar) {
      return ((BwCalendar)rec).getPath();
    }

    if (rec instanceof BwCategory) {
      return keyConverter.makeCategoryKey(((BwCategory) rec).getUid());
    }

    BwEvent ev = null;
    if (rec instanceof BwEvent) {
      ev = (BwEvent)rec;
    } else if (rec instanceof EventInfo) {
      ev = ((EventInfo)rec).getEvent();
    }

    if (ev != null) {
      String path = ev.getColPath();
      String guid = ev.getUid();
      String recurid = ev.getRecurrenceId();

      return keyConverter.makeEventKey(path, guid, recurid);
    }

    throw new IndexException(IndexException.unknownRecordType,
                             rec.getClass().getName());
  }

  private void index(final XContentBuilder builder,
                     final Object rec) throws CalFacadeException {
    if (rec instanceof BwCalendar) {
      makeDoc(builder, rec, null, null, null);
      return;
    }

    if (rec instanceof BwCategory) {
      makeDoc(builder, (BwCategory)rec);
      return;
    }

    if (!(rec instanceof EventInfo)) {
      throw new CalFacadeException(new IndexException(IndexException.unknownRecordType,
                               rec.getClass().getName()));
    }

    try {
      /* If it's not recurring or an override index it */

      EventInfo ei = (EventInfo)rec;
      BwEvent ev = ei.getEvent();

      if (!ev.getRecurring() || (ev.getRecurrenceId() != null)) {
        makeDoc(builder,
                rec,
                ev.getDtstart(),
                ev.getDtend(),
                ev.getRecurrenceId());
        return;
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
        return;
        //throw new CalFacadeException(CalFacadeException.noRecurrenceInstances);
      }

      String stzid = ev.getDtstart().getTzid();

      int instanceCt = maxInstances;

      boolean dateOnly = ev.getDtstart().getDateType();

      /* First build a table of overrides so we can skip these later
       */
      Map<String, String> overrides = new HashMap<String, String>();

      /*
      if (!Util.isEmpty(ei.getOverrideProxies())) {
        for (BwEvent ov: ei.getOverrideProxies()) {
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());
        }
      }
      */
      if (!Util.isEmpty(ei.getOverrides())) {
        for (EventInfo oei: ei.getOverrides()) {
          BwEvent ov = oei.getEvent();
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());
          makeDoc(builder,
                  oei,
                  ov.getDtstart(),
                  ov.getDtend(),
                  ov.getRecurrenceId());

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

        makeDoc(builder,
                rec,
                rstart,
                rend,
                recurrenceId);

        instanceCt--;
        if (instanceCt == 0) {
          // That's all you're getting from me
          break;
        }
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static class TypeId {
    String type;
    String id;

    TypeId(String type,
            String id) {
      this.type = type;
      this.id = id;
    }
  }

  private List<TypeId> makeKeys(final Object rec) throws CalFacadeException {
    try {
      List<TypeId> res = new ArrayList<>();

      if (rec instanceof BwCalendar) {
        BwCalendar col = (BwCalendar)rec;

        res.add(new TypeId(docTypeCollection, makeKeyVal(col)));

        return res;
      }

      if (rec instanceof BwCategory) {
        BwCategory cat = (BwCategory)rec;

        res.add(new TypeId(docTypeCategory,
                           makeKeyVal(makeKeyVal(cat))));

        return res;
      }

      if (rec instanceof BwIndexKey) {
        BwIndexKey ik = (BwIndexKey)rec;

        res.add(new TypeId(docTypeEvent, makeKeyVal(ik.getKey())));

        return res;
      }

      if (!(rec instanceof EventInfo)) {
        throw new CalFacadeException(new IndexException(IndexException.unknownRecordType,
                                                        rec.getClass().getName()));
      }

      /* If it's not recurring or an override delete it */

      EventInfo ei = (EventInfo)rec;
      BwEvent ev = ei.getEvent();

      if (!ev.getRecurring() || (ev.getRecurrenceId() != null)) {
        res.add(new TypeId(docTypeEvent,
                           makeKeyVal(keyConverter.makeEventKey(ev.getColPath(),
                                                     ev.getUid(),
                                                     ev.getRecurrenceId()))));

        return res;
      }

      /* Delete any possible non-recurring version */

      res.add(new TypeId(docTypeEvent,
                         makeKeyVal(keyConverter.makeEventKey(ev.getColPath(),
                                                   ev.getUid(),
                                                   null))));

      /* Delete all instances. */

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
        return res;
        //throw new CalFacadeException(CalFacadeException.noRecurrenceInstances);
      }

      String stzid = ev.getDtstart().getTzid();

      int instanceCt = maxInstances;

      boolean dateOnly = ev.getDtstart().getDateType();

      for (Period p: rp.instances) {
        String dtval = p.getStart().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        String recurrenceId = rstart.getDate();

        dtval = p.getEnd().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        res.add(new TypeId(docTypeEvent,
                           makeKeyVal(keyConverter.makeEventKey(ev.getColPath(),
                                                     ev.getUid(),
                                                     recurrenceId))));

        instanceCt--;
        if (instanceCt == 0) {
          // That's all you're getting from me
          break;
        }
      }

      return res;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static Map<String, String> interestingXprops = new HashMap<String, String>();

  static {
    interestingXprops.put(BwXproperty.bedeworkImage, "image_url");
    interestingXprops.put(BwXproperty.bedeworkThumbImage, "thumb_image_url");
    interestingXprops.put(BwXproperty.bedeworkAlias, "topical_area");

    interestingXprops.put(BwXproperty.bedeworkEventRegMaxTickets, "eventreg_max_tickets");
    interestingXprops.put(BwXproperty.bedeworkEventRegMaxTicketsPerUser, "eventreg_max_tickets_per_user");
    interestingXprops.put(BwXproperty.bedeworkEventRegStart, "eventreg_start");
    interestingXprops.put(BwXproperty.bedeworkEventRegEnd, "eventreg_end");
  }

  private void makeDoc(final XContentBuilder builder,
                       final BwCategory cat) throws CalFacadeException {
    try {
      //makeField(builder, "key", makeKeyVal(cat));
      makeField(builder, "itemType", itemTypeCategory);
      makeField(builder, "uid", cat.getUid());
      makeField(builder, "creator", cat.getCreatorHref());
      makeField(builder, "owner", cat.getOwnerHref());

      /* Manufacture a name and path for the time being - it's a required field */
      makeField(builder, "name", cat.getWord().getValue());
      makeField(builder, "path", Util.buildPath(false,
                                            cat.getOwnerHref(), "/",
                                            cat.getWord().getValue()));

      makeField(builder, "category", cat.getWord());
      makeField(builder, "description", cat.getDescription());

      batchCurSize++;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void makeDoc(final XContentBuilder builder,
                       final Object rec,
                       final BwDateTime start,
                       final BwDateTime end,
                       final String recurid) throws CalFacadeException {
    try {
      BwCalendar col = null;
      EventInfo ei = null;
      BwEvent ev = null;

      String colPath = null;
      Collection <BwCategory> cats = null;

      String key = null;
      String name = null;
      String created = null;
      String creator = null;
      String description = null;
      String lastmod = null;
      String owner = null;
      String summary = null;
      String itemType;
      String acl;

      if (rec instanceof BwCalendar) {
        col = (BwCalendar)rec;

        key = makeKeyVal(col);
        itemType = itemTypeCalendar;

        name = col.getName();
        colPath = col.getColPath();
        cats = col.getCategories();
        created = col.getCreated();
        creator = col.getCreatorHref();
        description = col.getDescription();
        lastmod = col.getLastmod().getTimestamp();
        owner = col.getOwnerHref();
        summary = col.getSummary();
        acl = col.getAccess();
      } else if (rec instanceof EventInfo) {
        ei = (EventInfo)rec;
        ev = ei.getEvent();

        name = ev.getName();

        key = keyConverter.makeEventKey(ev.getColPath(),
                                        ev.getUid(),
                                        recurid);

        itemType = itemTypeEvent;

        /*
        if (ev instanceof BwEventProxy) {
          // Index with the master key
          rec.addField(BwIndexLuceneDefs.keyEventMaster,
                       makeKeyVal(((BwEventProxy)ev).getTarget()));
        } else {
          rec.addField(BwIndexLuceneDefs.keyEventMaster,
                       makeKeyVal(ev));
        }
        */

        colPath = ev.getColPath();
        cats = ev.getCategories();
        created = ev.getCreated();
        creator = ev.getCreatorHref();
        description = ev.getDescription();
        lastmod = ev.getLastmod();
        owner = ev.getOwnerHref();
        summary = ev.getSummary();
        acl = ev.getAccess();

        if (start == null) {
          warn("No start for " + ev);
          return;
        }

        if (end == null) {
          warn("No end for " + ev);
          return;
        }
      } else {
        throw new IndexException(IndexException.unknownRecordType,
                                 rec.getClass().getName());
      }

      /* Start doc and do common collection/event fields */

      makeField(builder, "key", key);
      makeField(builder, "itemType", itemType);

      if (colPath == null) {
        colPath = "";
      }

      makeField(builder, "name", name);
      makeField(builder, "path", colPath);

      indexCategories(builder, cats);

      makeField(builder, "created", created);
      makeField(builder, "last_modified", lastmod);
      makeField(builder, "creator", creator);
      makeField(builder, "owner", owner);
      makeField(builder, "summary", summary);
      makeField(builder, "description", description);
      makeField(builder, "acl", acl);

      if (col != null) {
        // Doing collection - we're done

        return;
      }

      /* comment */
      /* contact */
      /* location - lat/long */
      /* resources */

      indexDate(builder, "start_", start);
      indexDate(builder, "end_", end);

      makeField(builder, "start_present", String.valueOf(ev.getNoStart()));
      makeField(builder, "end_type", String.valueOf(ev.getEndType()));

      makeField(builder, "duration", ev.getDuration());
      makeField(builder, "uid", ev.getUid());
      makeField(builder, "status", ev.getStatus());

      if (recurid != null) {
        makeField(builder, "recurrenceid", recurid);
      }

      makeField(builder, "eventType", IcalDefs.entityTypeNames[ev.getEntityType()]);

      BwLocation loc = ev.getLocation();
      if (loc != null) {
        makeField(builder, "location_uid", loc.getUid());

        String s = null;

        if (loc.getAddress() != null) {
          s = loc.getAddress().getValue();
        }

        if (loc.getSubaddress() != null) {
          if (s == null) {
            s = loc.getSubaddress().getValue();
          } else {
            s = s + " " + loc.getSubaddress().getValue();
          }
        }

        if (s != null) {
          makeField(builder, "location_str", s);
        }
      }

      if (ev.getXproperties() != null) {
        for (BwXproperty xp: ev.getXproperties()) {
          String solrname = interestingXprops.get(xp.getName());

          if (solrname != null) {
            String pars = xp.getPars();
            if (pars == null) {
              pars = "";
            }

            makeField(builder, solrname, pars + "\t" + xp.getValue());
          }
        }
      }

      batchCurSize++;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void indexDate(final XContentBuilder builder,
                         final String prefix,
                         final BwDateTime dt) throws CalFacadeException {
    makeField(builder, prefix + "utc", dt.getDate());
    makeField(builder, prefix + "local", dt.getDtval());
    makeField(builder, prefix + "tzid", dt.getTzid());
    makeField(builder, prefix + "floating", String.valueOf(dt.getFloating()));
  }

  private void makeId(final XContentBuilder builder,
                      final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      builder.field("_id", val);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final XContentBuilder builder,
                         final String name,
                         final BwString val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      // XXX Need to handle languages.
      builder.field(name, val.getValue());
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final XContentBuilder builder,
                         final String name,
                         final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      builder.field(name, val);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexCategories(final XContentBuilder builder,
                               final Collection <BwCategory> cats) throws CalFacadeException {
    if (cats == null) {
      return;
    }

    for (BwCategory cat: cats) {
      makeField(builder, "category", cat.getWord().getValue());
      makeField(builder, "category_uid", cat.getUid());
    }
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

  private String getType(Object rec) throws CalFacadeException {
    if (rec instanceof BwCategory) {
      return docTypeCategory;
    }

    if (rec instanceof BwCalendar) {
      return docTypeCollection;
    }

    if (rec instanceof EventInfo) {
      return docTypeEvent;
    }

    throw new CalFacadeException("Unhandled type " + rec.getClass());
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
