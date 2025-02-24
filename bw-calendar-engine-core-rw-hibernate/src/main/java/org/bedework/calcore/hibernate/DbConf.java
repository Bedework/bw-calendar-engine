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

import org.bedework.database.hibernate.HibConfig;
import org.bedework.database.hibernate.SchemaThread;
import org.bedework.util.jmx.ConfBase;
import org.bedework.util.jmx.InfoLines;

import java.util.List;

/**
 * @author douglm
 *
 */
public class DbConf extends ConfBase<DbConfig> implements DbConfMBean {
  /* Be safe - default to false */
  private boolean export;

  private String schemaOutFile;

  private class SchemaBuilder extends SchemaThread {

    SchemaBuilder(final String outFile,
                  final boolean export) {
      super(outFile, export, new HibConfig(getConfig(),
                                           DbConf.class.getClassLoader()));
      setContextClassLoader(DbConf.class.getClassLoader());
    }

    @Override
    public void completed(final String status) {
      if (status.equals(SchemaThread.statusDone)) {
        DbConf.this.setStatus(ConfBase.statusDone);
      } else {
        DbConf.this.setStatus(ConfBase.statusFailed);
      }
      setExport(false);
      info("Schema build completed with status " + status);
    }
  }

  private SchemaBuilder buildSchema;

  /**
   */
  public DbConf() {
    super("org.bedework.bwengine.core:service=DbConf",
          CoreConfigurations.confDirName,
          "dbconfig");
  }

  /* ========================================================================
   * Schema attributes
   * ======================================================================== */

  @Override
  public void setExport(final boolean val) {
    export = val;
  }

  @Override
  public boolean getExport() {
    return export;
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
      buildSchema = new SchemaBuilder(getSchemaOutFile(),
                                      getExport());

      setStatus(statusRunning);

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
    final List<String> ps = getConfig().getOrmProperties();

    for (final String p: ps) {
      res.append(p);
      res.append("\n");
    }

    return res.toString();
  }

  @Override
  public String displayHibernateProperty(final String name) {
    final String val = getConfig().getOrmProperty(name);

    if (val != null) {
      return val;
    }

    return "Not found";
  }

  @Override
  public void removeHibernateProperty(final String name) {
    getConfig().removeOrmProperty(name);
  }

  @Override
  public void addHibernateProperty(final String name,
                                   final String value) {
    getConfig().addOrmProperty(name, value);
  }

  @Override
  public void setHibernateProperty(final String name,
                                   final String value) {
    getConfig().setOrmProperty(name, value);
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

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */
}
