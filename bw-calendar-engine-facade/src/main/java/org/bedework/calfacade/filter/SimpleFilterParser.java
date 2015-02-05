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
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.parse.Filters;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.svc.BwView;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;

import ietf.params.xml.ns.caldav.TextMatchType;
import net.fortuna.ical4j.model.DateTime;
import org.apache.log4j.Logger;

import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
 * <p>This class is serially reusable but NOT thread-safe</p>
 *
 * @author Mike Douglass
 *
 */
public abstract class SimpleFilterParser {
  private transient Logger log;

  private boolean debug;

  private SfpTokenizer tokenizer;
  private String currentExpr;
  private String source;

  private SimpleFilterParser subParser;
  private boolean explicitSelection;

  private static class Token {
  }

  private static class OpenParenthesis extends Token {
    @Override
    public String toString() {
      return "OpenParenthesis{}";
    }
  }

  private final static Token openParen = new OpenParenthesis();

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

  private final Stack<Token> stack = new Stack<>();

  private final Stack<FilterBase> filterStack = new Stack<>();

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
      final ToString ts = new ToString(this);

      ts.append("ok", ok);

      if (ok) {
        ts.append("filter", filter);
      } else {
        ts.append("errcode", cfe.getMessage());
      }

      return ts.toString();
    }
  }

  /**
   *
   * @param path of collection
   * @return collection object or null.
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  public abstract BwCalendar getCollection(String path) throws CalFacadeException;

  /** Attempt to get collection referenced by the alias. For an internal alias
   * the result will also be set in the aliasTarget property of the parameter.
   *
   * @param val collection
   * @param resolveSubAlias - if true and the alias points to an alias, resolve
   *                  down to a non-alias.
   * @return BwCalendar
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  public abstract BwCalendar resolveAlias(BwCalendar val,
                                          boolean resolveSubAlias) throws CalFacadeException;

  /** Returns children of the given collection to which the current user has
   * some access.
   *
   * @param  col          parent collection
   * @return Collection   of BwCalendar
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  public abstract Collection<BwCalendar> getChildren(BwCalendar col)
          throws CalFacadeException;

  /** An unsatisfactory approach - we'll special case categories for the moment
   * to see if this works. When using these filters we need to search for a
   * category being a member of the set of categories for the event.
   *
   * <p>This only works if the current user is the owner of the named category -
   * at least with my current implementation.
   *
   * @param name of category
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

  /** Get the view given the path.
   *
   * @param path for view
   * @return view or null
   * @throws CalFacadeException
   */
  public abstract BwView getView(String path) throws CalFacadeException;

  /** A virtual path might be for example "/user/adgrp_Eng/Lectures/Lectures"
   * which has two two components<ul>
   * <li>"/user/adgrp_Eng/Lectures" and</li>
   * <li>"Lectures"</li></ul>
   *
   * <p>
   * "/user/adgrp_Eng/Lectures" is a real path which is an alias to
   * "/public/aliases/Lectures" which is a folder containing the alias
   * "/public/aliases/Lectures/Lectures" which is aliased to the single calendar.
   *
   * @param vpath the virtual path
   * @return collection of collection objects - null for bad vpath
   * @throws CalFacadeException
   */
  public abstract Collection<BwCalendar> decomposeVirtualPath(final String vpath)
          throws CalFacadeException;

  /**
   *
   * @return a parser so we can parse out sub-filters
   * @throws CalFacadeException
   */
  public abstract SimpleFilterParser getParser() throws CalFacadeException;

  /** Parse the given expression into a filter. The explicitSelection
   * flag determines whether or not we skip certain collections. For
   * example, we normally skip collections with display off or the
   * inbox. If we explicitly selct thos e items however, we want to
   * see them.
   *
   * @param expr the expression
   * @param explicitSelection true if we are explicitly selecting a
   *                          path or paths
   * @param source            Where the expression came from - for errors
   * @return ParseResult
   * @throws CalFacadeException
   */
  public ParseResult parse(final String expr,
                           final boolean explicitSelection,
                           final String source) throws CalFacadeException {
    this.explicitSelection = explicitSelection;
    this.source = source;
    debug = getLogger().isDebugEnabled();

    try {
      if (debug) {
        debugMsg("About to parse filter expression: " + expr +
                  " from " + source);
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
        throw new CalFacadeException(CalFacadeException.filterSyntax,
                                     "source: " + source);
      }

      /* We should be left with just the filter on the stack. */

      if (filterStack.size() != 1) {
        throw new CalFacadeException(CalFacadeException.filterSyntax,
                                     " source: " + source);
      }

      final FilterBase f = popFilters();

      if (debug) {
        debugMsg(f.toString());
      }

      return new ParseResult(f);
    } catch (final CalFacadeException cfe) {
      if (debug) {
        error(cfe);
      }
      return new ParseResult(cfe);
    }
  }

  /** Parse a comma list of sort terms. Each term is a property name
   * optionally followed by ":" then the terms "ASC" or "DESC". The
   * default is descending.
   *
   * <p>The property name is either a valid bedework property or the
   * word "RELEVANCE" - which is the default</p>
   *
   * @param sexpr - search expression
   * @return list of terms in order of application. Empty for no sort
   *         terms.
   * @throws CalFacadeException
   */
  public List<SortTerm> parseSort(final String sexpr) throws CalFacadeException {
    final List<SortTerm> res = new ArrayList<>();

    if (sexpr == null) {
      return res;
    }

    tokenizer = new SfpTokenizer(new StringReader(sexpr));
    for (;;) {
      int tkn = nextToken("parseSort()");

      if (tkn == StreamTokenizer.TT_EOF) {
        return res;
      }

      final List<PropertyInfoIndex> pis = getProperty(tkn);

      tkn = nextToken("parseSort() - :");

      boolean ascending = false;

      if (tkn == ':') {
        tkn = nextToken("parseSort() - asc/desc");

        if (tkn != StreamTokenizer.TT_WORD) {
          throw new CalFacadeException(CalFacadeException.filterExpectedAscDesc,
                                       String.valueOf(tkn) +
                                               " source: " + source);
        }

        if ("asc".equalsIgnoreCase(tokenizer.sval)) {
          ascending = true;
        } else if ("asc".equalsIgnoreCase(tokenizer.sval)) {
          ascending = false;
        } else {
          throw new CalFacadeException(CalFacadeException.filterExpectedAscDesc,
                                       String.valueOf(tkn) +
                                               " source: " + source);
        }
      } else if (tkn == StreamTokenizer.TT_EOF) {
        tokenizer.pushBack();
      } else if (tkn != ',') {
        throw new CalFacadeException(CalFacadeException.filterBadSort,
                                     String.valueOf(tkn) + " from " + sexpr +
                                             " source: " + source);
      }

      res.add(new SortTerm(pis, ascending));
    }
  }

  private boolean doFactor() throws CalFacadeException {
    if (debug) {
      debugMsg("doFactor: " + tokenizer.toString());
    }

    final int tkn = nextToken("doFactor(1)");

    if (tkn == StreamTokenizer.TT_EOF) {
      return false;
    }

    if (tkn == '(') {
      push(openParen);
      doExpr();

      if (nextToken("doFactor(2)") != ')') {
        throw new CalFacadeException(CalFacadeException.filterExpectedCloseParen,
                                             " source: " + source);
      }

      popOpenParen();
    } else {
      tokenizer.pushBack();
      if (!doPropertyComparison()) {
        return false;
      }
    }

    if (!topLOp()) {
      return true;
    }

    final FilterBase filter = popFilters();

    final FilterBase topFilter = popFilters();
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

    tkn = checkLop(tkn);

    if ((tkn != '&') && (tkn != '|')) {
      tokenizer.pushBack();
      if (topLOp()) {
        final FilterBase filter = popFilters();

        final FilterBase topFilter = popFilters();
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
          throw new CalFacadeException(CalFacadeException.filterMixedLogicalOperators,
                                               " source: " + source);
        }
      } else if (oper.op != orOp) {
        throw new CalFacadeException(CalFacadeException.filterMixedLogicalOperators,
                                             " source: " + source);
      }

      push(oper);
    } else if (tkn == '&') {
      push(andOperator);
    } else {
      push(orOperator);
    }
  }

  private int checkLop(final int tkn) {
    if (tkn == '&') {
      return tkn;
    }

    if (tkn == '|') {
      return tkn;
    }

    if (tkn != StreamTokenizer.TT_WORD) {
      return tkn;
    }

    final String pname = tokenizer.sval;

    if (pname.equalsIgnoreCase("and")) {
      return '&';
    }

    if (pname.equalsIgnoreCase("or")) {
      return '|';
    }

    return tkn;
  }

  private List<PropertyInfoIndex> getProperty(final int curtkn) throws CalFacadeException {
    int tkn = curtkn;
    final List<PropertyInfoIndex> pis = new ArrayList<>();

    for (;;) {
      if (tkn != StreamTokenizer.TT_WORD) {
        throw new CalFacadeException(CalFacadeException.filterExpectedPropertyName,
                                     String.valueOf(tkn) +
                                     " source: " + source);
      }

      final String pname = tokenizer.sval;
      final String pnameUc = pname.toUpperCase();

      if ((pis.size() == 0) && pnameUc.equals("CATUID")) {
        // These are stored all over the place.

        pis.add(PropertyInfoIndex.CATEGORIES);
        pis.add(PropertyInfoIndex.UID);
        return pis;
      }

      final PropertyInfoIndex pi = PropertyInfoIndex.fromName(pname);
      if (pi == null) {
        throw new CalFacadeException(CalFacadeException.unknownProperty,
                                     pname + ": expr was " + currentExpr +
                                             " source: " + source);
      }

      pis.add(pi);

      tkn = nextToken("getProperty");

      if (tkn != '.') {
        tokenizer.pushBack();
        return pis;
      }

      tkn = nextToken("getProperty: pname");
    }
  }

  private boolean doPropertyComparison() throws CalFacadeException {
    final List<PropertyInfoIndex> pis = getProperty(nextToken("getProperty()"));

    final Operator oper = nextOperator();

    final FilterBase pfilter = makePropFilter(pis, oper.op);

    if (pfilter == null) {
      error(new CalFacadeException(CalFacadeException.filterBadProperty,
                                   listProps(pis) +
                                           " source: " + source));
      return false;
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

    return true;
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
    final ArrayList<String> res = new ArrayList<>();

    if (tkn == '(') {
      push(openParen);
      paren = true;
    } else {
      tokenizer.pushBack();
    }

    for (;;) {
      tkn = nextToken("doWordList(2)");

      if ((tkn != '"') && (tkn != '\'')) {
        throw new CalFacadeException(CalFacadeException.filterBadList,
                                     "Expected quoted string: found" +
                                             String.valueOf(tkn) +
                                             " source: " + source);
      }

      res.add(tokenizer.sval);

      tkn = nextToken("doWordList(3)");

      if (tkn == ',') {
        if (!paren) {
          throw new CalFacadeException(CalFacadeException.filterBadList,
                                       String.valueOf(tkn) +
                                               " source: " + source);
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
    final Token tkn = pop();

    if (tkn != openParen) {
      throw new CalFacadeException(CalFacadeException.filterSyntax,
                                   "Expected openParen on stack." +
                                           " source: " + source);
    }
  }

  private FilterBase makePropFilter(final List<PropertyInfoIndex> pis,
                                    final int oper)
          throws CalFacadeException {
    final PropertyInfoIndex pi = pis.get(0);
    FilterBase filter = null;
    final boolean exact = (oper != like) && (oper != notLike);

    if (pi.equals(PropertyInfoIndex.ENTITY_TYPE)) {
      checkSub(pis, 1);
      return entityFilter(getMatch(oper).getValue());
    }

    if (oper == notDefined) {
      checkSub(pis, 2);
      return new PresenceFilter(null, pis, false);
    }

    if (oper == isDefined) {
      // Presence check
      checkSub(pis, 2);
      return new PresenceFilter(null, pis, true);
    }

    if (oper == inTimeRange) {
      checkSub(pis, 2);
      return ObjectFilter.makeFilter(null, pis, getTimeRange());
    }

    if (pi.equals(PropertyInfoIndex.VIEW)) {
      checkSub(pis, 1);
      // expect list of views.
      final ArrayList<String> views = doWordList();

      for (final String view: views) {
        final FilterBase vpf = viewFilter(view);

        if (vpf == null) {
          continue;
        }

        filter = and(filter, vpf);
      }

      return filter;
    }

    if (pi.equals(PropertyInfoIndex.VPATH)) {
      checkSub(pis, 1);
      // expect list of virtual paths.
      final ArrayList<String> vpaths = doWordList();

      for (final String vpath: vpaths) {
        final FilterBase vpf = resolveVpath(vpath);

        if (vpf == null) {
          continue;
        }

        filter = and(filter, vpf);
      }

      return filter;
    }

    if (pi.equals(PropertyInfoIndex.CATEGORIES) &&
            (pis.size() == 2)) {
      final PropertyInfoIndex subPi = pis.get(1);

      if (subPi.equals(PropertyInfoIndex.UID)) {
        // No match and category - expect list of uids.
        final ArrayList<String> uids = doWordList();

        for (final String uid: uids) {
          final BwCategory cat = getCategory(uid);

          if (cat == null) {
            error(new CalFacadeException(CalFacadeException.filterBadProperty,
                                         "category uid: " + uid +
                                         " source: " + source));
            return null;
          }

          final ObjectFilter<String> f = new ObjectFilter<String>(null, pis);

          f.setEntity(uid);

          f.setExact(exact);
          f.setNot(oper == notEqual);

          filter = and(filter, f);
        }

        return filter;
      }

      if (subPi.equals(PropertyInfoIndex.HREF)) {
        // No match and category - expect list of paths.
        final ArrayList<String> paths = doWordList();

        for (final String path: paths) {
          final ObjectFilter<String> f = new ObjectFilter<String>(null, pis);
          f.setEntity(path);

          f.setCaseless(false);

          f.setExact(exact);
          f.setNot(oper == notEqual);

          filter = and(filter, f);
        }

        return filter;
      }
    }

    if (pi.equals(PropertyInfoIndex.COLLECTION) ||
            pi.equals(PropertyInfoIndex.COLPATH)) {
      checkSub(pis, 1);
      final ArrayList<String> paths = doWordList();

      for (final String path: paths) {
        final FilterBase pf = resolveColPath(path, true, true);

        if (pf == null) {
          continue;
        }

        filter = and(filter, pf);
      }

      return filter;
    }

    final TextMatchType match = getMatch(oper);

    if (pi.equals(PropertyInfoIndex.CATEGORIES)) {
      checkSub(pis, 1);
      final String val = match.getValue();

      if (val.startsWith("/")) {
        pis.add(PropertyInfoIndex.HREF);
        // Assume a path match
        final ObjectFilter<String> f = new ObjectFilter<String>(null, pis);
        f.setEntity(val);

        f.setCaseless(false);

        f.setExact(exact);
        f.setNot(match.getNegateCondition().equals("yes"));

        return f;
      }

      // Try for name

      final BwCategory cat = getCategoryByName(val);

      if (cat == null) {
        throw new CalFacadeException(CalFacadeException.filterBadProperty,
                                     "category name: " + match.getValue() +
                                     " source: " + source);
      }

      pis.add(PropertyInfoIndex.UID);
      final ObjectFilter<BwCategory> f = new BwCategoryFilter(null, pis);

      f.setEntity(cat);

      f.setExact(exact);
      f.setNot(match.getNegateCondition().equals("yes"));

      return f;
    }

    checkSub(pis, 2);
    final ObjectFilter<String> f = new ObjectFilter<String>(null, pis);
    f.setEntity(match.getValue());

    f.setCaseless(Filters.caseless(match));

    f.setExact(exact);
    f.setNot(match.getNegateCondition().equals("yes"));

    return f;
  }

  /** Check the properties to ensure we have no more than the allowed
   * number. i.e. 1 means no subfields, 2 = only subfield e.g. a.b
   * 3 means 2, a.b.c etc.
   *
   * @param pis list of PropertyInfoIndex
   * @param depth we expect this and nothing else
   * @throws CalFacadeException
   */
  private void checkSub(final List<PropertyInfoIndex> pis,
                        final int depth) throws CalFacadeException {
    if (depth != pis.size()) {
      throw new CalFacadeException(CalFacadeException.filterBadProperty,
                                   listProps(pis) +
                                   " source: " + source);
    }
  }

  private String listProps(final List<PropertyInfoIndex> pis) {
    String delim = "";

    final StringBuilder sb = new StringBuilder();

    for (final PropertyInfoIndex pi: pis) {
      sb.append(delim);
      sb.append(BwIcalPropertyInfo.getPinfo(pi).getJname());
      delim = ".";
    }

    return sb.toString();
  }

  private TextMatchType getMatch(final int oper) throws CalFacadeException {
    final TextMatchType tm;

    // Expect a value
    tokenizer.assertString();

    tm = new TextMatchType();
    tm.setValue(tokenizer.sval);
    if (oper == notEqual) {
      tm.setNegateCondition("yes");
    } else {
      tm.setNegateCondition("no");
    }
    tm.setCollation("i;ascii-casemap");

    return tm;
  }

  private FilterBase entityFilter(final String val) throws CalFacadeException {
    try {
      return EntityTypeFilter.makeEntityTypeFilter(null, val, false);
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private FilterBase viewFilter(final String val) throws CalFacadeException {
    try {
      final BwView view = getView(val);

      if (view == null) {
        throw new CalFacadeException(CalFacadeException.filterUnknownView,
                                     val + " source: " + source);
      }

      FilterBase filter = view.getFilter();

      if (filter != null) {
        return filter;
      }

      for (final String vpath: view.getCollectionPaths()) {
        final FilterBase vpf = resolveVpath(vpath);

        if (vpf == null) {
          continue;
        }

        filter = or(filter, vpf);
      }

      final BwViewFilter vf = new BwViewFilter(null);

      vf.setEntity(view);
      vf.setFilter(filter);

      view.setFilter(filter);

      return vf;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private FilterBase or(final FilterBase of,
                        final FilterBase f) {
    if (of == null) {
      return f;
    }

    if (of instanceof OrFilter) {
      of.addChild(f);
      return of;
    }

    final OrFilter nof = new OrFilter();
    nof.addChild(of);
    nof.addChild(f);

    return nof;
  }

  private FilterBase and(final FilterBase af,
                         final FilterBase f) {
    if (af == null) {
      return f;
    }

    if (af instanceof AndFilter) {
      af.addChild(f);
      return af;
    }

    final AndFilter naf = new AndFilter();
    naf.addChild(af);
    naf.addChild(f);

    return naf;
  }

  /** A virtual path is the apparent path for a user looking at an explorer
   * view of collections.
   *
   * <p>We might have,
   * <pre>
   *    home-->Arts-->Theatre
   * </pre>
   *
   * <p>In reality the Arts collection might be an alias to another alias which
   * is an alias to a collection containing aliases including "Theatre".
   *
   * <p>So the real picture might be something like...
   * <pre>
   *    home-->Arts             (categories="ChemEng")
   *            |
   *            V
   *           Arts             (categories="Approved")
   *            |
   *            V
   *           Arts-->Theatre   (categories="Arts" AND categories="Theatre")
   *                     |
   *                     V
   *                    MainCal
   * </pre>
   * where the vertical links are aliasing. The importance of this is that
   * each alias might introduce another filtering term, the intent of which is
   * to restrict the retrieval to a specific subset. The parenthesized terms
   * represent example filters.
   *
   * <p>The desired filter is the ANDing of all the above.
   *
   * @param  vpath  a String virtual path
   * @return FilterBase object or null for bad path
   * @throws CalFacadeException
   */
  private FilterBase resolveVpath(final String vpath) throws CalFacadeException {
    /* We decompose the virtual path into it's elements and then try to
     * build a sequence of collections that include the aliases and their
     * targets until we reach the last element in the path.
     *
     * We'll assume the path is already normalized and that no "/" are allowed
     * as parts of names.
     *
     * What we're doing here is resolving aliases to aliases and accumulating
     * any filtering that might be in place as a sequence of ANDed terms. For
     * example:
     *
     * /user/eng/Lectures has the filter cat=eng and is aliased to
     * /public/aliases/Lectures which has the filter cat=lectures and is aliased to
     * /public/cals/MainCal
     *
     * We want the filter (cat=eng) & (cat=Lectures) on MainCal.
     *
     * Below, we decompose the virtual path and we save the path to an actual
     * folder or calendar collection.
     */

    final Collection<BwCalendar> cols = decomposeVirtualPath(vpath);

    if (cols == null) {
      // Bad vpath
      return null;
    }

    FilterBase vfilter = null;
    BwCalendar vpathTarget = null;

    for (final BwCalendar col: cols) {
      if (debug) {
        debugMsg("      vpath collection:" + col.getPath());
      }

      if (col.getFilterExpr() != null) {
        if (subParser == null) {
          subParser = getParser();
        }

        final ParseResult pr = subParser.parse(col.getFilterExpr(),
                                               false,
                                               col.getPath());
        if (pr.cfe != null) {
          throw pr.cfe;
        }

        if (pr.filter != null) {
          vfilter = and(vfilter, pr.filter);
        }
      }

      if (col.getCollectionInfo().onlyCalEntities ||
              (col.getCalType() == BwCalendar.calTypeFolder)) {
        // reached an end point
        vpathTarget = col;
      }
    }

    if (vpathTarget == null) {
      throw new CalFacadeException("Bad vpath - no calendar collection" +
                                           " vpath: " + vpath +
                                           " source: " + source);
    }

    return and(vfilter,
               resolveColPath(vpathTarget.getPath(),
                              false, false));
  }

  /**
   *
   * @param path to be resolved
   * @param applyFilter - filter may already have been applied
   * @return filter or null
   * @throws CalFacadeException
   */
  private FilterBase resolveColPath(final String path,
                                    final boolean applyFilter,
                                    final boolean explicitSelection) throws CalFacadeException {
    return new FilterBuilder(this).buildFilter(path,
                                               applyFilter,
                                               explicitSelection);
  }

  private TimeRange getTimeRange() throws CalFacadeException {
    tokenizer.assertString();
    final String startStr = tokenizer.sval;

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
    } catch (final Throwable t) {
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
                                   tokenizer.sval +
                                           " source: " + source);
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
                                 tokenizer.sval +
                                         " source: " + source);
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
      throw new CalFacadeException(CalFacadeException.filterSyntax,
                                           " source: " + source);
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
      throw new CalFacadeException(CalFacadeException.filterSyntax,
                                           " source: " + source);
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
    final int tkn = tokenizer.next();

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
