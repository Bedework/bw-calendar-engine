/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate;

import org.bedework.calfacade.BwEvent;

import org.hibernate.EmptyInterceptor;
import org.hibernate.type.Type;

import java.io.Serializable;

/**
 * User: mike Date: 9/10/24 Time: 23:19
 */
public class HibernateInterceptor extends EmptyInterceptor {
  @Override
  public boolean onSave(final Object entity,
                        final Serializable id,
                        final Object[] state,
                        final String[] propertyNames,
                        final Type[] types) {
    if (!(entity instanceof final BwEvent event)) {
      return false;
    }

    event.onSave();
    return true;
  }
}
