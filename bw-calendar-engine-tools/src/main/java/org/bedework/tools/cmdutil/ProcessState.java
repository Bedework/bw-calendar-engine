/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.tools.cmdutil;

import org.bedework.calfacade.filter.SfpTokenizer;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.util.misc.Logged;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;

/**
 * User: mike
 * Date: 3/7/17
 * Time: 11:10
 */
public class ProcessState extends Logged {
  private String account;

  private boolean superUser;

  private BwCalSuite calsuite;

  private CalSvcI svci;

  private SfpTokenizer tokenizer;

  private final Collection<String> errors = new ArrayList<>();

  private final Collection<String> info = new ArrayList<>();

  public void setAccount(final String val) {
    account = val;
  }

  public String getAccount() {
    return account;
  }

  public void setCalsuite(final BwCalSuite val) {
    calsuite = val;
  }

  public BwCalSuite getCalsuite() {
    return calsuite;
  }

  public void setSuperUser(final boolean val) {
    superUser = val;
  }

  public boolean getSuperUser() {
    return superUser;
  }

  public void clear() {
    errors.clear();
    info.clear();
  }
  
  public CalSvcI getSvci() {
    if ((svci == null) && !initSvci()) {
      return null;
    }
    
    return svci;
  }

  public void setSvci(final CalSvcI val) {
    svci = val;
  }

  public SfpTokenizer getTokenizer() {
    return tokenizer;
  }

  public void setTokenizer(final SfpTokenizer val) {
    tokenizer = val;
  }

  public Collection<String> getErrors() {
    return errors;
  }

  public void addError(final String msg) {
    error(msg);
    errors.add(msg);
  }

  public Collection<String> getInfo() {
    return info;
  }

  public void addInfo(final String msg) {
    info(msg);
    info.add(msg);
  }

  public void closeSvci() {
    try {
      if (getSvci() != null) {
        getSvci().close();
      }
    } catch (final Throwable t) {
      t.printStackTrace();
    } finally {
      setSvci(null);
    }
  }

  public String toString() {
    String res = "";
    
    if (!Util.isEmpty(errors)) {
      res = "Errors: \n";
      
      final StringBuilder sb = new StringBuilder();
      for (final String s : errors) {
        sb.append(s);
        sb.append('\n');
      }
      res += sb.toString();
    }

    if (!Util.isEmpty(info)) {
      res += "\n";

      final StringBuilder sb = new StringBuilder();
      for (final String s : info) {
        sb.append(s);
        sb.append('\n');
      }
      res += sb.toString();
    }
    
    return res;
  }

  private boolean initSvci() {
    if (getAccount() == null) {
      addError("No current user");
      return false;
    }
    
    final CalSvcIPars pars = new CalSvcIPars("tools",
                                             getAccount(),
                                             getAccount(),
                                             null,   // calsuite
                                             true,   // publicAdmin
                                             getSuperUser(),   // superUser,
                                             true,   // service
                                             false,// publicSubmission
                                             true,// adminCanEditAllPublicCategories
                                             true,// adminCanEditAllPublicLocations
                                             true,// adminCanEditAllPublicSponsors
                                             false, // sessionless
                                             true); // system
    try {
      setSvci(new CalSvcFactoryDefault().getSvc(pars));
      return true;
    } catch (final Throwable t) {
      error(t);
      addError(t.getLocalizedMessage());
      return false;
    }
  }
}
