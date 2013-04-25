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
package org.bedework.calsvc.scheduling.hosts;

import org.bedework.calfacade.exc.CalFacadeException;

import edu.rpi.cmt.config.ConfigurationStore;
import edu.rpi.cmt.config.ConfigurationType;
import edu.rpi.cmt.jmx.ConfBase;
import edu.rpi.sss.util.Util;

import org.apache.james.jdkim.DKIMVerifier;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.management.ObjectName;

/**
 * @author douglm
 *
 */
public class BwHosts extends ConfBase implements BwHostsMBean {
  /* Name of the property holding the location of the config data */
  private static final String datauriPname = "org.bedework.hosts.datauri";

  private static List<HostInfo> hostInfos = new ArrayList<HostInfo>();

  private static List<BwHost> hostConfigs = new ArrayList<BwHost>();

  /**
   */
  public BwHosts() {
    super("org.bedework.ischedule:service=Conf");
    setConfigPname(datauriPname);
  }

  @Override
  public ConfigurationType getConfigObject() {
    return null;
  }

  /* ================================= Operations =========================== */

  @Override
  public String getHost(final String hostname) {
    HostInfo host = get(hostname);

    if (host != null) {
      return host.toString();
    }

    return hostname + " not found";
  }

  @Override
  public String addIscheduleHost(final String hostname,
                                 final int port,
                                 final boolean secure,
                                 final String url,
                                 final String principal,
                                 final String pw) {
    String result = null;

    try {
      BwHost bh = find(hostname);

      if (bh == null) {
        HostInfo hi = new HostInfo();

        hi.setHostname(hostname);
        hi.setPort(port);
        hi.setSecure(secure);
        hi.setIScheduleUrl(url);
        hi.setISchedulePrincipal(principal);
        hi.setIScheduleCredentials(pw);
        hi.setSupportsISchedule(true);

        bh = new BwHost(getStore().getStore(hostname),
                        getServiceName(),
                        hi);

        bh.saveConfig();
        register("hostconf", hostname, bh);
        hostConfigs.add(bh);

        return "added OK";
      }

      HostInfo hi = bh.getInfo();

      if (hi.getSupportsISchedule()) {
        return "already supports ischedule";
      }

      hi.setPort(port);
      hi.setSecure(secure);
      hi.setIScheduleUrl(url);
      hi.setISchedulePrincipal(principal);
      hi.setIScheduleCredentials(pw);
      hi.setSupportsISchedule(true);

      bh.saveConfig();

      result = "updated OK";
    } catch (Throwable t) {
      result = t.getMessage();
      error(t);
    }

    return result;
  }

  @Override
  public List<String> listHosts() {
    List<String> hosts = new ArrayList<String>();

    if (Util.isEmpty(hostInfos)) {
      hosts.add("No hosts defined");

      return hosts;
    }

    for (HostInfo hi: hostInfos) {
      hosts.add(hi.getHostname());
    }

    return hosts;
  }

  @Override
  public String loadConfigs() {
    try {
      /* Load up the end-point configs */

      ConfigurationStore cfs = getStore();

      hostInfos = new ArrayList<HostInfo>();

      Collection<String> hostNames = cfs.getConfigs();

      if (hostNames.isEmpty()) {
        return "No hosts";
      }

      hostConfigs = new ArrayList<BwHost>();

      for (String hn: hostNames) {
        HostInfo hi = getHostInfo(cfs, hn);

        if (hi == null) {
          continue;
        }

        hostInfos.add(hi);

        /* Create and register the mbean */
        ObjectName objectName = createObjectName("hostconf", hn);
        BwHost bh = new BwHost(getStore().getStore(hn),
                               objectName.toString(),
                               hi);

        bh.saveConfig(); // Just to ensure we have it for next time

        register("hostconf", hn, bh);
        hostConfigs.add(bh);
      }

      return "OK";
    } catch (Throwable t) {
      error("Failed to start management context");
      error(t);
      return "failed";
    }
  }

  /**
   * @param name
   * @return corresponding host info or null
   */
  public HostInfo get(final String name) {
    return getHostInfo(name);
  }

  /**
   * @param name
   * @return BwHost object for name
   */
  public BwHost find(final String name) {
    for (BwHost bh: hostConfigs) {
      if (bh.getInfo().getHostname().equals(name)) {
        return bh;
      }
    }

    return null;
  }

  /** Add a host.
   *
   * @param val
   * @throws CalFacadeException
   */
  public void add(final HostInfo val) throws CalFacadeException {

  }

  /** Update a host.
   *
   * @param val
   * @throws CalFacadeException
   */
  public void update(final HostInfo val) throws CalFacadeException {

  }

  /** Delete a host.
   *
   * @param val
   * @throws CalFacadeException
   */
  public void delete(final HostInfo val) throws CalFacadeException {

  }

  /** Return host information for the given parameter which might be a url or
   * an email address. This information may be obtained through discovery of the
   * service.
   *
   * <p>The key to the object is the calculated closest domain of the given
   * recipient.
   *
   * @param val - a url or an email address
   * @return HostInfo - null if no service available.
   * @throws CalFacadeException
   */
  public static HostInfo getHostForRecipient(final String val) throws CalFacadeException {
    try {
      URI uri = new URI(val);

      String scheme = uri.getScheme();
      String domain = null;

      if ((scheme == null) || ("mailto".equals(scheme.toLowerCase()))) {
        if (val.indexOf("@") > 0) {
          domain = val.substring(val.indexOf("@") + 1);
        }
      } else {
        domain = uri.getHost();
      }

      if (domain == null) {
        throw new CalFacadeException(CalFacadeException.badCalendarUserAddr);
      }

      //  Don't iuse db at all return findClosest(domain);
      HostInfo hi = getHostInfo(domain);

      if (hi != null) {
        return hi;
      }

      hi = new HostInfo();
      hi.setHostname(domain);
      hi.setSupportsISchedule(true);

      return hi;
    } catch (URISyntaxException use) {
      throw new CalFacadeException(CalFacadeException.badCalendarUserAddr);
    }
  }

  /**
   * @param name
   * @return host info or null
   */
  public static HostInfo getHostInfo(final String name) {
    for (HostInfo hi: hostInfos) {
      if (hi.getHostname().equals(name)) {
        return hi;
      }
    }

    return null;
  }

  /** Should be called by BwHost instances on key update
   *
   */
  public static void refreshStoredKeys() {
    for (BwHost bwh: hostConfigs) {
      HostInfo hi = bwh.getInfo();

      List<String> keys = hi.getDkimPublicKeys();

      if (Util.isEmpty(keys)) {
        continue;
      }

      for (String s: keys) {
        String[] selKey = s.split("=");

        DKIMVerifier.addStoredKey(hi.getHostname(), selKey[0], selKey[1]);
      }
    }
  }

  /**
   * @return current state of config
   */
  private synchronized HostInfo getHostInfo(final ConfigurationStore cfs,
                                            final String configName) {
    try {
      /* Try to load it */

      ConfigurationType config = cfs.getConfig(configName);

      if (config == null) {
        return null;
      }

      HostInfo cfg =
          (HostInfo)makeObject(HostInfo.class.getCanonicalName());

      cfg.setConfig(config);

      return cfg;
    } catch (Throwable t) {
      error(t);
      return null;
    }
  }
}
