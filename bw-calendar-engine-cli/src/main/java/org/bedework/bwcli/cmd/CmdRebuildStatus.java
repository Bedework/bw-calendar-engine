/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.cmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:46
 */
public class CmdRebuildStatus extends Cmd {
  public CmdRebuildStatus() {
    super("rebuildstatus", null, "Show status of index rebuild");
  }

  public void doExecute() throws Throwable {
    multiLine(jcc.rebuildIdxStatus());
  }
}
