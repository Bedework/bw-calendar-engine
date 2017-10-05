/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli;

import org.bedework.bwcli.bwcmd.CmdAdminGroups;
import org.bedework.bwcli.bwcmd.HttpClient;
import org.bedework.bwcli.jmxcmd.CmdCalSchema;
import org.bedework.bwcli.jmxcmd.CmdListIdx;
import org.bedework.bwcli.jmxcmd.CmdPurgeIdx;
import org.bedework.bwcli.jmxcmd.CmdRebuildIdx;
import org.bedework.bwcli.jmxcmd.CmdRebuildStatus;
import org.bedework.bwcli.jmxcmd.CmdRestoreCalData;
import org.bedework.bwcli.toolcmd.ToolCmd;
import org.bedework.bwcli.toolcmd.ToolSource;
import org.bedework.bwcli.toolcmd.ToolUser;
import org.bedework.calfacade.responses.AdminGroupsResponse;
import org.bedework.util.args.Args;
import org.bedework.util.jolokia.JolokiaCli;
import org.bedework.util.jolokia.JolokiaClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;

/**
 * User: mike
 * Date: 5/5/15
 * Time: 4:26 PM
 */
public class BwCli extends JolokiaCli {
  private WebClient webClient;
  private final String url;
  private final String id;
  private final String pw;
  
  // Last response
  private AdminGroupsResponse adgrs;
  
  public BwCli(final String url,
               final String jmxUrl,
               final String id,
               final String pw,
               final boolean debug) throws Throwable {
    super(jmxUrl, debug);
    
    this.url = url;
    this.id = id;
    this.pw = pw;

    register(new CmdAdminGroups());
    
    // jmx
    register(new CmdCalSchema());
    register(new CmdListIdx());
    register(new CmdPurgeIdx());
    register(new CmdRebuildIdx());
    register(new CmdRebuildStatus());
    register(new CmdRestoreCalData());

    register(new ToolCmd());
    register(new ToolSource());
    register(new ToolUser());
  }

  public JolokiaClient makeClient(final String uri) throws Throwable {
    return new JolokiaConfigClient(uri, id, pw);
  }

  public WebClient getWebClient() {
    if (webClient == null) {
      webClient = new WebClient(url);
    }
    
    return webClient;
  }

  public HttpClient getCl() throws Throwable {
    return getWebClient().getCl();
  }
  
  public void setAdgrs(final AdminGroupsResponse val) {
    adgrs = val;
  }
  
  public AdminGroupsResponse getAdgrs() {
    return adgrs;
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
    String id = null;
    String pw = null;
    String cmd = null;
    String jmxUrl = null;
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

        if (pargs.ifMatch("jmxUrl")) {
          jmxUrl = pargs.next();
          continue;
        }

        if (pargs.ifMatch("-id")) {
          id = pargs.next();
          continue;
        }

        if (pargs.ifMatch("-pw")) {
          pw = pargs.next();
          continue;
        }

        if (pargs.ifMatch("-cmds")) {
          cmd = pargs.next();
          continue;
        }

        usage("Illegal argument: " +
                      pargs.current());
        return;
      }

      final BwCli jc = new BwCli(url, jmxUrl, id, pw, debug);
      
      if (cmd != null) {
        jc.setSingleCmd("sou \"" + cmd + "\"");
      }

      jc.processCmds();
    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  /**
   * Client to interact with the bedework web interfaces
   */
  public static class WebClient {
    private final String url;
    
    private HttpClient cl;
    private final ObjectMapper om;

    WebClient(final String url) {
      this.url = url;
      om = new JsonMapper();
    }

    public HttpClient getCl() throws Throwable {
      if (cl != null) {
        return cl;
      }

      cl = new HttpClient(new URI(url));
      
      return cl;
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
