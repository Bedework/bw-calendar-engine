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

package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.base.PropertiesEntity;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** handle properties.
 *
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 1.0
 */
public class PropertiesRule extends RestoreRule {
  PropertiesRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) {
    if (name.equals("properties")) {
      push(new TreeSet<BwProperty>());
    } else {
      push(new BwProperty());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void end(final String ns, final String name) throws Exception {
    if (name.equals("properties")) {
      /* Top should be the collection
       */
      Set<BwProperty> ps = (Set<BwProperty>)pop();

      PropertiesEntity pe = (PropertiesEntity)top();

      pe.setProperties(ps);

      return;
    }

    /* Top should be a BwProperty to add to the collection */

    BwProperty p = (BwProperty)pop();

    Collection<BwProperty> ps = (Collection<BwProperty>)top();

    ps.add(p);
  }
}
