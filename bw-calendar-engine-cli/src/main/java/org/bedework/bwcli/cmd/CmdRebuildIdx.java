/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.cmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:45
 */
public class CmdRebuildIdx extends Cmd {
  public CmdRebuildIdx() {
    super("rebuildidx", null, "Rebuild the indexes");
  }

  public void doExecute() throws Throwable {
    info(jcc.rebuildIndexes());
  }
}
