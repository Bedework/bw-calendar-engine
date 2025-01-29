/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate;

import org.bedework.database.db.DbSession;

/**
 * User: mike Date: 2/1/20 Time: 15:23
 */
public class CoreResourcesDAO extends EntityDAO {
  /** Constructor
   *
   * @param sess the session
   */
  CoreResourcesDAO(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return CoreResourcesDAO.class.getName();
  }
}
