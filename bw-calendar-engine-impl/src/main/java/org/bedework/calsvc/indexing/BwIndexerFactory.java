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

import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;

import edu.rpi.cct.misc.indexing.IndexLuceneImpl;
import edu.rpi.sss.util.Util;

import org.apache.log4j.Logger;

import java.io.File;

/** Create an instance of an indexer for bedework.
 *
 * @author douglm
 *
 */
public class BwIndexerFactory {
  /* No instantiation
   */
  private BwIndexerFactory() {
  }

  /** Factory method to get current indexer
   *
   * @param publick
   * @param principal
   * @param writeable true if the caller can update the index
   * @param syspars
   * @return indexer
   * @throws CalFacadeException
   */
  public static BwIndexer getIndexer(final boolean publick,
                                     final String principal,
                                     final boolean writeable,
                                     final SystemProperties syspars) throws CalFacadeException {
    if (publick && syspars.getUseSolr()) {
      try {
        return new BwIndexSolrImpl(syspars.getSolrURL(), writeable,
                                   syspars.getMaxYears(),
                                   syspars.getMaxInstances(),
                                   syspars.getSolrDefaultCore(),  // Default index
                                   null); // No admin
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    return getIndexer(publick, principal, writeable,
                      syspars,
                      getIndexPath(syspars.getIndexRoot(),
                                   BwIndexLuceneDefs.currentIndexname),
                      null);
  }

  /** Factory method allowing us to specify the system root. This should only
   * be called from the crawler which will be indexing into an alternative
   * index.
   *
   * @param publick
   * @param principal
   * @param writeable true if the caller can update the index
   * @param syspars
   * @param indexRoot
   * @return indexer
   * @param adminPath  - path for administration of cores
   * @throws CalFacadeException
   */
  public static BwIndexer getIndexer(final boolean publick,
                                     final String principal,
                                     final boolean writeable,
                                     final SystemProperties syspars,
                                     final String indexRoot,
                                     final String adminPath) throws CalFacadeException {
    try {
      String suffix;

      if (publick && syspars.getUseSolr()) {
        return new BwIndexSolrImpl(syspars.getSolrURL(), writeable,
                                   syspars.getMaxYears(),
                                   syspars.getMaxInstances(),
                                   indexRoot,
                                   adminPath);
      }

      if (publick) {
        suffix = syspars.getPublicCalendarRoot();
      } else if (principal == null) {
        throw new CalFacadeException(CalFacadeException.notIndexPrincipal,
                                     "null");
      } else {
        suffix = Util.buildPath(true, "/", syspars.getUserCalendarRoot(), "/", principal);
      }

      String path = getIndexPath(indexRoot, suffix);
      File f = new File(path + IndexLuceneImpl.getPathSuffix());
      if (f.isFile()) {
        throw new CalFacadeException(CalFacadeException.notIndexDirectory,
                                     f.getAbsolutePath());
      }

      if (!f.exists()) {
        if (!f.mkdirs()) {
          Logger.getLogger(BwIndexerFactory.class).error(
                  "Index creation failed for path: " + f.getAbsolutePath());
          throw new CalFacadeException(CalFacadeException.indexCreateFailed,
                                       f.getAbsolutePath());
        }
      }

      return new BwIndexLuceneImpl(path, writeable,
                                   syspars.getMaxYears(),
                                   syspars.getMaxInstances());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static String getIndexPath(final String prefix, final String suffix) {
    String p = Util.buildPath(true, prefix, "/", suffix);

    File f = new File(p);

    if (f.isAbsolute()) {
      return p;
    }

    String dataPrefix = System.getProperty("org.bedework.data.dir");

    if (dataPrefix == null) {
      return p;
    }

    return Util.buildPath(true, dataPrefix, "/", p);
  }
}
