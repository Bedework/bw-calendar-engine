/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.cmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:47
 */
public class CmdCalSchema extends Cmd {
  public CmdCalSchema() {
    super("calschema", null, "Create the calendar core schema");
  }

  public void doExecute() throws Throwable {
    multiLine(jcc.coreSchema());
  }
}
