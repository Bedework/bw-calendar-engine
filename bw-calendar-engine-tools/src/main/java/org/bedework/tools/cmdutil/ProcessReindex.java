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
package org.bedework.tools.cmdutil;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.CollectionInfo;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;

import java.util.Collection;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeCategory;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeCollection;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeContact;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeLocation;

/**
 * @author douglm
 *
 */
public class ProcessReindex extends CmdUtilHelper {
  ProcessReindex(final ProcessState pstate) {
    super(pstate);
  }

  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("collection".equals(wd)) {
      return reindexCollections();
    }

    if ("categories".equals(wd)) {
      return reindexCategories();
    }

    if ("contacts".equals(wd)) {
      return reindexContacts();
    }

    if ("locations".equals(wd)) {
      return reindexLocations();
    }

    return false;
  }

  @Override
  String command() {
    return "reindex";
  }

  @Override
  String description() {
    return "reindex categories, contacts or locations";
  }

  private boolean reindexCollections() throws Throwable {
    try {
      final String path = quotedVal();
      if (path == null) {
        error("Path required");
        return true;
      }
      
      open();
      info("Number reindexed: " + 
                   indexCollection(path, getIndexer(docTypeCollection)));

      return true;
    } finally {
      close();
    }
  }

  protected int indexCollection(final String path,
                                final BwIndexer indexer) {
    int reindexed = 0;

    try {
      BwCalendar col = null;

      try {
        col = getSvci().getCalendarsHandler().get(path);
      } catch (final CalFacadeAccessException cfe) {
        error("No access to " + path);
      }

      if (col == null) {
        if (debug()) {
          debug("path " + path + " not found");
        }

        return 0;
      }

      indexer.indexEntity(col);
//      close();

      reindexed++;
      
      final CollectionInfo ci = col.getCollectionInfo();
      if (!ci.childrenAllowed) {
        return reindexed;
      }

      Refs refs = null;

      for (;;) {
        refs = getChildCollections(path, refs);

        if (refs == null) {
          break;
        }

        for (final String cpath: refs.refs) {
          reindexed += indexCollection(cpath, indexer);
        }
      }
    } catch (final Throwable t) {
      error(t);
    }

    return reindexed;
  }

  /**
   *
   */
  public static class Refs {
    /** Where we are in the list */
    public int index;

    /** How many to request */
    public int batchSize;

    /** List of references - names or hrefs depending on context */
    public Collection<String> refs;
  }

  /** Get the next batch of child collection paths.
   *
   * @param path to parent
   * @param refs - null on first call.
   * @return next batch of hrefs or null for no more.
   */
  protected Refs getChildCollections(final String path,
                                     final Refs refs) {
    Refs r = refs;

    if (r == null) {
      r = new Refs();
      r.batchSize = 100;
    }

    final BwCalendar col = getSvci().getCalendarsHandler().get(path);

    if (col == null) {
      warn("No collection");
      return null;
    }

    r.refs = getSvci().getAdminHandler().getChildCollections(path, r.index, r.batchSize);

    if (r.refs == null) {
      return null;
    }

    r.index += r.refs.size();

    return r;
  }

  private boolean reindexCategories() throws Throwable {
    try {
      open();
      info("Number reindexed: " +
                   getSvci().getCategoriesHandler().reindex(getIndexer(docTypeCategory)));

      return true;
    } finally {
      close();
    }
  }

  private boolean reindexContacts() throws Throwable {
    try {
      open();
      info("Number reindexed: " +
                   getSvci().getContactsHandler().reindex(getIndexer(docTypeContact)));

      return true;
    } finally {
      close();
    }
  }

  private boolean reindexLocations() throws Throwable {
    try {
      open();
      info("Number reindexed: " +
                   getSvci().getLocationsHandler().reindex(getIndexer(docTypeLocation)));

      return true;
    } finally {
      close();
    }
  }
}
