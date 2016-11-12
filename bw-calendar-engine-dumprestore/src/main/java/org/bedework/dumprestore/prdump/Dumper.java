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
package org.bedework.dumprestore.prdump;

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.DumpIntf;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.dump.DumpGlobals;
import org.bedework.util.misc.Logged;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/** Base class for dumping. Provides logging and useful methods.
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 4.0
 */
public class Dumper extends Logged {
  private final Deque<String> pathStack = new ArrayDeque<>();

  private final DumpGlobals globals;

  /* ===================================================================
   *                       Constructor
   * =================================================================== */

  /**
   * @param globals for dump
   * @throws CalFacadeException on error
   */
  public Dumper(final DumpGlobals globals) throws CalFacadeException {
    this.globals = globals;
  }
  
  protected void addLn(final String val) {
    globals.info.addLn(val);
  }

  /**
   * @return true if ok
   * @throws CalFacadeException on error
   */
  boolean open() throws CalFacadeException {
    pathStack.clear();
    pushPath(globals.getDirPath());

    if (globals.getDirPath() == null) {
      throw new CalFacadeException("Null directory name");
    }

    return true;
  }

  protected void dumpCategories() throws CalFacadeException {
    try {
      makeDir(Defs.categoriesDirName, false);

      final Collection<BwCategory> cats =
              getSvc().getCategoriesHandler().get();

      for (final BwCategory cat: cats) {
        incCount(DumpGlobals.categories);
        cat.dump(makeFile(cat.getUid() + ".xml"));
      }
    } finally {
      popPath();
    }
  }

  void incCount(final int index) {
    globals.counts[index]++;
  }
  
  protected void pushPath(final String val) {
    pathStack.push(val);
  }

  protected String topPath() {
    return pathStack.peek();
  }

  protected void popPath() {
    pathStack.pop();
  }
  
  /**
   * @throws CalFacadeException on error
   */
  protected void close() throws CalFacadeException {
  }

  /**
   * @return list of external subscriptions
   */
  public List<AliasInfo> getExternalSubs() {
    return globals.externalSubs;
  }

  /** Add a file using stack top as path
   * 
   * @param name filename
   * @return a File object
   * @throws CalFacadeException on error
   */
  protected File makeFile(final String name) throws CalFacadeException {
    try {
      final Path p =  FileSystems.getDefault().getPath(topPath(), name);

      final File f = p.toFile();

      if (f.exists()) {
        globals.info.addLn("Path " + p + " already exists.");
        return null;
      }

      if (!f.createNewFile()) {
        throw new CalFacadeException("Unable to create file " + p);
      }

      return f;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Add a directory using stack top as path and pushes the new path
   *
   * @param name dirname
   * @return true if created
   * @throws CalFacadeException on error
   */
  protected boolean makeDir(final String name,
                           final boolean pushDup) throws CalFacadeException {
    try {
      final Path p =  FileSystems.getDefault().getPath(topPath(), name);

      final File f = p.toFile();

      if (f.exists()) {
        globals.info.addLn("Path " + p + " already exists.");
        
        if (pushDup) {
          pushPath(p.toString());
        }
        return false;
      }

      if (!f.mkdirs()) {
        throw new CalFacadeException("Unable to create directory " + p);
      }

      pushPath(p.toString());
      return true;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  protected BasicSystemProperties getSysRoots() {
    return globals.getSysRoots();
  }

  protected DumpIntf getDi() {
    return globals.di;
  }

  protected CalSvcI getSvc() {
    return globals.svci;
  }
}
