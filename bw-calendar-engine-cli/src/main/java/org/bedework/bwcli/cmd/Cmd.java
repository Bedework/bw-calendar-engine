/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.cmd;

import org.bedework.bwcli.JolokiaConfigClient;
import org.bedework.util.jolokia.CommandInterpreter;
import org.bedework.util.jolokia.JolokiaCli;

import java.util.List;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:41
 */
public abstract class Cmd extends CommandInterpreter {
  protected JolokiaCli cli;
  protected JolokiaConfigClient jcc;

  protected Cmd(final String cmdName,
                final String cmdPars,
                final String cmdDescription) {
    super(cmdName, cmdPars, cmdDescription);
  }

  public void execute(final JolokiaCli cli) {
    this.cli = cli;
    try {
      jcc = (JolokiaConfigClient)cli.getClient();

      doExecute();
    } catch (final Throwable t) {
      cli.error(t);
    }
  }

  public abstract void doExecute() throws Throwable;

  protected void multiLine(final List<String> resp) {
    if (resp == null) {
      info("Null response");
      return;
    }

    for (final String s: resp) {
      info(s);
    }
  }

  public void info(final String msg) {
    cli.info(msg);
  }
}
