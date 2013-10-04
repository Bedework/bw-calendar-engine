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

import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.parse.Filters;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import ietf.params.xml.ns.caldav.ParamFilterType;
import ietf.params.xml.ns.caldav.TextMatchType;
import net.fortuna.ical4j.model.DateTime;
import org.apache.log4j.Logger;

import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;

/** This is a simple filter parser to allow us to embed filter expressions in the
 * request stream. This is not implemented the correct way - we need a grammar
 * and allow for some greater complexity, however...
 *
 * <p>The expressions have very few token types, "(", ")", "&", "|", "=", "!=",
 * ", ", "&lt;", "&gt;", word, number and string value.
 *
 * <p>The word specifies a property.
 *
 * <p>For example
 *  <pre>entity_type="event" & owner="abcd" & (category="lecture" | category="music")
 *  </pre>
 *
 * <p>The entity_type term must be first.
 *
 * @author Mike Douglass
 *
 */
public abstract class SimpleFilterParser {
  private transient Logger log;

  private boolean debug;

  private SfpTokenizer tokenizer;
  private String currentExpr;

  private static class Token {
  }

  private static class OpenParenthesis extends Token {
    @Override
    public String toString() {
      return "OpenParenthesis{}";
    }
  }

  private static Token openParen = new OpenParenthesis();

  private final static int isDefined = 0;

  private final static int notDefined = 1;

  private final static int equal = 2;

  private final static int notEqual = 3;

  private final static int like = 4;

  private final static int notLike = 5;

  private final static int greaterThan = 6;

  private final static int lessThan = 7;

  private final static int greaterThanOrEqual = 8;

  private final static int lessThanOrEqual = 9;

  private final static int inTimeRange = 10;

  private final static int andOp = 11;

  private final static int orOp = 12;

  private static class Operator extends Token {
    int op;

    Operator(final int op) {
      this.op = op;
    }

    @Override
    public String toString() {
      return "Operator{op=" + op + "}";
    }
  }

  private static class LogicalOperator extends Operator {
    LogicalOperator(final int op) {
      super(op);
    }
  }

  private static final LogicalOperator andOperator = new LogicalOperator(andOp);

  private static final LogicalOperator orOperator = new LogicalOperator(orOp);

  private Stack<Token> stack = new Stack<Token>();

  private Stack<FilterBase> filterStack = new Stack<FilterBase>();

  /** */
  public static class ParseResult {
    /** true if the parse went ok */
    public boolean ok;

    /** result of a successful parse */
    public FilterBase filter;

    /**  */
    public CalFacadeException cfe;

    ParseResult(final FilterBase filter) {
      ok = true;
      this.filter = filter;
    }

    ParseResult(final CalFacadeException cfe) {
      this.cfe = cfe;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      sb.append("ParseResult[ok=");
      sb.append(ok);

      if (ok) {
        sb.append(" filter=");
        sb.append(filter);
      } else {
        sb.append(" errcode=");
        sb.append(cfe.getMessage());
      }

      sb.append("]");
      return sb.toString();
    }
  }

  /** An unsatisfactory approach - we'll special case categories for the moment
   * to see if this works. When using these filters we need to search for a
   * category being a member of the set of categories for the event.
   *
   * <p>This only works if the current user is the owner of the named category -
   * at least with my current implementation.
   *
   * @param name
   * @return category entity or null.
   * @throws CalFacadeException
   */
  public abstract BwCategory getCategoryByName(String name) throws CalFacadeException;

  /** A slightly better approach - we'll special case categories for the moment
   * to see if this works. When using these filters we need to search for a
   * category being a member of the set of categories for the event.
   *
   * <p>method takes the uid of the category and is used by the
   * <pre>
   *   catuid=(uid1,uid2,uid3)
   * </pre>
   * construct where the list members must ALL be present.
   *
   * @param uid of the category
   * @return category entity or null.
   * @throws CalFacadeException
   */
  public abstract BwCategory getCategory(String uid) throws CalFacadeException;

  /** Parse the given expression into a filter
   *
   * @param expr
   * @return ParseResult
   * @throws CalFacadeException
   */
  public ParseResult parse(final String expr) throws CalFacadeException {
    debug = getLogger().isDebugEnabled();

    try {
      if (debug) {
        debugMsg("About to parse filter expression: " + expr);
      }

      currentExpr = expr;
      tokenizer = new SfpTokenizer(new StringReader(expr));

      doExpr();

      /* We should have 0 or 1 logical operators on the stack and a single
       * filter expression (or none for a null filter)
       */

      if (topLOp()) {
        pop();
      }

      if (!stackEmpty()) {
        throw new CalFacadeException(CalFacadeException.filterSyntax);
      }

      /* We should be left with just the filter on the stack. */

      if (filterStack.size() != 1) {
        throw new CalFacadeException(CalFacadeException.filterSyntax);
      }

      FilterBase f = popFilters();

      if (debug) {
        debugMsg(f.toString());
      }

      return new ParseResult(f);
    } catch (CalFacadeException cfe) {
      if (debug) {
        error(cfe);
      }
      return new ParseResult(cfe);
    }
  }

  private boolean doFactor() throws CalFacadeException {
    if (debug) {
      debugMsg("doFactor: " + tokenizer.toString());
    }

    int tkn = nextToken("doFactor(1)");

    if (tkn == StreamTokenizer.TT_EOF) {
      return false;
    }

    if (tkn == '(') {
      push(openParen);
      doExpr();

      if (nextToken("doFactor(2)") != ')') {
        throw new CalFacadeException(CalFacadeException.filterExpectedCloseParen);
      }

      popOpenParen();
    } else {
      tokenizer.pushBack();
      doPropertyComparison();
    }

    if (!topLOp()) {
      return true;
    }

    FilterBase filter = popFilters();

    FilterBase topFilter = popFilters();
    if (anding()) {
      filterStack.push(FilterBase.addAndChild(topFilter, filter));
    } else {
      filterStack.push(FilterBase.addOrChild(topFilter, filter));
    }

    pop(); // The operator

    return true;
  }

  private boolean doExpr() throws CalFacadeException {
    // Don't seem quite right
    if (debug) {
      debugMsg("doExpr: " + tokenizer.toString());
    }
    return doTerm();
  }

  private boolean doTerm() throws CalFacadeException {
    if (!doFactor()) {
      return false;
    }

    if (debug) {
      debugMsg("doTerm: " + tokenizer.toString());
    }

    /* If we have a logical operator next then handle that and combine the
     * two filters on top of the stack.
     */

    int tkn = nextToken("doTerm()");

    if (tkn == StreamTokenizer.TT_EOF) {
      return false;
    }

    if ((tkn != '&') && (tkn != '|')) {
      tokenizer.pushBack();
      if (topLOp()) {
        FilterBase filter = popFilters();

        FilterBase topFilter = popFilters();
        if (anding()) {
          filterStack.push(FilterBase.addAndChild(topFilter, filter));
        } else {
          filterStack.push(FilterBase.addOrChild(topFilter, filter));
        }
        // Pop it - we used it to ensure all operators at the same level are the
        // same.
        pop();
      }
      return true;
    }

    doLop(tkn);

    doTerm();

    /* doPropertyComparison will do the anding/oring */
/*    Filter filter = popFilters();

    Filter topFilter = popFilters();
    if (anding()) {
      filterStack.push(Filter.addAndChild(topFilter, filter));
    } else {
      filterStack.push(Filter.addOrChild(topFilter, filter));
    }*/

    return true;
  }

  private void doLop(final int tkn) throws CalFacadeException {
    LogicalOperator oper = null;

    if (topLOp()) {
      oper = popLOp();
    }

    if (oper != null) {
      // Must match - not allowed to mix logical operators
      if (tkn == '&') {
        if (oper.op != andOp) {
          throw new CalFacadeException(CalFacadeException.filterMixedLogicalOperators);
        }
      } else if (oper.op != orOp) {
        throw new CalFacadeException(CalFacadeException.filterMixedLogicalOperators);
      }

      push(oper);
    } else if (tkn == '&') {
      push(andOperator);
    } else {
      push(orOperator);
    }
  }

  private void doPropertyComparison() throws CalFacadeException {
    int tkn = nextToken("doPropertyComparison()");

    if (tkn != StreamTokenizer.TT_WORD) {
      throw new CalFacadeException(CalFacadeException.filterExpectedPropertyName,
                                   String.valueOf(tkn));
    }

    String pname = tokenizer.sval;
    String pnameUc = pname.toUpperCase();

    boolean catuid = pnameUc.equals("CATUID");
    PropertyInfoIndex pi;

    if (catuid) {
      pi = PropertyInfoIndex.lookupPname("CATEGORIES"); // XXX do that properly
    } else {
      pi = PropertyInfoIndex.lookupPname(pnameUc);
    }

    if (pi == null) {
      throw new CalFacadeException(CalFacadeException.unknownProperty,
                                   pname + ": expr was " + currentExpr);
    }

    Operator oper = nextOperator();
    TimeRange tr = null;
    TextMatchType tm = null;

    if (oper.op == inTimeRange) {
      tr = getTimeRange();
    } else if ((oper.op != isDefined) && (oper.op != notDefined)) {
      if (!catuid) {
        // Expect a value
        tokenizer.assertString();

        tm = new TextMatchType();
        tm.setValue(tokenizer.sval);
        if (oper.op == notEqual) {
          tm.setNegateCondition("yes");
        } else {
          tm.setNegateCondition("no");
        }
        tm.setCollation("i;ascii-casemap");
      }
    }

    FilterBase pfilter = makePropFilter(pi, oper.op,
                                    tr,
                                    tm, null);

    if (pfilter == null) {
      throw new CalFacadeException(CalFacadeException.filterBadProperty, pname);
    }

    /* If there is a logical operator on the stack top (and/or) then we create
     * an anded or ored filter and push that.
     *
     * Otherwise we just push
     *
     * WRONG - should be done by doFactor
     */
//    if (!topLOp()) {
      filterStack.push(pfilter);
/*    } else {
      Filter topFilter = popFilters();
      if (anding()) {
        filterStack.push(Filter.addAndChild(topFilter, pfilter));
      } else {
        filterStack.push(Filter.addOrChild(topFilter, pfilter));
      }

      pop(); // The operator
    }*/
  }

  /** Enter with either
   * ( word [, word]* ) or
   * word
   *
   * @return list of word values
   * @throws CalFacadeException
   */
  private ArrayList<String> doWordList() throws CalFacadeException {
    int tkn = nextToken("doWordList(1)");

    if (tkn == StreamTokenizer.TT_EOF) {
      return null;
    }

    boolean paren = false;
    ArrayList<String> res = new ArrayList<String>();

    if (tkn == '(') {
      push(openParen);
      paren = true;
    } else {
      tokenizer.pushBack();
    }

    for (;;) {
      tkn = nextToken("doWordList(2)");

      if ((tkn != '"') && (tkn != '\'')) {
        throw new CalFacadeException(CalFacadeException.filterExpectedUid,
                                     String.valueOf(tkn));
      }

      res.add(tokenizer.sval);

      tkn = nextToken("doWordList(3)");

      if (tkn == ',') {
        if (!paren) {
          throw new CalFacadeException(CalFacadeException.filterBadList,
                                       String.valueOf(tkn));
        }
        continue;
      }

      if (paren) {
        if (tkn == ')') {
          popOpenParen();
          return res;
        }
      } else {
        tokenizer.pushBack();
        return res;
      }

      tokenizer.pushBack();
    }
  }

  private void popOpenParen() throws CalFacadeException {
    Token tkn = pop();

    if (tkn != openParen) {
      throw new CalFacadeException(CalFacadeException.filterSyntax,
                                   "Expected openParen on stack");
    }
  }

  private FilterBase makePropFilter(final PropertyInfoIndex pi,
                                final int oper,
                                final TimeRange timeRange,
                                final TextMatchType match,
                                final Collection<ParamFilterType> paramFilters) throws CalFacadeException {
    FilterBase filter = null;
    final boolean exact = (oper != like) && (oper != notLike);

    if (pi.equals(PropertyInfoIndex.ENTITY_TYPE)) {
      return entityFilter(match.getValue());
    }

    if (oper  == notDefined) {
      filter = new PresenceFilter(null, pi, false);
    } else if (oper == isDefined) {
      // Presence check
      filter = new PresenceFilter(null, pi, true);
    } else if (timeRange != null) {
      filter = ObjectFilter.makeFilter(null, pi, timeRange);
    } else if (match != null) {
      if (pi.equals(PropertyInfoIndex.CATEGORIES)) {
        BwCategory cat = getCategoryByName(match.getValue());

        if (cat == null) {
          throw new CalFacadeException(CalFacadeException.filterBadProperty,
                  "category name: " + match.getValue());
        }

        ObjectFilter<BwCategory> f = new BwCategoryFilter(null);

        f.setEntity(cat);

        f.setExact(exact);
        f.setNot(match.getNegateCondition().equals("yes"));

        filter = f;
      } else {
        ObjectFilter<String> f = new ObjectFilter<String>(null, pi);
        f.setEntity(match.getValue());

        f.setCaseless(Filters.caseless(match));

        f.setExact(exact);
        f.setNot(match.getNegateCondition().equals("yes"));

        filter = f;
      }
    } else if (pi.equals(PropertyInfoIndex.CATEGORIES)) {
      // No match and category - expect list of uids.
      ArrayList<String> uids = doWordList();

      for (String uid: uids) {
        BwCategory cat = getCategory(uid);

        if (cat == null) {
          throw new CalFacadeException(CalFacadeException.filterBadProperty,
        		                       "category uid: " + uid);
        }

        ObjectFilter<BwCategory> f = new BwCategoryFilter(null);

        f.setEntity(cat);

        f.setExact(exact);
        f.setNot(oper == notEqual);

        if (filter == null) {
          filter = f;
        } else {
          if (filter instanceof BwCategoryFilter) {
            AndFilter af = new AndFilter();

            af.addChild(filter);

            filter = af;
          }

          ((AndFilter)filter).addChild(f);
        }
      }
    } else {
      // Must have param filters
      if (Util.isEmpty(paramFilters)) {
        return null;  // Flag error
      }
    }

//    if (Util.isEmpty(paramFilters)) {
      return filter;
//    }

//    return BwFilter.addAndChild(filter, processParamFilters(pi, paramFilters));
  }

  private FilterBase entityFilter(final String val) throws CalFacadeException {
    try {
      return EntityTypeFilter.makeEntityTypeFilter(null, val, false);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private TimeRange getTimeRange() throws CalFacadeException {
    tokenizer.assertString();
    String startStr = tokenizer.sval;

    tokenizer.assertToken("to");

    tokenizer.assertString();

    return makeTimeRange(startStr, tokenizer.sval);
  }

  private TimeRange makeTimeRange(final String startStr,
                                  final String endStr) throws CalFacadeException {
    try {
      DateTime start = null;
      DateTime end = null;

      if (startStr != null) {
        start = new DateTime(startStr);
      }

      if (endStr != null) {
        end = new DateTime(endStr);
      }

      return new TimeRange(start, end);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private Operator nextOperator() throws CalFacadeException {
    int tkn = nextToken("nextOperator(1)");

    if (tkn == '=') {
      return new Operator(equal);
    }

    if (tkn == '~') {
      return new Operator(like);
    }

    if (tkn == '!') {
      if (tokenizer.testToken('~')) {
        return new Operator(notLike);
      }

      tokenizer.assertToken('=');
      return new Operator(notEqual);
    }

    if (tkn == '>') {
      tkn = nextToken("nextOperator(2)");

      if (tkn == '=') {
        return new Operator(greaterThanOrEqual);
      }

      tokenizer.pushBack();

      return new Operator(greaterThan);
    }

    if (tkn == '<') {
      tkn = nextToken("nextOperator(3)");

      if (tkn == '=') {
        return new Operator(lessThanOrEqual);
      }

      tokenizer.pushBack();

      return new Operator(lessThan);
    }

    if (tkn != StreamTokenizer.TT_WORD) {
      throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                   tokenizer.sval);
    }

    if (tokenizer.sval.equals("in")) {
      return new Operator(inTimeRange);
    }

    if (tokenizer.sval.equals("isdefined")) {
      return new Operator(isDefined);
    }

    if (tokenizer.sval.equals("notdefined")) {
      return new Operator(notDefined);
    }

    throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                 tokenizer.sval);
  }

  private boolean topLOp() {
    if (stackEmpty()) {
      return false;
    }

    return stack.peek() instanceof LogicalOperator;
  }

  private boolean anding() {
    /* If the stack is empty or it's not a logical operator we start with AND.
     * We can switch on first logical operator
     */

    if (stackEmpty()|| !topLOp()) {
      return true;
    }

    return ((LogicalOperator)stack.peek()).op == andOp;
  }

  private LogicalOperator popLOp() {
    return (LogicalOperator)stack.pop();
  }

  private void assertNotEmpty() throws CalFacadeException {
    if (stack.empty()) {
      throw new CalFacadeException(CalFacadeException.filterSyntax);
    }
  }

  private boolean stackEmpty() {
    return stack.empty();
  }

  private void push(final Token val) {
    stack.push(val);
  }

  private Token pop() throws CalFacadeException {
    assertNotEmpty();
    return stack.pop();
  }

  private void assertFiltersNotEmpty() throws CalFacadeException {
    if (filterStack.empty()) {
      throw new CalFacadeException(CalFacadeException.filterSyntax);
    }
  }

  private FilterBase popFilters() throws CalFacadeException {
    assertFiltersNotEmpty();
    return filterStack.pop();
  }

  private void showStack(final String tr) {
    debugMsg("nextToken(" + tr + "): Parse stack======");
    for (int i = 0; i < stack.size(); i++) {
      debugMsg(stack.elementAt(i).toString());
    }
  }

  private int nextToken(final String tr) throws CalFacadeException {
    int tkn = tokenizer.next();

    if (!debug) {
      return tkn;
    }

    showStack(tr);
    //showFilterStack();

    if (tkn == StreamTokenizer.TT_WORD) {
      debugMsg("nextToken(" + tr + ") = word: " + tokenizer.sval);
    } else if (tkn == '\'') {
      debugMsg("nextToken(" + tr + ") = '" + tokenizer.sval + "'");
    } else if (tkn > 0) {
      debugMsg("nextToken(" + tr + ") = " + (char)tkn);
    } else {
      debugMsg("nextToken(" + tr + ") = " + tkn);
    }

    return tkn;
  }

  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this.getClass(), t);
  }
}
