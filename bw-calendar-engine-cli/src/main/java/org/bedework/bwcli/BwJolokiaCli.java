/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli;

import org.bedework.util.args.Args;
import org.bedework.util.jolokia.CommandInterpreter;
import org.bedework.util.jolokia.JolokiaCli;
import org.bedework.util.jolokia.JolokiaClient;

import java.util.List;

/**
 * User: mike
 * Date: 5/5/15
 * Time: 4:26 PM
 */
public class BwJolokiaCli extends JolokiaCli {
  private abstract static class Cmd extends CommandInterpreter {
    protected JolokiaCli cli;
    protected JolokiaConfigClient jcc;

    Cmd(final String cmdName,
        final String cmdPars,
        final String cmdDescription) {
      super(cmdName, cmdPars, cmdDescription);
    }

    public void execute(final JolokiaCli cli) {
      this.cli = cli;
      try {
        jcc = (JolokiaConfigClient)cli.getClient();

        doExecute();
      } catch (final Throwable t) {
        cli.error(t);
      }
    }

    public abstract void doExecute() throws Throwable;

    protected void multiLine(final List<String> resp) {
      if (resp == null) {
        info("Null response");
        return;
      }

      for (final String s: resp) {
        info(s);
      }
    }

    public void info(final String msg) {
      cli.info(msg);
    }
  }

  public BwJolokiaCli(final String url,
                      final boolean debug) throws Throwable {
    super(url, debug);

    register(new CmdCalSchema());
    register(new CmdListIdx());
    register(new CmdPurgeIdx());
    register(new CmdRebuildIdx());
    register(new CmdRebuildStatus());
    register(new CmdRestoreCalData());
  }

  public JolokiaClient makeClient(final String uri) throws Throwable {
    return new JolokiaConfigClient(uri);
  }

  private static class CmdPurgeIdx extends Cmd {
    CmdPurgeIdx() {
      super("purgeidx", null, "Purge the old indexes");
    }

    public void doExecute() throws Throwable {
      info(jcc.purgeIndexes());
    }
  }

  private static class CmdListIdx extends Cmd {
    CmdListIdx() {
      super("listidx", null, "List the indexes");
    }

    public void doExecute() throws Throwable {
      info(jcc.listIndexes());
    }
  }

  private static class CmdRebuildIdx extends Cmd {
    CmdRebuildIdx() {
      super("rebuildidx", null, "Rebuild the indexes");
    }

    public void doExecute() throws Throwable {
      info(jcc.rebuildIndexes());
    }
  }

  private static class CmdRebuildStatus extends Cmd {
    CmdRebuildStatus() {
      super("rebuildstatus", null, "Show status of index rebuild");
    }

    public void doExecute() throws Throwable {
      multiLine(jcc.rebuildIdxStatus());
    }
  }

  private static class CmdCalSchema extends Cmd {
    CmdCalSchema() {
      super("calschema", null, "Create the calendar core schema");
    }

    public void doExecute() throws Throwable {
      multiLine(jcc.coreSchema());
    }
  }

  private static class CmdRestoreCalData extends Cmd {
    CmdRestoreCalData() {
      super("restoreCal", "[\"path\"]",
            "Restore the calendar data from the default or supplied " +
                    "data path. If a path is supplied it must be " +
                    "reachable by the server and will be set as the " +
                    "input data path.");
    }

    public void doExecute() throws Throwable {
      final String path = cli.string(null);
      multiLine(jcc.restoreCalData(path));
    }
  }

  /**
   * <p>Arguments<ul>
   *     <li>url: the url of the jolokia service</li>
   * </ul>
   * </p>
   *
   * @param args program arguments.
   */
  public static void main(final String[] args) {
    String url = null;
    String configUrl = null;
    boolean debug = false;

    try {
      final Args pargs = new Args(args);

      while (pargs.more()) {
        if (pargs.ifMatch("debug")) {
          debug = true;
          continue;
        }

        if (pargs.ifMatch("url")) {
          url = pargs.next();
          continue;
        }

        if (pargs.ifMatch("configUrl")) {
          configUrl = pargs.next();
          continue;
        }

        usage("Illegal argument: " +
                      pargs.current());
        return;
      }

      final BwJolokiaCli jc = new BwJolokiaCli(url, debug);

      jc.processCmds();
    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  private static void usage(final String msg) {
    if (msg != null) {
      System.err.println();
      System.err.println(msg);
    }

    System.err.println();
    System.err.println("Optional arguments:");
    System.err.println("   url <url>         Url of the jolokia jmx service");
    System.err.println("   debug             To enable debug traces");
  }

}
