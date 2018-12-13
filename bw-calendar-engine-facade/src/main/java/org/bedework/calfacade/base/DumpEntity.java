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
package org.bedework.calfacade.base;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.Dump.DumpFormat;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.annotations.NoWrap;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.xml.XmlEmit;

import net.fortuna.ical4j.vcard.VCard;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import javax.xml.namespace.QName;

/** An entity which can be dumped.
 *
 * @author Mike Douglass
 * @version 1.0
 *
 * @param <T>
 */
public class DumpEntity<T> implements Logged {
  /** We're dumping the entire object */
  public enum DumpType {
    /** We're dumping the entire object */
    def,

    /** We're dumping a compound type */
    compound,

    /** We're dumping enough to refer to an entity */
    reference
  }

  /** Override this if we want to optionally suppress the dump based on some
   * attributes. This allows us to skip empty objects which occassionally turn
   * up.
   *
   * @return boolean true to continue with dump.
   * @throws CalFacadeException
   */
  @NoWrap
  public boolean hasDumpValue() throws CalFacadeException {
    return true;
  }

  /** Dump the entire entity into the given file.
   *
   * @param f - the file
   * @throws CalFacadeException
   */
  @NoWrap
  public void dump(final File f) throws CalFacadeException {
    Dump dCl = getClass().getAnnotation(Dump.class);

    if (dCl.format() == DumpFormat.xml) {
      Writer wtr = null;

      try {
        XmlEmit xml = new XmlEmit();
        wtr = new FileWriter(f);
        xml.startEmit(wtr);

        dump(xml, DumpType.def, false);
        xml.flush();
        return;
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      } finally {
        if (wtr != null) {
          try {
            wtr.close();
          } catch (Throwable t) {
            throw new CalFacadeException(t);
          }
        }
      }
    }

    if (dCl.format() == DumpFormat.vCard) {
      Writer wtr = null;

      try {
        VCard vc = new VCard();

        dump(vc, DumpType.def);

        String vcStr = vc.toString();
        wtr = new FileWriter(f);
        wtr.append(vcStr);
        return;
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      } finally {
        if (wtr != null) {
          try {
            wtr.close();
          } catch (Throwable t) {
            throw new CalFacadeException(t);
          }
        }
      }
    }

    throw new CalFacadeException("Unsupported dump format " + dCl.format());
  }


  /** Dump the entire entity.
   *
   * @param xml
   * @throws CalFacadeException
   */
  @NoWrap
  public void dump(final XmlEmit xml) throws CalFacadeException {
    dump(xml, DumpType.def, false);
  }

  /* ====================================================================
   *                   Private XML methods
   * ==================================================================== */

  /** Dump this entity as xml.
   *
   * @param xml
   * @param dtype
   * @param fromCollection  true if the value is a member of a collection
   * @throws CalFacadeException
   */
  @NoWrap
  private void dump(final XmlEmit xml, final DumpType dtype,
                   final boolean fromCollection) throws CalFacadeException {
    if (!hasDumpValue()) {
      return;
    }

    NoDump ndCl = getClass().getAnnotation(NoDump.class);
    Dump dCl = getClass().getAnnotation(Dump.class);

    boolean dumpKeyFields = dtype == DumpType.reference;

    ArrayList<String> noDumpMethods = null;
    ArrayList<String> firstMethods = null;

    try {
      if (ndCl != null) {
        if (ndCl.value().length == 0) {
          return;
        }

        noDumpMethods = new ArrayList<String>();
        for (String m: ndCl.value()) {
          noDumpMethods.add(m);
        }
      }

      if (!dumpKeyFields && (dCl != null) && (dCl.firstFields().length != 0)) {
        firstMethods = new ArrayList<String>();
        for (String f: dCl.firstFields()) {
          firstMethods.add(methodName(f));
        }
      }

      QName qn = null;

      if (fromCollection || (dtype != DumpType.compound)) {
        qn = startElement(xml, getClass(), dCl);
      }

      Collection<ComparableMethod> ms = findGetters(dCl, dtype);

      if (firstMethods != null) {
        doFirstMethods:
        for (String methodName: firstMethods) {
          for (ComparableMethod cm: ms) {
            Method m = cm.m;

            if (methodName.equals(m.getName())) {
              Dump d = m.getAnnotation(Dump.class);

              dumpValue(xml, m, d, m.invoke(this, (Object[])null), fromCollection);

              continue doFirstMethods;
            }
          }

          error("Listed first field has no corresponding getter: " + methodName);
        }
      }

      for (ComparableMethod cm: ms) {
        Method m = cm.m;

        if ((noDumpMethods != null) &&
            noDumpMethods.contains(fieldName(m.getName()))) {
          continue;
        }

        if ((firstMethods != null) &&
            firstMethods.contains(m.getName())) {
          continue;
        }

        Dump d = m.getAnnotation(Dump.class);

        dumpValue(xml, m, d, m.invoke(this, (Object[])null), fromCollection);
      }

      if (qn != null) {
        closeElement(xml, qn);
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private boolean dumpValue(final XmlEmit xml, final Method m, final Dump d,
                            final Object methVal,
                            final boolean fromCollection) throws Throwable {
    /* We always open the methodName or elementName tag if this is the method
     * value.
     *
     * If this is an element from a collection we generally don't want a tag.
     *
     * We do open a tag if the annottaion specifies a collectionElementName
     */
    if (methVal instanceof DumpEntity) {
      DumpEntity de = (DumpEntity)methVal;

      if (!de.hasDumpValue()) {
        return false;
      }

      boolean compound = (d!= null) && d.compound();

      QName mqn = startElement(xml, m, d, fromCollection);

      DumpType dt;
      if (compound) {
        dt = DumpType.compound;
      } else {
        dt = DumpType.reference;
      }

      de.dump(xml, dt, false);

      if (mqn != null) {
        closeElement(xml, mqn);
      }

      return true;
    }

    if (methVal instanceof Collection) {
      Collection c = (Collection)methVal;

      if (c.isEmpty()) {
        return false;
      }

      QName mqn = null;

      for (Object o: c) {
        if ((o instanceof DumpEntity) &&
            (!((DumpEntity)o).hasDumpValue())) {
          continue;
        }

        if (mqn == null) {
          mqn = startElement(xml, m, d, fromCollection);
        }

        dumpValue(xml, m, d, o, true);
      }

      if (mqn != null) {
        closeElement(xml, mqn);
      }

      return true;
    }

    property(xml, m, d, methVal, fromCollection);

    return true;
  }

  private QName startElement(final XmlEmit xml, final Class c, final Dump d) throws CalFacadeException {
    try {
      QName qn;

      if (d == null) {
        qn = new QName(c.getName());
      } else {
        qn = new QName(d.elementName());
      }

      xml.openTag(qn);
      return qn;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private QName startElement(final XmlEmit xml, final Method m, final Dump d,
                             final boolean fromCollection) throws CalFacadeException {
    try {
      QName qn = getTag(m, d, fromCollection);

      if (qn != null) {
        xml.openTag(qn);
      }

      return qn;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private QName getTag(final Method m, final Dump d,
                       final boolean fromCollection) {
    String tagName = null;

    if (d != null) {
      if (!fromCollection) {
        if (d.elementName().length() > 0) {
          tagName = d.elementName();
        }
      } else if (d.collectionElementName().length() > 0) {
        tagName = d.collectionElementName();
      }
    }

    if ((tagName == null) && !fromCollection) {
      tagName = fieldName(m.getName());
    }

    if (tagName == null) {
      return null;
    }

    return new QName(tagName);
  }

  private void property(final XmlEmit xml, final Method m,
                        final Dump d, final Object p,
                        final boolean fromCollection) throws CalFacadeException {
    if (p == null) {
      return;
    }

    try {
      QName qn = getTag(m, d, fromCollection);

      if (qn == null) {
        /* Collection and no collection element name specified */
        qn = new QName(p.getClass().getName());
      }

      String sval;

      if (p instanceof char[]) {
        sval = new String((char[])p);
      } else {
        sval = String.valueOf(p);
      }

      if ((sval.indexOf('&') < 0) && (sval.indexOf('<') < 0)) {
        xml.property(qn, sval);
      } else {
        xml.cdataProperty(qn, sval);
      }
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void closeElement(final XmlEmit xml, final QName qn) throws CalFacadeException {
    try {
      xml.closeTag(qn);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
   *                   Private Vcard methods
   * ==================================================================== */

  /** Dump this entity as a vcard.
   *
   * @param vc
   * @param dtype
   * @throws CalFacadeException
   */
  @NoWrap
  private void dump(final VCard vc,
                    final DumpType dtype) throws CalFacadeException {
    if (!hasDumpValue()) {
      return;
    }

    NoDump ndCl = getClass().getAnnotation(NoDump.class);
    Dump dCl = getClass().getAnnotation(Dump.class);

    /* If dumpKeyFields is true we are dumping a field which is referred to by
     * a key field - for example, a principal is addressed by the principal
     * href.
     */
    boolean dumpKeyFields = dtype == DumpType.reference;

    ArrayList<String> noDumpMethods = null;
    ArrayList<String> firstMethods = null;

    try {
      if (ndCl != null) {
        if (ndCl.value().length == 0) {
          return;
        }

        noDumpMethods = new ArrayList<String>();
        for (String m: ndCl.value()) {
          noDumpMethods.add(m);
        }
      }

      if (!dumpKeyFields && (dCl != null) && (dCl.firstFields().length != 0)) {
        firstMethods = new ArrayList<String>();
        for (String f: dCl.firstFields()) {
          firstMethods.add(methodName(f));
        }
      }

      QName qn = null;

      /*
      if (dtype != DumpType.compound) {
        qn = startElement(xml, getClass(), dCl);
      }

      Collection<ComparableMethod> ms = findGetters(dCl, dtype);

      if (firstMethods != null) {
        doFirstMethods:
        for (String methodName: firstMethods) {
          for (ComparableMethod cm: ms) {
            Method m = cm.m;

            if (methodName.equals(m.getName())) {
              Dump d = m.getAnnotation(Dump.class);

              dumpValue(xml, m, d, m.invoke(this, (Object[])null), fromCollection);

              continue doFirstMethods;
            }
          }

          error("Listed first field has no corresponding getter: " + methodName);
        }
      }

      for (ComparableMethod cm: ms) {
        Method m = cm.m;

        if ((noDumpMethods != null) &&
            noDumpMethods.contains(fieldName(m.getName()))) {
          continue;
        }

        if ((firstMethods != null) &&
            firstMethods.contains(m.getName())) {
          continue;
        }

        Dump d = m.getAnnotation(Dump.class);

        dumpValue(xml, m, d, m.invoke(this, (Object[])null), fromCollection);
      }

      if (qn != null) {
        closeElement(xml, qn);
      }
      */
//    } catch (CalFacadeException cfe) {
//      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private static class ComparableMethod implements Comparable<ComparableMethod> {
    Method m;

    ComparableMethod(final Method m) {
      this.m = m;
    }

    @Override
    public int compareTo(final ComparableMethod that) {
      return this.m.getName().compareTo(that.m.getName());
    }
  }

  private Collection<ComparableMethod> findGetters(final Dump d,
                                                   final DumpType dt) throws CalFacadeException {
    Method[] meths = getClass().getMethods();
    Collection<ComparableMethod> getters = new TreeSet<ComparableMethod>();
    Collection<String> keyMethods = null;

    if (dt == DumpType.reference) {
      if ((d == null) || (d.keyFields().length == 0)) {
        error("No key fields defined for class " + getClass().getCanonicalName());
        throw new CalFacadeException(CalFacadeException.noKeyFields);
      }
      keyMethods = new ArrayList<String>();
      for (String f: d.keyFields()) {
        keyMethods.add(methodName(f));
      }
    }

    for (int i = 0; i < meths.length; i++) {
      Method m = meths[i];

      String mname = m.getName();

      if (mname.length() < 4) {
        continue;
      }

      /* Name must start with get */
      if (!mname.startsWith("get")) {
        continue;
      }

      /* Don't want getClass */
      if (mname.equals("getClass")) {
        continue;
      }

      /* No parameters */
      Class[] parClasses = m.getParameterTypes();
      if (parClasses.length != 0) {
        continue;
      }

      /* Not annotated with NoDump */
      if (m.getAnnotation(NoDump.class) != null) {
        continue;
      }

      /* If we have a list of key methods it must be in that list */
      if ((keyMethods != null) && !keyMethods.contains(mname)) {
        continue;
      }

      getters.add(new ComparableMethod(m));
    }

    return getters;
  }

  private String methodName(final String val) {
    String m = "get" + val.substring(0, 1).toUpperCase();
    if (val.length() > 1) {
      m += val.substring(1);
    }

    return m;
  }

  private String fieldName(final String val) {
    if (val.length() < 4) {
      return null;
    }

    return val.substring(3, 4).toLowerCase() + val.substring(4);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
