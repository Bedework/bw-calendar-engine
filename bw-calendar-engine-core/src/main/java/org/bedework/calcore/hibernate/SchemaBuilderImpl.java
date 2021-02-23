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
package org.bedework.calcore.hibernate;

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.configs.SchemaBuilder;

import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import java.util.EnumSet;
import java.util.Properties;

/** Implementation of interface which defines a schema builder.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class SchemaBuilderImpl implements SchemaBuilder {
  @Override
  public void execute(final Properties props,
                      final String outputFile,
                      final boolean export,
                      final String delimiter) throws CalFacadeException {
    try {
      SchemaExport se = new SchemaExport();

      if (delimiter != null) {
        se.setDelimiter(delimiter);
      }

      se.setFormat(true);
      se.setHaltOnError(false);
      se.setOutputFile(outputFile);

      final EnumSet<TargetType> targets = EnumSet.noneOf(TargetType.class );

      if (export) {
        targets.add(TargetType.DATABASE);
      } else {
        targets.add(TargetType.SCRIPT);
      }
      
      Properties allProps = getConfiguration(props).getProperties();

      final BootstrapServiceRegistry bsr = new BootstrapServiceRegistryBuilder().build();
      final StandardServiceRegistryBuilder ssrBuilder = new StandardServiceRegistryBuilder(bsr);

      ssrBuilder.applySettings(allProps);

      se.execute(targets, SchemaExport.Action.BOTH, null,
                 ssrBuilder.getBootstrapServiceRegistry());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private Configuration getConfiguration(final Properties props) throws Throwable {
    Configuration cfg = new Configuration();

    cfg.addProperties(props).configure();

    return cfg;
  }
}

