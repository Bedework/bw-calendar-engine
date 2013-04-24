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
package org.bedework.calcore;

import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.StandardBasicTypes;

import java.sql.Types;

/**
 * Update the hibernate distributed dialect. Effectively apply changes
 * referred to in http://opensource.atlassian.com/projects/hibernate/browse/HHH-3401
 *
 */
public class H2Dialect extends org.hibernate.dialect.H2Dialect {
  /**
   *
   */
  public H2Dialect() {
    super();

    registerColumnType(Types.BIT, "boolean");
    registerColumnType(Types.NUMERIC, "decimal($p,$s)");

    registerFunction("quarter",
                     new StandardSQLFunction("quarter", StandardBasicTypes.INTEGER));
  }
}
