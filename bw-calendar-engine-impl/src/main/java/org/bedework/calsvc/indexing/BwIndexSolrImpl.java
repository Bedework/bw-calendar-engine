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
import edu.rpi.sss.util.xml.XmlEmit;

import net.fortuna.ical4j.model.Period;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

import static org.bedework.calsvc.indexing.BwIndexLuceneDefs.itemTypeCalendar;
import static org.bedework.calsvc.indexing.BwIndexLuceneDefs.itemTypeCategory;
import static org.bedework.calsvc.indexing.BwIndexLuceneDefs.itemTypeEvent;

/**
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class BwIndexSolrImpl implements BwIndexer {
  private transient Logger log;

  private boolean debug;

  private BwIndexKey keyConverter = new BwIndexKey();

  private String curQuery;
  private SearchLimits curLimits;

  private StringWriter xmlWtr = null;
  private int batchMaxSize = 0;
  private int batchCurSize = 0;
  private XmlEmit batch;

  private Object batchLock = new Object();

  private static final QName solrTagAdd = new QName(null, "add");
  private static final QName solrTagDelete = new QName(null, "delete");
  private static final QName solrTagDoc = new QName(null, "doc");
  private static final QName solrTagField = new QName(null, "field");
  private static final QName solrTagId = new QName(null, "id");
  //private static final QName solrTagCommit = new QName(null, "commit");
//  private static final QName solrTagOptimize = new QName(null, "optimize");

  private String solrURL;

  private boolean publick;
  private String principal;

  private int maxYears;
  private int maxInstances;

  private String targetIndex;
  private String coreAdminPath;
  private boolean writeable;

  /* Used to batch index */

  /** Constructor
   *
   * @param publick - if false we add an owner term to the searches
   * @param principal - who we are searching for
   * @param solrURL - Path for the index files - must exist
   * @param writeable - true for an updatable index
   * @param maxYears - max years for recurrences
   * @param maxInstances - max instances for recurrences
   * @param indexName - null for default
   * @param coreAdminPath  - path for administration of cores
   * @throws IndexException
   */
  public BwIndexSolrImpl(final boolean publick,
                         final String principal,
                         final String solrURL,
                         final boolean writeable,
                         final int maxYears,
                         final int maxInstances,
                         final String indexName,
                         final String coreAdminPath) throws IndexException {
    debug = getLog().isDebugEnabled();

    this.publick = publick;
    this.principal = principal;
    this.solrURL = solrURL;
    this.writeable = writeable;
    this.maxYears = maxYears;
    this.maxInstances = maxInstances;

    if (indexName == null) {
      targetIndex = "";
    } else {
      targetIndex = indexName + "/";
    }

    if (coreAdminPath != null) {
      this.coreAdminPath = coreAdminPath;
      if (coreAdminPath.endsWith("/")) {
        // Not valid
        this.coreAdminPath = coreAdminPath.substring(0, coreAdminPath.length() - 1);
      }
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#setBatchSize(int)
   */
  @Override
  public void setBatchSize(final int val) {
    batchMaxSize = val;
    batchCurSize = 0;
    if (batchMaxSize > 1) {
      batch = new XmlEmit();
    }
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

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#getKeys(int, edu.rpi.cct.misc.indexing.Index.Key[])
   */
  @Override
  public int getKeys(final int n, final Index.Key[] keys) throws CalFacadeException {
    String from = null;
    String to = null;

    if (curLimits !=null) {
      from = curLimits.fromDate;
      to = curLimits.toDate;
    }

    SolrDocumentList sdl = search(curQuery, from,
                                  to,
                                  n, keys.length);

    if (sdl == null) {
      return 0;
    }

    int num = sdl.size();
    if (keys.length < num) {
      // Bad result?
      num = keys.length;
    }

    for (int i = 0; i < num; i++) {
      SolrDocument sd = sdl.get(i);

      if (sd == null) {
        // Shouldn't happen?
        continue;
      }

      keys[i] = makeKey(keys[i], sd);
    }

    return num;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#indexEntity(java.lang.Object)
   */
  @Override
  public void indexEntity(final Object rec) throws CalFacadeException {
    try {
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

      // Unbatched

      xml = new XmlEmit();
      xmlWtr = new StringWriter();
      xml.startEmit(xmlWtr);

      /* Delete it first - we have issues with null fields. Also watch for
       * a non-recurring event being converted to a recurring event */
      xml.openTag(solrTagDelete);

      unindex(xml, rec);

      xml.closeTag(solrTagDelete);

      indexAndCommit(xmlWtr.toString());

      xml = new XmlEmit();
      xmlWtr = new StringWriter();
      xml.startEmit(xmlWtr);
      xml.openTag(solrTagAdd);

      index(xml, rec);

      xml.closeTag(solrTagAdd);

      indexAndCommit(xmlWtr.toString());
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#unindexEntity(java.lang.Object)
   */
  @Override
  public void unindexEntity(final Object rec) throws CalFacadeException {
    try {
      // Always unbatched

      XmlEmit xml = new XmlEmit();
      xmlWtr = new StringWriter();
      xml.startEmit(xmlWtr);

      xml.openTag(solrTagDelete);

      unindex(xml, rec);

      xml.closeTag(solrTagDelete);

      indexAndCommit(xmlWtr.toString());
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#search(java.lang.String, edu.rpi.cct.misc.indexing.SearchLimits)
   */
  @Override
  public int search(final String query,
                    final SearchLimits limits) throws CalFacadeException {
    if (publick) {
      curQuery = query;
    } else {
      curQuery = "{!term f=owner}\"" + principal + "\" AND (" + query + ")";
    }

    curLimits = limits;

    String from = null;
    String to = null;

    if (curLimits !=null) {
      from = curLimits.fromDate;
      to = curLimits.toDate;
    }

    SolrDocumentList sdl = search(curQuery, from, to, 0, 0);

    if (sdl == null) {
      return 0;
    }

    return (int)sdl.getNumFound();
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
    /* See http://wiki.apache.org/solr/CoreAdmin#CREATE
     * for a description of this.
     *
     * We'll create a data directory with the same name as the new core.
     */

    String newName = name + "-" + DateTimeUtil.isoDateTime();
    String encName;
    try {
      encName = URLEncoder.encode(newName,
                                  HTTP.DEFAULT_CONTENT_CHARSET);
    } catch (UnsupportedEncodingException Uee) {
      throw new CalFacadeException(Uee);
    }


    StringBuilder sb = new StringBuilder("?action=CREATE&name=");
    sb.append(encName);
    sb.append("&instanceDir=");
    sb.append(name);
    sb.append("/&dataDir=");
    sb.append(encName);
    sb.append("/data");
//    sb.append("/&config=../");
//    sb.append(name);
//    sb.append("/conf/solrconfig.xml");
//    sb.append("&schema=../");
//    sb.append(name);
//    sb.append("/conf/schema.xml");

//    loadOnStartup="true"
//    transient="false"

    try {
      InputStream str = getServer().callForStream(sb.toString(),
                                                  true); // coreAdmin

      if (str == null) {
        return null;
      }

      XMLResponseParser parser = new XMLResponseParser();

      NamedList<Object> resp = parser.processResponse(new InputStreamReader(str));

      SimpleOrderedMap sol = (SimpleOrderedMap)resp.get("responseHeader");

      int status = (Integer)sol.get("status");
      if (debug) {

      }

      return newName;
    } finally {
      getServer().close();
    }
  }

  @Override
  public List<String> listIndexes() throws CalFacadeException {
    try {
      InputStream str = getServer().callForStream("?action=STATUS",
                                                  true); // coreAdmin

      if (str == null) {
        return null;
      }

      XMLResponseParser parser = new XMLResponseParser();

      NamedList<Object> resp = parser.processResponse(new InputStreamReader(str));

      @SuppressWarnings("unchecked")
      NamedList<Object> st = (NamedList<Object>)resp.get("status");

      List<String> res = new ArrayList<String>();

      for (Map.Entry<String, Object> entry: st) {
        res.add(entry.getKey());
      }

      if (debug) {

      }

      return res;
    } finally {
      getServer().close();
    }
  }

  @Override
  public List<String> purgeIndexes(final List<String> preserve) throws CalFacadeException {
    try {
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
    } finally {
      getServer().close();
    }
  }

  private void unloadIndex(final String name) throws CalFacadeException {
//    try {
      /*InputStream str =*/ getServer().callForStream("?action=UNLOAD&core=" +
          name + "&deleteIndex=true",
          true); // coreAdmin

      /*
      if (str == null) {
        return null;
      }

      XMLResponseParser parser = new XMLResponseParser();

      NamedList<Object> resp = parser.processResponse(new InputStreamReader(str));

      @SuppressWarnings("unchecked")
      NamedList<Object> st = (NamedList<Object>)resp.get("status");

      List<String> res = new ArrayList<String>();

      for (Map.Entry<String, Object> entry: st) {
        res.add(entry.getKey());
      }

      if (debug) {

      }

      return res;
    } finally {
      getServer().close();
    }*/
  }

  @Override
  public int swapIndex(final String index,
                       final String other) throws CalFacadeException {
    /* See http://wiki.apache.org/solr/CoreAdmin#SWAP
     * for a description of this.
     */

    StringBuilder sb = new StringBuilder("?action=SWAP&core=");
    sb.append(index);
    sb.append("&other=");
    sb.append(other);

    try {
      InputStream str = getServer().callForStream(sb.toString(),
                                                  true); // coreAdmin
      if (str != null) {
        return getServer().status;
      }

      XMLResponseParser parser = new XMLResponseParser();

      NamedList<Object> resp = parser.processResponse(new InputStreamReader(str));

      SolrDocumentList sdl = (SolrDocumentList)resp.get("response");

      if (debug) {

      }

      return 0;
    } finally {
      getServer().close();
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
    StringBuilder query = new StringBuilder();

    if (!publick) {
      query.append("{!term f=owner}\"");
      query.append(principal);
      query.append("\" AND ");
    }

    query.append("itemType:");
    query.append(itemTypeCategory);

    query.append(" AND ");
    query.append(field);
    query.append(":\"");
    query.append(val);
    query.append("\"");

    List<BwCategory> res = new ArrayList<>();

    SolrDocumentList sdl = search(query.toString(),
                                  null,
                                  null,
                                  0,
                                  2);
    if (Util.isEmpty(sdl)) {
      return null;
    }

    if (sdl.size() > 1) {
      error("Multiple categories with field " + field +
            " value " + val);
      return null;
    }

    return makeCat(sdl.get(0));
  }

  @Override
  public List<BwCategory> fetchAllCats() throws CalFacadeException {
    StringBuilder query = new StringBuilder();

    if (!publick) {
      query.append("{!term f=owner}\"");
      query.append(principal);
      query.append("\" AND ");
    }

    query.append("itemType:");
    query.append(itemTypeCategory);

    int tries = 0;
    int ourPos = 0;
    int ourCount = maxFetchCount;

    List<BwCategory> res = new ArrayList<>();

    for (;;) {
      if (tries > absoluteMaxTries) {
        // huge count or we screwed up
        warn("Solr indexer: too many tries");
        break;
      }

      SolrDocumentList sdl = search(query.toString(),
                                    null,
                                    null,
                                    ourPos,
                                    ourCount);
      if (Util.isEmpty(sdl)) {
        break;
      }

      for (SolrDocument sd: sdl) {
        BwCategory cat = makeCat(sd);
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
                              final Holder<Integer> found,
                              final int pos,
                              final int count,
                              final AccessChecker accessCheck) throws CalFacadeException {
    int ourPos = pos;
    int ourCount = count;

    if ((ourCount < 0) | (ourCount > maxFetchCount)) {
      ourCount = maxFetchCount;
    }

    int fetched = 0;
    int tries = 0;
    Set<EventInfo> res = new ConcurrentSkipListSet<>();

    StringBuilder query = new StringBuilder("itemType:");
    query.append(itemTypeEvent);

    if (filter != null) {
      StringBuilder sb = new StringBuilder();

      makeQuery(sb, filter);

      if (sb.length() > 0) {
        query.append(" AND (");
        query.append(sb);
        query.append(")");
      }
    }

    if (!publick) {
      query.insert(0, "{!term f=owner}\"" + principal + "\" AND (");
      query.append(")");
    }

    int toFetch = count;

    while ((toFetch < 0) || (fetched < toFetch)) {
      if (tries > absoluteMaxTries) {
        // huge count or we screwed up
        warn("Solr indexer: too many tries");
        break;
      }

      SolrDocumentList sdl = search(query.toString(),
                                    start,
                                    end,
                                    ourPos,
                                    ourCount);
      if (sdl == null) {
        if (found != null) {
          found.value = 0;
        }

        break;
      }

      if (toFetch < 0) {
        toFetch = (int)sdl.getNumFound();
      }

      if (found != null) {
        found.value = toFetch;
      }

      if (Util.isEmpty(sdl)) {
        break;
      }

      for (SolrDocument sd: sdl) {
        EventInfo ei = makeEvent(sd);
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

  private void makeQuery(final StringBuilder sb,
                         final FilterBase f) throws CalFacadeException {
    if ((f instanceof AndFilter) ||
        (f instanceof OrFilter)) {
      sb.append("(");
      boolean first = true;
      boolean ands = f instanceof AndFilter;

      for (FilterBase flt: f.getChildren()) {
        if (!first) {
          if (ands) {
            sb.append(" AND ");
          } else {
            sb.append(" OR ");
          }
        }

        first = false;
        makeQuery(sb, flt);
      }

      sb.append(")");

      return;
    }

    if (!(f instanceof PropertyFilter)) {
      return;
    }

    if (f instanceof PresenceFilter) {
      return;
    }

    if (f instanceof BwCreatorFilter) {
      String cre = ((BwCreatorFilter)f).getEntity();

      sb.append("creator:");
      sb.append(cre);

      return;
    }

    if (f instanceof BwCategoryFilter) {
      BwCategory cat = ((BwCategoryFilter)f).getEntity();

      sb.append("category_uid:");
      sb.append(cat.getUid());

      return;
    }

    if (f instanceof BwCollectionFilter) {
      BwCalendar col = ((BwCollectionFilter)f).getEntity();

      sb.append("path:");
      sb.append(col.getPath());

      return;
    }

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

  private BwCategory makeCat(final SolrDocument sd) throws CalFacadeException {
    String itemType = getString(sd, "itemType");

    if ((itemType == null) ||
            !itemType.equals(itemTypeCategory)) {
      return null;
    }

    BwCategory cat = new BwCategory();

    cat.setWord(new BwString(null,
                             (String)sd.getFirstValue("category")));
    cat.setDescription(new BwString(null,
                                    (String) sd.getFirstValue(
                                            "description")));
    cat.setCreatorHref(getString(sd, "creator"));
    cat.setOwnerHref(getString(sd, "owner"));
    cat.setUid(getString(sd, "uid"));

    return cat;
  }

  private EventInfo makeEvent(final SolrDocument sd) throws CalFacadeException {
    BwEvent ev = new BwEventObj();
    EventInfo ei = new  EventInfo(ev);

    /*
    Float score = (Float)sd.getFirstValue("score");

    if (score != null) {
      bwkey.setScore(score);
    }
    */

    String itemType = getString(sd, "itemType");

    if ((itemType == null) ||
        !itemType.equals(itemTypeEvent)) {
      return null;
    }

    ev.setName((String)sd.getFirstValue("name"));
    ev.setColPath((String)sd.getFirstValue("path"));

    Collection<Object> vals = sd.getFieldValues("category_uid");
    if (vals != null) {
      Set<String> catUids = new TreeSet<String>();

      for (Object o: vals) {
        catUids.add((String)o);
      }

      ev.setCategoryUids(catUids);
    }

    ev.setCreated(fromSolrDate(sd, "created"));

    ev.setLastmod(fromSolrDate(sd, "last_modified"));
    ev.setDtstamp(ev.getLastmod());
    ev.setCreatorHref(getString(sd, "creator"));
    ev.setOwnerHref(getString(sd, "owner"));
    ev.setAccess(getString(sd, "acl"));
    ev.setSummary(getString(sd, "summary"));
    ev.setDescription(getString(sd, "description"));

    /* comment */
    /* contact */
    /* location - lat/long */
    /* resources */

    ev.setDtstart(unindexDate(sd, "start_"));
    ev.setDtend(unindexDate(sd, "end_"));

    ev.setDuration(getString(sd, "duration"));
    ev.setNoStart((Boolean)sd.getFirstValue("start_present"));
    ev.setEndType(getString(sd, "end_type").charAt(0));

    ev.setUid(getString(sd, "uid"));

    ev.setRecurrenceId(getString(sd, "recurrenceid"));

    ev.setEntityType(makeEntityType(getString(sd, "eventType")));

    ev.setStatus(getString(sd, "status"));

    ev.setLocationUid(getString(sd, "location_uid"));

    Set<String> xpnames = interestingXprops.keySet();

    if (!Util.isEmpty(xpnames)) {
      for (String xpname: xpnames) {
        @SuppressWarnings("unchecked")
        Collection<String> xvals = (Collection)sd.getFieldValues(interestingXprops.get(xpname));

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

  private String getString(final SolrDocument sd,
                           final String name) {
    return (String)sd.getFirstValue(name);
  }

  private BwDateTime unindexDate(final SolrDocument sd,
                                 final String prefix) throws CalFacadeException {
    String utc = fromSolrDate(sd, prefix + "utc");
    String local = getString(sd, prefix + "local");
    String tzid = getString(sd, prefix + "tzid");
    Boolean floating = (Boolean)sd.getFirstValue(prefix + "floating");

    boolean dateType = (local != null) && (local.length() == 8);

    return BwDateTime.makeBwDateTime(dateType, local, utc, tzid, floating);
  }

  private String fromSolrDate(final SolrDocument sd,
                              final String name) {
    Date dt = (Date)sd.getFirstValue(name);
    if (dt == null) {
      return null;
    }

    return DateTimeUtil.isoDateTimeUTC(dt);
  }

  private SolrDocumentList search(final String query,
                                  final String start,
                                  final String end,
                                  final int pos,
                                  final int count) throws CalFacadeException {
    StringBuilder sb = new StringBuilder();
    boolean needAnd = false;


    if (query != null) {
      sb.append(query);
      needAnd = true;
    }

    if (start != null) {
      // End of events must be on or after the start of the range
      if (needAnd) {
        sb.append(" AND ");
      }

      needAnd = true;

      sb.append(" end_utc:[");
      sb.append(toSolrDate(start));
      sb.append(" TO *]");
    }

    if (end != null) {
      // Start of events must be before the end of the range
      if (needAnd) {
        sb.append(" AND ");
      }

      needAnd = true;

      sb.append(" start_utc:[* TO ");
      sb.append(toSolrDate(end));
      sb.append("]");
    }

    try {
      InputStream str = getServer().query(sb.toString(), pos, count);
      if (str == null) {
        return null;
      }

      XMLResponseParser parser = new XMLResponseParser();

      NamedList<Object> resp = parser.processResponse(new InputStreamReader(str));

      return (SolrDocumentList)resp.get("response");
    } finally {
      getServer().close();
    }
  }

  /** Called to make or fill in a Key object.
   *
   * @param key   Possible Index.Key object for reuse
   * @param sd    The retrieved document
   * @return Index.Key  new or reused object
   * @throws CalFacadeException
   */
  private Index.Key makeKey(final Index.Key key,
                            final SolrDocument sd) throws CalFacadeException {
    BwIndexKey bwkey;

    if ((key == null) || (!(key instanceof BwIndexKey))) {
      bwkey = new BwIndexKey();
    } else {
      bwkey = (BwIndexKey)key;
    }

    Float score = (Float)sd.getFirstValue("score");

    if (score != null) {
      bwkey.setScore(score);
    }

    String itemType = (String)sd.getFirstValue("itemType");

    if (itemType == null) {
      throw new CalFacadeException("org.bedework.index.noitemtype");
    }

    bwkey.setItemType(itemType);

    String kval = (String)sd.getFirstValue("key");

    if (kval == null) {
      throw new CalFacadeException("org.bedework.index.noitemkey");
    }

    if (itemType.equals(itemTypeCalendar)) {
      bwkey.setKey1(kval);
    } else if (itemType.equals(itemTypeCategory)) {
      bwkey.setKey1(kval);
    } else if (itemType.equals(itemTypeEvent)) {
      try {
        bwkey.setEventKey(kval);
      } catch (IndexException ie) {
        throw new CalFacadeException(ie);
      }
    } else {
      throw new CalFacadeException(IndexException.unknownRecordType,
                               itemType);
    }

    return bwkey;
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

    if (rec instanceof BwEvent) {
      BwEvent ev = (BwEvent)rec;

      String path = ev.getColPath();
      String guid = ev.getUid();
      String recurid = ev.getRecurrenceId();

      return keyConverter.makeEventKey(path, guid, recurid);
    }

    throw new IndexException(IndexException.unknownRecordType,
                             rec.getClass().getName());
  }

  private void index(final XmlEmit xml,
                     final Object rec) throws CalFacadeException {
    if (rec instanceof BwCalendar) {
      makeDoc(xml, rec, null, null, null);
      return;
    }

    if (rec instanceof BwCategory) {
      makeDoc(xml, (BwCategory)rec);
      return;
    }

    if (!(rec instanceof EventInfo)) {
      throw new CalFacadeException(new IndexException(IndexException.unknownRecordType,
                               rec.getClass().getName()));
    }

    /* If it's not recurring or an override index it */

    EventInfo ei = (EventInfo)rec;
    BwEvent ev = ei.getEvent();

    if (!ev.getRecurring() || (ev.getRecurrenceId() != null)) {
      makeDoc(xml,
              rec,
              ev.getDtstart(),
              ev.getDtend(),
              ev.getRecurrenceId());
      return;
    }

    /* Emit all instances that aren't overridden. */

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
        makeDoc(xml,
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

      makeDoc(xml,
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
  }

  private void unindex(final XmlEmit xml, final Object rec) throws CalFacadeException {
    try {
      if (rec instanceof BwCalendar) {
        BwCalendar col = (BwCalendar)rec;

        makeId(xml, makeKeyVal(col));

        return;
      }

      if (rec instanceof BwCategory) {
        BwCategory cat = (BwCategory)rec;

        makeId(xml, makeKeyVal(cat));

        return;
      }

      if (rec instanceof BwIndexKey) {
        BwIndexKey ik = (BwIndexKey)rec;

        makeId(xml, ik.getKey());

        return;
      }

      if (!(rec instanceof EventInfo)) {
        throw new CalFacadeException(new IndexException(IndexException.unknownRecordType,
                                                        rec.getClass().getName()));
      }

      /* If it's not recurring or an override delete it */

      EventInfo ei = (EventInfo)rec;
      BwEvent ev = ei.getEvent();

      if (!ev.getRecurring() || (ev.getRecurrenceId() != null)) {
        String key = keyConverter.makeEventKey(ev.getColPath(),
                                               ev.getUid(),
                                               ev.getRecurrenceId());

        makeId(xml, key);

        return;
      }

      /* Delete any possible non-recurring version */

      String key = keyConverter.makeEventKey(ev.getColPath(),
                                             ev.getUid(),
                                             null);

      makeId(xml, key);

      /* Delete all instances. */

      RecurPeriods rp = RecurUtil.getPeriods(ev, maxYears, maxInstances);

      if (rp.instances.isEmpty()) {
        // No instances for an alleged recurring event.
        return;
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

        key = keyConverter.makeEventKey(ev.getColPath(),
                                               ev.getUid(),
                                               recurrenceId);

        makeId(xml, key);

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

  private void makeDoc(final XmlEmit xml,
                       final BwCategory cat) throws CalFacadeException {
    try {
      xml.openTag(solrTagDoc);

      makeField(xml, "key", makeKeyVal(cat));
      makeField(xml, "itemType", itemTypeCategory);
      makeField(xml, "uid", cat.getUid());
      makeField(xml, "creator", cat.getCreatorHref());
      makeField(xml, "owner", cat.getOwnerHref());

      /* Manufacture a name and path for the time being - it's a required field */
      makeField(xml, "name", cat.getWord().getValue());
      makeField(xml, "path", Util.buildPath(false,
                                            cat.getOwnerHref(), "/",
                                            cat.getWord().getValue()));

      makeField(xml, "category", cat.getWord());
      makeField(xml, "description", cat.getDescription());

      xml.closeTag(solrTagDoc);

      batchCurSize++;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void makeDoc(final XmlEmit xml,
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

      xml.openTag(solrTagDoc);

      makeField(xml, "key", key);
      makeField(xml, "itemType", itemType);

      if (colPath == null) {
        colPath = "";
      }

      makeField(xml, "name", name);
      makeField(xml, "path", colPath);

      indexCategories(xml, cats);

      makeField(xml, "created", toSolrDate(created));
      makeField(xml, "last_modified", toSolrDate(lastmod));
      makeField(xml, "creator", creator);
      makeField(xml, "owner", owner);
      makeField(xml, "summary", summary);
      makeField(xml, "description", description);
      makeField(xml, "acl", acl);

      if (col != null) {
        // Doing collection - we're done
        xml.closeTag(solrTagDoc);
        return;
      }

      /* comment */
      /* contact */
      /* location - lat/long */
      /* resources */

      indexDate(xml, "start_", start);
      indexDate(xml, "end_", end);

      makeField(xml, "start_present", String.valueOf(ev.getNoStart()));
      makeField(xml, "end_type", String.valueOf(ev.getEndType()));

      makeField(xml, "duration", ev.getDuration());
      makeField(xml, "uid", ev.getUid());
      makeField(xml, "status", ev.getStatus());

      if (recurid != null) {
        makeField(xml, "recurrenceid", recurid);
      }

      makeField(xml, "eventType", IcalDefs.entityTypeNames[ev.getEntityType()]);

      BwLocation loc = ev.getLocation();
      if (loc != null) {
        makeField(xml, "location_uid", loc.getUid());

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
          makeField(xml, "location_str", s);
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

            makeField(xml, solrname, pars + "\t" + xp.getValue());
          }
        }
      }

      xml.closeTag(solrTagDoc);

      batchCurSize++;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void indexDate(final XmlEmit xml,
                         final String prefix,
                         final BwDateTime dt) throws CalFacadeException {
    makeField(xml, prefix + "utc", toSolrDate(dt.getDate()));
    makeField(xml, prefix + "local", dt.getDtval());
    makeField(xml, prefix + "tzid", dt.getTzid());
    makeField(xml, prefix + "floating", String.valueOf(dt.getFloating()));
  }

  private String toSolrDate(final String val) {
    if (val == null) {
      return null;
    }

    // Make into form 1995-12-31T23:59:59Z
    // from 19951231T235959Z
    //      0   4 6    1 3
    StringBuilder sb = new StringBuilder();

    sb.append(val.substring(0, 4));
    sb.append("-");
    sb.append(val.substring(4, 6));
    sb.append("-");

    if (val.length() == 8) {
      sb.append(val.substring(6));
      sb.append("T00:00:00.00Z");
    } else {
      sb.append(val.substring(6, 11));
      sb.append(":");
      sb.append(val.substring(11, 13));
      sb.append(":");
      sb.append(val.substring(13, 15));
      sb.append(".000Z");
    }

    return sb.toString();
  }

  private void makeId(final XmlEmit xml,
                      final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      xml.openTagNoNewline(solrTagId);
      xml.value(val);
      xml.closeTagNoblanks(solrTagId);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final XmlEmit xml,
                         final String name,
                         final BwString val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      // XXX Need to handle languages.
      xml.openTagNoNewline(solrTagField, "name", name);
      xml.value(val.getValue());
      xml.closeTagNoblanks(solrTagField);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final XmlEmit xml,
                         final String name,
                         final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      xml.openTagNoNewline(solrTagField, "name", name);
      xml.value(val);
      xml.closeTagNoblanks(solrTagField);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexCategories(final XmlEmit xml,
                               final Collection <BwCategory> cats) throws CalFacadeException {
    if (cats == null) {
      return;
    }

    for (BwCategory cat: cats) {
      makeField(xml, "category", cat.getWord().getValue());
      makeField(xml, "category_uid", cat.getUid());
    }
  }

  SolrServer server;

  private int indexAndCommit(final String indexInfo) throws CalFacadeException {
    try {
      return getServer().postUpdate(indexInfo);
    } finally {
      getServer().close();
    }
  }

  private SolrServer getServer() {
    if (server == null) {
      server = new SolrServer(solrURL, targetIndex, coreAdminPath, getLog());
    }

    return server;
  }

  /** CLass to allow us to call the server
   */
  private static class SolrServer {
    private transient Logger log;

    private boolean debug;

    private String serverUri;
    private String targetIndex;
    private String coreAdminPath;

    //private static JAXBContext jc;

    private HttpPost poster;
    private HttpGet getter;
    int status;
    HttpResponse response;

    SolrServer(final String uri,
               final String targetIndex,
               final String coreAdminPath,
               final Logger log) {
      serverUri = slashIt(uri);
      this.targetIndex = slashIt(targetIndex);
      this.coreAdminPath = coreAdminPath; // No slash
      this.log = log;

      debug = log.isDebugEnabled();
    }

    public int postUpdate(final String xmlUpdate) throws CalFacadeException {
      try {
        return doPost(xmlUpdate, "update", "application/xml");
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    public InputStream query(final String query,
                             final int from,
                             final int count) throws CalFacadeException {
      try {
        /* Note we do these queries with POST - GET is subject to header length
         * limitations which can be exceeded.
         */
        StringBuilder sb = new StringBuilder("q=");

        sb.append(URLEncoder.encode(query,
                                    HTTP.DEFAULT_CONTENT_CHARSET));

        sb.append("&start=");
        sb.append(from);

        sb.append("&rows=");
        sb.append(count);

        sb.append("&fl=*+score");

        doPost(sb.toString(), "select", "application/x-www-form-urlencoded");

        if (status != HttpServletResponse.SC_OK) {
          return null;
        }

        HttpEntity ent = response.getEntity();

        return ent.getContent();
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    public InputStream callForStream(final String req,
                                     final boolean coreAdmin) throws CalFacadeException {
      try {
        doCall(req, coreAdmin, null);

        if (status != HttpServletResponse.SC_OK) {
          return null;
        }

        HttpEntity ent = response.getEntity();

        return ent.getContent();
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    public void close() throws CalFacadeException {
      try {
        if (response == null) {
          return;
        }

        HttpEntity ent = response.getEntity();

        if (ent != null) {
          InputStream is = ent.getContent();
          is.close();
        }

        getter = null;
        response = null;
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    private int doPost(final String body,
                       final String path,
                       final String contentType) throws CalFacadeException {
      try {
        HttpClient client = new DefaultHttpClient();
        String fullPath = getUrl(false, path);

        //if (debug) {
        //  log.debug("Solr-post: path: " + fullPath + " body: " + body);
        //}

        poster = new HttpPost(fullPath);

        poster.setEntity(new StringEntity(body, contentType, "UTF-8"));

        response = client.execute(poster);
        status = response.getStatusLine().getStatusCode();

        return status;
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (UnknownHostException uhe) {
        throw new CalFacadeException(uhe);
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    private void doCall(final String req,
                        final boolean coreAdmin,
                        final String etag) throws CalFacadeException {
      try {
        HttpClient client = new DefaultHttpClient();

        getter = new HttpGet(getUrl(coreAdmin, req));

        if (etag != null) {
          getter.addHeader(new BasicHeader("If-None-Match", etag));
        }

        response = client.execute(getter);
        status = response.getStatusLine().getStatusCode();
      } catch (CalFacadeException cfe) {
        throw cfe;
     } catch (UnknownHostException uhe) {
        throw new CalFacadeException(uhe);
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    private String slashIt(final String s) {
      if (s.endsWith("/")) {
        return s;
      }

      if (s.length() == 0) {
        return s;
      }

      return s + "/";
    }

    private String getUrl(final boolean coreAdmin,
                          final String req) throws CalFacadeException {
      if (serverUri == null) {
        throw new CalFacadeException("No server URI defined");
      }

      StringBuilder sb = new StringBuilder(serverUri);

      if (coreAdmin) {
        sb.append(coreAdminPath);
      } else {
        sb.append(targetIndex);
      }

      sb.append(req);

      return sb.toString();
    }
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

  protected void warn(final String msg) {
    getLog().warn(msg);
  }

  protected void error(final String msg) {
    getLog().error(msg);
  }
}
