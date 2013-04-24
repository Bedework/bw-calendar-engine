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
package org.bedework.calfacade.base;

import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.annotations.NoDump;

import java.util.Set;

/** An entity that can have one or more properties will implement this interface.
 *
 * @author douglm
 */
public interface PropertiesEntity {

  /**
   * @param val
   */
  public void setProperties(Set<BwProperty> val);

  /**
   * @return properties
   */
  public Set<BwProperty> getProperties();

  /**
   * @param name
   * @return properties with given name
   */
  public Set<BwProperty> getProperties(String name);

  /** Remove all with given name
   *
   * @param name
   */
  public void removeProperties(String name);

  /**
   * @return int
   */
  @NoDump
  public int getNumProperties();

  /**
   * @param name
   * @return property or null
   */
  public BwProperty findProperty(String name);

  /**
   * @param val
   */
  public void addProperty(BwProperty val);

  /**
   * @param val
   * @return boolean
   */
  public boolean removeProperty(BwProperty val);

  /**
   * @return BwProperty
   */
  public Set<BwProperty> copyProperties();

  /**
   * @return BwProperty
   */
  public Set<BwProperty> cloneProperties();
}
