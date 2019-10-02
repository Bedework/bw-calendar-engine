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

import org.bedework.calfacade.filter.SfpTokenizer;
import org.bedework.util.jmx.ConfBase;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Utility to execute various operations specified by commands.
 *
 * <p>Takes one or more lines of input specifying the action. Lines starting
 * with "#" are treated as comments.
 *
 * Non-comment lines are:
 * <pre>
 * create category val
 * </pre>
 *
 * <p>Creates a category. val must be quoted if it contains non-work characters.
 *
 * <pre>
 * create collection (folder| calendar) parent-path name
 *            [filter=qfexpr] (category=cat)* [owner]
 * create collection alias parent-path name alias-target
 *            [filter=qfexpr] (category=cat)* [owner]
 * </pre>
 *
 * <p>Creates a folder or calendar collection under the given parent with the
 * given name and optional owner.
 *
 * <p>qfexpr is a quoted filter expression.
 *
 * <pre>
 * list categories val*
 * </pre>
 *
 * <p>List categories. If no val is supplied all categories are listed. Any
 * supplied values are matched against the categories. Any case insignificant
 * match results in the category being listed.
 *
 * <pre>
 *   move events [addcats] from to category*
 * </pre>
 *
 * <p>where from and to are possibly quoted paths, e.g. /public/Arts/Dance
 * and category* is 0 or more possibly quoted categories to set on the entities
 * events as they are moved.
 *
 * <p>If addcats is specified the events will have categories set derived from
 * the from path. e.g. if from="/public/Arts/Dance" then the event will have
 * categories "Arts" and "Dance" (which must exist)
 *
 * <pre>
 *   stop
 * </pre>
 *
 * <p>Terminate the utility
 *
 * @author douglm
 */
public class CmdUtil extends ConfBase<CmdUtilPropertiesImpl>
        implements CmdUtilMBean {
  /* Name of the property holding the location of the config data */
  private static final String confuriPname = "org.bedework.bwengine.confuri";

  private boolean echo = false;

  private final static String nm = "cmdutil";

  private final ProcessState pstate = new ProcessState();

  private final Map<String, CmdUtilHelper> processors = new HashMap<>();

  public CmdUtil() throws Throwable {
    super(getServiceName(nm));

    setConfigName(nm);

    setConfigPname(confuriPname);

    addProcessor(new ProcessAdd(pstate));
    addProcessor(new ProcessAuthUser(pstate));
    addProcessor(new ProcessCalsuite(pstate));
    addProcessor(new ProcessCreate(pstate));
    addProcessor(new ProcessDelete(pstate));
    addProcessor(new ProcessList(pstate));
    addProcessor(new ProcessMove(pstate));
    addProcessor(new ProcessRealias(pstate));
    addProcessor(new ProcessPrefs(pstate));
    addProcessor(new ProcessReindex(pstate));
    addProcessor(new ProcessSetstatus(pstate));
    addProcessor(new ProcessSetup(pstate));
    addProcessor(new ProcessUpdate(pstate));
    addProcessor(new ProcessUser(pstate));
  }
  
  private void addProcessor(final CmdUtilHelper proc) throws Throwable {
    final String cmd = proc.command();
    
    if (processors.get(cmd) != null) {
      throw new Exception("Processor already exists for command " + cmd);
    }
    
    processors.put(cmd, proc);
  }

  /**
   * @param name of the service
   * @return object name value for the mbean with this name
   */
  @SuppressWarnings("WeakerAccess")
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=" + name;
  }

  @Override
  public void setAccount(final String val) {
    getConfig().setAccount(val);
  }

  @Override
  public String getAccount() {
    return getConfig().getAccount();
  }

  @Override
  public void setSuperUser(final boolean val) {
    getConfig().setSuperUser(val);
  }

  @Override
  public boolean getSuperUser() {
    return getConfig().getSuperUser();
  }

  @Override
  public void setDataOut(final String val) {
    getConfig().setDataOut(val);
  }

  @Override
  public String getDataOut() {
    return getConfig().getDataOut();
  }

  @Override
  public String execfile(final String path) {
    return process(path);
  }

  @Override
  public String exec(final String cmd) {
    initPstate();
    pstate.clear();
    processLine(cmd);
    return pstate.toString();
  }

  @Override
  public String loadConfig() {
    return loadConfig(CmdUtilPropertiesImpl.class);
  }

  String process(final String infileName) {
    try {
      initPstate();
      pstate.clear();
      final InputStream is;

      is = new FileInputStream(infileName.trim());
      echo = true;

      final LineNumberReader lis = new LineNumberReader(new InputStreamReader(is));

      for (;;) {
        final String ln = lis.readLine();

        if (ln == null) {
          break;
        }

        if (echo) {
          info(ln);
        }

        if (ln.startsWith("#")) {
          continue;
        }

        processLine(ln);
      }
    } catch (final Throwable t) {
      t.printStackTrace();
      pstate.addError(t.getLocalizedMessage());
    }

    return pstate.toString();
  }

  private void initPstate() {
    if (pstate.getAccount() == null) {
      pstate.setAccount(getAccount());
      pstate.setSuperUser(getSuperUser());
    }
  }
  
  private void processLine(final String ln) {
    try {
      pstate.setTokenizer(new SfpTokenizer(new StringReader(ln)));

      final String wd = word();

      if (wd == null) {
        if (pstate.getTokenizer().atEof()) {
          // Blank line
          return;
        }

        pstate.addError("No cmd: " + ln);
        return;
      }

      if ("stop".equals(wd)) {
        return;
      }

      if ("clear".equals(wd)) {
        pstate.clear();
        return;
      }

      if ("help".equals(wd)) {
        final Set<String> cmdNames = new TreeSet<>(processors.keySet());
        
        for (final String cmdName: cmdNames) {
          pstate.addInfo(cmdName + ": " + processors.get(cmdName).description());
          pstate.addInfo("\n");
        }
        
        pstate.addInfo("Type\n   <cmdname> help\n for extra info\n");
        
        return;
      }

      final CmdUtilHelper cuh = processors.get(wd);
      if (cuh == null) {
        pstate.addError("Unknown cmd: " + ln + " searched for \"" + wd +"\"");
        return;
      }

      if (cuh.process()) {
        return;
      }

      pstate.addError("Failed cmd: " + ln);
    } catch (final Throwable t) {
      error(t);
      pstate.addError(t.getLocalizedMessage());
    }
  }

  private String word() throws Throwable {
    if (pstate.getTokenizer().testToken(StreamTokenizer.TT_WORD)) {
      return pstate.getTokenizer().sval;
    }

    return null;
  }
}
