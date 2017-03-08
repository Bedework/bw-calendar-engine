/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd;

import org.bedework.bwcli.JolokiaConfigClient;
import org.bedework.util.cli.Cli;
import org.bedework.util.cli.CommandInterpreter;
import org.bedework.util.jolokia.JolokiaCli;

import java.util.List;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:41
 */
public abstract class JmxCmd extends CommandInterpreter {
  protected JolokiaCli cli;
  protected JolokiaConfigClient jcc;

  protected JmxCmd(final String cmdName,
                   final String cmdPars,
                   final String cmdDescription) {
    super(cmdName, cmdPars, cmdDescription);
  }

  public void execute(final Cli cli) {
    this.cli = (JolokiaCli)cli;
    try {
      jcc = (JolokiaConfigClient)this.cli.getClient();

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
