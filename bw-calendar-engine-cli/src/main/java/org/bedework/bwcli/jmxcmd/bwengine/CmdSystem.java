/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.bwcli.jmxcmd.bwengine;

import org.bedework.bwcli.jmxcmd.JmxCmd;

/**
 * User: mike
 * Date: 11/11/16
 * Time: 21:46
 */
public class CmdSystem extends JmxCmd {
  public CmdSystem() {
    super("engine.system", null, "Set or display system settings");
  }

  public void doExecute() throws Throwable {
    final String wd = cli.word(null);
    
    if (wd == null) {
      info("Full display not implemented");
      return;
    }
    
    switch (wd) {
      case "tzid": {
        final String tzid = cli.string(null);
        
        if (tzid != null) {
          jcc.setSystemTzid(tzid);
        }
        info(jcc.getSystemTzid());
        break;
      }
      
      case "autokill": {
        final Double num = cli.number(null);

        if (num != null) {
          jcc.setAutoKillMinutes(num.intValue());
        }
        info(String.valueOf(jcc.getAutoKillMinutes()));
        break;
      }
    }
  }
}
