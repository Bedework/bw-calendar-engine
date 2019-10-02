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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author douglm
 *
 */
public class ProcessCreate extends CmdUtilHelper {
  ProcessCreate(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      addInfo("create admingroup <account> \"description\"\\\n" +
                      "          <group-owner> [<event-owner>]\n" +
                      "   create admin group\n");


      addInfo("create calsuite <name> <owner> \n" +
                      "   add a calsuite\n");
      
      addInfo("create collection " +
                      "          (folder | calendar | alias | topic) \\\n" +
                      "          \"<parent-path>\" \"<name>\"\\\n" +
                      "          \"<summary>\" \\\n" +
                      "          [\"<alias-target-path>\"]\\\n" +
                      "          \"<owner-href>\"\\\n" +
                      "          \"<creator-href>\"\\\n" +
                      "          [desc=\"<description>\"] \\\n" +
                      "          [filter=\"<fexpr>\"] \\\n" +
                      "          [category=\"<cat>\"]* \\\n" +
                      "   create given collection with possible filter and categories\n" +
                      "   Each <cat> is the unique name of the category\n" +
                      "        <fexpr> is a filter expression\n");

      addInfo("create category ... \\\n");

      addInfo("create location ... \\\n");

      addInfo("create csvloc ... \\\n");

      addInfo("create view \"<name>\" \\\n");
      
      return true;
    }

    if ("admingroup".equals(wd)) {
      return createAdminGroup(word());
    }

    if ("calsuite".equals(wd)) {
      return createCalsuite(word()) != null;
    }

    if ("collection".equals(wd)) {
      return createCollection();
    }

    if ("category".equals(wd)) {
      return processCreateCategory(wordOrQuotedVal());
    }

    if ("csvloc".equals(wd)) {
      return createLocationsCsv();
    }

    if ("location".equals(wd)) {
      return createLocation();
    }

    if ("view".equals(wd)) {
      return createView(quotedVal());
    }

    return false;
  }

  @Override
  String command() {
    return "create";
  }

  @Override
  String description() {
    return "create admin group, calendar suite, category, collection or location";
  }

  private boolean processCreateCategory(final String catVal) throws Throwable {
    if (catVal == null) {
      error("Expected a category value");
      return false;
    }

    try {
      open();

      createCategory(catVal, quotedVal());

      return true;
    } finally {
      close();
    }
  }

  private boolean createAdminGroup(final String account) throws Throwable {
    if (debug()) {
      debug("About to create admin group " + account);
    }
    
    if (account == null) {
      addError("Must supply account");
      return false;
    }

    try {
      open();

      final BwAdminGroup grp = makeAdminGroup(account,
                                              quotedVal(), // description,
                                              word(),      //owner,
                                              word());     // eventOwner);
      
      if (grp == null) {
        return false;
      }

      return true;
    } finally {
      close();
    }
  }

  private BwCalSuiteWrapper createCalsuite(final String name) throws Throwable {
    if (debug()) {
      debug("About to create cal suite " + name);
    }

    if (name == null) {
      addError("Must supply name");
      return null;
    }

    final String adminGroup = word();

    if (adminGroup == null) {
      addError("Must supply admin group");
      return null;
    }

    try {
      open();

      final BwCalSuiteWrapper cs = 
              getSvci().getCalSuitesHandler().add(name,
                                                  adminGroup,
                                                  null,
                                                  null);

      return cs;
    } finally {
      close();
    }
  }

  private boolean createView(final String name) throws Throwable {
    if (debug()) {
      debug("About to create view " + name);
    }

    if (name == null) {
      addError("Must supply name");
      return false;
    }

    try {
      open();

      final BwView view = new BwView();
      view.setName(name);

      if (!getSvci().getViewsHandler().add(view, false)) {
        error("view \"" + name + "\" already exists.");
      }
      
      return true;
    } finally {
      close();
    }
  }

  private BwCategory createCategory(final String catVal,
                                    final String catDesc) throws Throwable {
    if (debug()) {
      debug("About to create category " + catVal);
    }

    final BwCategory cat = BwCategory.makeCategory();

    cat.setWordVal(catVal);
    cat.setDescriptionVal(catDesc);
    //cat.setOwner(svci.getUser());

    getSvci().getCategoriesHandler().add(cat);

    return cat;
  }

  private boolean createCollection() throws Throwable {
    try {
      open();

      final String type = word();
      final String parentPath = wordOrQuotedVal();
      final String calName = wordOrQuotedVal();
      final String calSummary = wordOrQuotedVal();
      final String aliasTarget;
      if ("topic".equals(type) || "alias".equals(type)) {
        aliasTarget = wordOrQuotedVal();
      } else {
        aliasTarget = null;
      }
      final String ownerHref = quotedVal();
      final String creatorHref = quotedVal();
      final String description;
      if (test("desc")) {
        assertToken('=');

        description = quotedVal();
      } else {
        description = null;
      }

      final String filter;
      if (test("filter")) {
        assertToken('=');

        filter = quotedVal();
      } else {
        filter = null;
      }

      final List<String> cats = new ArrayList<>();

      while (testToken("category")) {
        assertToken('=');
        cats.add(quotedVal());
      }

      final BwCalendar cal = makeCollection(type,
                                            parentPath,
                                            calName,
                                            calSummary,
                                            aliasTarget,
                                            ownerHref,
                                            creatorHref,
                                            description,
                                            filter,
                                            cats);

      return cal != null;
    } finally {
      close();
    }
  }

  private boolean createLocation() throws Throwable {
    try {
      open();

      final BwLocation loc = BwLocation.makeLocation();

      if (!testToken("address")) {
        error("address required for location");
      }

      assertToken('=');

      loc.setAddressField(quotedVal());

      if (testToken("room")) {
        assertToken('=');

        loc.setRoomField(quotedVal());
      }

      if (testToken("subField1")) {
        assertToken('=');

        loc.setSubField1(quotedVal());
      }

      if (testToken("subField2")) {
        assertToken('=');

        loc.setSubField1(quotedVal());
      }

      if (testToken("accessible")) {
        loc.setAccessible(true);
      }

      if (testToken("geouri")) {
        assertToken('=');

        loc.setGeouri(quotedVal());
      }

      if (testToken("street")) {
        assertToken('=');

        loc.setStreet(quotedVal());
      }

      if (testToken("city")) {
        assertToken('=');

        loc.setCity(quotedVal());
      }

      if (testToken("state")) {
        assertToken('=');

        loc.setState(quotedVal());
      }

      if (testToken("zip")) {
        assertToken('=');

        loc.setZip(quotedVal());
      }

      if (testToken("link")) {
        assertToken('=');

        loc.setLink(quotedVal());
      }

      getSvci().getLocationsHandler().add(loc);

      return true;
    } finally {
      close();
    }
  }

  private boolean createLocationsCsv() throws Throwable {
    try {
      open();

      final String fileName = quotedVal();
      if (fileName == null) {
        error("Expected csv filename");
        return false;
      }
      
      final File csvf = new File(fileName);
      if (!csvf.exists()) {
        error("File " + fileName + " does not exist");
        return false;
      }
      
      final CSVParser parser = 
              CSVFormat.DEFAULT
                      .withHeader()
                      .withAllowMissingColumnNames()
                      .parse(new FileReader(csvf));
      
      for (final CSVRecord rec: parser) {
        final BwLocation loc = BwLocation.makeLocation();

        String fld = rec.get("Address");
        if (fld == null) {
          error("address required for location");
          continue;
        }

        loc.setAddressField(fld);

        fld = rec.get("Room");
        if (fld != null) {
          loc.setRoomField(fld);
        }

        fld = rec.get("Code");
        if (fld != null) {
          loc.setCode(fld);
        }

        fld = rec.get("alternateAddress");
        if (fld != null) {
          loc.setAlternateAddress(fld);
        }

        fld = rec.get("Accessible");
        if (fld != null) {
          loc.setAccessible("Y".equals(fld));
        }

        fld = rec.get("oldCode");
        if (fld != null) {
          loc.setSubField1(fld);
        }

        fld = rec.get("Street");
        if (fld != null) {
          loc.setStreet(fld);
        }

        fld = rec.get("City");
        if (fld != null) {
          loc.setCity(fld);
        }

        fld = rec.get("State");
        if (fld != null) {
          loc.setState(fld);
        }

        fld = rec.get("Zip");
        if (fld != null) {
          loc.setZip(fld);
        }

        fld = rec.get("Link");
        if (fld != null) {
          loc.setLink(fld);
        }

        getSvci().getLocationsHandler().add(loc);
      }

      return true;
    } finally {
      close();
    }
  }
}
