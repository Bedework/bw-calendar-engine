/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.calfacade.security;

import org.bedework.util.security.pki.PKITools;

import org.apache.log4j.Logger;

/**
 * @author douglm
 *
 */
public class GenKeys implements GenKeysMBean {
  private transient Logger log;

  private String privKeyFileName;
  private String publicKeyFileName;

  private PKITools pki;

  /** Following is some random text which we encode and decode to ensure
   *  generated keys work
   */
  String testText =
    "A variable of array type holds a reference to an object. ";

  public String getName() {
    /* This apparently must be the same as the name attribute in the
     * jboss service definition
     */
    return "org.bedework:service=BwSysMonitor";
  }

  /* an example say's we need this  - we'll see
  public MBeanInfo getMBeanInfo() throws Exception {
    InitialContext ic = new InitialContext();
    RMIAdaptor server = (RMIAdaptor) ic.lookup("jmx/rmi/RMIAdaptor");

    ObjectName name = new ObjectName(MBEAN_OBJ_NAME);

    // Get the MBeanInfo for this MBean
    MBeanInfo info = server.getMBeanInfo(name);
    return info;
  }
  */

  public void setPrivKeyFileName(final String val) {
    privKeyFileName = val;
  }

  public String getPrivKeyFileName() {
    return privKeyFileName;
  }

  public void setPublicKeyFileName(final String val) {
    publicKeyFileName = val;
  }

  public String getPublicKeyFileName() {
    return publicKeyFileName;
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  public Msg genKeys() {
    Msg infoLines = new Msg();

    try {
      pki = new PKITools(true);

      if (privKeyFileName == null) {
        infoLines.add("Must provide a -privkey <file> parameter");
        return infoLines;
      }

      if (publicKeyFileName == null) {
        infoLines.add("Must provide a -pubkey <file> parameter");
        return infoLines;
      }

      PKITools.RSAKeys keys = pki.genRSAKeysIntoFiles(privKeyFileName,
                                                      publicKeyFileName,
                                                      true);
      if (keys == null) {
        infoLines.add("Generation of keys failed");
        return infoLines;
      }

      // Now try the keys on the test text.

      int numKeys = pki.countKeys(privKeyFileName);

      //if (debug) {
      //  infoLines.add("Number of keys: " + numKeys);
      //}

      infoLines.add("test with---->" + testText);
      String etext = pki.encryptWithKeyFile(publicKeyFileName, testText, numKeys - 1);
      infoLines.add("encrypts to-->" + etext);
      String detext = pki.decryptWithKeyFile(privKeyFileName, etext, numKeys - 1);
      infoLines.add("decrypts to-->" + detext);

      if (!testText.equals(detext)) {
        infoLines.add("Validity check failed: encrypt/decrypt failure");
      } else {
        infoLines.add("");
        infoLines.add("Validity check succeeded");
      }
    } catch (Throwable t) {
      error(t);
      infoLines.add("Exception - check logs: " + t.getMessage());
    }

    return infoLines;
  }

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  public void create() {
  }

  public void start() {
  }

  public void stop() {
  }

  public boolean isStarted() {
    return false;
  }

  public void destroy() {
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(t);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}
