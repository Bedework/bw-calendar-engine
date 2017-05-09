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
public class ToolCmd extends JmxCmd {
  public ToolCmd() {
    super("tool", "cmd",
          "Execute the command.");
  }

  public void doExecute() throws Throwable {
    final String line = cli.getCurline();
    
    
    final int pos = line.indexOf("tool");
    
    info(jcc.execCmdutilCmd(line.substring(pos + 4)));
  }
}
