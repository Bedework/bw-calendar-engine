/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.common.indexing;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchAllFilterBuilder;
import org.elasticsearch.index.query.NotFilterBuilder;
import org.elasticsearch.index.query.TermFilterBuilder;
import org.elasticsearch.index.query.TermsFilterBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: mike
 * Date: 11/18/16
 * Time: 21:06
 */
public class TermOrTerms  extends BaseFilterBuilder {
  String fldName;
  Object value;
  private String exec;
  private boolean anding;
  boolean isTerms;
  boolean not;
  String filterName;

  /* If true we don't merge this term with other terms. This might
   * help with the expansion of views which we would expect to turn
   * up frequently.
   */
  boolean dontMerge;

  TermOrTerms(final String fldName,
              final Object value,
              final boolean not,
              final String filterName) {
    this.fldName = fldName;
    this.value = value;
    this.not = not;
    this.filterName = filterName;
  }

  TermOrTerms anding(final boolean anding) {
    this.anding = anding;
    if (anding) {
      exec = "and";
    } else {
      exec = "or";
    }

    return this;
  }

  FilterBuilder makeFb() {
    final FilterBuilder fb;

    if (!isTerms) {
      final TermFilterBuilder tfb = FilterBuilders.termFilter(fldName, value);
      if (filterName != null) {
        tfb.filterName(filterName);
      }
      
      fb = tfb;
    } else {
      final List vals = (List)value;
      FilterBuilder newFb = null;
      if (anding) {
        for (final Object o: vals) {
          if (o instanceof MatchNone) {
            // and false is always false
            newFb = (FilterBuilder)o;
            break;
          }
        }
      } else {
        for (final Object o: vals) {
          if (o instanceof MatchAllFilterBuilder) {
            // or true is always true
            newFb = (FilterBuilder)o;
            break;
          }
        }
      }

      if (newFb != null) {
        fb = newFb;
      } else {
        final TermsFilterBuilder tfb = 
                FilterBuilders.termsFilter(fldName,
                                           (Iterable<?>)value)
                              .execution(exec);
        if (filterName != null) {
          tfb.filterName(filterName);
        }

        fb = tfb;
      }
    }

    if (!not) {
      return fb;
    }

    if (fb instanceof MatchAllFilterBuilder) {
      return new MatchNone();
    }

    if (fb instanceof MatchNone) {
      return new MatchAllFilterBuilder();
    }

    return new NotFilterBuilder(fb);
  }

  void addValue(final Object val) {
    if (value == null) {
      value = val;
    } else if (value instanceof Collection) {
      ((Collection)value).add(val);
    } else {
      List vals = new ArrayList();

      vals.add(value);
      vals.add(val);

      value = vals;
      isTerms = true;
    }
  }

  @Override
  public XContentBuilder toXContent(final XContentBuilder builder,
                                    final Params params)
          throws IOException {
    return null;
  }

  @Override
  protected void doXContent(final XContentBuilder builder,
                            final Params params)
          throws IOException {
  }
}
