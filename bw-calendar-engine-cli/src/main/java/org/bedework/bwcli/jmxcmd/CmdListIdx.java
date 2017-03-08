/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:45
 */
public class CmdListIdx extends JmxCmd {
  public CmdListIdx() {
    super("listidx", null, "List the indexes");
  }

  public void doExecute() throws Throwable {
    info(jcc.listIndexes());
  }
}
