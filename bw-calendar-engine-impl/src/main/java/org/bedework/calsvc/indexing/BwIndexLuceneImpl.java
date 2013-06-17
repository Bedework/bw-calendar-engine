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

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;

import edu.rpi.cct.misc.indexing.Index;
import edu.rpi.cct.misc.indexing.IndexException;
import edu.rpi.cct.misc.indexing.IndexLuceneImpl;
import edu.rpi.cct.misc.indexing.SearchLimits;
import edu.rpi.sss.util.Util;

import net.fortuna.ical4j.model.Period;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.ChainedFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.RangeFilter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.ws.Holder;

/**
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class BwIndexLuceneImpl extends IndexLuceneImpl implements BwIndexer {
  private BwIndexKey keyConverter = new BwIndexKey();

  /* Used to batch index */
  private ArrayList<Object> batch;
  private int batchSize = 0;

  private int maxYears;
  private int maxInstances;

  /** Constructor
   *
   * @param sysfilePath - Path for the index files - must exist
   * @param writeable - true if the caller can update the index
   * @param maxYears
   * @param maxInstances
   * @throws IndexException
   */
  public BwIndexLuceneImpl(final String sysfilePath,
                           final boolean writeable,
                           final int maxYears,
                           final int maxInstances) throws IndexException {
    super(sysfilePath,
          BwIndexLuceneDefs.defaultFieldInfo.getName(),
          writeable);

    this.maxYears = maxYears;
    this.maxInstances = maxInstances;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#setBatchSize(int)
   */
  @Override
  public void setBatchSize(final int val) {
    batchSize = val;
    if (batchSize > 1) {
      batch = new ArrayList<Object>();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#endBwBatch()
   */
  @Override
  public void endBwBatch() throws CalFacadeException {
    try {
      endBatch();
    } catch (IndexException ie) {
      throw new CalFacadeException(ie);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#flush()
   */
  @Override
  public void flush() throws CalFacadeException {
    if ((batch == null) || (batch.size() == 0)) {
      return;
    }

    Object[] recs = batch.toArray();
    try {
      indexNewRecs(recs);
    } catch (IndexException ie) {
      if (ie.getMessage().equals(IndexException.noFiles)) {
        // Retry after create
        try {
          create();
          indexNewRecs(recs);
        } catch (Throwable t) {
          throw new CalFacadeException(t);
        }
      }
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    batch.clear();
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#getKeys(int, edu.rpi.cct.misc.indexing.Index.Key[])
   */
  @Override
  public int getKeys(final int n, final Index.Key[] keys) throws CalFacadeException {
    try {
      return retrieve(n, keys);
    } catch (IndexException ie) {
      throw new CalFacadeException(ie);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#indexEntity(java.lang.Object)
   */
  @Override
  public void indexEntity(final Object rec) throws CalFacadeException {
    try {
      if (batchSize > 1) {
        if (batch.size() == batchSize) {
          flush();
        }

        batch.add(makeRec(rec));
        return;
      }
      indexRec(rec);
    } catch (IndexException ie) {
      if (ie.getMessage().equals(IndexException.noFiles)) {
        // Retry after create
        try {
          create();
          indexRec(rec);
        } catch (Throwable t) {
          throw new CalFacadeException(t);
        }
      } else {
        throw new CalFacadeException(ie);
      }
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#unindexEntity(java.lang.Object)
   */
  @Override
  public void unindexEntity(final Object rec) throws CalFacadeException {
    try {
      unindexRec(rec);
    } catch (IndexException ie) {
      if (ie.getMessage().equals(IndexException.noFiles)) {
        // Ignore
      }
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvc.indexing.BwIndexer#search(java.lang.String, edu.rpi.cct.misc.indexing.SearchLimits)
   */
  @Override
  public int search(final String query, final SearchLimits limits) throws CalFacadeException {
    Filter filter = null;

    try {
      if (limits != null) {
        RangeFilter from = null;
        RangeFilter to = null;

        if (limits.fromDate != null) {
          from = RangeFilter.More(BwIndexLuceneDefs.startDate.getName(),
                                  limits.fromDate);
        }

        if (limits.toDate != null) {
          to = RangeFilter.Less(BwIndexLuceneDefs.startDate.getName(),
                                limits.toDate);
        }

        if ((from != null) && (to != null)) {
          filter = new ChainedFilter(new Filter[] {from, to}, ChainedFilter.AND);
        } else if (from != null) {
          filter = from;
        } else if (to != null) {
          filter = to;
        }
      }

      return search(query, filter);
    } catch (IndexException ie) {
      throw new CalFacadeException(ie);
    }
  }

  @Override
  public String newIndex(final String name) throws CalFacadeException {
    throw new RuntimeException("unimplemented");
  }

  @Override
  public List<String> listIndexes() throws CalFacadeException {
    return null;
  }

  @Override
  public List<String> purgeIndexes(final List<String> preserve) throws CalFacadeException {
    return null;
  }

  @Override
  public int swapIndex(final String index,
                       final String other) throws CalFacadeException {
    throw new RuntimeException("unimplemented");
  }

  @Override
  public boolean isFetchEnabled() throws CalFacadeException {
    return false;
  }

  @Override
  public Set<EventInfo> fetch(final FilterBase filter,
                              final String start,
                              final String end,
                              final Holder<Integer> found,
                              final int pos,
                              final int count) throws CalFacadeException {
    return null;
  }

  /** Called to make or fill in a Key object.
   *
   * @param key   Possible Index.Key object for reuse
   * @param doc   The retrieved Document
   * @param score The rating for this entry
   * @return Index.Key  new or reused object
   */
  @Override
  public Index.Key makeKey(Index.Key key,
                           final Document doc,
                           final float score) throws IndexException {
    if ((key == null) || (!(key instanceof BwIndexKey))) {
      key = new BwIndexKey();
    }

    BwIndexKey bwkey = (BwIndexKey)key;

    bwkey.setScore(score);

    String itemType = doc.get(BwIndexLuceneDefs.itemTypeInfo.getName());
    bwkey.setItemType(itemType);

    if (itemType == null) {
      throw new IndexException("org.bedework.index.noitemtype");
    }

    if (itemType.equals(BwIndexLuceneDefs.itemTypeCalendar)) {
      bwkey.setCalendarKey(doc.get(BwIndexLuceneDefs.keyCalendar.getName()));
    } else if (itemType.equals(BwIndexLuceneDefs.itemTypeEvent)) {
      bwkey.setEventKey(doc.get(BwIndexLuceneDefs.keyEvent.getName()));
    } else {
      throw new IndexException(IndexException.unknownRecordType,
                               itemType);
    }

    return key;
  }

  /** Called to make a key term for a record.
   *
   * @param   rec      The record
   * @return  Term     Lucene term which uniquely identifies the record
   */
  @Override
  public Term makeKeyTerm(final Object rec) throws IndexException {
    if (rec instanceof BwCalendar) {
      return new Term(makeKeyName(rec),
                      makeKeyVal((BwCalendar)rec));
    }

    if (rec instanceof EventInfo) {
      return new Term(makeKeyName(rec),
                      makeKeyVal(((EventInfo)rec).getEvent()));
    }

    if (rec instanceof BwIndexKey) {
      BwIndexKey ik = (BwIndexKey)rec;

      return new Term(ik.getItemType(),
                      ik.getKey());
    }

    throw new IndexException(IndexException.unknownRecordType,
                             rec.getClass().getName());
  }

  /** Called to make a key value for a record.
   *
   * @param   rec      The record
   * @return  String   String which uniquely identifies the record
   * @throws IndexException
   */
  public String makeKeyVal(final BwCalendar rec) throws IndexException {
    return rec.getPath();
  }

  /** Called to make a key value for a record.
   *
   * @param   ev      The record
   * @return  String   String which uniquely identifies the record
   * @throws IndexException
   */
  public String makeKeyVal(final BwEvent ev) throws IndexException {
    String path = ev.getColPath();
    String guid = ev.getUid();
    String recurid = ev.getRecurrenceId();

    return keyConverter.makeEventKey(path, guid, recurid);
  }

  /** Called to make the primary key name for a record.
   *
   * @param   rec      The record
   * @return  String   Name for the field/term
   * @throws IndexException
   */
  @Override
  public String makeKeyName(final Object rec) throws IndexException {
    if (rec instanceof BwCalendar) {
      return BwIndexLuceneDefs.keyCalendar.getName();
    }

    if (rec instanceof EventInfo) {
      return BwIndexLuceneDefs.keyEvent.getName();
    }

    throw new IndexException(IndexException.unknownRecordType,
                             rec.getClass().getName());
  }

  private static class IndexField implements Serializable {
    FieldInfo finfo;
    String val;

    IndexField(final FieldInfo finfo,
               final String val) {
      this.finfo = finfo;
      this.val = val;
    }
  }

  private static class Rec implements Serializable {
    Collection<IndexField> fields = new ArrayList<IndexField>();

    void addField(final FieldInfo finfo,
                  final String val) {
      fields.add(new IndexField(finfo, val));
    }
  }

  /** Called to create a rec from an object.
   *
   * @param o     Object to be indexed
   * @return Rec
   * @throws IndexException
   */
  public Rec makeRec(final Object o) throws IndexException {
    if (o == null) {
      return null;
    }

    Rec rec = new Rec();

    String colPath = null;
    Collection <BwCategory> cats = null;
    String created = null;
    String creator = null;
    String description = null;
    String lastmod = null;
    String owner = null;
    String summary = null;

    String start = null;
    String end = null;

    Collection<String> recurringStarts = null;
    Collection<String> recurringEnds = null;
    Collection<String> recurrenceIds = null;

    if (o instanceof BwCalendar) {
      BwCalendar cal = (BwCalendar)o;

      rec.addField(BwIndexLuceneDefs.itemTypeInfo,
                   BwIndexLuceneDefs.itemTypeCalendar);

      /* Path is the key */
      rec.addField(BwIndexLuceneDefs.calendarPath, cal.getPath());

      colPath = cal.getColPath();
      cats = cal.getCategories();
      created = cal.getCreated();
      creator = cal.getCreatorHref();
      description = cal.getDescription();
      lastmod = cal.getLastmod().getTimestamp();
      owner = cal.getOwnerHref();
      summary = cal.getSummary();
    } else if (o instanceof EventInfo) {
      EventInfo ei = (EventInfo)o;
      BwEvent ev = ei.getEvent();

      rec.addField(BwIndexLuceneDefs.itemTypeInfo,
                   BwIndexLuceneDefs.itemTypeEvent);

      rec.addField(BwIndexLuceneDefs.keyEvent, makeKeyVal(ev));

      if (ev instanceof BwEventProxy) {
        // Index with the master key
        rec.addField(BwIndexLuceneDefs.keyEventMaster,
                     makeKeyVal(((BwEventProxy)ev).getTarget()));
      } else {
        rec.addField(BwIndexLuceneDefs.keyEventMaster,
                     makeKeyVal(ev));
      }

      BwLocation loc = ev.getLocation();
      if (loc != null) {
        if (loc.getAddress() != null) {
          rec.addField(BwIndexLuceneDefs.location, loc.getAddress().getValue());
        }
        if (loc.getSubaddress() != null) {
          rec.addField(BwIndexLuceneDefs.location, loc.getSubaddress().getValue());
        }
      }

      colPath = ev.getColPath();
      cats = ev.getCategories();
      created = ev.getCreated();
      creator = ev.getCreatorHref();
      description = ev.getDescription();
      lastmod = ev.getLastmod();
      owner = ev.getOwnerHref();
      summary = ev.getSummary();

      if (!ev.getNoStart()) {
        start = ev.getDtstart().getDate();
      }

      if (ev.getDtend() != null) {
        end = ev.getDtend().getDtval();
      }

//      if (ev.isRecurringEntity()) {
      if ((ev.getRecurrenceId() == null) && ev.testRecurring()) {
        recurringStarts = new ArrayList<String>();
        recurringEnds = new ArrayList<String>();
        recurrenceIds = new ArrayList<String>();

        getRecurrences(ei, recurringStarts, recurringEnds, recurrenceIds);
      }
    } else {
      throw new IndexException(IndexException.unknownRecordType,
                               o.getClass().getName());
    }

    if (colPath != null) {
      rec.addField(BwIndexLuceneDefs.calendar, colPath);
    }

    indexCategories(rec, cats);

    rec.addField(BwIndexLuceneDefs.created, created);
    rec.addField(BwIndexLuceneDefs.creator, creator);
    rec.addField(BwIndexLuceneDefs.description, description);
    rec.addField(BwIndexLuceneDefs.lastmod, lastmod);
    rec.addField(BwIndexLuceneDefs.owner, owner);
    rec.addField(BwIndexLuceneDefs.summary, summary);

    addDate(rec, BwIndexLuceneDefs.startDate, start);
    addDate(rec, BwIndexLuceneDefs.endDate, end);
    addDate(rec, BwIndexLuceneDefs.dueDate, end);

    if (recurrenceIds != null) {
      for (String dt: recurrenceIds) {
        rec.addField(BwIndexLuceneDefs.recurrenceid, dt);
      }
    }

    if (recurringStarts != null) {
      for (String dt: recurringStarts) {
        rec.addField(BwIndexLuceneDefs.startDate, dt.substring(0, 8));
      }
    }

    if (recurringEnds != null) {
      for (String dt: recurringEnds) {
        rec.addField(BwIndexLuceneDefs.endDate, dt.substring(0, 8));
      }
    }

    return rec;
  }

  private void getRecurrences(final EventInfo val,
                              final Collection<String> recurringStarts,
                              final Collection<String> recurringEnds,
                              final Collection<String> recurrenceIds) throws IndexException {

    try {
      BwEvent ev = val.getEvent();

      RecurPeriods rp = RecurUtil.getPeriods(ev, maxYears, maxInstances);

      if (rp.instances.isEmpty()) {
        // No instances for an alleged recurring event.
        return;
        //throw new IndexException(CalFacadeException.noRecurrenceInstances);
      }

      String stzid = ev.getDtstart().getTzid();

//      ev.setLatestDate(Timezones.getUtc(rp.rangeEnd.toString(),
//                                            stzid));
      int instanceCt = maxInstances;

      boolean dateOnly = ev.getDtstart().getDateType();

      Map<String, String> overrides = new HashMap<String, String>();

      if (!Util.isEmpty(val.getOverrideProxies())) {
        for (BwEvent ov: val.getOverrideProxies()) {
          overrides.put(ov.getRecurrenceId(), ov.getRecurrenceId());
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

        recurrenceIds.add(rstart.getDate());

        dtval = p.getEnd().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        BwDateTime rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        recurringStarts.add(rstart.getDtval());
        recurringEnds.add(rend.getDtval());

        instanceCt--;
        if (instanceCt == 0) {
          // That's all you're getting from me
          break;
        }
      }
    } catch (Throwable t) {
      throw new IndexException(t);
    }
  }

  /** Called to fill in a Document from an object.
   *
   * @param doc   The Document
   * @param o     Object to be indexed
   */
  @Override
  public void addFields(final Document doc,
                        final Object o) throws IndexException {
    if (o == null) {
      System.out.println("Tried to index null record");
      return;
    }

    Rec r;

    if (!(o instanceof Rec)) {
      r = makeRec(o);
    } else {
      r = (Rec)o;
    }

    for (IndexField f: r.fields) {
      if (f.val == null) {
        continue;
      }

      addField(doc, f.finfo, f.val);
      addField(doc, BwIndexLuceneDefs.defaultFieldInfo, f.val);
    }
  }

  /** Called to return an array of valid term names.
   *
   * @return  String[]   term names
   */
  @Override
  public String[] getTermNames() {
    return BwIndexLuceneDefs.getTermNames();
  }

  private void indexCategories(final Rec r,
                               final Collection <BwCategory> cats) throws IndexException {
    if (cats == null) {
      return;
    }

    for (BwCategory cat: cats) {
      r.addField(BwIndexLuceneDefs.category, cat.getWord().getValue());
    }
  }

  private void addDate(final Rec r,
                       final FieldInfo fld,
                       String val) throws IndexException {
     if (val == null) {
       return;
     }

     val = val.substring(0, 8); // Date part only
     r.addField(fld, val);
  }
}
