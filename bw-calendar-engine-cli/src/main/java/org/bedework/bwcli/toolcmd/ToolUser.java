/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.toolcmd;

import org.bedework.bwcli.jmxcmd.JmxCmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:48
 */
public class ToolUser extends JmxCmd {
  public ToolUser() {
    super("tooluser", "account",
          "Set user for tools.");
  }

  public void doExecute() throws Throwable {
    final String account = cli.word(null);
    info(jcc.setCmdutilUser(account));
  }
}
