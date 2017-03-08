/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:47
 */
public class CmdCalSchema extends JmxCmd {
  public CmdCalSchema() {
    super("calschema", null, "Create the calendar core schema");
  }

  public void doExecute() throws Throwable {
    multiLine(jcc.coreSchema());
  }
}
