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
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.misc.Util;

import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;

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

  private static Configurations conf;

  public static SystemProperties getSystemProperties() throws CalFacadeException {
    return new CalSvcFactoryDefault().getSystemConfig()
                                     .getSystemProperties();
  }
  
  @Override
  public CalSvcI getSvc(final CalSvcIPars pars) throws CalFacadeException {
    final CalSvcI svc = 
            (CalSvcI)loadInstance(defaultSvciClass,
                                  CalSvcI.class);

    svc.init(pars);

    return svc;
  }

  @Override
  public SchemaBuilder getSchemaBuilder() throws CalFacadeException {
    return (SchemaBuilder)loadInstance(schemaBuilderClass,
                                       SchemaBuilder.class);
  }

  @Override
  public Configurations getSystemConfig() throws CalFacadeException {
    if (conf != null) {
      return conf;
    }

    synchronized (defaultSvciClass) {
      if (conf != null) {
        return conf;
      }

      conf = (Configurations)loadInstance(systemConfigClass,
                                          Configurations.class);

      return conf;
    }
  }

  public static Properties getPr() throws CalFacadeException {
    final InputStream is = null;

    try {
      final SystemProperties sysProps = 
              CalSvcFactoryDefault.getSystemProperties();

      /* Load properties file */

      final Properties pr = new Properties();

      if (Util.isEmpty(sysProps.getSyseventsProperties())) {
        throw new CalFacadeException("No sysevent properties defined");
      }
      
      final StringBuilder sb = new StringBuilder();

      @SuppressWarnings("unchecked")
      final List<String> ps = sysProps.getSyseventsProperties();

      for (final String p: ps) {
        sb.append(p);
        sb.append("\n");
      }

      pr.load(new StringReader(sb.toString()));

      return pr;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      //Logger.getLogger(CalSvcFactoryDefault.class.getName()).throwing(CalSvcFactory.class, t);
      throw new CalFacadeException(t.getMessage());
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (final Throwable ignored) {}
      }
    }
  }

  private static Object loadInstance(final String cname,
                                     final Class interfaceClass) {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Class cl = loader.loadClass(cname);

      if (cl == null) {
        throw new CalFacadeException("Class " + cname + " not found");
      }

      Object o = cl.newInstance();

      if (o == null) {
        throw new CalFacadeException("Unable to instantiate class " + cname);
      }

      if (!interfaceClass.isInstance(o)) {
        throw new CalFacadeException("Class " + cname +
                                     " is not a subclass of " +
                                     interfaceClass.getName());
      }

      return o;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    }
  }
}
