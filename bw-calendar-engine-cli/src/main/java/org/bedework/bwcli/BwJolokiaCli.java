/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli;

import org.bedework.bwcli.cmd.CmdCalSchema;
import org.bedework.bwcli.cmd.CmdListIdx;
import org.bedework.bwcli.cmd.CmdPurgeIdx;
import org.bedework.bwcli.cmd.CmdRebuildIdx;
import org.bedework.bwcli.cmd.CmdRebuildStatus;
import org.bedework.bwcli.cmd.CmdRestoreCalData;
import org.bedework.util.args.Args;
import org.bedework.util.jolokia.JolokiaCli;
import org.bedework.util.jolokia.JolokiaClient;

/**
 * User: mike
 * Date: 5/5/15
 * Time: 4:26 PM
 */
public class BwJolokiaCli extends JolokiaCli {
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
