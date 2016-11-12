/*
#    Copyright (c) 2007-2013 Cyrus Daboo. All rights reserved.
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
*/
package org.bedework.dumprestore;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.misc.Logged;
import org.bedework.util.misc.Util;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@SuppressWarnings("WeakerAccess")
public class Utils extends Logged {
  public Utils() {
  }

  /** for principal "/principals/user/mike/" will return <br/>
   * /m/principals/user/mike <br/>
   *
   * <p>The directory with the name "m" is the first lowercased character 
   * of the principal if in the set 0-9a-z. Anything else goes in "_". This
   * is all to reduce the number of files in directories so it is navigable</p>
   * 
   * @param pr the principal
   * @return a directory path in which to dump all the data
   */
  public static String principalDirPath(final BwPrincipal pr) {
    String s;
    final String account = pr.getAccount();
    
    if (account.length() == 0) {
      s = "_";
    } else {
      s = account.toLowerCase().substring(0, 1);
      final char c = s.charAt(0);

      if (!Character.isLetterOrDigit(c)) {
        s = "_";
      }
    }
    
    return Util.buildPath(true, "/", s, pr.getPrincipalRef());
  }

  public Path createFile(final String path) throws CalFacadeException {
    try {
      final Path pathToFile = Paths.get(path);
      Files.createDirectories(pathToFile.getParent());
      return Files.createFile(pathToFile);
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  public boolean empty(final String path) {
    return delete(new File(path), false);
  }

  public static boolean makeDir(final String path) throws CalFacadeException {
    final File f = new File(path);

    if (!f.exists()) {
      return f.mkdir();
    }

    if (!f.isDirectory()) {
      throw new CalFacadeException(f.getAbsolutePath() +
                                  " must be a directory");
    }

    return false;
  }

  public static File directory(final String path) throws CalFacadeException {
    final File f = new File(path);

    if (!f.exists()) {
      return null;
    } 
    
    if (!f.isDirectory()) {
      throw new CalFacadeException(f.getAbsolutePath() +
                                           " must be a directory");
    }

    return f;
  }

  public File subDirectory(final String path,
                           final String name) throws Throwable {
    final Path p = Paths.get(path, name);
    final File f = p.toFile();

    if (!f.exists() || !f.isDirectory()) {
      throw new Exception(name + " in " +
                                  path +
                                  " must exist and be a directory");
    }

    return f;
  }

  public File subDirectory(final File f,
                           final String name,
                           final boolean mustExist) throws Throwable {
    final File dir = new File(f.getAbsolutePath(), name);

    if (dir.exists() && !dir.isDirectory()) {
      throw new Exception(name + " in " +
                                  f.getAbsolutePath() +
                                  " must be a directory");
    }

    if (!dir.exists() && mustExist) {
      throw new Exception(name + " in " +
                                  f.getAbsolutePath() +
                                  " must exist and be a directory");
    }

    return dir;
  }

  public static File file(final File dir,
                          final String name,
                          final boolean mustExist) throws CalFacadeException {
    final File f = new File(dir.getAbsolutePath(), name);

    if (f.exists() && !f.isFile()) {
      throw new CalFacadeException(name + " in " +
                                  f.getAbsolutePath() +
                                  " must be a file");
    }

    if (!f.exists() && mustExist) {
      throw new CalFacadeException(name + " in " +
                                  f.getAbsolutePath() +
                                  " must exist and be a file");
    }

    return f;
  }

  public File fileOrDir(final File dir,
                        final String name) throws Throwable {
    final File f = new File(dir.getAbsolutePath(), name);

    if (!f.exists()) {
      throw new Exception(name + " in " +
                                  f.getAbsolutePath() +
                                  " must exist");
    }

    return dir;
  }

  public static File file(final String path) throws CalFacadeException {
    final File f = new File(path);

    if (!f.exists() || !f.isFile()) {
      throw new CalFacadeException(path + " must exist and be a file");
    }

    return f;
  }

  /** Parse a reader and return the DOM representation.
   *
   * @param rdr        Reader
   * @param nameSpaced true if this document has namespaces
   * @return Document  Parsed body or null for no body
   * @exception CalFacadeException Some error occurred.
   */
  public static Document parseXml(final Reader rdr,
                                  final boolean nameSpaced) throws CalFacadeException {
    if (rdr == null) {
      // No content?
      return null;
    }

    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(nameSpaced);

    try {
      final DocumentBuilder builder = factory.newDocumentBuilder();

      return builder.parse(new InputSource(rdr));
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** If it's a file - delete it.
   * If it's a directory delete the contents and if deleteThis is true
   * delete the directory as well.
   *
   * @param file file/dir
   * @param deleteThis true to delete directory
   * @return true if something deleted
   */
  public boolean delete(final File file,
                        final boolean deleteThis) {
    final File[] flist;

    if(file == null){
      return false;
    }

    if (file.isFile()) {
      return file.delete();
    }

    if (!file.isDirectory()) {
      return false;
    }

    flist = file.listFiles();
    if (flist != null && flist.length > 0) {
      for (final File f : flist) {
        if (!delete(f, true)) {
          return false;
        }
      }
    }

    if (!deleteThis) {
      return true;
    }
    return file.delete();
  }

  private final static CopyOption[] copyOptionAttributes =
          new CopyOption[] { REPLACE_EXISTING, COPY_ATTRIBUTES };

  /**
   * A {@code FileVisitor} that copies a file-tree ("cp -r")
   */
  private class DirCopier implements FileVisitor<Path> {
    private final Path in;
    private final Path out;
    private final boolean outExists;

    DirCopier(final Path in,
              final Path out,
              final boolean outExists) {
      this.in = in;
      this.out = out;
      this.outExists = outExists;
    }

    @Override
    public FileVisitResult preVisitDirectory(final Path dir,
                                             final BasicFileAttributes attrs) {
      // before visiting entries in a directory we copy the directory
      final Path newdir = out.resolve(in.relativize(dir));

      try {
//        if ((newdir.compareTo(out) == 0) && outExists) {
  //        return CONTINUE;
    //    }

        //Utils.debug("**** Visit dir " + dir);
        final File nd = newdir.toFile();
        if (nd.exists()) {
          if (nd.isDirectory()) {
            return CONTINUE;
          }

          error(dir.toString() + " already exists and is not a directory");
          return SKIP_SUBTREE;
        }
        //Utils.debug("**** Copy dir " + dir);
        Files.copy(dir, newdir, copyOptionAttributes);
      } catch (final FileAlreadyExistsException faee) {
        error("File already exists" + faee.getFile());
      } catch (final Throwable t) {
        error("Unable to create: " + newdir + ": " + t);
        return SKIP_SUBTREE;
      }
      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file,
                                     final BasicFileAttributes attrs) {
      //Utils.debug("**** Copy file " + file);
      copyFile(file, out.resolve(in.relativize(file)));
      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir,
                                              final IOException exc) {
      // fix up modification time of directory when done
      if (exc == null) {
        final Path newdir = out.resolve(in.relativize(dir));
        try {
          final FileTime time = Files.getLastModifiedTime(dir);
          Files.setLastModifiedTime(newdir, time);
        } catch (final Throwable t) {
          error("Unable to copy all attributes to: " + newdir +
                        ": " + t);
        }
      }
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

  public void copy(final Path inPath,
                   final Path outPath,
                   final boolean outExists) throws Throwable {
    final EnumSet<FileVisitOption> opts = EnumSet.of(
            FileVisitOption.FOLLOW_LINKS);
    final DirCopier tc = new DirCopier(inPath, outPath, outExists);
    Files.walkFileTree(inPath, opts, Integer.MAX_VALUE, tc);
  }

  public void copyFile(final Path in,
                       final Path out) {
//    if (Files.notExists(out)) {
    try {
      Files.copy(in, out, copyOptionAttributes);
    } catch (final Throwable t) {
      error("Unable to copy: " + in + " to " + out +
                    ": " + t);
    }
    //  }
  }

  public class DeletingFileVisitor extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(final Path file,
                                     final BasicFileAttributes attributes)
            throws IOException {
      if(attributes.isRegularFile()) {
        Files.delete(file);
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path directory,
                                              final IOException ioe)
            throws IOException {
      Files.delete(directory);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file,
                                           final IOException ioe)
            throws IOException {
      error("Unable to delete: " + file +
                    ": " + ioe);
      return FileVisitResult.CONTINUE;
    }
  }

  public void deleteAll(final Path dir) throws Throwable {
    final DeletingFileVisitor delFileVisitor = new DeletingFileVisitor();
    Files.walkFileTree(dir, delFileVisitor);
  }

  /** Return a Properties object containing all those properties that
   * match the given prefix. The property name will have the prefix
   * replaced by the new prefix.
   *
   * @param props source properties
   * @param prefix to match
   * @param newPrefix replacement
   * @return never null
   */
  public static Properties filter(final Properties props,
                                  final String prefix,
                                  final String newPrefix) {
    final Properties res = new Properties();

    for (final String pname: props.stringPropertyNames()) {
      if (pname.startsWith(prefix)) {
        res.setProperty(newPrefix + pname.substring(prefix.length()),
                        props.getProperty(pname));
      }
    }
    return res;
  }

  void print(final String fmt,
                    final Object... params) {
    final Formatter f = new Formatter();

    info(f.format(fmt, params).toString());
  }

  void assertion(final boolean test,
                 final String fmt,
                 final Object... params) {
    if (test) {
      return;
    }

    final Formatter f = new Formatter();

    throw new RuntimeException(f.format(fmt, params).toString());
  }

  int getInt(final String val) {
    try {
      return Integer.valueOf(val);
    } catch (final Throwable ignored) {
      throw new RuntimeException("Failed to parse as Integer " + val);
    }
  }
}

