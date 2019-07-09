/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.common.indexing;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * User: mike Date: 5/9/18 Time: 17:17
 */
public class TermOrTermsQuery extends
        AbstractQueryBuilder<TermOrTermsQuery> {
  String fldName;
  Object value;
  private boolean anding;
  boolean isTerms;
  boolean not;
  String qName;

  /* If true we don't merge this term with other terms. This might
   * help with the expansion of views which we would expect to turn
   * up frequently.
   */
  boolean dontMerge;

  static class AndQB extends BoolQueryBuilder {
    AndQB() {
    }

    AndQB(final QueryBuilder term) {
      must(term);
    }

    void add(final QueryBuilder term) {
      must(term);
    }
  }

  static class OrQB extends BoolQueryBuilder {
    OrQB() {
    }

    OrQB(final QueryBuilder term) {
      should(term);
    }

    void add(final QueryBuilder term) {
      should(term);
    }
  }

  static class NotQB extends BoolQueryBuilder {
    NotQB(final QueryBuilder term) {
      mustNot(term);
    }
  }

  TermOrTermsQuery(final String fldName,
                   final Object value,
                   final boolean not) {
    this.fldName = fldName;
    this.value = value;
    this.not = not;
  }

  /**
   *
   * @param fldName property
   * @param value the value
   * @param not true if we negate
   * @param qName allows naming of query
   */
  TermOrTermsQuery(final String fldName,
                   final Object value,
                   final boolean not,
                   final String qName) {
    this.fldName = fldName;
    this.value = value;
    this.not = not;
    this.qName = qName;
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
      final Iterable <?> it = (Iterable <?>)value;

      if (anding) {
        final AndQB and = new AndQB();

        for (final Object o: it) {
          and.add(termQuery(fldName, o));
        }

        qb = and;
      } else {
        final OrQB or = new OrQB();

        for (final Object o: it) {
          or.add(termQuery(fldName, o));
        }

        qb = or;
      }
    }

    if (qName != null) {
      qb.queryName(qName);
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
  protected void doWriteTo(final StreamOutput streamOutput)
          throws IOException {

  }

  @Override
  protected void doXContent(final XContentBuilder builder,
                            final Params params) throws IOException {
  }

  @Override
  protected Query doToQuery(
          final QueryShardContext queryShardContext)
          throws IOException {
    return null;
  }

  @Override
  public String getWriteableName() {
    return null;
  }

  @Override
  protected boolean doEquals(final TermOrTermsQuery qb) {
    return false;
  }

  @Override
  protected int doHashCode() {
    return 0;
  }
}
