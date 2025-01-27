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

import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.util.config.ConfigurationStore;
import org.bedework.util.jmx.ConfBase;
import org.bedework.util.misc.Util;

import org.apache.james.jdkim.api.JDKIM;

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
  /* Name of the directory holding the config data */
  private static final String confDirName = "hosts";

  private static List<HostInfo> hostInfos = new ArrayList<>();

  private static List<BwHost> hostConfigs = new ArrayList<>();

  private static JDKIM jdkim;

  /**
   */
  public BwHosts() {
    super("org.bedework.ischedule:service=Hosts",
          confDirName,
          "Hosts");
  }

  @Override
  public String loadConfig() {
    return null;
  }

  /* ================================= Operations =========================== */

  @Override
  public String getHost(final String hostname) {
    final HostInfo host = get(hostname);

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
    String result;

    try {
      BwHost bh = find(hostname);

      if (bh == null) {
        final HostInfo hi = new HostInfo();

        hi.setHostname(hostname);
        hi.setPort(port);
        hi.setSecure(secure);
        hi.setIScheduleUrl(url);
        hi.setISchedulePrincipal(principal);
        hi.setIScheduleCredentials(pw);
        hi.setSupportsISchedule(true);

        bh = new BwHost(getStore(),
                        getServiceName(),
                        hi);

        bh.saveConfig();
        register("hostconf", hostname, bh);
        hostConfigs.add(bh);

        return "added OK";
      }

      final HostInfo hi = bh.getConfig();

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
    } catch (final Throwable t) {
      result = t.getMessage();
      error(t);
    }

    return result;
  }

  @Override
  public List<String> listHosts() {
    final List<String> hosts = new ArrayList<>();

    if (Util.isEmpty(hostInfos)) {
      hosts.add("No hosts defined");

      return hosts;
    }

    for (final HostInfo hi: hostInfos) {
      hosts.add(hi.getHostname());
    }

    return hosts;
  }

  @Override
  public String loadConfigs() {
    try {
      /* Load up the end-point configs */

      final ConfigurationStore cfs = getStore();

      hostInfos = new ArrayList<>();

      final Collection<String> hostNames = cfs.getConfigs();

      if (hostNames.isEmpty()) {
        return "No hosts";
      }

      hostConfigs = new ArrayList<>();

      for (final String hn: hostNames) {
        final HostInfo hi = getHostInfo(cfs, hn);

        if (hi == null) {
          continue;
        }

        hostInfos.add(hi);

        /* Create and register the mbean */
        final ObjectName objectName = createObjectName("hostconf", hn);
        final BwHost bh = new BwHost(getStore(),
                                     objectName.toString(),
                                     hi);

        register("hostconf", hn, bh);
        hostConfigs.add(bh);
      }

      return "OK";
    } catch (final Throwable t) {
      error("Failed to start management context");
      error(t);
      return "failed";
    }
  }

  /**
   * @param name of host
   * @return corresponding host info or null
   */
  public HostInfo get(final String name) {
    return getHostInfo(name);
  }

  /**
   * @param name of host
   * @return BwHost object for name
   */
  public BwHost find(final String name) {
    for (final BwHost bh: hostConfigs) {
      if (bh.getConfig().getHostname().equals(name)) {
        return bh;
      }
    }

    return null;
  }

  /** Add a host.
   *
   * @param val host info
   */
  public void add(final HostInfo val) {

  }

  /** Update a host.
   *
   * @param val host info
   */
  public void update(final HostInfo val) {

  }

  /** Delete a host.
   *
   * @param val host info
   */
  public void delete(final HostInfo val) {

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
   */
  public static HostInfo getHostForRecipient(final String val) {
    try {
      final URI uri = new URI(val);

      final String scheme = uri.getScheme();
      String domain = null;

      if ((scheme == null) || ("mailto".equals(scheme.toLowerCase()))) {
        if (val.indexOf("@") > 0) {
          domain = val.substring(val.indexOf("@") + 1);
        }
      } else {
        domain = uri.getHost();
      }

      if (domain == null) {
        throw new BedeworkException(CalFacadeErrorCode.badCalendarUserAddr);
      }

      //  Don't iuse db at all return findClosest(domain);
      /*
      HostInfo hi = getHostInfo(domain);

      if (hi != null) {
        return hi;
      }

      hi = new HostInfo();
      hi.setHostname(domain);
      hi.setSupportsISchedule(true);
      hi.setIScheduleUrl(domain);

      return hi;
       */
      return getHostInfo(domain);
    } catch (final URISyntaxException use) {
      throw new BedeworkException(CalFacadeErrorCode.badCalendarUserAddr);
    }
  }

  /**
   * @param name of host
   * @return host info or null
   */
  public static HostInfo getHostInfo(final String name) {
    for (final HostInfo hi: hostInfos) {
      if (hi.getHostname().equals(name)) {
        return hi;
      }
    }

    return null;
  }

  private static final String defaultJDKIMClass = "org.apache.james.jdkim.api.JDKIM";

  public static JDKIM getJDKIM() {
    /* should determine the class from configs and load it.
     */
    if (jdkim == null) {
      jdkim = (JDKIM)loadInstance(
              Thread.currentThread().getContextClassLoader(),
              defaultJDKIMClass,
              JDKIM.class);
    }

    return jdkim;
  }

  /** Should be called by BwHost instances on key update
   *
   */
  public static void refreshStoredKeys() {
    for (final BwHost bwh: hostConfigs) {
      final HostInfo hi = bwh.getConfig();

      final List<String> keys = hi.getDkimPublicKeys();

      if (Util.isEmpty(keys)) {
        continue;
      }

      for (final String s: keys) {
        final String[] selKey = s.split("=");

        getJDKIM().addDKIMVerifierStoredKey(hi.getHostname(),
                                            selKey[0],
                                            selKey[1]);
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

      return (HostInfo)cfs.getConfig(configName);
    } catch (final Throwable t) {
      error(t);
      return null;
    }
  }

  private static Object loadInstance(final ClassLoader loader,
                                     final String cname,
                                     final Class<?> interfaceClass) {
    try {
      final Class<?> cl = loader.loadClass(cname);

      if (cl == null) {
        throw new BedeworkException("Class " + cname + " not found");
      }

      final Object o = cl.getDeclaredConstructor().newInstance();

      if (!interfaceClass.isInstance(o)) {
        throw new BedeworkException("Class " + cname +
                                             " is not a subclass of " +
                                             interfaceClass.getName());
      }

      return o;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new BedeworkException(t);
    }
  }
}
