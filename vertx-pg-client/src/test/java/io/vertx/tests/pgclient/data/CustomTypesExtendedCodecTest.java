package io.vertx.tests.pgclient.data;

import io.vertx.pgclient.PgConnection;
import io.vertx.tests.sqlclient.ColumnChecker;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

public class CustomTypesExtendedCodecTest extends ExtendedQueryDataTypeCodecTestBase {
  @Test
  public void testEncodeCustomType(TestContext ctx) {
    Async async = ctx.async();
    String actual = "('Othercity',\" 'Second Ave'\",f)";
    PgConnection.connect(vertx, options).onComplete(ctx.asyncAssertSuccess(conn -> {
      conn.prepare("UPDATE \"CustomDataType\" SET \"address\" = $1  WHERE \"id\" = $2 RETURNING \"address\"").onComplete(
        ctx.asyncAssertSuccess(p -> {
          p.query()
            .execute(Tuple.tuple().addString("('Othercity', 'Second Ave', false)").addInteger(2))
            .onComplete(ctx.asyncAssertSuccess(result -> {
              ctx.assertEquals(1, result.size());
              ctx.assertEquals(1, result.rowCount());
              Row row = result.iterator().next();
              ColumnChecker.checkColumn(0, "address")
                .returns(Tuple::getValue, Row::getValue, actual)
                .returns(Tuple::getString, Row::getString, actual)
                .forRow(row);
              async.complete();
            }));
        }));
    }));
  }
}
