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
package org.bedework.calsvci.indexing;


/**
 * @author Mike Douglass
 *
 */
public class SearchResultEntry {
  private Object entity;

  private String docType;

  private float score;

  /** Constructor
   *
   * @param entity
   * @param score
   */
  public SearchResultEntry(final Object entity,
                           final String docType,
                           final float score) {
    this.entity = entity;
    this.docType = docType;
    this.score = score;
  }

  /**
   * @return score from search engine
   */
  public float getScore() {
    return score;
  }

  /**
   *
   * @param val the entity
   */
  public void setEntity(Object val) {
    entity = val;
  }

  /**
   *
   * @return the entity
   */
  public Object getEntity() {
    return entity;
  }

  /**
   *
   * @param val the doctype from BwIndexer
   */
  public void setDocType(String val) {
    docType = val;
  }

  /**
   *
   * @return the doctype from BwIndexer
   */
  public String getDocType() {
    return docType;
  }
}
