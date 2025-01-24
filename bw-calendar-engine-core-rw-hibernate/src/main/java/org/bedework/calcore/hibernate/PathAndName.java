/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate;

/**
 * User: mike Date: 6/13/18 Time: 23:06
 */
public class PathAndName {
  final String colPath;
  final String name;

  public PathAndName(final String href) {
    final int pos = href.lastIndexOf("/");
    if (pos < 0) {
      throw new RuntimeException("Bad href: " + href);
    }

    name = href.substring(pos + 1);

    colPath = href.substring(0, pos);
  }
}
