/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:45
 */
public class CmdIdxStats extends JmxCmd {
  public CmdIdxStats() {
    super("idxstats", "indexName", "Get the index stats");
  }

  public void doExecute() throws Throwable {
    final String indexName = cli.word("indexName");
    info(jcc.indexStats(indexName).toString());
  }
}
