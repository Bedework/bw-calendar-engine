/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.tools.cmdutil;

/**
 * User: mike Date: 2019-08-17 Time: 23:51
 */

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper;
import org.bedework.util.misc.Util;

import org.apache.commons.text.CaseUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public class ProcessSetup extends CmdUtilHelper {
  ProcessSetup(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    /*
      user will enter e.g. setup calsuite "Payroll"
      We camelcase the name and then do effectively the following:

        1. user admin super
            Need admin for the rest
        2. create admingroup payrollAdminGroup "Payroll" public-user agrp_payroll
            Creates the admin group - will also create users
        3. create admingroup payrollSubmissionGroup "Payroll Submissions" public-user agrp_payroll
            Creates the submission group
        4. create calsuite payroll payrollAdminGroup
            Creates the calsuite
        5. calsuite payroll
            Switch to the suite
        6. create category ".payroll"
            Default category for suite
        7. delete all views
            Cleanup
        8. create view "All"
        9. prefs defaultCategory ".payroll"
        10. prefs preferredView "All"
        11. prefs hour24 false
        12. create collection topic /user/agrp_payroll "payroll"
                   "Payroll" "/public/Aliases/payroll"
                   "/principals/users/public-user"
                   "/principals/users/agrp_payroll"
        13. create collection topic /user/agrp_payroll
                     "Ongoing" "Ongoing" "/public/_aliases/_Ongoing"
                     "/principals/users/public-user"
                     "/principals/users/agrp_payroll"

        # all groups need to be members of campusAdminGroups
        14. add admbr "payrollAdminGroup" group to campusAdminGroups
        15. add admbr "payrollSubmissionGroup" group to payrollAdminGroup
     */
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      addInfo("setup calsuite <name> [12 | 24] [nosubmit] \n" +
                      "   add a calsuite\n" +
              "<name> is a quoted string" +
                      "12/24 is on of those - default 24" +
                      "nosubmit indicates no submission group");

      return true;
    }

    if ("calsuite".equals(wd)) {
      return setupCalsuite() != null;
    }

    return false;
  }

  @Override
  String command() {
    return "setup";
  }

  @Override
  String description() {
    return "setup calendar suite";
  }

  private BwCalSuiteWrapper setupCalsuite() throws Throwable {
    final String name = quotedVal();

    if (name == null) {
      addError("Must provide a name");
      return null;
    }

    final String ccName = CaseUtils.toCamelCase(name, false, ' ');
    final String eventOwnerAccount = "agrp_" + ccName;
    final String adminGroupname = ccName + "AdminGroup";
    final String submissionGroupName = ccName + "SubmissionGroup";
    final String suiteHome = "/user/" + eventOwnerAccount;
    final String eventOwnerPr = "/principals/users/" + eventOwnerAccount;

    boolean hrs12 = false;
    boolean hrs24 = false;
    boolean nosubmit = false;

    String wd = word();

    while (wd != null) {
      if ("12".equals(wd)) {
        hrs12 = true;
        wd = word();
        continue;
      }

      if ("24".equals(wd)) {
        hrs24 = true;
        wd = word();
        continue;
      }

      if ("nosubmit".equals(wd)) {
        nosubmit = true;
        wd = word();
        continue;
      }

      addError("Unknown parameter " + wd);
      return null;
    }

    try {
      open();

      setUser("admin", true);

      // 2. The admin group

      final BwAdminGroup adgrp =
              makeAdminGroup(adminGroupname,
                             name,           // description,
                             "public-user",  //owner,
                             eventOwnerAccount);     // eventOwner);

      if (adgrp == null) {
        return null;
      }

      // 3. The submissions group

      final BwAdminGroup subgrp;

      if (nosubmit) {
        subgrp = null;
      } else {
        subgrp = makeAdminGroup(submissionGroupName,
                                name + "  Submissions", // description,
                                "public-user",          //owner,
                                eventOwnerAccount);     // eventOwner);

        if (subgrp == null) {
          return null;
        }
      }

      // 4. The calsuite

      final BwCalSuiteWrapper cs =
              getSvci().getCalSuitesHandler().add(name,
                                                  adminGroupname,
                                                  null,
                                                  null);

      if (cs == null) {
        return null;
      }

      // 5. switch to calsuite

      pstate.setCalsuite(cs);

      // 6. create default category

      final String defcat = "." + ccName;
      final BwCategory cat = BwCategory.makeCategory();

      cat.setWordVal(defcat);

      getSvci().getCategoriesHandler().add(cat);

      // 7. delete all views

      final Collection<BwView> theViews = getSvci().getViewsHandler().getAll();

      if (!Util.isEmpty(theViews)) {
        final List<BwView> views = new ArrayList<>(theViews);

        for (final BwView view: views) {
          if (getSvci().getViewsHandler().remove(view)) {
            info("Removed view " + view.getName());
          } else {
            warn("Unable to remove view " + view.getName());
          }
        }
      }

      // 8. create view "All"

      final BwView view = new BwView();
      view.setName("All");

      if (!getSvci().getViewsHandler().add(view, false)) {
        error("view \"All\" already exists.");
      }

      // 9. prefs defaultCategory ".payroll"

      final BwPreferences prefs = getPrefs();
      if (prefs ==null) {
        error("Unable to fetch prefs for current user");
        return null; // No change
      }

      Set<String> catUids = new TreeSet<>();
      catUids.add(cat.getUid());
      prefs.setDefaultCategoryUids(catUids);

      // 10. prefs preferredView "All"

      prefs.setPreferredView("All");

      // 11. prefs hour24 false

      if (hrs12) {
        prefs.setHour24(false);
      } else if (hrs24) {
        prefs.setHour24(true);
      }

      getSvci().getPrefsHandler().update(prefs);

      // create collection topic /user/agrp_payroll
      // "Ongoing" "Ongoing"
      // "/public/_aliases/_Ongoing"
      // "/principals/users/public-user"
      // "/principals/users/agrp_payroll"

      makeCollection("topic",
                     suiteHome,
                     "Ongoing",
                     "Ongoing",
                     "/public/_aliases/_Ongoing",
                     "/principals/users/public-user",
                     eventOwnerPr,
                     null,
                     null,
                     null);

      //    # all groups need to be members of campusAdminGroups

      addToAdminGroup(adminGroupname, "group", "campusAdminGroups");

      if (!nosubmit) {
        addToAdminGroup(submissionGroupName, "group", adminGroupname);
      }

      return cs;
    } finally {
      close();
    }
  }
}
