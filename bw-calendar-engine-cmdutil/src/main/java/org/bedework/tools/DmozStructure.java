package org.bedework.tools;

import org.bedework.util.args.Args;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class DmozStructure {
  String inFileName;

  String outDirName;

  boolean create;

  static class Topic {
    String name; // canonical

    String path;

    Map<String, String> displayNames = new HashMap<String, String>();

    Map<String, String> descriptions = new HashMap<String, String>();

    Set<String> narrows = new TreeSet<String>();
    Set<String> narrow1s = new TreeSet<String>();
    Set<String> narrow2s = new TreeSet<String>();
  }

  static class LangRef {
    String lang;
    Topic topic;

    LangRef(String lang, Topic topic) {
      this.lang = lang;
      this.topic =topic;
    }
  }

  private Map<String, Topic> topics = new HashMap<String, Topic>();
  private Map<String, LangRef> altLangs = new HashMap<String, LangRef>();

  private QName skippingElement;

  private Topic curTopic;
  private StringBuilder curText;
  private String curLang;

  private boolean doingAltLang;

  private final static String defLang = "DEF";

  private static String dmozns = "http://dmoz.org/rdf/";
  private static String w3ns = "http://www.w3.org/TR/RDF/";
  private static String purlns = "http://purl.org/dc/elements/1.0/";

  private static QName qnTopic = new QName(dmozns, "Topic");
  private static QName qnTopicIdAttr = new QName(w3ns, "id");

  private static QName qnDescription = new QName(purlns, "Description");
  private static QName qnTitle = new QName(purlns, "Title");

  private static QName qnAltlang = new QName(dmozns, "altlang");
  private static QName qnAltlang1 = new QName(dmozns, "altlang1");

  private static QName qnNarrow = new QName(dmozns, "narrow");
  private static QName qnNarrow1 = new QName(dmozns, "narrow1");
  private static QName qnNarrow2 = new QName(dmozns, "narrow2");

  private static QName qnResource = new QName(w3ns, "resource");

  /* Keep if id starts with one of these */
  private static List<String> keepList = new ArrayList<String>();

  /* Skip if id starts with one of these */
  private static List<String> skipList = new ArrayList<String>();

  /* Skip if id ends with one of these */
  private static List<String> skipEndList = new ArrayList<String>();

  /* Skip if id starts with one of these + a single path element and "/" */
  private static List<String> skip1elementFollowingList = new ArrayList<String>();

  /* Skip if id ends with one of these + a single character path element */
  private static List<String> skip1charList = new ArrayList<String>();

  /* Skip if id starts with one of these + a single character path element + "/" */
  private static List<String> skip1charFollowingList = new ArrayList<String>();

  /* Elements we skip */
  private static Map<QName, String> skipEls = new HashMap<QName, String>();

  static {
    skipEls.put(new QName(dmozns, "Alias"), "skip");
    skipEls.put(new QName(dmozns, "catid"), "skip");
    skipEls.put(new QName(dmozns, "editor"), "skip");
    skipEls.put(new QName(dmozns, "lastUpdate"), "skip");
    skipEls.put(new QName(dmozns, "letterbar"), "skip");
    skipEls.put(new QName(dmozns, "newsgroup"), "skip");
    skipEls.put(new QName(dmozns, "related"), "skip");
    skipEls.put(new QName(dmozns, "symbolic"), "skip");
    skipEls.put(new QName(dmozns, "symbolic1"), "skip");
    skipEls.put(new QName(dmozns, "symbolic2"), "skip");
  }

  /* Elements with no end processing */
  private static Map<QName, String> noEndEls = new HashMap<QName, String>();

  static {
    noEndEls.put(qnNarrow, "noend");
    noEndEls.put(qnNarrow1, "noend");
    noEndEls.put(qnNarrow2, "noend");
    noEndEls.put(qnAltlang, "noend");
    noEndEls.put(qnAltlang1, "noend");
  }

  private Map<String, String> langs = new HashMap<String, String>();

  boolean process() throws Throwable {
    File in = new File(inFileName);

    File out = null;

    if (create) {
      out = new File(outDirName);

      if (!out.isDirectory()) {
        error("Not a directory: " + outDirName);
        return false;
      }
    }

    if (!in.isFile()) {
      error("Not a file: " + inFileName);
      return false;
    }

    if (!processInFile(in)) {
      error("Failed?");
    }

    info("topics: " + topics.keySet().size());
    info("altLangs: " + altLangs.keySet().size());

    /*
    Set<String> sorted = new TreeSet(langs.keySet());

    info("Languages:");

    for (String s: sorted) {
      info(s + ": " + langs.get(s));
    }
    */

    if (!create) {
      return true;
    }

    Set<String> sortedTopics = new TreeSet<String>(topics.keySet());

    if (!outDirName.endsWith("/")) {
      outDirName += "/";
    }

    info("Create directory structure under " + outDirName);

    for (String s: sortedTopics) {
      String path = outDirName + s;

      File dir = new File(path);

      dir.mkdir();
    }

    return true;
  }

  boolean processInFile(File f) throws Throwable {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    Reader fileReader = new FileReader(f);
    XMLEventReader reader = factory.createXMLEventReader(fileReader);

    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();

      if (skippingElement != null) {
        if (event.isEndElement()) {
          QName elname = event.asEndElement().getName();

          if (elname.equals(skippingElement)) {
            skippingElement = null;
          }
        }

        continue;
      }

      if (event.isStartElement()) {
        processStartElement(reader, event.asStartElement());

        continue;
      }

      if (event.isEndElement()) {
        processEndElement(reader, event.asEndElement());

        continue;
      }

      if (event.isCharacters()) {
        String s = event.asCharacters().getData();

        if (curText != null) {
          curText.append(s);
          continue;
        }

        if (s.trim().length() == 0) {
          continue;
        }

        info("Text: " + s);

        continue;
      }
    }
    return true;
  }

  void processStartElement(XMLEventReader reader,
                           StartElement element) {
    QName elname = element.getName();

    if (skipEls.containsKey(elname)) {
      skippingElement = elname;
      return;
    }

    if (elname.equals(qnTopic)) {
      processTopicStart(element);
      return;
    }

    if (elname.equals(qnAltlang)) {
      processAltLang(element);
      return;
    }

    if (elname.equals(qnAltlang1)) {
      processAltLang(element);
      return;
    }

    if (elname.equals(qnDescription)) {
      curText = new StringBuilder();

      return;
    }

    if (elname.equals(qnTitle)) {
      curText = new StringBuilder();

      return;
    }

    if (elname.equals(qnNarrow)) {
      if (curTopic != null) {
        curTopic.narrows.add(getResource(element));
      }

      return;
    }

    if (elname.equals(qnNarrow1)) {
      if (curTopic != null) {
        curTopic.narrow1s.add(getResource(element));
      }

      return;
    }

    if (elname.equals(qnNarrow2)) {
      if (curTopic != null) {
        curTopic.narrow2s.add(getResource(element));
      }

      return;
    }

    info("Start Element: " + elname);

    Iterator iterator = element.getAttributes();
    while (iterator.hasNext()) {
      Attribute attribute = (Attribute) iterator.next();
      QName name = attribute.getName();
      String value = attribute.getValue();
      System.out.println("Attribute name/value: " + name + "/" + value);
    }
  }

  void processEndElement(XMLEventReader reader,
                           EndElement element) {
    QName elname = element.getName();

    if (skipEls.containsKey(elname) || noEndEls.containsKey(elname)) {
      return;
    }

    if (elname.equals(qnTopic)) {
      curTopic = null;
      doingAltLang = false;

      return;
    }

    if (elname.equals(qnDescription)) {
      if ((curText != null) && (curTopic != null)) {
        if (curLang == null) {
          curTopic.descriptions.put(defLang, curText.toString());
        } else {
          curTopic.descriptions.put(curLang, curText.toString());
        }
      }

      curText = null;

      return;
    }

    if (elname.equals(qnTitle)) {
      String s = curText.toString();
      if ((curText != null) && (curTopic != null)) {
        curTopic.name = s;
      }

      if (curLang == null) {
        curTopic.displayNames.put(defLang, s);
      } else {
        curTopic.displayNames.put(curLang, s);
      }

      curText = null;

      return;
    }

    System.out.println("End element:" + element.getName());
  }

  void processTopicStart(StartElement element) {
    String id = getAttr(element, qnTopicIdAttr);

    if (topics.get(id) != null) {
      error("Duplicate topic " + id);
    }

    id = fixTopicName(id);
    if (id == null) {
      skippingElement = qnTopic;
      return;
    }

    if (topics.get(id) != null) {
      // Probably fine
      skippingElement = qnTopic;
      return;
    }

    LangRef lr = altLangs.get(id);
    if (lr != null) {
      /* This is a topic referenced as an altlang. Just switch to that as the
       * topic but set the curLang
       */

      curTopic = lr.topic;
      curLang = lr.lang;
      doingAltLang = true;

      return;
    }

    if (!checkSkips(id)) {
      skippingElement = qnTopic;
      return;
    }

    curTopic = new Topic();
    curTopic.path = id;

    topics.put(id, curTopic);

    info(id);
  }

  boolean checkSkips(String id) {

    for (String s: keepList) {
      if (id.equals(s)) {
        return true;
      }
    }

    for (String s: skipEndList) {
      if (id.endsWith(s)) {
        return false;
      }
    }

    for (String s: skip1charList) {
      if (id.startsWith(s)) {
        for (int i = 0; i < skipSuffices.length(); i++) {
          String sfx = skipSuffices.substring(i, i + 1);
          String s1 = s + sfx;

          if (id.equals(s1)) {
            return false;
          }
        }
      }
    }

    for (String s: skip1elementFollowingList) {
      if (id.startsWith(s)) {
        if (id.indexOf("/", s.length()) > 0) {
          return false;
        }
      }
    }

    for (String s: skip1charFollowingList) {
      if (id.startsWith(s)) {
        for (int i = 0; i < skipSuffices.length(); i++) {
          String sfx = skipSuffices.substring(i, i + 1);
          String s1 = s + sfx;

          if (id.equals(s1) || (id.startsWith(s1 + "/"))) {
            return false;
          }
        }
      }
    }

    for (String s: skipList) {
      if (s.endsWith("/")) {
        if (id.startsWith(s)) {
          return false;
        }
      } else {
        if (id.equals(s) || (id.startsWith(s + "/"))) {
          return false;
        }
      }
    }

    return true;
  }

  String skipSuffices = "ABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890";

  void processAltLang(StartElement element) {
    if (curTopic == null) {
      return;
    }

    String id = getResource(element);
    int pos = id.indexOf(':');

    if (pos < 0) {
      error("No ':' in " + id);
      return;
    }

    String lang = id.substring(0, pos);
    id = fixTopicName(id.substring(pos + 1));

    altLangs.put(id, new LangRef(lang, curTopic));

    if (!langs.containsKey(lang)) {
      langs.put(lang, null);
    }
  }

  String fixTopicName(String val) {
    int pos = val.lastIndexOf('/');

    if (pos < 0) {
      return val;
    }

    if (val.length() - pos == 2) {
      // Don't want 1 char element
      return null;
    }

    return val.replaceFirst("/\\p{Upper}/", "/");
  }

  String getAttr(StartElement element, QName qn) {
    Attribute attr = element.getAttributeByName(qn);

    return attr.getValue();
  }

  String getResource(StartElement element) {
    return getAttr(element, qnResource);
  }

  void processSkips(File f) throws Throwable {
    FileReader fr = new FileReader(f);
    LineNumberReader lnr = new LineNumberReader(fr);

    for (;;) {
      String ln = lnr.readLine();

      if (ln == null) {
        break;
      }

      ln = ln.trim();

      if ((ln.length() == 0) || ln.startsWith("#")) {
        continue;
      }

      if (ln.startsWith("+")) {
        ln = ln.substring(1);

        keepList.add(ln);

        continue;
      }

      if (ln.startsWith("//*//")) {
        ln = ln.substring(4); // Leave one slash

        skipEndList.add(ln);

        continue;
      }

      if (ln.endsWith("?/")) {
        ln = ln.substring(0, ln.length() - 2);

        skip1elementFollowingList.add(ln);

        continue;
      }

      if (ln.endsWith("*1*")) {
        ln = ln.substring(0, ln.length() - 3);

        skip1charList.add(ln);

        continue;
      }

      if (ln.endsWith("*1*/")) {
        ln = ln.substring(0, ln.length() - 4);

        skip1charFollowingList.add(ln);

        continue;
      }

      skipList.add(ln);
    }
  }

  boolean processArgs(final Args args) throws Throwable {
    if (args == null) {
      return true;
    }

    while (args.more()) {
      if (args.ifMatch("")) {
        continue;
      }

      if (args.ifMatch("-i")) {
        inFileName = args.next();
      } else if (args.ifMatch("-o")) {
        outDirName = args.next();
      } else if (args.ifMatch("-c")) {
        create = true;
      } else if (args.ifMatch("-sf")) {
        String sfName = args.next();
        File sf = new File(sfName);

        if (!sf.isFile()) {
          error("Not a file: " + sfName);
          return false;
        }

        processSkips(sf);
      } else {
        error("Illegal argument: " + args.current());
        usage();
        return false;
      }
    }

    return true;
  }

  protected void info(final String msg) {
    System.out.println(msg);
  }

  protected void error(final String msg) {
    System.err.println(msg);
  }

  void usage() {
    info("Usage:");
    info("args   -i <file>");
    info("            specify dmoz file containing input");
    info("       -o <dirname>");
    info("            specify directory containing result");
    info("       -sf <file>");
    info("            specify file containing skip paths");
    info("       -c");
    info("            create directory structure");
    info("");
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    DmozStructure ds = null;

    try {
      ds = new DmozStructure();

      if (!ds.processArgs(new Args(args))) {
        return;
      }

      ds.process();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

}
