/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.util;

import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: mike Date: 10/26/17 Time: 23:44
 */
public class FieldSplitter {
  final String fieldDelimiter;
  List<String> flds;

  public FieldSplitter(final String fieldDelimiter) {
    this.fieldDelimiter = fieldDelimiter;
  }

  /** Set the combined field value
   *
   * @param fld the value
   */
  public void setVal(final String fld) {
    if (fld != null) {
      flds = new ArrayList<>(
              Arrays.asList(fld.split(fieldDelimiter)));
    }
  }

  public void setFld(final int i, final String val) {
    final String checkVal;
    if (val == null) {
      checkVal = null;
    } else {
      checkVal = val.replace(fieldDelimiter, "-");
    }

    if (flds == null) {
      flds = new ArrayList<>(i + 1);
    }

    while (i > flds.size() - 1) {
      flds.add(null);
    }
    flds.set(i, checkVal);
  }

  public String getFld(final int i) {
    if (flds == null) {
      return null;
    }
    if (i >= flds.size()) {
      return null;
    }

    final String s = flds.get(i);

    if (s == null) {
      return null;
    }

    if (s.length() == 0) {
      return null;
    }

    return s;
  }

  public void setFlds(final List<String> vals) {
    if (Util.isEmpty(vals)) {
      setVal(null);
      return;
    }

    for (int i = 0; i < vals.size(); i++) {
      setFld(i, vals.get(i));
    }
  }

  public List<String> getFlds() {
    return flds;
  }

  public String getCombined() {
    if (flds == null) {
      return null;
    }

    final StringBuilder fld = new StringBuilder();
    for (final String s: flds) {
      if (fld.length() != 0) {
        fld.append(fieldDelimiter);
      }

      if (s != null) {
        fld.append(s);
      }
    }

    return fld.toString();
  }
}
