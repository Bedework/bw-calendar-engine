/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert.jscal;

import org.bedework.jsforj.impl.JSFactory;

/** Allows registration of new types
 * User: mike Date: 7/26/20 Time: 00:13
 */
public class BwJSFactory extends JSFactory {
  static {
    register(new BwJSRegistration());
  }
}
