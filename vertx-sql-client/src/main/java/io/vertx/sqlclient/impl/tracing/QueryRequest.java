package io.vertx.sqlclient.impl.tracing;

import io.vertx.sqlclient.Tuple;

import java.util.List;

/**
 * A traceable query.
 */
public class QueryRequest {

  final QueryReporter tracer;
  final String sql;
  final List<Tuple> tuples;

  public QueryRequest(QueryReporter tracer, String sql, List<Tuple> tuples) {
    this.tracer = tracer;
    this.sql = sql;
    this.tuples = tuples;
  }

  public String sql() {
    return sql;
  }

  public List<Tuple> tuples() {
    return tuples;
  }
}
