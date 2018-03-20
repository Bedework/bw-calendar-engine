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
package org.bedework.tools;

import org.bedework.util.args.Args;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class FixCdTestTzs {
  String dirName;

  int changed;

  /* Skip if path ends with one of these */
  private static ArrayList<String> skipList = new ArrayList<String>();

  static {
    skipList.add("CalDAV/timezones");
  }

  boolean process() throws Throwable {
    File d = new File(dirName);

    if (!d.isDirectory()) {
      error("Not a directory: " + dirName);
      return false;
    }

    if (!processDir(d, "")) {
      error("Failed?");
      return false;
    }

    info("\n\nChanged: " + changed);

    return true;
  }

  boolean processDir(File d, String indent) throws Throwable {
    String path = d.getPath();

    for (String p: skipList) {
      if (path.endsWith(p)) {
        info(indent + path + "  *** Skipped ***");
        return true;
      }
    }

    info(indent + path);

    String[] l = d.list();

    Set<String> sorted = new TreeSet<String>();

    if ((l != null) && (l.length > 0)) {
      for (String s: l) {
        if (s.equals(".svn")) {
          continue;
        }

        sorted.add(d.getPath() + "/" + s);
      }
    }

    for (String s: sorted) {
      File f = new File(s);

      if (f.isDirectory()) {
        if (!processDir(f, indent + "  ")) {
          return false;
        }

        continue;
      }

      if (f.isFile()) {
        processFile(f, indent + "  ");

        continue;
      }
    }


    return true;
  }

  private static final String tzMarker = "--Put tzdef here--";

  private static class Syms {
    String idSym;
    String defSym;
    String spuriousSym;

    Syms(String idSym, String defSym, String spuriousSym) {
      this.idSym = idSym;
      this.defSym = defSym;
      this.spuriousSym = spuriousSym;
    }
  }

  private static Map<String, Syms> tzidMap = new HashMap<String, Syms>();

  static {
    tzidMap.put("US/Eastern", new Syms("$tzidUSE:",
                                       "$tzspecUSE:",
                                       "$tzspecUSEspurious:"));
    tzidMap.put("US/Mountain", new Syms("$tzidUSMountain:",
                                        "$tzspecUSMountain:",
                                        null));
    tzidMap.put("US/Pacific", new Syms("$tzidUSPacific:", 
                                       "$tzspecUSPacific:", null));

    tzidMap.put("America/Vancouver", new Syms("$tzidVancouver:",
                                              "$tzspecVancouver:",
                                              null));
    tzidMap.put("America/Montreal", new Syms("$tzidMontreal:", 
                                             "$tzspecMontreal:", null));
    tzidMap.put("America/New_York", new Syms("$tzidNew_York:", 
                                             "$tzspecNew_York:", null));
    tzidMap.put("America/St_Johns", new Syms("$tzidSt_Johns:", 
                                             "$tzspecSt_Johns:", null));

    tzidMap.put("Brazil/East", new Syms("$tzidBrazilEast:", 
                                        "$tzspecBrazilEast:", null));

    tzidMap.put("Etc/GMT+5", new Syms("$tzidEtcGMT5:", 
                                      "$tzspecEtcGMT5:", null));
    tzidMap.put("Etc/GMT+8", new Syms("$tzidEtcGMT8:", 
                                      "$tzspecEtcGMT8:", null));
  }

  void processFile(File f, String indent) throws Throwable {
    FileReader fr = new FileReader(f);
    LineNumberReader lnr = new LineNumberReader(fr);

    String ln = lnr.readLine();

    if ((ln == null) | (!"BEGIN:VCALENDAR".equals(ln))) {
      if (ln.startsWith("BEGIN:VCALENDAR")) {
        error("Starts with BEGIN:VCALENDAR");
      }

      return;
    }

    ArrayList<String> lines = new ArrayList<String>();

    lines.add(ln);

    boolean rewrite = false;
    boolean inTz = false;
    boolean defUsed = false;
    String tzName = null;

    for (;;) {
      ln = lnr.readLine();

      if (ln == null) {
        break;
      }

      if (inTz) {
        if (tzName != null) {
          // Looking for end
          if (ln.equals("END:VTIMEZONE")) {
            inTz = false;
          }
          continue;
        }

        if (ln.startsWith("TZID:")) {
          tzName = ln.substring(5);
        }

        continue;
      }

      if (ln.equals("BEGIN:VTIMEZONE")) {
        if (tzName != null) {
          error("Multiple timezones for " + f.getPath());
          break;
        }

        inTz = true;
        rewrite = true;
        lines.add(tzMarker);
        continue;
      }

      if (tzName == null) {
        lines.add(ln);
        continue;
      }

      int pos = ln.indexOf(";TZID=" + tzName + ":");

      if (pos < 0) {
        lines.add(ln);
        continue;
      }

      Syms syms = tzidMap.get(tzName);

      if (syms == null) {
        error("Unmapped timezone " + tzName + " for " + f.getPath());
        break;
      }

      lines.add(ln.replace(";TZID=" + tzName + ":",
                           ";TZID=" + syms.idSym + ":"));

      defUsed = true;
    }

    lnr.close();
    fr.close();

    if (!rewrite) {
      return;
    }

    changed++;

    FileWriter fw = new FileWriter(f);

    Syms syms = tzidMap.get(tzName);

    for (String s: lines) {
      if (s.equals(tzMarker)) {
        if (syms == null) {
          error("Unmapped timezone " + tzName + " for " + f.getPath());
          continue;
        } else if (defUsed) {
          s = syms.defSym;
        } else if (syms.spuriousSym == null) {
          error("No spurious sym for " + tzName + " for " + f.getPath());
          s = syms.defSym;
        } else {
          s = syms.spuriousSym;
        }
      }

      if (s == null) {
        error("No def for timezone " + tzName + " for " + f.getPath());
        continue;
      }
      fw.write(s);
      fw.write("\n");
    }

    fw.close();

    return;
  }

  boolean processArgs(final Args args) throws Throwable {
    if (args == null) {
      return true;
    }

    while (args.more()) {
      if (args.ifMatch("")) {
        continue;
      }

      if (args.ifMatch("-d")) {
        dirName = args.next();
      } else {
        error("Illegal argument: " + args.current());
        usage();
        return false;
      }
    }

    return true;
  }

  protected void info(final String msg) {
    System.out.println(msg);
  }

  protected void error(final String msg) {
    System.err.println(msg);
  }

  void usage() {
    info("Usage:");
    info("args   -d <dirname>");
    info("            specify directory containing files");
    info("");
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    FixCdTestTzs fx = null;

    try {
      fx = new FixCdTestTzs();

      if (!fx.processArgs(new Args(args))) {
        return;
      }

      fx.process();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}
