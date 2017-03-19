/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.bwcmd;

import org.bedework.bwcli.BwCli;
import org.bedework.util.cli.Cli;
import org.bedework.util.cli.CommandInterpreter;

import java.util.List;

/**
 * User: mike
 * Date: 5/31/16
 * Time: 15:12
 */
public abstract class ApiCommand extends CommandInterpreter {
  private BwCli cli;
  
  public final static int exitNoApi = 100;

  public ApiCommand(final String cmdName,
                    final String cmdPars,
                    final String cmdDescription) {
    super(cmdName, cmdPars, cmdDescription);
  }

  public abstract void doExecute() throws Throwable;

  protected BwCli getBwCli() {
    return cli;
  }
  
  protected void setCli(final Cli val) {
    cli = (BwCli)val;
  }

  public HttpClient getCl() throws Throwable {
    return cli.getWebClient().getCl();
  }

  public void execute(final Cli cli) {
    setCli(cli);
    try {
      doExecute();
    } catch (final Throwable t) {
      //cli.setExitStatus(exitException);
      cli.error(t);
    }
  }

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
