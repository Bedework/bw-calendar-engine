/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:45
 */
public class CmdSync extends JmxCmd {
  public CmdSync() {
    super("sync", "[get] attrname|cmd [val]",
          "Interact with sync engine. Example commands are\n," +
                  "  sync get HibernateDialect\n" +
                  "  sync HibernateDialect \"val\"\n" +
                  "attrname may be:" +
                  "   HibernateDialect Privkeys, Pubkeys, TimezonesURI\n" +
                  "   SchemaOutFile\n" +
                  "attrname may be:" +
                  "   schema, start, stop");
  }

  public void doExecute() throws Throwable {
    final String wd = cli.word("schemaWd");

    boolean get = "get".equals(wd);

    final String attrCmd;

    if (get) {
      attrCmd = cli.word("attrname");
    } else {
      attrCmd = wd;
    }

    if (attrCmd == null) {
      info("Need an attribute or cmd");
      return;
    }

    if ("schema".equals(attrCmd)) {
      multiLine(jcc.syncSchema());
      return;
    }

    if ("start".equals(attrCmd)) {
      jcc.syncStart();
      return;
    }

    if ("stop".equals(attrCmd)) {
      jcc.syncStop();
      return;
    }

    if (get) {
      info(jcc.getSyncAttr(attrCmd));
    } else {
      jcc.setSyncAttr(attrCmd, cli.string(null));
    }
  }
}
