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
package org.bedework.tools.xmlutil;

import org.bedework.schemas.licenses.JarLicenseType;
import org.bedework.schemas.licenses.JarLicensesType;
import org.bedework.util.args.Args;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

/** Print out licence info.
 *
 *   @author Mike Douglass
 */
public class LicensesUtil implements Logged {
  private String infileName;

  private String outfileName;

  private static class JarKey implements Comparable<JarKey> {
    protected String name;
    protected String version;

    JarKey(final JarLicenseType jl) {
      name = jl.getName();
      version = jl.getVersion();
    }

    String getName() {
      return name;
    }

    String getVersion() {
      return version;
    }

    public String printable() {
      StringBuilder sb = new StringBuilder(getName());

      if (getVersion() != null) {
        sb.append("-");
        sb.append(getVersion());
      }

      return sb.toString();
    }

    @Override
    public int compareTo(final JarKey that) {
      if (this == that) {
        return 0;
      }

      int res = Util.compareStrings(getName(), that.getName());

      if (res != 0) {
        return res;
      }

      return Util.compareStrings(getVersion(), that.getVersion());
    }

    @Override
    public int hashCode() {
      int hc = getName().hashCode();

      if (getVersion() != null) {
        hc *= getVersion().hashCode();
      }

      return hc;
    }

    @Override
    public boolean equals(final Object o) {
      return compareTo((JarKey)o) == 0;
    }
  }

  class FromMap extends TreeMap<String, JarLicenseType>{};

  /**
   * @return true if processing went ok
   * @throws Throwable
   */
  public boolean process() throws Throwable {
    JarLicensesType jls = null;

    if (infileName != null) {
      InputStream in = new FileInputStream(new File(infileName));

      jls = unmarshal(in).getValue();
    }

    if (jls == null) {
      System.err.println("No license information");
      return false;
    }

    OutputStream out;

    if (outfileName == null) {
      out = System.out;
    } else {
      out = new FileOutputStream(new File(outfileName));
    }

    Map<String, FromMap> byLicense = new HashMap<String, FromMap>();

    SortedMap<JarKey, FromMap> byJar = new TreeMap<JarKey, FromMap>();

    for (JarLicenseType jl: jls.getJarLicense()) {
      addJl(jl.getLicense(), jl, byLicense);

      JarKey jk = new JarKey(jl);
      if (jl.getVersion() != null) {
        addJl(jk, jl, byJar);
      } else {
        addJl(jk, jl, byJar);
      }
    }

//    marshal(tai, out);

    PrintStream ps = new PrintStream(out);

    for (JarKey jk: byJar.keySet()) {
      ps.print("Jar: " + jk.printable());

      boolean first = true;
      FromMap fm = byJar.get(jk);

      for (JarLicenseType jl: fm.values()) {
        if (first) {
          ps.println("   Licence: " + jl.getLicense());
          ps.println("Referenced by the following components:");
          first = false;
        }

        ps.println("   " + jl.getFrom());
      }
      ps.println();
    }

    out.close();

    return true;
  }

  private <T> void addJl(final T key,
                     final JarLicenseType jl,
                     final Map<T, FromMap> jlMap) {
    FromMap fm = jlMap.get(key);

    if (fm == null) {
      fm = new FromMap();

      jlMap.put(key, fm);
    }

    fm.put(jl.getFrom(), jl);
  }

  /** Main
   *
   * @param args
   */
  public static void main(final String[] args) {
    LicensesUtil lu = null;

    try {
      lu = new LicensesUtil();

      if (!lu.processArgs(new Args(args))) {
        return;
      }

      lu.process();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  @SuppressWarnings("unchecked")
  protected void marshal(final JarLicensesType jls,
                         final OutputStream out) throws Throwable {
    JAXBContext contextObj = JAXBContext.newInstance(jls.getClass());

    Marshaller marshaller = contextObj.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

    //ObjectFactory of = new ObjectFactory();
    marshaller.marshal(new JAXBElement(new QName("http://bedework.org/schemas/licenses",
                                                 "JarLicensesType"),
                                                 jls.getClass(), jls), out);
  }

  protected JAXBElement<JarLicensesType> unmarshal(final InputStream in) throws Throwable {
    JAXBContext jc = JAXBContext.newInstance(JarLicensesType.class,
                                             JarLicenseType.class);
    Unmarshaller u = jc.createUnmarshaller();
    return u.unmarshal(new StreamSource(in), JarLicensesType.class);
  }

  private boolean processArgs(final Args args) throws Throwable {
    if (args == null) {
      return true;
    }

    while (args.more()) {
      if (args.ifMatch("")) {
        continue;
      }

      if (args.ifMatch("-f")) {
        infileName = args.next();
      } else if (args.ifMatch("-o")) {
        outfileName = args.next();
      } else {
        error("Illegal argument: " + args.current());
        usage();
        return false;
      }
    }

    return true;
  }

  private void usage() {
    System.out.println("Usage:");
    System.out.println("With the appropriate options the build generates");
    System.out.println("jar references in bedework/dist/jarlicenses.xml");
    System.out.println("Create a copy of that file with the content wrapped");
    System.out.println("with the following:");
    System.out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    System.out.println("<jarLicenses xmlns=\"http://bedework.org/schemas/licenses\">");
    System.out.println("...");
    System.out.println("</jarLicenses>");
    System.out.println("");
    System.out.println("That provides input for this utility.");
    System.out.println("The output is a sorted list of jars, their licences");
    System.out.println("and which components use them.");
    System.out.println("");
    System.out.println("args   -f <filename>");
    System.out.println("            specify file containing xml data");
    System.out.println("       -o <filename>");
    System.out.println("            specify file for processed output");
    System.out.println("");
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
