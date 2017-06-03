/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.toolcmd;

import org.bedework.bwcli.jmxcmd.JmxCmd;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:48
 */
public class ToolSource extends JmxCmd {
  public ToolSource() {
    super("toolsou", "path",
          "Execute the commands in a file.");
  }

  public void doExecute() throws Throwable {
    try {
      final InputStream is;
      
      final String path = cli.string("path");
      
      if (path == null) {
        info("Require a path");
        return;
      }

      is = new FileInputStream(path.trim());

      final LineNumberReader lis = new LineNumberReader(new InputStreamReader(is));

      for (;;) {
        final String ln = lis.readLine();

        if (ln == null) {
          break;
        }

        info(ln);

        if (ln.startsWith("#")) {
          continue;
        }

        info(jcc.execCmdutilCmd(ln.trim()));
      }
    } catch (final Throwable t) {
      t.printStackTrace();
      info(t.getLocalizedMessage());
    }
  }
}
