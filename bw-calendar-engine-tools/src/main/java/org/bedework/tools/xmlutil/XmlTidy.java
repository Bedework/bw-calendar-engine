package org.bedework.tools.xmlutil;

import org.apache.commons.io.FileUtils;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.File;
import java.io.FileOutputStream;

/**
 * @author douglm
 */
public class XmlTidy {
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception {
		String text = FileUtils.readFileToString(new File(args[0]));
		OutputFormat format = OutputFormat.createPrettyPrint();
		format.setTrimText(false);

		//XMLWriter writer = new XMLWriter(System.out, format);
    XMLWriter writer = new XMLWriter(new FileOutputStream(args[1]), format);
		writer.write(DocumentHelper.parseText(text));
	}
}
