/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:43
 */
public class CmdPurgeIdx extends JmxCmd {
  public CmdPurgeIdx() {
    super("purgeidx", null, "Purge the old indexes");
  }

  public void doExecute() throws Throwable {
    info(jcc.purgeIndexes());
  }
}
