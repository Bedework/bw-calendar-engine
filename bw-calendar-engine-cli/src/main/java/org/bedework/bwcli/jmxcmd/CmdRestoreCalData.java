/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:48
 */
public class CmdRestoreCalData extends JmxCmd {
  public CmdRestoreCalData() {
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
