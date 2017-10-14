/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:45
 */
public class CmdReindex extends JmxCmd {
  public CmdReindex() {
    super("reindex", "indexName",
          "Reindex current index into given index");
  }

  public void doExecute() throws Throwable {
    final String indexName = cli.word("indexName");
    info(jcc.reindex(indexName));
  }
}
