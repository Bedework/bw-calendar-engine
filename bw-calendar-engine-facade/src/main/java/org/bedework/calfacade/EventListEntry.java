/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade;

/**
 * User: mike Date: 6/13/18 Time: 14:06
 */
public class EventListEntry implements Comparable<EventListEntry> {
  private String href;

  private String path;
  private String name;

  /**
   * @param href
   */
  public EventListEntry(final String href) {
    this.href = href;
  }

  /**
   * @param path
   * @param name
   */
  public EventListEntry(final String path,
                        final String name) {
    this.path = path;
    this.name = name;

    href = path + "/" + name;
  }

  /**
   * @return href
   */
  public String getHref() {
    return href;
  }

  /**
   * @return path part
   */
  public String getPath() {
    if (path == null) {
      split();
    }

    return path;
  }

  /**
   * @return name part
   */
  public String getName() {
    if (name == null) {
      split();
    }

    return name;
  }

  private void split() {
    int pos = href.lastIndexOf("/");

    path = href.substring(0, pos );
    name = href.substring(pos + 1); // Skip the "/"
  }

  @Override
  public int compareTo(final EventListEntry that) {
    return href.compareTo(that.href);
  }
}
