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
package org.bedework.calfacade;

import org.bedework.util.misc.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Provide information about the aliases to a collection that can
 * reference an href.
 * 
 * <p>When we have a change to a resource we need to determine if that
 * resource is visible via aliases to the containing collection</p>
 * 
 * <p>So if the containing collection is C and the resource has the name
 * C/N it might be visible via the resource A which aliases C with the
 * href A/N</p>
 * 
 * <p>The resource might not be visible if C or A are filtered or if
 * dav access has restricted the rights.</p>
 * 
 * <p>Furthermore, we might have an alias A' to A, with a possibly different
 * owner and different filtering.</p>
 * 
 * <p>This class is the result of querying the collections and aliases
 * to determine that visibility. As this is a relatively expensive 
 * process we try to cache as much as possible. We can cache a copy
 * that only contains entries for each collection and alias without
 * reference to the entities. This can form the basis of an entity
 * search. This cache needs to be flushed whenever there is a change 
 * to the system that affects any of these entries</p>
 * 
 * <p>We could also store the structure created when checking a given 
 * entity. This is flushed if the entity is deleted or - again - if
 * the alias structure changes</p>
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public class AliasesInfo implements Serializable {
  /* The name of the entity that was updated. Null if this is a master
     copy for the collection
   */
  private final String entityName;

  /* The collection containing the entity
   */
  private final BwCalendar collection;
  
  private final String principalHref;

  /* Principal ref is a cua of an external user
   */
  private boolean externalCua;

  /* Is the entity visible
   */
  private boolean visible;

  /* Is the entity shared
   */
  private boolean shared;

  /* Notifications enabled?
   */
  private boolean notificationsEnabled;
  
  private final Map<String, AliasesInfo> shareeInfoMap = new HashMap<>();

  /**
   * @param principalHref the owner of the collection
   * @param collection collection of interest
   * @param entityName - the entity being updated
   */
  public AliasesInfo(final String principalHref,
                     final BwCalendar collection,
                     final String entityName) {
    this.principalHref = principalHref;
    this.collection = collection;
    this.entityName = entityName;
  }

  /**
   * @return owner
   */
  public String getPrincipalHref() {
    return principalHref;
  }

  /**
   * @return collection
   */
  public BwCalendar getCollection() {
    return collection;
  }

  /**
   * @return name of entity
   */
  public String getEntityName() {
    return entityName;
  }

  public void setExternalCua(final boolean val) {
    externalCua = val;
  }
  
  /**
   * @return true if Principal ref is a cua of an external user
   */
  public boolean getExternalCua() {
    return externalCua;
  }

  public void setVisible(final boolean val) {
    visible = val;
  }

  /**
   * @return true if entity is visible
   */
  public boolean getVisible() {
    return visible;
  }

  /**
   * @return true for shared collection
   */
  public boolean getShared() {
    return shared;
  }

  public void setNotificationsEnabled(final boolean val) {
    notificationsEnabled = val;
  }
  
  /**
   * @return true if notificationsEnabled
   */
  public boolean getNotificationsEnabled() {
    return notificationsEnabled;
  }

  public void addSharee(final AliasesInfo ai) {
    shareeInfoMap.put(ai.getCollection().getPath(), ai);
    shared = true;
  }
  
  public List<AliasesInfo> getAliases() {
    return new ArrayList<>(shareeInfoMap.values());
  }

  /**
   * @param val href of a collection that may be referenced by an alias
   * @return true if this object references that collection
   * 
   */
  public boolean referencesCollection(final String val) {
    return collection.getPath().equals(val) ||
            (shareeInfoMap.get(val) != null);
  }

  public String makeKey() {
    if (getEntityName() == null) {
      return getCollection().getPath();
    }
    
    return Util.buildPath(false, getCollection().getPath(),
                          "/",
                          getEntityName());
  }
  
  public static String makeKey(final String collectionHref,
                               final String entityName) {
    if (entityName == null) {
      return collectionHref;
    }

    return Util.buildPath(false, collectionHref,
                          "/",
                          entityName);
  }

  public AliasesInfo copyForEntity(final String entityName,
                                   final boolean visible) {
    final AliasesInfo ai = new AliasesInfo(getPrincipalHref(),
                                           getCollection(),
                                           entityName);
    
    ai.setVisible(visible);
    
    return ai;
  }
}
