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
package org.bedework.indexer;


import java.util.ArrayList;
import java.util.List;

/** This class provides crawler status.
 *
 */
public class CrawlStatus {
  /** */
  public String name;

  /** */
  public String currentStatus;

  /** */
  public IndexStats stats;

  /** Generated when complete */
  public List<String> infoLines = new ArrayList<>();

  /**
   * @param name of status
   */
  public CrawlStatus(final String name) {
    this.name = name;
    stats = new IndexStats(name);
  }
}
