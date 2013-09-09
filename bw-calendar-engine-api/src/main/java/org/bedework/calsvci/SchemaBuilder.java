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

import org.bedework.calfacade.exc.CalFacadeException;

import java.util.Properties;

/** Interface which defines a schema builder.
 *
 * @author Mike Douglass   douglm@bedework.edu
 * @version 1.0
 */
public interface SchemaBuilder {
  /** Build a schema and optionally export it to the database
   *
   * @param props
   * @param outputFile
   * @param export
   * @param delimiter
   * @throws CalFacadeException
   */
  void execute(Properties props,
               String outputFile,
               boolean export,
               String delimiter) throws CalFacadeException;
}

