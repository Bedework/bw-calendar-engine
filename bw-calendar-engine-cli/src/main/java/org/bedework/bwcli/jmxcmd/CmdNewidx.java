/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:43
 */
public class CmdNewidx extends JmxCmd {
  public CmdNewidx() {
    super("newidx", null, "Create new index");
  }

  public void doExecute() throws Throwable {
    info(jcc.newIndexes());
  }
}
