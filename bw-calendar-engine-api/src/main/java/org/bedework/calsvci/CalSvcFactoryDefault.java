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
package org.bedework.calsvci;

import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.SchemaBuilder;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.CalSvcIPars;
import org.bedework.util.misc.Util;

import java.io.StringReader;
import java.util.List;
import java.util.Properties;

import static org.bedework.calsvci.CalSvcI.upgradeToReadWriteMessage;

/** Default svc factory - just gets an instance of the default class.
 *
 * @author Mike Douglass       douglm@rpi.edu
 */
public class CalSvcFactoryDefault implements CalSvcFactory {
  private static final String defaultSvciClass = "org.bedework.calsvc.CalSvc";

  private static final String schemaBuilderClass =
      "org.bedework.calcore.hibernate.SchemaBuilderImpl";

  private static final String systemConfigClass =
      "org.bedework.calsvc.jmx.ConfigurationsImpl";

  private static class ConfigHolder {
    final static Configurations conf =
            (Configurations)loadInstance(systemConfigClass,
                                         Configurations.class);
  }


  public static SystemProperties getSystemProperties() throws CalFacadeException {
    return CalSvcFactoryDefault.getSystemConfig()
                               .getSystemProperties();
  }
  
  @Override
  public CalSvcI getSvc(final CalSvcIPars pars) {
    CalSvcI svc =
            (CalSvcI)loadInstance(defaultSvciClass,
                                  CalSvcI.class);

    try {
      svc.init(pars);
    } catch (final RuntimeException t) {
      if (t.getMessage().equals(upgradeToReadWriteMessage)) {
        final var clonedPars = (CalSvcIPars)pars.clone();
        clonedPars.setReadonly(false);

        svc = (CalSvcI)loadInstance(defaultSvciClass,
                                    CalSvcI.class);

        svc.init(clonedPars);
      }
    }

    return svc;
  }

  @Override
  public SchemaBuilder getSchemaBuilder() {
    return (SchemaBuilder)loadInstance(schemaBuilderClass,
                                       SchemaBuilder.class);
  }

  public static Configurations getSystemConfig() {
    return ConfigHolder.conf;
  }

  public static Properties getPr() {
    try {
      final SystemProperties sysProps = 
              CalSvcFactoryDefault.getSystemProperties();

      /* Load properties file */

      final Properties pr = new Properties();

      if (Util.isEmpty(sysProps.getSyseventsProperties())) {
        throw new CalFacadeException("No sysevent properties defined");
      }
      
      final StringBuilder sb = new StringBuilder();

      final List<String> ps = sysProps.getSyseventsProperties();

      for (final String p: ps) {
        sb.append(p);
        sb.append("\n");
      }

      pr.load(new StringReader(sb.toString()));

      return pr;
    } catch (final Throwable t) {
      //Logger.getLogger(CalSvcFactoryDefault.class.getName()).throwing(CalSvcFactory.class, t);
      throw new RuntimeException(t);
    }
  }

  private static Object loadInstance(final String cname,
                                     final Class<?> interfaceClass) {
    try {
      final ClassLoader loader = Thread.currentThread().getContextClassLoader();
      final Class<?> cl = loader.loadClass(cname);

      if (cl == null) {
        throw new CalFacadeException("Class " + cname + " not found");
      }

      final Object o = cl.getDeclaredConstructor().newInstance();

      if (!interfaceClass.isInstance(o)) {
        throw new CalFacadeException("Class " + cname +
                                     " is not a subclass of " +
                                     interfaceClass.getName());
      }

      return o;
    } catch (final Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    }
  }
}
