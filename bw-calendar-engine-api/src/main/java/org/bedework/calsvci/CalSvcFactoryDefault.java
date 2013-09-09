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
import org.bedework.calfacade.exc.CalFacadeException;

/** Default svc factory - just gets an instance of the default class.
 *
 * @author Mike Douglass       douglm@bedework.edu
 */
public class CalSvcFactoryDefault implements CalSvcFactory {
  private static final String defaultSvciClass = "org.bedework.calsvc.CalSvc";

  private static final String schemaBuilderClass =
      "org.bedework.calcore.hibernate.SchemaBuilderImpl";

  private static final String systemConfigClass =
      "org.bedework.calsvc.jmx.ConfigurationsImpl";

  private static Configurations conf;

  @Override
  public CalSvcI getSvc(final CalSvcIPars pars) throws CalFacadeException {
    CalSvcI svc = (CalSvcI)loadInstance(defaultSvciClass,
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

    synchronized (this) {
      if (conf != null) {
        return conf;
      }

      conf = (Configurations)loadInstance(systemConfigClass,
                                          Configurations.class);

      return conf;
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
