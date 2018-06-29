/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.common.indexing;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseFilterBuilder;

import java.io.IOException;

/**
 * User: mike
 * Date: 11/18/16
 * Time: 21:10
 */
public class MatchNone extends BaseFilterBuilder {
  @Override
  protected void doXContent(final XContentBuilder xContentBuilder,
                            final Params params)
          throws IOException {
  }
}
