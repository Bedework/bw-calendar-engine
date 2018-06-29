/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.common.indexing;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: mike Date: 5/9/18 Time: 17:17
 */
public class TermOrTermsQuery extends BaseQueryBuilder {
  String fldName;
  Object value;
  private boolean anding;
  boolean isTerms;
  boolean not;

  /* If true we don't merge this term with other terms. This might
   * help with the expansion of views which we would expect to turn
   * up frequently.
   */
  boolean dontMerge;

  TermOrTermsQuery(final String fldName,
                   final Object value,
                   final boolean not) {
    this.fldName = fldName;
    this.value = value;
    this.not = not;
  }

  TermOrTermsQuery anding(final boolean anding) {
    this.anding = anding;

    return this;
  }

  QueryBuilder makeQb() {
    final QueryBuilder qb;

    if (!isTerms) {
      qb = QueryBuilders.termQuery(fldName, value);
    } else {
      final TermsQueryBuilder tqb =
              QueryBuilders.termsQuery(fldName,
                                       (Iterable <?>)value);
      if (anding) {
        tqb.minimumShouldMatch("100%");
      }

      qb = tqb;
    }

    if (!not) {
      return qb;
    }

    final BoolQueryBuilder bqb = new BoolQueryBuilder();
    bqb.mustNot(qb);

    return bqb;
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
  protected void doXContent(final XContentBuilder builder,
                            final Params params) throws IOException {
  }
}
