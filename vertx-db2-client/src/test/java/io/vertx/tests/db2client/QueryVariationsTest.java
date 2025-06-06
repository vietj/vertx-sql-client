package io.vertx.tests.db2client;

import static org.junit.Assume.assumeFalse;

import java.util.Arrays;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

/**
 * Tests for subqueries which are documented here:
 * https://www.ibm.com/support/knowledgecenter/SSEPEK_10.0.0/intro/src/tpc/db2z_subqueries.html
 */
@RunWith(VertxUnitRunner.class)
public class QueryVariationsTest extends DB2TestBase {

  @Test
  public void testFetchFirst(TestContext ctx) {
    connect(ctx.asyncAssertSuccess(conn -> {
      conn
        .query("select message from immutable order by id fetch first 1 rows only")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(rowSet -> {
          ctx.assertEquals(1, rowSet.size());
          ctx.assertEquals(Arrays.asList("MESSAGE"), rowSet.columnsNames());
          RowIterator<Row> rows = rowSet.iterator();
          ctx.assertTrue(rows.hasNext());
          Row row = rows.next();
          ctx.assertEquals("fortune: No such file or directory", row.getString(0));
          ctx.assertFalse(rows.hasNext());
          conn.close();
        }));
    }));
  }

  @Test
  public void testRenamedColumns(TestContext ctx) {
    connect(ctx.asyncAssertSuccess(conn -> {
      conn
        .query("SELECT id AS THE_ID," +
                   "message AS \"the message\"" +
          "FROM immutable " +
          "WHERE id = 10")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(rowSet -> {
        ctx.assertEquals(1, rowSet.size());
        ctx.assertEquals(Arrays.asList("THE_ID", "the message"), rowSet.columnsNames());
        RowIterator<Row> rows = rowSet.iterator();
        ctx.assertTrue(rows.hasNext());
        Row row = rows.next();
        ctx.assertEquals(10, row.getInteger(0));
        ctx.assertEquals(10, row.getInteger("THE_ID"));
        ctx.assertEquals("Computers make very fast, very accurate mistakes.", row.getString(1));
        ctx.assertEquals("Computers make very fast, very accurate mistakes.", row.getString("the message"));
        ctx.assertFalse(rows.hasNext());
        conn.close();
      }));
    }));
  }

  @Test
  public void testSubquery(TestContext ctx) {
    connect(ctx.asyncAssertSuccess(conn -> {
      conn
        .query("SELECT id,message FROM immutable " +
          "WHERE message IN " +
          "(SELECT message FROM immutable WHERE id = '4' OR id = '7')")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(rowSet -> {
        ctx.assertEquals(2, rowSet.size());
        ctx.assertEquals(Arrays.asList("ID", "MESSAGE"), rowSet.columnsNames());
        RowIterator<Row> rows = rowSet.iterator();
        ctx.assertTrue(rows.hasNext());
        Row row = rows.next();
        ctx.assertEquals(4, row.getInteger(0));
        ctx.assertEquals("A bad random number generator: 1, 1, 1, 1, 1, 4.33e+67, 1, 1, 1", row.getString(1));
        ctx.assertTrue(rows.hasNext());
        row = rows.next();
        ctx.assertEquals(7, row.getInteger(0));
        ctx.assertEquals("Any program that runs right is obsolete.", row.getString(1));
        conn.close();
      }));
    }));
  }

  @Test
  public void testSubqueryPrepared(TestContext ctx) {
    connect(ctx.asyncAssertSuccess(conn -> {
      conn
        .preparedQuery("SELECT id,message FROM immutable " +
          "WHERE message IN " +
          "(SELECT message FROM immutable WHERE id = ? OR id = ?)")
        .execute(Tuple.of(4, 7))
        .onComplete(ctx.asyncAssertSuccess(rowSet -> {
        ctx.assertEquals(2, rowSet.size());
        ctx.assertEquals(Arrays.asList("ID", "MESSAGE"), rowSet.columnsNames());
        RowIterator<Row> rows = rowSet.iterator();
        ctx.assertTrue(rows.hasNext());
        Row row = rows.next();
        ctx.assertEquals(4, row.getInteger(0));
        ctx.assertEquals("A bad random number generator: 1, 1, 1, 1, 1, 4.33e+67, 1, 1, 1", row.getString(1));
        ctx.assertTrue(rows.hasNext());
        row = rows.next();
        ctx.assertEquals(7, row.getInteger(0));
        ctx.assertEquals("Any program that runs right is obsolete.", row.getString(1));
        conn.close();
      }));
    }));
  }

  @Test
  public void testLikeQuery(TestContext ctx) {
    connect(ctx.asyncAssertSuccess(conn -> {
      conn
        .query("SELECT id,message FROM immutable " +
          "WHERE message LIKE '%computer%'")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(rowSet -> {
        ctx.assertEquals(2, rowSet.size());
        ctx.assertEquals(Arrays.asList("ID", "MESSAGE"), rowSet.columnsNames());
        RowIterator<Row> rows = rowSet.iterator();
        ctx.assertTrue(rows.hasNext());
        Row row = rows.next();
        ctx.assertEquals(2, row.getInteger(0));
        ctx.assertEquals("A computer scientist is someone who fixes things that aren't broken.", row.getString(1));
        ctx.assertTrue(rows.hasNext());
        row = rows.next();
        ctx.assertEquals(5, row.getInteger(0));
        ctx.assertEquals("A computer program does what you tell it to do, not what you want it to do.", row.getString(1));
        conn.close();
      }));
    }));
  }

  /**
   * Verify that the same connection issuing multiple different prepared statements
   * has isolated result sections on the DB2 side. If the same section is reused for
   * both statements, then query 1 might get the results from query 2 or vice versa
   */
  @Test
  public void testSectionReuse(TestContext ctx) {
    connect(ctx.asyncAssertSuccess(con -> {
        con.prepare("SELECT * FROM Fortune WHERE id=1")
          .flatMap(ps -> ps.query().execute())
          .onComplete(ctx.asyncAssertSuccess(rowSet -> {
            ctx.assertEquals(1, rowSet.size());
            ctx.assertEquals(Arrays.asList("ID", "MESSAGE"), rowSet.columnsNames());
            RowIterator<Row> rows = rowSet.iterator();
            ctx.assertTrue(rows.hasNext());
            Row row = rows.next();
            ctx.assertEquals(1, row.getInteger(0));
            ctx.assertEquals("fortune: No such file or directory", row.getString(1));
            ctx.assertFalse(rows.hasNext());
          }));
        con.prepare("SELECT * FROM immutable WHERE id=2")
          .flatMap(ps -> ps.query().execute())
          .onComplete(ctx.asyncAssertSuccess(rowSet -> {
            ctx.assertEquals(1, rowSet.size());
            ctx.assertEquals(Arrays.asList("ID", "MESSAGE"), rowSet.columnsNames());
            RowIterator<Row> rows = rowSet.iterator();
            ctx.assertTrue(rows.hasNext());
            Row row = rows.next();
            ctx.assertEquals(2, row.getInteger(0));
            ctx.assertEquals("A computer scientist is someone who fixes things that aren't broken.", row.getString(1));
            ctx.assertFalse(rows.hasNext());
          }));
    }));
  }

  /**
   * Test that queries starting with the "VALUES" keyword work properly
   */
  @Test
  public void testSequenceQuery(TestContext ctx) {
      assumeFalse("TODO: Sequences behave differently on DB2/z and need to be implemented properly", rule.isZOS());

    connect(ctx.asyncAssertSuccess(con -> {
      con
        .query("values nextval for my_seq")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(rowSet1 -> {
        // Initially the sequence should be N (where N >= 1)
        int startingSeq = assertSequenceResult(ctx, rowSet1, seqVal -> {
          ctx.assertTrue(seqVal >= 1, "Sequence value was not >= 1. Value: " + seqVal);
        });
        con
          .query("VALUES nextval for my_seq")
          .execute()
          .onComplete(ctx.asyncAssertSuccess(rowSet2 -> {
          // Next the sequence should be N+1
          assertSequenceResult(ctx, rowSet2, seqVal -> ctx.assertEquals(startingSeq + 1, seqVal));
          con
            .query("VALUES nextval for my_seq")
            .execute()
            .onComplete(ctx.asyncAssertSuccess(rowSet3 -> {
            // Finally, the sequence should be N+2
            assertSequenceResult(ctx, rowSet3, seqVal -> ctx.assertEquals(startingSeq + 2, seqVal));
          }));
        }));
      }));
    }));
  }

  /**
   * Like testSequenceQuery but with prepared statements
   */
  @Test
  public void testSequenceQueryPrepared(TestContext ctx) {
      assumeFalse("TODO: Sequences behave differently on DB2/z and need to be implemented properly", rule.isZOS());

    connect(ctx.asyncAssertSuccess(con -> {
      con
        .preparedQuery("VALUES nextval for my_seq")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(rowSet1 -> {
        // Initially the sequence should be N (where N >= 1)
        int startingSeq = assertSequenceResult(ctx, rowSet1, seqVal -> {
          ctx.assertTrue(seqVal >= 1, "Sequence value was not >= 1. Value: " + seqVal);
        });
        con
          .preparedQuery("values nextval for my_seq")
          .execute()
          .onComplete(ctx.asyncAssertSuccess(rowSet2 -> {
          // Next the sequence should be N+1
          assertSequenceResult(ctx, rowSet2, seqVal -> ctx.assertEquals(startingSeq + 1, seqVal));
          con
            .preparedQuery("values nextval for my_seq")
            .execute()
            .onComplete(ctx.asyncAssertSuccess(rowSet3 -> {
            // Finally, the sequence should be N+2
            assertSequenceResult(ctx, rowSet3, seqVal -> ctx.assertEquals(startingSeq + 2, seqVal));
          }));
        }));
      }));
    }));
  }

  /**
   * Test comment in query
   */
  @Test
  public void testComment(TestContext ctx) {
    connect(ctx.asyncAssertSuccess(con -> {
      con
        .query("SELECT id,message FROM immutable " +
          "WHERE message IN " +
          "(SELECT message FROM immutable WHERE id = '4' OR id = '7') -- comment")
        .execute()
        .onComplete(ctx.asyncAssertSuccess(rowSet -> {
        ctx.assertEquals(2, rowSet.size());
        con
          .query("SELECT id,message FROM immutable " +
            "WHERE message IN " +
            "(SELECT message FROM immutable WHERE id = '4' OR id = '7') /* test comment */")
          .execute()
          .onComplete(ctx.asyncAssertSuccess(rowSet2 -> {
          ctx.assertEquals(2, rowSet.size());
          con
            .query("/* /* test comment */*/ /* /* Overly */ /*Complicated*/ /* Comment  */*/SELECT id,message FROM immutable " +
              "WHERE message IN " +
              "(SELECT message FROM immutable WHERE id = '4' OR id = '7')")
            .execute()
            .onComplete(ctx.asyncAssertSuccess(rowSet3 -> {
            ctx.assertEquals(2, rowSet.size());
          }));
        }));
      }));
    }));
  }

  private int assertSequenceResult(TestContext ctx, RowSet<Row> rowSet, Consumer<Integer> validation) {
    ctx.assertEquals(1, rowSet.size());
    RowIterator<Row> rows = rowSet.iterator();
        ctx.assertTrue(rows.hasNext());
        Row row = rows.next();
        ctx.assertNotNull(row);
        int seqVal = row.getInteger(0);
        validation.accept(seqVal);
        return seqVal;
  }
}
