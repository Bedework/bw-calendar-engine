/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:45
 */
public class CmdMakeIdxProd extends JmxCmd {
  public CmdMakeIdxProd() {
    super("makeidxprod", "indexName",
          "Move the prod alias to the given index");
  }

  public void doExecute() throws Throwable {
    final String indexName = cli.word("indexName");
    info(jcc.makeIdxProd(indexName));
  }
}
