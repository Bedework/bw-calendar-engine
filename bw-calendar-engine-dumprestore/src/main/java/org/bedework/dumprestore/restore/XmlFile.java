package org.bedework.dumprestore.restore;

import org.bedework.dumprestore.Utils;
import org.bedework.util.xml.XmlOutput;
import org.bedework.util.xml.XmlUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;

/** Represent an xml file in a directory.
 *
 * @author douglm
 */
public class XmlFile {
  protected final Document doc;
  protected final File theXml;
  protected final Element root;

  public XmlFile(final File dir,
                 final String name,
                 final boolean nameSpaced) throws Throwable {
    this(Utils.file(dir, name, true), nameSpaced);
  }

  public XmlFile(final File f,
                 final boolean nameSpaced) throws Throwable {
    theXml = f;
    doc = Utils.parseXml(new FileReader(f),
                         nameSpaced);

    root = doc.getDocumentElement();
  }

  public XmlFile(final String path,
                 final boolean nameSpaced) throws Throwable {
    this(Utils.file(path), nameSpaced);
  }

  public void output() throws Throwable {
    final OutputStream out = new FileOutputStream(theXml, false);

    XmlOutput.printDocument(doc, out);
  }

  public Element getRoot() {
    return doc.getDocumentElement();
  }
  
  public Element findElement(final Element root,
                             final String tagName) throws Throwable {
    for (final Element el: XmlUtil.getElementsArray(root)) {
      if (tagName.equals(el.getTagName())) {
        return el;
      }
    }

    return null;
  }
}
