/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.calcore.rw.common.dao.AccessDAO;
import org.bedework.database.db.DbSession;

/**
 * User: mike
 * Date: 11/21/16
 * Time: 23:30
 */
public class AccessDAOImpl extends DAOBaseImpl
        implements AccessDAO {
  /**
   * @param sess             the session
   */
  public AccessDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return AccessDAOImpl.class.getName();
  }
}
