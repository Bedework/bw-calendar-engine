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
package org.bedework.tools.cmdutil;

import org.bedework.calfacade.BwCategory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;

/**
 * @author douglm
 *
 */
public class ProcessList extends CmdUtilHelper {
  ProcessList(ProcessState pstate) {
    super(pstate);
  }

  boolean process() throws Throwable {
    String wd = word();

    if (wd == null) {
      return false;
    }

    if ("categories".equals(wd)) {
      return listCategories();
    }

    return false;
  }

  @Override
  String command() {
    return "list";
  }

  @Override
  String description() {
    return "list categories";
  }

  private boolean listCategories() throws Throwable {
    try {
      info("Categories:");

      Collection<String> vals = new ArrayList<String>();

      while (!cmdEnd()) {
        String catVal = wordOrQuotedVal();
        if (catVal == null) {
          break;
        }

        vals.add(catVal.toLowerCase());
      }

      open();

      Collection<BwCategory> cats = getSvci().getCategoriesHandler().get();

      for (BwCategory cat: cats) {
        if (vals.isEmpty()) {
          listCategory(cat);
          continue;
        }

        String lc = cat.getWordVal().toLowerCase();

        for (String s: vals) {
          if (lc.contains(s)) {
            listCategory(cat);
          }
        }
      }

      return true;
    } finally {
      close();
    }
  }

  private void listCategory(BwCategory cat) {
    StringBuilder sb = new StringBuilder();

    Formatter fmt = new Formatter(sb);

    fmt.format("%40s %50s ",
               cat.getWordVal(),
               cat.getOwnerHref());

    info(sb.toString());
  }
}
