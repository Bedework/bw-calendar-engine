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
package org.bedework.dumprestore.dump.dumpling;

import org.bedework.dumprestore.AliasEntry;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.dump.DumpGlobals;

import java.util.Iterator;

import javax.xml.namespace.QName;

/** Dumps the alias info as a separate file.
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class DumpAliases extends Dumpling<AliasInfo> {
  /** Constructor
   *
   * @param globals dump global
   */
  public DumpAliases(final DumpGlobals globals) {
    super(globals, new QName(aliasInfoTag), -1, globals.aliasesXml);
  }

  @Override
  public void dumpSection(final Iterator it) throws Throwable {
    tagStart(sectionTag);

    versionDate();

    open();
    new Dumpling<AliasEntry>(globals,
                            new QName(aliasesTag),
                            globals.aliases,
                            xml).dumpSection(
            globals.aliasInfo.values().iterator());
    close();

    open();
    new Dumpling<AliasInfo>(globals,
                            new QName(extsubsTag),
                            globals.externalSubscriptions,
                            xml).dumpSection(
            globals.externalSubs.iterator());
    close();

    tagEnd(sectionTag);
  }

  private void open() throws Throwable {
    globals.svci.beginTransaction();
  }

  private void close() throws Throwable {
    globals.svci.endTransaction();
  }
}

