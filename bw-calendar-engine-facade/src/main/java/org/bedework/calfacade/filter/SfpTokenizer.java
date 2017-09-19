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
package org.bedework.calfacade.filter;

import org.bedework.calfacade.exc.CalFacadeBadRequest;
import org.bedework.calfacade.exc.CalFacadeException;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.regex.Pattern;

/**
 * @author douglm
 *
 */
public class SfpTokenizer extends StreamTokenizer {
  private transient Logger log;

  private boolean debug;

  private static final int WORD_CHAR_START = 32;

  private static final int WORD_CHAR_END = 255;

  private static final int WHITESPACE_CHAR_START = 0;

  private static final int WHITESPACE_CHAR_END = ' ';

  private static final Pattern quotePattern = Pattern.compile("([\"'])");

  /**
   * @param rdr
   */
  public SfpTokenizer(final Reader rdr) {
    super(rdr);

    debug = getLogger().isDebugEnabled();

    lowerCaseMode(false);
    wordChars(WORD_CHAR_START, WORD_CHAR_END);
    whitespaceChars(WHITESPACE_CHAR_START,
                              WHITESPACE_CHAR_END);
    ordinaryChar('.');
    ordinaryChar(':');
    ordinaryChar(';');
    ordinaryChar(',');
    ordinaryChar('~');
    ordinaryChar('=');
    ordinaryChar('!');
    ordinaryChar('>');
    ordinaryChar('<');
    ordinaryChar('&');
    ordinaryChar('|');
    ordinaryChar('(');
    ordinaryChar(')');
    ordinaryChar('[');
    ordinaryChar(']');
    ordinaryChar('\t');
    eolIsSignificant(false);
    whitespaceChars(0, 0);

    quoteChar('"');
    quoteChar('\'');
  }

  /**
   * @return int
   * @throws CalFacadeException
   */
  public int next() throws CalFacadeException {
    try {
      return nextToken();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  /**
   * Asserts that the next token in the stream matches the specified token.
   *
   * @param token expected token
   * @throws CalFacadeException
   */
  public void assertToken(final int token) throws CalFacadeException {
    try {
      if (nextToken() != token) {
        throw new CalFacadeBadRequest("Expected [" + token + "], read [" +
                                  ttype + "] at " + lineno());
      }

      if (debug) {
        if (token > 0) {
          debugMsg("[" + (char)token + "]");
        } else {
          debugMsg("[" + token + "]");
        }
      }
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  /**
   * @throws CalFacadeException
   */
  public void assertWord() throws CalFacadeException {
    assertToken(StreamTokenizer.TT_WORD);
  }

  /**
   * @throws CalFacadeException
   */
  public void assertString() throws CalFacadeException {
    if (testToken('"') || testToken('\'')) {
      return;
    }

    throw new CalFacadeBadRequest("Expected <quoted-string>, read [" +
                                  ttype + "] at " + lineno());
  }

  /**
   * @return true if it's a quoted string
   * @throws CalFacadeException
   */
  public boolean testString() throws CalFacadeException {
    return testToken('"') || testToken('\'');
  }

  /**
   * Asserts that the next token in the stream matches the specified token.
   * This method is case-sensitive.
   *
   * @param token
   * @throws CalFacadeException
   */
  public void assertToken(final String token) throws CalFacadeException {
    assertToken(token, false);
  }

  /**
   * Asserts that the next token in the stream matches the specified token.
   *
   * @param token expected token
   * @param ignoreCase
   * @throws CalFacadeException
   */
  public void assertToken(final String token, final boolean ignoreCase) throws CalFacadeException {
    // ensure next token is a word token..
    assertWord();

    if (ignoreCase) {
      if (!token.equalsIgnoreCase(sval)) {
        throw new CalFacadeBadRequest("Expected [" + token + "], read [" +
                                  sval + "] at " + lineno());
      }
    } else if (!token.equals(sval)) {
      throw new CalFacadeBadRequest( "Expected [" + token + "], read [" +
                                sval + "] at " + lineno());
    }

    if (debug) {
      debugMsg("[" + token + "]");
    }
  }

  /**
   * @return boolean true if eof flagged
   */
  public boolean atEof() {
    return ttype == StreamTokenizer.TT_EOF;
  }

  /**
   * Tests that the next token in the stream matches the specified token.
   * This method is case-sensitive.
   *
   * @param token
   * @return boolean
   * @throws CalFacadeException
   */
  public boolean testToken(final int token) throws CalFacadeException {
    try {
      boolean res = nextToken() == token;

      if (!res) {
        pushBack();
        return false;
      }

      return true;
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  /**
   * Tests if the next token in the stream matches the specified token.
   *
   * @param token expected token
   * @return int
   * @throws CalFacadeException
   */
  public boolean testToken(final String token) throws CalFacadeException {
    return testToken(token, true);
  }

  /**
   * Tests if the next token in the stream matches the specified token.
   *
   * @param token expected token
   * @param ignoreCase
   * @return boolean
   * @throws CalFacadeException
   */
  public boolean testToken(final String token, final boolean ignoreCase) throws CalFacadeException {
    // ensure next token is a word token..
    if (!testToken(StreamTokenizer.TT_WORD)) {
      return false;
    }

    if (ignoreCase) {
      if (!token.equalsIgnoreCase(sval)) {
        pushBack();
        return false;
      }
    } else if (!token.equals(sval)) {
      pushBack();
      return false;
    }

    return true;
  }

  /**
   * Absorbs extraneous newlines.
   * @throws CalFacadeException
   */
  public void skipWhitespace() throws CalFacadeException {
    while (true) {
      assertToken(StreamTokenizer.TT_EOL);
    }
  }

  public static String escapeQuotes(String s) {
         return quotePattern.matcher(s).replaceFirst("\\\\$1");
  }

  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void logIt(final String msg) {
    getLogger().info(msg);
  }

}
