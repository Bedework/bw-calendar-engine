/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.indexing;

import org.bedework.calfacade.responses.Response;
import org.bedework.util.misc.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * User: mike Date: 10/7/17 Time: 13:27
 */
public class ReindexResponse extends Response {
  public static class Failure extends Response {
    private String hitInfo;
    private String source;

    public String getHitInfo() {
      return hitInfo;
    }

    public void setHitInfo(final String val) {
      hitInfo = val;
    }

    public String getSource() {
      return source;
    }

    public void setSource(final String val) {
      source = val;
    }
  }

  private final String docType;

  private String indexName;

  private long processed;

  private long recurring;

  private long totalFailed;

  /** */
  private final IndexStatistics stats;

  private List<Failure> failures;

  public ReindexResponse(final String docType) {
    this.docType = docType;
    stats = new IndexStatistics(docType);
  }

  public String getDocType() {
    return docType;
  }

  public String getIndexName() {
    return indexName;
  }

  public void setIndexName(final String val) {
    indexName = val;
  }

  /**
   * @return Total number processed
   */
  public long getProcessed() {
    return processed;
  }

  public void incProcessed() {
    processed++;
  }

  /**
   * @return Total number recurring processed
   */
  public long getRecurring() {
    return recurring;
  }

  public void incRecurring() {
    recurring++;
  }

  /**
   * @return Total number failed
   */
  public long getTotalFailed() {
    return totalFailed;
  }

  public void incTotalFailed() {
    totalFailed++;
  }
    
  public IndexStatistics getStats() {
    return stats;
  }

  public List<Failure> getFailures() {
    return failures;
  }

  public void addFailure(final Failure val) {
    if (failures == null) {
      failures = new ArrayList<>();
    }

    failures.add(val);
  }

  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("docType", getDocType());
    ts.append("indexName", getIndexName());
    ts.append("processed", getProcessed());
    ts.append("recurring", getRecurring());
    ts.append("totalFailed", getTotalFailed());
    ts.append("failures", getFailures());
    ts.append("stats", getStats());
  }
}
