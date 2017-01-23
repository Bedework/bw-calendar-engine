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

import org.bedework.util.jmx.ConfBase;
import org.bedework.util.jmx.InfoLines;

import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import java.io.StringReader;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/**
 * @author douglm
 *
 */
public class DbConf extends ConfBase<DbConfig> implements DbConfMBean {
  /* Be safe - default to false */
  private TargetType target = TargetType.SCRIPT;

  private String schemaOutFile;

  private Configuration hibCfg;

  private class SchemaThread extends Thread {
    InfoLines infoLines = new InfoLines();

    SchemaThread() {
      super("BuildSchema");
    }

    @Override
    public void run() {
      try {
        infoLines.addLn("Started export of schema");

        final long startTime = System.currentTimeMillis();

        final SchemaExport se = new SchemaExport();

        se.setFormat(true);       // getFormat());
        se.setHaltOnError(false); // getHaltOnError());
        se.setOutputFile(getSchemaOutFile());
        /* There appears to be a bug in the hibernate code. Everybody initialises
        this to /import.sql. Set to null causes an NPE
        Make sure it refers to a non-existant file */
        //se.setImportFile("not-a-file.sql");

        setStatus(statusRunning);

        final EnumSet<TargetType> targets = EnumSet.noneOf(TargetType.class );

        targets.add(target);
        
        Properties prop = getHibConfiguration().getProperties();

        final BootstrapServiceRegistry bsr = new BootstrapServiceRegistryBuilder().build();
        final StandardServiceRegistryBuilder ssrBuilder = new StandardServiceRegistryBuilder(bsr);

        ssrBuilder.applySettings(prop);

        se.execute(targets, SchemaExport.Action.BOTH, null, 
                   ssrBuilder.getBootstrapServiceRegistry());

        final long millis = System.currentTimeMillis() - startTime;
        final long seconds = millis / 1000;
        final long minutes = seconds / 60;

        infoLines.addLn("Elapsed time: " + minutes + ":" +
                                twoDigits(seconds - (minutes * 60)));
        setStatus(statusDone);
      } catch (final Throwable t) {
        error(t);
        infoLines.exceptionMsg(t);
        setStatus(statusFailed);
      } finally {
        infoLines.addLn("Schema build completed");
        target = TargetType.SCRIPT;
      }
    }
  }

  private final SchemaThread buildSchema = new SchemaThread();

  /**
   */
  public DbConf() {
    super("org.bedework.bwengine.core:service=DbConf");
    setConfigPname(CoreConfigurations.confuriPname);
    setConfigName("dbconfig");
  }

  /* ========================================================================
   * Schema attributes
   * ======================================================================== */

  @Override
  public void setExport(final boolean val) {
    target = TargetType.DATABASE;
  }

  @Override
  public boolean getExport() {
    return target == TargetType.DATABASE;
  }

  @Override
  public void setSchemaOutFile(final String val) {
    schemaOutFile = val;
  }

  @Override
  public String getSchemaOutFile() {
    return schemaOutFile;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String schema() {
    try {
//      buildSchema = new SchemaThread();

      setStatus(statusStopped);

      buildSchema.start();

      return "OK";
    } catch (final Throwable t) {
      error(t);

      return "Exception: " + t.getLocalizedMessage();
    }
  }

  @Override
  public synchronized List<String> schemaStatus() {
    if (buildSchema == null) {
      final InfoLines infoLines = new InfoLines();

      infoLines.addLn("Schema build has not been started");

      return infoLines;
    }

    return buildSchema.infoLines;
  }

  @Override
  public void setHibernateDialect(final String value) {
    getConfig().setHibernateDialect(value);
  }

  @Override
  public String getHibernateDialect() {
    return getConfig().getHibernateDialect();
  }

  @Override
  public String listHibernateProperties() {
    final StringBuilder res = new StringBuilder();

    @SuppressWarnings("unchecked")
    final List<String> ps = getConfig().getHibernateProperties();

    for (final String p: ps) {
      res.append(p);
      res.append("\n");
    }

    return res.toString();
  }

  @Override
  public String displayHibernateProperty(final String name) {
    final String val = getConfig().getHibernateProperty(name);

    if (val != null) {
      return val;
    }

    return "Not found";
  }

  @Override
  public void removeHibernateProperty(final String name) {
    getConfig().removeHibernateProperty(name);
  }

  @Override
  public void addHibernateProperty(final String name,
                                   final String value) {
    getConfig().addHibernateProperty(name, value);
  }

  @Override
  public void setHibernateProperty(final String name,
                                   final String value) {
    getConfig().setHibernateProperty(name, value);
  }

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  @Override
  public boolean isStarted() {
    return true;
  }

  @Override
  public String loadConfig() {
    return loadConfig(DbConfig.class);
  }

  /**
   * @return Configuration based on the properties
   */
  public synchronized Configuration getHibConfiguration() {
    if (hibCfg == null) {
      try {
        hibCfg = new Configuration();

        final StringBuilder sb = new StringBuilder();

        @SuppressWarnings("unchecked")
        final List<String> ps = getConfig().getHibernateProperties();

        for (final String p: ps) {
          sb.append(p);
          sb.append("\n");
        }

        final Properties hprops = new Properties();
        hprops.load(new StringReader(sb.toString()));

        hibCfg.addProperties(hprops).configure();
      } catch (final Throwable t) {
        // Always bad.
        error(t);
      }
    }

    return hibCfg;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /**
   * @param val the number
   * @return 2 digit val
   */
  private static String twoDigits(final long val) {
    if (val < 10) {
      return "0" + val;
    }

    return String.valueOf(val);
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */
}
