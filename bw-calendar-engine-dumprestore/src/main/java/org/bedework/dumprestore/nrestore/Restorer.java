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
package org.bedework.dumprestore.nrestore;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.RestoreIntf;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Counters;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.dumprestore.restore.XmlFile;
import org.bedework.util.logging.Logged;
import org.bedework.util.xml.FromXml;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import static java.nio.file.FileVisitResult.CONTINUE;

/** Base class for dumping. Provides logging and useful methods.
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 4.0
 */
public class Restorer implements Logged, Closeable {
  private final Deque<String> pathStack = new ArrayDeque<>();

  protected final RestoreGlobals globals;

  final FromXml fxml = new FromXml();

  /* ===================================================================
   *                       Constructor
   * =================================================================== */

  /**
   * @param globals for restore
   */
  public Restorer(final RestoreGlobals globals) {
    this.globals = globals;
  }
  
  public RestoreGlobals getGlobals() {
    return globals;
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

  protected void restoreCategories() throws CalFacadeException {
    try {
      final Path p = openDir(Defs.categoriesDirName);

      if (p == null) {
        return;
      }

      final DirRestore<BwCategory> dirRestore =
              new DirRestore<>(p,
                               restoreCat);
      final EnumSet<FileVisitOption> opts = EnumSet.of(
              FileVisitOption.FOLLOW_LINKS);
      Files.walkFileTree(p, opts, Integer.MAX_VALUE, dirRestore);
    } catch (final IOException ie) {
      throw new CalFacadeException(ie);
    } finally {
      popPath();
    }
  }

  protected void restoreLocations() throws CalFacadeException {
    try {
      final Path p = openDir(Defs.locationsDirName);
      
      if (p == null) {
        return;
      }
      
      final DirRestore<BwLocation> dirRestore = 
              new DirRestore<>(p,
                               restoreLoc);
      final EnumSet<FileVisitOption> opts = EnumSet.of(
              FileVisitOption.FOLLOW_LINKS);
      Files.walkFileTree(p, opts, Integer.MAX_VALUE, dirRestore);
    } catch (final IOException ie) {
      throw new CalFacadeException(ie);
    } finally {
      popPath();
    }
  }

  protected void restoreContacts() throws CalFacadeException {
    try {
      final Path p = openDir(Defs.contactsDirName);

      if (p == null) {
        return;
      }

      final DirRestore<BwContact> dirRestore =
              new DirRestore<>(p,
                               restoreCtct);
      final EnumSet<FileVisitOption> opts = EnumSet.of(
              FileVisitOption.FOLLOW_LINKS);
      Files.walkFileTree(p, opts, Integer.MAX_VALUE, dirRestore);
    } catch (final IOException ie) {
      throw new CalFacadeException(ie);
    } finally {
      popPath();
    }
  }

  protected void restoreCollections() throws CalFacadeException {
    final Path p = openDir(Defs.collectionsDirName);

    if (p == null) {
      return;
    }
    
    globals.colsHome = topPath();
    restoreCollections(p);
  }

  protected void restoreCollections(final Path p) throws CalFacadeException {
    try {
      final DirRestore<BwCalendar> colRestore =
              new DirRestore<>(p,
                               restoreCol);
      final EnumSet<FileVisitOption> opts = EnumSet.of(
              FileVisitOption.FOLLOW_LINKS);
      Files.walkFileTree(p, opts, Integer.MAX_VALUE, colRestore);
    } catch (final IOException ie) {
      throw new CalFacadeException(ie);
    } finally {
      popPath();
    }
  }

  private final Function<RestoreValue, RestoreResult<BwCategory>> restoreCat = rv -> {
    final File f = rv.path.toFile();

    try {
      final String name = f.getName();
      final XmlFile catXml = 
              new XmlFile(f.getParentFile(), 
                          name, false);
  
      final BwCategory cat =
              fxml.fromXml(catXml.getRoot(),
                           BwCategory.class,
                           BwCategory.getRestoreCallback());

      if (rv.globals.getMerging()) {
        final BwCategory curcat = getRi().getCategory(cat.getUid());
        if (curcat != null) {
          incSkipped(Counters.categories);

          if (rv.globals.getDryRun()) {
            info(cat.toString());
          }
          return new RestoreResult(true,
                                   "Skip existing category " + cat.getUid());
        }
      }

      incCount(Counters.categories);

      if (rv.globals.getDryRun()) {
        info(cat.toString());        
      } else {
        getRi().restoreCategory(cat);
      }
        
      return new RestoreResult(cat);
    } catch (final FileNotFoundException fnfe) {
      return new RestoreResult("File not found: " + rv.path);
    } catch (final Throwable t) {
      return new RestoreResult("Failed restore for " + rv.path + 
                                       " message: " + t.getLocalizedMessage());
    }
  };
  
  private final Function<RestoreValue, RestoreResult<BwLocation>> restoreLoc = rv -> {
    final File f = rv.path.toFile();

    try {
      final String name = f.getName();
      final XmlFile catXml =
              new XmlFile(f.getParentFile(),
                          name, false);

      final BwLocation ent =
              fxml.fromXml(catXml.getRoot(),
                           BwLocation.class,
                           BwLocation.getRestoreCallback());

      if (rv.globals.getMerging()) {
        final BwLocation curent = getRi().getLocation(ent.getUid());
        if (curent != null) {
          incSkipped(Counters.locations);

          if (rv.globals.getDryRun()) {
            info(ent.toString());
          }
          return new RestoreResult(true,
                                   "Skip existing location " + ent.getUid());
        }
      }

      incCount(Counters.locations);

      if (rv.globals.getDryRun()) {
        info(ent.toString());
      } else {
        getRi().restoreLocation(ent);
      }

      return new RestoreResult(ent);
    } catch (final FileNotFoundException fnfe) {
      return new RestoreResult("File not found: " + rv.path);
    } catch (final Throwable t) {
      return new RestoreResult("Failed restore for " + rv.path +
                                       " message: " + t.getLocalizedMessage());
    }
  };

  private final Function<RestoreValue, RestoreResult<BwContact>> restoreCtct = rv -> {
    final File f = rv.path.toFile();

    try {
      final String name = f.getName();
      final XmlFile catXml =
              new XmlFile(f.getParentFile(),
                          name, false);

      final BwContact ent =
              fxml.fromXml(catXml.getRoot(),
                           BwContact.class,
                           BwContact.getRestoreCallback());

      if (rv.globals.getMerging()) {
        final BwContact curent = getRi().getContact(ent.getUid());
        if (curent != null) {
          incSkipped(Counters.contacts);

          if (rv.globals.getDryRun()) {
            info(ent.toString());
          }
          return new RestoreResult(true,
                                   "Skip existing contact " + ent.getUid());
        }
      }

      incCount(Counters.contacts);

      if (rv.globals.getDryRun()) {
        info(ent.toString());
      } else {
        getRi().restoreContact(ent);
      }

      return new RestoreResult(ent);
    } catch (final FileNotFoundException fnfe) {
      return new RestoreResult("File not found: " + rv.path);
    } catch (final Throwable t) {
      return new RestoreResult("Failed restore for " + rv.path +
                                       " message: " + t.getLocalizedMessage());
    }
  };

  private final Function<RestoreValue, RestoreResult<BwCalendar>> restoreCol = rv -> {
    final File f = rv.path.toFile();

    try {
      final String name = f.getName();
      final XmlFile catXml =
              new XmlFile(f.getParentFile(),
                          name, false);

      final BwCalendar col =
              fxml.fromXml(catXml.getRoot(),
                           BwCalendar.class,
                           BwCalendar.getRestoreCallback());

      final boolean home = f.getParent().equals(rv.globals.colsHome);

      if (home && ("Trash".equals(name))) {
        // Skip this one - old stuff
        incSkipped(Counters.collections);

        if (rv.globals.getDryRun()) {
          info(col.toString());
        }
        return new RestoreResult(true,
                                 "Skip trash collection " + col.getColPath());
      }

      if (rv.globals.getMerging() && !f.isDirectory()) {
        final BwCalendar curcol = getRi().getCalendar(col.getColPath());
        if (curcol != null) {
          incSkipped(Counters.collections);

          if (rv.globals.getDryRun()) {
            info(col.toString());
          }
          return new RestoreResult(true,
                                   "Skip existing collection " + col.getColPath());
        }
      }

      incCount(Counters.collections);

      if (rv.globals.getDryRun()) {
        info(col.toString());
      } else {
        getRi().addCalendar(col);
      }
      
      if (f.isDirectory()) {
        final Path folder = openDir(name);
        restoreCollections(folder);
      }

      return new RestoreResult(col);
    } catch (final FileNotFoundException fnfe) {
      return new RestoreResult("File not found: " + rv.path);
    } catch (final Throwable t) {
      return new RestoreResult("Failed restore for " + rv.path +
                                       " message: " + t.getLocalizedMessage());
    }
  };
  
  void incCount(final int index) {
    globals.counts[index]++;
  }

  void incSkipped(final int index) {
    globals.skipCounts[index]++;
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
  
  public void close() {
  }

  /**
   * @return list of external subscriptions
   */
  public List<AliasInfo> getExternalSubs() {
    return globals.externalSubs;
  }

  /** Open a directory using stack top as path and pushes the new path
   *
   * @param name dirname
   * @return Path if created
   */
  protected Path openDir(final String name) {
    final Path p =  FileSystems.getDefault().getPath(topPath(), name);

    final File f = p.toFile();

    if (!f.exists()) {
      globals.info.addLn("Path " + p + " does not exist.");
        
      return null;
    }

    pushPath(p.toString());
    return p;
  }

  static class RestoreValue {
    Path path;
    RestoreGlobals globals;
    
    RestoreValue(final Path path,
                 final RestoreGlobals globals) {
      this.path = path;
      this.globals = globals;
    }
  }

  static class RestoreColValue extends RestoreValue {
    boolean home;

    RestoreColValue(final Path path,
                    final RestoreGlobals globals,
                    final boolean home) {
      super(path, globals);
      this.home = home;
    }
  }

  static class RestoreResult<T extends BwDbentity> {
    T entity;
    boolean ok;
    String msg;

    RestoreResult(final T entity) {
      ok = true;
      this.entity = entity;
    }

    RestoreResult(final String msg) {
      ok = false;
      this.msg = msg;
    }

    RestoreResult(final boolean ok,
                  final String msg) {
      this.ok = ok;
      this.msg = msg;
    }
  }
  
  /**
   * A {@code FileVisitor} that restores entities in a directory
   */
  class DirRestore<T extends BwDbentity>  implements
          FileVisitor<Path> {
    
    private final Path in;
    private final Function<RestoreValue, RestoreResult<T>> restore;

    DirRestore(final Path in,
               final Function<RestoreValue, RestoreResult<T>> restore) {
      this.in = in;
      this.restore = restore;
    }

    @Override
    public FileVisitResult preVisitDirectory(final Path dir,
                                             final BasicFileAttributes attrs) {
      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file,
                                     final BasicFileAttributes attrs) {
      final RestoreResult<T> rr = restore.apply(new RestoreValue(file, globals));
      
      if (!rr.ok) {
        warn("Restore had problems: " + rr.msg);
      }

      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir,
                                              final IOException exc) {
      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file,
                                           final IOException exc) {
      if (exc instanceof FileSystemLoopException) {
        error("cycle detected: " + file);
      } else {
        error("Unable to copy: " + file + "; " + exc);
      }
      return CONTINUE;
    }
  }

//  protected BasicSystemProperties getSysRoots() {
//    return globals.getSysRoots();
//  }

  protected RestoreIntf getRi() {
    return globals.rintf;
  }

  protected CalSvcI getSvc() {
    return globals.svci;
  }
}
