/*
 * IGinX - the polystore system with high performance
 * Copyright (C) Tsinghua University
 * TSIGinX@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package cn.edu.tsinghua.iginx.integration.func.session;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import cn.edu.tsinghua.iginx.exception.SessionException;
import cn.edu.tsinghua.iginx.integration.tool.CombinedInsertTests;
import cn.edu.tsinghua.iginx.session.SessionAggregateQueryDataSet;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import cn.edu.tsinghua.iginx.thrift.AggregateType;
import cn.edu.tsinghua.iginx.thrift.DataType;
import cn.edu.tsinghua.iginx.thrift.StorageEngineType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionIT extends BaseSessionIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionIT.class);

  protected StorageEngineType storageEngineType;
  protected int defaultPort2 = 6668;
  protected Map<String, String> extraParams;
  protected Boolean ifNeedCapExp = false;

  private static boolean needCompareResult = true;

  // params for downSample
  private static final long PRECISION = 123L;
  // params for datatype test
  private static final String ranStr =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int STRING_LEN = 1000;

  @SuppressWarnings("unused") // dead code because 100000 % 123 != 0
  long factSampleLen = (KEY_PERIOD / PRECISION) + ((KEY_PERIOD % PRECISION == 0) ? 0 : 1);

  double originAvg = (START_KEY + END_KEY) / 2.0;

  private String getSinglePath(int startPosition, int offset) {
    int pos = startPosition + offset;
    return ("sg1.d" + pos + ".s" + pos);
  }

  private String getRandomStr(int seed, int length) {
    Random random = new Random(seed);
    StringBuilder sb = new StringBuilder();
    for (int k = 0; k < length; k++) {
      int number = random.nextInt(ranStr.length());
      sb.append(ranStr.charAt(number));
    }
    return sb.toString();
  }

  private void insertTestsByFourInterfaces() throws SessionException {
    CombinedInsertTests test = new CombinedInsertTests(session);
    test.testInserts();
  }

  private void insertFakeNumRecords(List<String> insertPaths, long count) throws SessionException {
    int pathLen = insertPaths.size();
    long[] keys = new long[(int) KEY_PERIOD];
    for (long i = 0; i < KEY_PERIOD; i++) {
      keys[(int) i] = i + START_KEY + count;
    }

    Object[] valuesList = new Object[pathLen];
    for (int i = 0; i < pathLen; i++) {
      int pathNum = getPathNum(insertPaths.get(i));
      Object[] values = new Object[(int) KEY_PERIOD];
      for (long j = 0; j < KEY_PERIOD; j++) {
        values[(int) j] = pathNum + j + START_KEY;
      }
      valuesList[i] = values;
    }

    List<DataType> dataTypeList = new ArrayList<>();
    for (int i = 0; i < pathLen; i++) {
      dataTypeList.add(DataType.LONG);
    }
    session.insertNonAlignedColumnRecords(insertPaths, keys, valuesList, dataTypeList, null);
  }

  // the length of the insertPaths must be 6
  private void insertDataTypeRecords(List<String> insertPaths, int startPathNum)
      throws SessionException {
    long[] keys = new long[(int) KEY_PERIOD];
    for (long i = 0; i < KEY_PERIOD; i++) {
      keys[(int) i] = i + START_KEY;
    }

    Object[] valuesList = new Object[6];
    for (int i = 0; i < 6; i++) {
      Object[] values = new Object[(int) KEY_PERIOD];
      int pathNum = getPathNum(insertPaths.get(i)) - startPathNum;
      for (int j = 0; j < KEY_PERIOD; j++) {
        switch (pathNum) {
          case 1:
            // integer
            values[j] = (int) (pathNum + (KEY_PERIOD - j - 1) + START_KEY);
            break;
          case 2:
            // long
            values[j] = (pathNum + j + START_KEY) * 1000;
            break;
          case 3:
            values[j] = (float) (pathNum + j + START_KEY + 0.01);
            // float
            break;
          case 4:
            // double
            values[j] = (pathNum + (KEY_PERIOD - j - 1) + START_KEY + 0.01) * 999;
            break;
          case 5:
            // binary
            values[j] = getRandomStr(j, STRING_LEN).getBytes();
            break;
          default:
            // boolean
            values[j] = (j % 2 == 0);
            break;
        }
      }
      valuesList[i] = values;
    }

    List<DataType> dataTypeList = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      dataTypeList.add(DataType.findByValue(i));
    }
    session.insertNonAlignedColumnRecords(insertPaths, keys, valuesList, dataTypeList, null);
  }

  private float changeResultToFloat(Object rawResult) {
    float result = 0;
    if (rawResult instanceof Double) {
      result = (float) ((double) rawResult);
    } else {
      try {
        result = (float) rawResult;
      } catch (Exception e) {
        LOGGER.error("unexpected error: ", e);
        fail();
      }
    }
    return result;
  }

  private int changeResultToInteger(Object rawResult) {
    int result = 0;
    if (rawResult instanceof Long) {
      result = (int) ((long) rawResult);
    } else {
      try {
        result = (int) rawResult;
      } catch (Exception e) {
        LOGGER.error("unexpected error: ", e);
        fail();
      }
    }
    return result;
  }

  @Test
  public void sessionTest() throws SessionException, InterruptedException {
    int simpleLen = 2;
    List<String> paths = getPaths(currPath, simpleLen);
    // Simple Test(Including query,valueFilter,aggr:max/min/first/last/count/sum/avg)
    insertTestsByFourInterfaces();
    insertNumRecords(paths);
    // query
    SessionQueryDataSet simpleQueryDataSet = session.queryData(paths, START_KEY, END_KEY + 1);
    int simpleResLen = simpleQueryDataSet.getKeys().length;
    List<String> queryResPaths = simpleQueryDataSet.getPaths();
    assertEquals(simpleLen, queryResPaths.size());
    assertEquals(KEY_PERIOD, simpleResLen);
    assertEquals(KEY_PERIOD, simpleQueryDataSet.getValues().size());
    for (int i = 0; i < simpleResLen; i++) {
      long key = simpleQueryDataSet.getKeys()[i];
      assertEquals(i + START_KEY, key);
      List<Object> queryResult = simpleQueryDataSet.getValues().get(i);
      for (int j = 0; j < simpleLen; j++) {
        int pathNum = getPathNum(queryResPaths.get(j));
        assertNotEquals(pathNum, -1);
        assertEquals(key + pathNum, queryResult.get(j));
      }
    }
    // aggrMax
    SessionAggregateQueryDataSet maxDataSet =
        session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.MAX);
    List<String> maxResPaths = maxDataSet.getPaths();
    Object[] maxResult = maxDataSet.getValues();
    assertEquals(simpleLen, maxResPaths.size());
    //        assertEquals(simpleLen, maxDataSet.getTimestamps().length);
    assertEquals(simpleLen, maxDataSet.getValues().length);
    for (int i = 0; i < simpleLen; i++) {
      // assertEquals(-1, maxDataSet.getTimestamps()[i]);
      int pathNum = getPathNum(maxResPaths.get(i));
      assertNotEquals(pathNum, -1);
      assertEquals(END_KEY + pathNum, maxResult[i]);
    }
    // aggrMin
    SessionAggregateQueryDataSet minDataSet =
        session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.MIN);
    List<String> minResPaths = minDataSet.getPaths();
    Object[] minResult = minDataSet.getValues();
    assertEquals(simpleLen, minResPaths.size());
    //        assertEquals(simpleLen, minDataSet.getTimestamps().length);
    assertEquals(simpleLen, minDataSet.getValues().length);
    for (int i = 0; i < simpleLen; i++) {
      // assertEquals(-1, minDataSet.getTimestamps()[i]);
      int pathNum = getPathNum(minResPaths.get(i));
      assertNotEquals(pathNum, -1);
      assertEquals(START_KEY + pathNum, minResult[i]);
    }
    // aggrFirst
    SessionAggregateQueryDataSet firstDataSet =
        session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.FIRST_VALUE);
    List<String> firstResPaths = firstDataSet.getPaths();
    Object[] firstResult = firstDataSet.getValues();
    assertEquals(simpleLen, firstResPaths.size());
    //        assertEquals(simpleLen, firstDataSet.getTimestamps().length);
    assertEquals(simpleLen, firstDataSet.getValues().length);
    for (int i = 0; i < simpleLen; i++) {
      int pathNum = getPathNum(firstResPaths.get(i));
      assertNotEquals(pathNum, -1);
      assertEquals(START_KEY + pathNum, firstResult[i]);
    }
    // aggrLast
    SessionAggregateQueryDataSet lastDataSet =
        session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.LAST_VALUE);
    List<String> lastResPaths = lastDataSet.getPaths();
    Object[] lastResult = lastDataSet.getValues();
    assertEquals(simpleLen, lastResPaths.size());
    //        assertEquals(simpleLen, lastDataSet.getTimestamps().length);
    assertEquals(simpleLen, lastDataSet.getValues().length);
    for (int i = 0; i < simpleLen; i++) {
      int pathNum = getPathNum(lastResPaths.get(i));
      assertNotEquals(pathNum, -1);
      assertEquals(END_KEY + pathNum, lastResult[i]);
    }
    // aggrCount
    SessionAggregateQueryDataSet countDataSet =
        session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.COUNT);
    assertNull(countDataSet.getKeys());
    List<String> countResPaths = countDataSet.getPaths();
    Object[] countResult = countDataSet.getValues();
    assertEquals(simpleLen, countResPaths.size());
    assertEquals(simpleLen, countDataSet.getValues().length);
    for (int i = 0; i < simpleLen; i++) {
      assertEquals(KEY_PERIOD, countResult[i]);
    }
    // aggrSum
    SessionAggregateQueryDataSet sumDataSet =
        session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.SUM);
    assertNull(sumDataSet.getKeys());
    List<String> sumResPaths = sumDataSet.getPaths();
    Object[] sumResult = sumDataSet.getValues();
    assertEquals(simpleLen, sumResPaths.size());
    assertEquals(simpleLen, sumDataSet.getValues().length);
    for (int i = 0; i < simpleLen; i++) {
      double sum = (START_KEY + END_KEY) * KEY_PERIOD / 2.0;
      int pathNum = getPathNum(sumResPaths.get(i));
      assertNotEquals(pathNum, -1);
      assertEquals(sum + pathNum * KEY_PERIOD, changeResultToDouble(sumResult[i]), delta);
    }

    // aggrAvg
    SessionAggregateQueryDataSet avgDataSet =
        session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.AVG);
    assertNull(avgDataSet.getKeys());
    List<String> avgResPaths = avgDataSet.getPaths();
    Object[] avgResult = avgDataSet.getValues();
    assertEquals(simpleLen, avgResPaths.size());
    assertEquals(simpleLen, avgDataSet.getValues().length);
    for (int i = 0; i < simpleLen; i++) {
      double avg = (START_KEY + END_KEY) / 2.0;
      int pathNum = getPathNum(avgResPaths.get(i));
      assertNotEquals(pathNum, -1);
      assertEquals(avg + pathNum, changeResultToDouble(avgResult[i]), delta);
    }

    /*
    // downSample Aggregate
    //TODO the same aggregate return different value in iotdb and influxdb, unlocked this test until fixed
    // downSample max
    SessionQueryDataSet dsMaxDataSet = session.downsampleQuery(paths, START_TIME, END_TIME + 1, AggregateType.MAX, PRECISION);
    int dsMaxLen = dsMaxDataSet.getTimestamps().length;
    List<String> dsMaxResPaths = dsMaxDataSet.getPaths();
    assertEquals(simpleLen, dsMaxResPaths.size());
    assertEquals(factSampleLen, dsMaxLen);
    assertEquals(factSampleLen, dsMaxDataSet.getValues().size());
    for (int i = 0; i < dsMaxLen; i++) {
        long dsKey = dsMaxDataSet.getTimestamps()[i];
        assertEquals(START_TIME + i * PRECISION, dsKey);
        List<Object> dsResult = dsMaxDataSet.getValues().get(i);
        for (int j = 0; j < simpleLen; j++) {
            long maxNum = Math.min((START_TIME + (i + 1) * PRECISION - 1), END_TIME);
            int pathNum = getPathNum(dsMaxResPaths.get(j));
            assertNotEquals(pathNum, -1);
            assertEquals(maxNum + pathNum, dsResult.get(j));
        }
    }
    //min
    SessionQueryDataSet dsMinDataSet = session.downsampleQuery(paths, START_TIME, END_TIME + 1, AggregateType.MIN, PRECISION);
    int dsMinLen = dsMinDataSet.getTimestamps().length;
    List<String> dsMinResPaths = dsMinDataSet.getPaths();
    assertEquals(simpleLen, dsMinResPaths.size());
    assertEquals(factSampleLen, dsMinLen);
    assertEquals(factSampleLen, dsMinDataSet.getValues().size());
    for (int i = 0; i < dsMinLen; i++) {
        long dsKey = dsMinDataSet.getTimestamps()[i];
        assertEquals(START_TIME + i * PRECISION, dsKey);
        List<Object> dsResult = dsMinDataSet.getValues().get(i);
        for (int j = 0; j < simpleLen; j++) {
            long minNum = START_TIME + i * PRECISION;
            int pathNum = getPathNum(dsMinResPaths.get(j));
            assertNotEquals(pathNum, -1);
            assertEquals(minNum + pathNum, dsResult.get(j));
        }
    }
    //first
    SessionQueryDataSet dsFirstDataSet = session.downsampleQuery(paths, START_TIME, END_TIME + 1, AggregateType.FIRST, PRECISION);
    int dsFirstLen = dsFirstDataSet.getTimestamps().length;
    List<String> dsFirstResPaths = dsFirstDataSet.getPaths();
    assertEquals(simpleLen, dsFirstResPaths.size());
    assertEquals(factSampleLen, dsFirstLen);
    assertEquals(factSampleLen, dsFirstDataSet.getValues().size());
    for (int i = 0; i < dsFirstLen; i++) {
        long dsKey = dsFirstDataSet.getTimestamps()[i];
        assertEquals(START_TIME + i * PRECISION, dsKey);
        List<Object> dsResult = dsFirstDataSet.getValues().get(i);
        for (int j = 0; j < simpleLen; j++) {
            long firstNum = START_TIME + i * PRECISION;
            int pathNum = getPathNum(dsFirstResPaths.get(j));
            assertNotEquals(pathNum, -1);
            assertEquals(firstNum + pathNum, dsResult.get(j));
        }
    }
    //last
    SessionQueryDataSet dsLastDataSet = session.downsampleQuery(paths, START_TIME, END_TIME + 1, AggregateType.LAST, PRECISION);
    int dsLastLen = dsLastDataSet.getTimestamps().length;
    List<String> dsLastResPaths = dsLastDataSet.getPaths();
    assertEquals(simpleLen, dsLastResPaths.size());
    assertEquals(factSampleLen, dsLastLen);
    assertEquals(factSampleLen, dsLastDataSet.getValues().size());
    for (int i = 0; i < dsLastLen; i++) {
        long dsKey = dsLastDataSet.getTimestamps()[i];
        assertEquals(START_TIME + i * PRECISION, dsKey);
        List<Object> dsResult = dsLastDataSet.getValues().get(i);
        for (int j = 0; j < simpleLen; j++) {
            long lastNum = Math.min((START_TIME + (i + 1) * PRECISION - 1), END_TIME);
            int pathNum = getPathNum(dsLastResPaths.get(j));
            assertNotEquals(pathNum, -1);
            assertEquals(lastNum + pathNum, dsResult.get(j));
        }
    }
    //Count
    SessionQueryDataSet dsCountDataSet = session.downsampleQuery(paths, START_TIME, END_TIME + 1, AggregateType.COUNT, PRECISION);
    int dsCountLen = dsCountDataSet.getTimestamps().length;
    List<String> dsCountResPaths = dsCountDataSet.getPaths();
    assertEquals(simpleLen, dsCountResPaths.size());
    assertEquals(factSampleLen, dsCountLen);
    assertEquals(factSampleLen, dsCountDataSet.getValues().size());
    for (int i = 0; i < dsCountLen; i++) {
        long dsKey = dsCountDataSet.getTimestamps()[i];
        assertEquals(START_TIME + i * PRECISION, dsKey);
        List<Object> dsResult = dsCountDataSet.getValues().get(i);
        for (int j = 0; j < simpleLen; j++) {
            long countNum = ((START_TIME + (i + 1) * PRECISION - 1) <= END_TIME) ? PRECISION : END_TIME - dsKey + 1;
            assertEquals(countNum, dsResult.get(j));
        }
    }
    //Sum
    SessionQueryDataSet dsSumDataSet = session.downsampleQuery(paths, START_TIME, END_TIME + 1, AggregateType.SUM, PRECISION);
    int dsSumLen = dsSumDataSet.getTimestamps().length;
    List<String> dsSumResPaths = dsSumDataSet.getPaths();
    assertEquals(simpleLen, dsSumResPaths.size());
    assertEquals(factSampleLen, dsSumLen);
    assertEquals(factSampleLen, dsSumDataSet.getValues().size());
    for (int i = 0; i < dsSumLen; i++) {
        long dsKey = dsSumDataSet.getTimestamps()[i];
        assertEquals(START_TIME + i * PRECISION, dsKey);
        List<Object> dsResult = dsSumDataSet.getValues().get(i);
        for (int j = 0; j < simpleLen; j++) {
            long maxNum = Math.min((START_TIME + (i + 1) * PRECISION - 1), END_TIME);
            double sum = (dsKey + maxNum) * (maxNum - dsKey + 1) / 2.0;
            int pathNum = getPathNum(dsSumResPaths.get(j));
            assertNotEquals(pathNum, -1);
            assertEquals(sum + pathNum * (maxNum - dsKey + 1), changeResultToDouble(dsResult.get(j)), delta);
        }
    }

    //avg
    SessionQueryDataSet dsAvgDataSet = session.downsampleQuery(paths, START_TIME, END_TIME + 1, AggregateType.AVG, PRECISION);
    int dsAvgLen = dsAvgDataSet.getTimestamps().length;
    List<String> dsAvgResPaths = dsAvgDataSet.getPaths();
    assertEquals(simpleLen, dsAvgResPaths.size());
    assertEquals(factSampleLen, dsAvgLen);
    assertEquals(factSampleLen, dsAvgDataSet.getValues().size());
    for (int i = 0; i < dsAvgLen; i++) {
        long dsKey = dsAvgDataSet.getTimestamps()[i];
        assertEquals(START_TIME + i * PRECISION, dsKey);
        List<Object> dsResult = dsAvgDataSet.getValues().get(i);
        for (int j = 0; j < simpleLen; j++) {
            long maxNum = Math.min((START_TIME + (i + 1) * PRECISION - 1), END_TIME);
            double avg = (dsKey + maxNum) / 2.0;
            int pathNum = getPathNum(dsAvgResPaths.get(j));
            assertNotEquals(pathNum, -1);
            assertEquals(avg + pathNum, changeResultToDouble(dsResult.get(j)), delta);
        }
    }*/

    // Simple delete and aggregate
    if (isAbleToDelete) {
      // deletePartialDataInColumnTest
      int removeLen = 1;
      List<String> delPartPaths = getPaths(currPath, removeLen);
      // ensure after delete there are still points in the timeseries

      // delete data
      session.deleteDataInColumns(delPartPaths, delStartKey, delEndKey);
      Thread.sleep(1000);
      SessionQueryDataSet delPartDataSet = session.queryData(paths, START_KEY, END_KEY + 1);

      int delPartLen = delPartDataSet.getKeys().length;
      List<String> delPartResPaths = delPartDataSet.getPaths();
      assertEquals(simpleLen, delPartResPaths.size());
      assertEquals(KEY_PERIOD, delPartDataSet.getKeys().length);
      assertEquals(KEY_PERIOD, delPartDataSet.getValues().size());
      for (int i = 0; i < delPartLen; i++) {
        long key = delPartDataSet.getKeys()[i];
        assertEquals(i + START_KEY, key);
        List<Object> result = delPartDataSet.getValues().get(i);
        if (delStartKey <= key && key < delEndKey) {
          for (int j = 0; j < simpleLen; j++) {
            int pathNum = getPathNum(delPartResPaths.get(j));
            assertNotEquals(pathNum, -1);
            if (pathNum >= currPath + removeLen) {
              assertEquals(key + pathNum, result.get(j));
            } else {
              assertNull(result.get(j));
            }
          }
        } else {
          for (int j = 0; j < simpleLen; j++) {
            int pathNum = getPathNum(delPartResPaths.get(j));
            assertNotEquals(pathNum, -1);
            assertEquals(key + pathNum, result.get(j));
          }
        }
      }

      // Test avg for the delete
      SessionAggregateQueryDataSet delPartAvgDataSet =
          session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.AVG);
      List<String> delPartAvgResPaths = delPartAvgDataSet.getPaths();
      Object[] delPartAvgResult = delPartAvgDataSet.getValues();
      assertEquals(simpleLen, delPartAvgResPaths.size());
      assertEquals(simpleLen, delPartAvgDataSet.getValues().length);
      for (int i = 0; i < simpleLen; i++) {
        int pathNum = getPathNum(delPartAvgResPaths.get(i));
        assertNotEquals(pathNum, -1);
        if (pathNum < currPath + removeLen) { // Here is the removed rows
          assertEquals(deleteAvg + pathNum, changeResultToDouble(delPartAvgResult[i]), delta);
        } else {
          assertEquals(originAvg + pathNum, changeResultToDouble(delPartAvgResult[i]), delta);
        }
      }

      // Test count for the delete
      SessionAggregateQueryDataSet delPartCountDataSet =
          session.aggregateQuery(paths, START_KEY, END_KEY + 1, AggregateType.COUNT);
      List<String> delPartCountResPaths = delPartCountDataSet.getPaths();
      Object[] delPartCountResult = delPartCountDataSet.getValues();
      assertEquals(simpleLen, delPartCountResPaths.size());
      assertEquals(simpleLen, delPartCountDataSet.getValues().length);
      for (int i = 0; i < simpleLen; i++) {
        int pathNum = getPathNum(delPartAvgResPaths.get(i));
        assertNotEquals(pathNum, -1);
        if (pathNum < currPath + removeLen) { // Here is the removed rows
          assertEquals(KEY_PERIOD - delKeyPeriod, delPartCountResult[i]);
        } else {
          assertEquals(KEY_PERIOD, delPartCountResult[i]);
        }
      }

      // Test downSample avg of the delete
      SessionQueryDataSet delDsAvgDataSet =
          session.downsampleQuery(paths, START_KEY, END_KEY + 1, AggregateType.AVG, PRECISION);
      int delDsLen = delDsAvgDataSet.getKeys().length;
      List<String> delDsResPaths = delDsAvgDataSet.getPaths();
      assertEquals(factSampleLen, delDsLen);
      assertEquals(factSampleLen, delDsAvgDataSet.getValues().size());
      for (int i = 0; i < delDsLen; i++) {
        long dsStartKey = delDsAvgDataSet.getKeys()[i];
        assertEquals(START_KEY + i * PRECISION, dsStartKey);
        List<Object> dsResult = delDsAvgDataSet.getValues().get(i);
        // j starts from 2 to skip WINDOW_START & WINDOW_END
        for (int j = 2; j < delDsResPaths.size(); j++) {
          long dsEndKey = Math.min((START_KEY + (i + 1) * PRECISION - 1), END_KEY);
          double delDsAvg = (dsStartKey + dsEndKey) / 2.0;
          int pathNum = getPathNum(delDsResPaths.get(j));
          assertNotEquals(pathNum, -1);
          if (pathNum < currPath + removeLen) { // Here is the removed rows
            if (dsStartKey > delEndKey || dsEndKey < delStartKey) {
              assertEquals(delDsAvg + pathNum, changeResultToDouble(dsResult.get(j)), delta);
            } else if (dsStartKey >= delStartKey && dsEndKey < delEndKey) {
              assertNull(dsResult.get(j));
            } else if (dsStartKey < delStartKey) {
              assertEquals(
                  (dsStartKey + delStartKey - 1) / 2.0 + pathNum,
                  changeResultToDouble(dsResult.get(j)),
                  delta);
            } else {
              assertEquals(
                  (dsEndKey + (delEndKey - 1) + 1) / 2.0 + pathNum,
                  changeResultToDouble(dsResult.get(j)),
                  delta);
            }
          } else {
            assertEquals(delDsAvg + pathNum, changeResultToDouble(dsResult.get(j)), delta);
          }
        }
      }

      currPath += simpleLen;

      // deleteAllDataInColumnTest, make new insert and delete here
      int dataInColumnLen = 3;
      List<String> delDataInColumnPaths = getPaths(currPath, dataInColumnLen);
      insertNumRecords(delDataInColumnPaths);
      // TODO add test to test if the insert is right(Is it necessary?)
      int deleteDataInColumnLen = 2;
      List<String> delAllDataInColumnPaths = getPaths(currPath, deleteDataInColumnLen);
      session.deleteDataInColumns(delAllDataInColumnPaths, START_KEY, END_KEY + 1);
      Thread.sleep(1000);
      SessionQueryDataSet delDataInColumnDataSet =
          session.queryData(delDataInColumnPaths, START_KEY, END_KEY + 1);
      int delDataInColumnLen = delDataInColumnDataSet.getKeys().length;
      List<String> delDataInColumnResPaths = delDataInColumnDataSet.getPaths();
      assertEquals(KEY_PERIOD, delDataInColumnLen);
      assertEquals(KEY_PERIOD, delDataInColumnDataSet.getValues().size());
      for (int i = 0; i < delDataInColumnLen; i++) {
        long key = delDataInColumnDataSet.getKeys()[i];
        assertEquals(i + START_KEY, key);
        List<Object> result = delDataInColumnDataSet.getValues().get(i);
        assertEquals(dataInColumnLen - deleteDataInColumnLen, delDataInColumnResPaths.size());
        for (int j = 0; j < dataInColumnLen - deleteDataInColumnLen; j++) {
          int pathNum = getPathNum(delDataInColumnResPaths.get(j));
          assertNotEquals(pathNum, -1);
          if (pathNum < currPath + deleteDataInColumnLen) { // Here is the removed rows
            assertNull(result.get(j));
          } else {
            assertEquals(key + pathNum, result.get(j));
          }
        }
      }

      /*
      // Test value filter for the delete TODO change the value filter to the right test
      int vfTime = 1123;
      String booleanExpression = COLUMN_D2_S2 + " > " + vfTime;
      SessionQueryDataSet vfDataSet = session.valueFilterQuery(delDataInColumnPaths, START_TIME, END_TIME, booleanExpression);
      int vfLen = vfDataSet.getTimestamps().length;
      List<String> vfResPaths = vfDataSet.getPaths();
      assertEquals(TIME_PERIOD + START_TIME - vfTime - 1, vfDataSet.getTimestamps().length);
      for (int i = 0; i < vfLen; i++) {
          long key = vfDataSet.getTimestamps()[i];
          assertEquals(i + vfTime, key);
          List<Object> result = vfDataSet.getValues().get(i);
          for (int j = 0; j < dataInColumnLen; j++) {
              int pathNum = getPathNum(delDataInColumnResPaths.get(j));
              assertNotEquals(pathNum, -1);
              if (pathNum < deleteDataInColumnLen){ // Here is the removed rows
                  assertNull(result.get(j));
              } else {
                  assertEquals(key + pathNum, result.get(j));
              }
          }
      }*/

      // Test aggregate function for the delete
      SessionAggregateQueryDataSet delDataAvgSet =
          session.aggregateQuery(delDataInColumnPaths, START_KEY, END_KEY + 1, AggregateType.AVG);
      List<String> delDataAvgResPaths = delDataAvgSet.getPaths();
      Object[] delDataAvgResult = delDataAvgSet.getValues();
      assertEquals(dataInColumnLen - deleteDataInColumnLen, delDataAvgResPaths.size());
      assertEquals(dataInColumnLen - deleteDataInColumnLen, delDataAvgSet.getValues().length);
      for (int i = 0; i < dataInColumnLen - deleteDataInColumnLen; i++) {
        int pathNum = getPathNum(delDataAvgResPaths.get(i));
        assertNotEquals(pathNum, -1);
        assertEquals((START_KEY + END_KEY) / 2.0 + pathNum, delDataAvgResult[i]);
      }

      // Test downsample function for the delete
      SessionQueryDataSet dsDelDataInColSet =
          session.downsampleQuery(
              delDataInColumnPaths, START_KEY, END_KEY + 1, AggregateType.AVG, PRECISION);
      int dsDelDataLen = dsDelDataInColSet.getKeys().length;
      List<String> dsDelDataResPaths = dsDelDataInColSet.getPaths();
      assertEquals(factSampleLen, dsDelDataLen);
      assertEquals(factSampleLen, dsDelDataInColSet.getValues().size());
      for (int i = 0; i < dsDelDataLen; i++) {
        long dsKey = dsDelDataInColSet.getKeys()[i];
        assertEquals(START_KEY + i * PRECISION, dsKey);
        List<Object> dsResult = dsDelDataInColSet.getValues().get(i);
        // j starts from 2 to skip WINDOW_START & WINDOW_END
        for (int j = 2; j < dsDelDataResPaths.size(); j++) {
          long maxNum = Math.min((START_KEY + (i + 1) * PRECISION - 1), END_KEY);
          double avg = (dsKey + maxNum) / 2.0;
          int pathNum = getPathNum(dsDelDataResPaths.get(j));
          assertNotEquals(pathNum, -1);
          assertEquals(avg + pathNum, changeResultToDouble(dsResult.get(j)), delta);
        }
      }
      currPath += dataInColumnLen;

      // deleteAllColumnsTest
      int delAllColumnLen = 3;
      List<String> delAllColumnPaths = getPaths(currPath, delAllColumnLen);
      insertNumRecords(delAllColumnPaths);
      session.deleteColumns(delAllColumnPaths);
      SessionQueryDataSet delAllColumnDataSet =
          session.queryData(delAllColumnPaths, START_KEY, END_KEY + 1);
      assertEquals(0, delAllColumnDataSet.getPaths().size());
      assertEquals(0, delAllColumnDataSet.getKeys().length);
      assertEquals(0, delAllColumnDataSet.getValues().size());
      currPath += delAllColumnLen;

      // deletePartColumnsTest
      int delPartColumnLen = 3;
      List<String> partColumnPaths = getPaths(currPath, delPartColumnLen);
      insertNumRecords(partColumnPaths);
      int delPartColumnNum = 2;
      List<String> delPartColumnPaths = getPaths(currPath, delPartColumnNum);
      session.deleteColumns(delPartColumnPaths);
      SessionQueryDataSet delPartColumnDataSet =
          session.queryData(partColumnPaths, START_KEY, END_KEY + 1);
      int delPartResLen = delPartColumnDataSet.getKeys().length;
      List<String> delPartColResPaths = delPartColumnDataSet.getPaths();
      assertEquals(delPartColumnLen - delPartColumnNum, delPartColResPaths.size());
      assertEquals(KEY_PERIOD, delPartResLen);
      assertEquals(KEY_PERIOD, delPartColumnDataSet.getValues().size());
      for (int i = 0; i < delPartResLen; i++) {
        long key = delPartColumnDataSet.getKeys()[i];
        assertEquals(i + START_KEY, key);
        List<Object> result = delPartColumnDataSet.getValues().get(i);
        for (int j = 0; j < delPartColumnLen - delPartColumnNum; j++) {
          int pathNum = getPathNum(delPartColResPaths.get(j));
          assertNotEquals(pathNum, -1);
          if (pathNum < currPath + delPartColumnNum) { // Here is the removed rows
            fail();
          } else {
            assertEquals(key + pathNum, result.get(j));
          }
        }
      }
      currPath += delPartColumnLen;
    } else {
      currPath += simpleLen;
    }

    // fake data insert test
    int fakeDataLen = 2;
    List<String> fakeDataPaths = getPaths(currPath, fakeDataLen);
    insertNumRecords(fakeDataPaths);
    int count = 1;
    boolean isError = false;
    // TODO Add some more specific conditions, and try to merge this to the origin code
    try {
      insertFakeNumRecords(paths, count + KEY_PERIOD * 100);
    } catch (Exception e) {
      LOGGER.error("unexpected error: ", e);
      isError = true;
    } finally {
      // assertTrue(isError);
    }
    currPath += fakeDataLen;

    // dataTypeTest
    int dataTypeLen = 6;
    List<String> dataTypePaths = getPaths(currPath, dataTypeLen);
    insertDataTypeRecords(dataTypePaths, currPath);

    // queryData
    SessionQueryDataSet dtQueryDataSet = session.queryData(dataTypePaths, START_KEY, END_KEY + 1);
    int dtQueryLen = dtQueryDataSet.getKeys().length;
    List<String> dataTypeResPaths = dtQueryDataSet.getPaths();
    assertEquals(6, dataTypeResPaths.size());
    assertEquals(KEY_PERIOD, dtQueryLen);
    assertEquals(KEY_PERIOD, dtQueryDataSet.getValues().size());
    for (int i = 0; i < dtQueryLen; i++) {
      long key = dtQueryDataSet.getKeys()[i];
      assertEquals(i + START_KEY, key);
      List<Object> result = dtQueryDataSet.getValues().get(i);
      for (int j = 0; j < 6; j++) {
        int currPathPos = getPathNum(dataTypeResPaths.get(j)) - currPath;
        switch (currPathPos) {
          case 1:
            assertEquals((int) ((END_KEY - i) + 1), changeResultToInteger(result.get(j)));
            break;
          case 2:
            assertEquals((i + 2 + START_KEY) * 1000, result.get(j));
            break;
          case 3:
            assertEquals(
                (float) (i + 3 + START_KEY + 0.01),
                changeResultToFloat(result.get(j)),
                (float) delta);
            break;
          case 4:
            assertEquals(
                ((END_KEY - i) + 4 + 0.01) * 999, changeResultToDouble(result.get(j)), delta);
            break;
          case 5:
            assertArrayEquals(getRandomStr(i, STRING_LEN).getBytes(), (byte[]) (result.get(j)));
            break;
          case 0:
            assertEquals(i % 2 == 0, result.get(j));
            break;
          default:
            fail();
            break;
        }
      }
    }

    // aggregateData max & avg
    List<String> dTAggrPaths = getPaths(currPath + 1, 4);
    // max
    SessionAggregateQueryDataSet dtMaxDataSet =
        session.aggregateQuery(dTAggrPaths, START_KEY, END_KEY + 1, AggregateType.MAX);
    List<String> dtMaxPaths = dtMaxDataSet.getPaths();
    Object[] dtMaxResult = dtMaxDataSet.getValues();
    assertEquals(4, dtMaxPaths.size());
    assertEquals(4, dtMaxResult.length);
    for (int i = 0; i < 4; i++) {
      int currPathPos = getPathNum(dtMaxPaths.get(i)) - currPath;
      switch (currPathPos) {
        case 1:
          assertEquals((int) (END_KEY + 1), changeResultToInteger(dtMaxResult[i]));
          break;
        case 2:
          assertEquals((END_KEY + 2) * 1000, dtMaxResult[i]);
          break;
        case 3:
          assertEquals(
              (float) (END_KEY + 3 + 0.01), changeResultToFloat(dtMaxResult[i]), (float) delta);
          break;
        case 4:
          assertEquals((END_KEY + 4 + 0.01) * 999, changeResultToDouble(dtMaxResult[i]), delta);
          break;
        default:
          fail();
          break;
      }
    }

    // avg
    SessionAggregateQueryDataSet dtAvgDataSet =
        session.aggregateQuery(dTAggrPaths, START_KEY, END_KEY + 1, AggregateType.AVG);
    List<String> dtAvgPaths = dtAvgDataSet.getPaths();
    Object[] dtAvgResult = dtAvgDataSet.getValues();
    assertEquals(4, dtAvgPaths.size());
    assertEquals(4, dtAvgResult.length);
    for (int i = 0; i < 4; i++) {
      int currPathPos = getPathNum(dtAvgPaths.get(i)) - currPath;
      switch (currPathPos) {
        case 1:
          assertEquals(
              (START_KEY + END_KEY) / 2.0 + 1, changeResultToDouble(dtAvgResult[i]), delta);
          break;
        case 2:
          assertEquals((START_KEY + END_KEY) * 500.0 + 2000, dtAvgResult[i]);
          break;
        case 3:
          assertEquals(
              (START_KEY + END_KEY) / 2.0 + 3 + 0.01,
              changeResultToDouble(dtAvgResult[i]),
              delta * 10000);
          break;
        case 4:
          assertEquals(
              (START_KEY + END_KEY) * 999 / 2.0 + 4.01 * 999,
              changeResultToDouble(dtAvgResult[i]),
              delta * 10000);
          break;
        default:
          fail();
          break;
      }
    }

    if (isAbleToDelete) {
      // deletePartialData

      List<String> dtDelPaths = new ArrayList<>();
      dtDelPaths.add(getSinglePath(currPath, 1));
      dtDelPaths.add(getSinglePath(currPath, 3));
      dtDelPaths.add(getSinglePath(currPath, 5));

      // ensure after delete there are still points in the timeseries
      long dtDelStartKey = START_KEY + KEY_PERIOD / 5;
      long dtDelEndKey = START_KEY + KEY_PERIOD / 10 * 9;
      long dtDelKeyPeriod = (dtDelEndKey - 1) - dtDelStartKey + 1;

      session.deleteDataInColumns(dtDelPaths, dtDelStartKey, dtDelEndKey);
      Thread.sleep(1000);
      SessionQueryDataSet dtDelPartDataSet =
          session.queryData(dataTypePaths, START_KEY, END_KEY + 1);

      int dtDelPartLen = dtDelPartDataSet.getKeys().length;
      List<String> dtDelPartResPaths = dtDelPartDataSet.getPaths();
      assertEquals(KEY_PERIOD, dtDelPartDataSet.getKeys().length);
      assertEquals(KEY_PERIOD, dtDelPartDataSet.getValues().size());
      for (int i = 0; i < dtDelPartLen; i++) {
        long key = dtDelPartDataSet.getKeys()[i];
        assertEquals(i + START_KEY, key);
        List<Object> result = dtDelPartDataSet.getValues().get(i);
        for (int j = 0; j < 6; j++) {
          int currPathPos = getPathNum(dtDelPartResPaths.get(j)) - currPath;
          switch (currPathPos) {
            case 0:
              assertEquals(key % 2 == 0, result.get(j));
              break;
            case 2:
              assertEquals((key + 2) * 1000, result.get(j));
              break;
            case 4:
              assertEquals(
                  (4 + (END_KEY - key) + START_KEY + 0.01) * 999,
                  changeResultToDouble(result.get(j)),
                  delta);
              break;
            case 1:
              if (delStartKey <= key && key < delEndKey) {
                assertNull(result.get(j));
              } else {
                assertEquals((int) ((END_KEY - i) + 1), changeResultToInteger(result.get(j)));
              }
              break;
            case 3:
              if (delStartKey <= key && key < delEndKey) {
                assertNull(result.get(j));
              } else {
                assertEquals(
                    (float) (i + 3 + START_KEY + 0.01),
                    changeResultToFloat(result.get(j)),
                    (float) delta);
              }
              break;
            case 5:
              if (delStartKey <= key && key < delEndKey) {
                assertNull(result.get(j));
              } else {
                assertArrayEquals(getRandomStr(i, STRING_LEN).getBytes(), (byte[]) (result.get(j)));
              }
              break;
            default:
              fail();
              break;
          }
        }
      }

      // Test aggregate function for the delete
      SessionAggregateQueryDataSet dtDelPartAvgDataSet =
          session.aggregateQuery(dTAggrPaths, START_KEY, END_KEY + 1, AggregateType.AVG);
      List<String> dtDelPartAvgResPaths = dtDelPartAvgDataSet.getPaths();
      Object[] dtDelPartAvgResult = dtDelPartAvgDataSet.getValues();
      assertEquals(4, dtDelPartAvgResPaths.size()); // fixed
      assertEquals(4, dtDelPartAvgResult.length);
      for (int i = 0; i < 4; i++) {
        int currPathPos = getPathNum(dtDelPartAvgResPaths.get(i)) - currPath;
        switch (currPathPos) {
          case 1:
            assertEquals(
                ((START_KEY + END_KEY) * KEY_PERIOD / 2.0
                            - (END_KEY
                                    - (dtDelStartKey - START_KEY)
                                    + END_KEY
                                    - (dtDelEndKey - 1 - START_KEY))
                                * dtDelKeyPeriod
                                / 2.0)
                        / (KEY_PERIOD - dtDelKeyPeriod)
                    + 1.0,
                changeResultToDouble(dtDelPartAvgResult[i]),
                delta * 10000);
            break;
          case 2:
            assertEquals((START_KEY + END_KEY) * 500.0 + 2000, dtDelPartAvgResult[i]);
            break;
          case 3:
            assertEquals(
                ((START_KEY + END_KEY) * KEY_PERIOD / 2.0
                            - (dtDelStartKey + dtDelEndKey - 1) * dtDelKeyPeriod / 2.0)
                        / (KEY_PERIOD - dtDelKeyPeriod)
                    + 3.01,
                changeResultToDouble(dtDelPartAvgResult[i]),
                delta * 10000);
            break;
          case 4:
            assertEquals(
                (START_KEY + END_KEY) * 999 / 2.0 + 4.01 * 999,
                changeResultToDouble(dtDelPartAvgResult[i]),
                delta * 10000);
            break;
          default:
            fail();
            break;
        }
      }

      currPath += dataTypeLen;

      // deleteAll data in column, before deletion must insert first
      List<String> dataTypePaths2 = getPaths(currPath, dataTypeLen);
      insertDataTypeRecords(dataTypePaths2, currPath);
      int dtDelColumnNum = 2;
      List<String> dtDelColumnPaths = getPaths(currPath, dtDelColumnNum);
      session.deleteDataInColumns(dtDelColumnPaths, START_KEY, END_KEY + 1);
      Thread.sleep(1000);
      SessionQueryDataSet dtDelColDataSet =
          session.queryData(dataTypePaths2, START_KEY, END_KEY + 1);
      int dtDelColLen = dtDelColDataSet.getKeys().length;
      List<String> dtDelColResPaths = dtDelColDataSet.getPaths();
      assertEquals(KEY_PERIOD, dtDelColDataSet.getKeys().length);
      assertEquals(KEY_PERIOD, dtDelColDataSet.getValues().size());
      for (int i = 0; i < dtDelColLen; i++) {
        long key = dtDelColDataSet.getKeys()[i];
        assertEquals(i + START_KEY, key);
        List<Object> result = dtDelColDataSet.getValues().get(i);
        for (int j = 0; j < 4; j++) {
          int currPathPos = getPathNum(dtDelColResPaths.get(j)) - currPath;
          if (currPathPos < dtDelColumnNum) {
            assertNull(result.get(j));
          } else {
            switch (getPathNum(dtDelColResPaths.get(j)) - currPath) {
              case 0:
                assertEquals(key % 2 == 0, result.get(j));
                break;
              case 1:
                assertEquals((int) ((END_KEY - key) + 1), changeResultToInteger(result.get(j)));
                break;
              case 2:
                assertEquals((key + 2) * 1000, result.get(j));
                break;
              case 3:
                assertEquals(
                    (float) (key + 3 + 0.01), changeResultToFloat(result.get(j)), (float) delta);
                break;
              case 4:
                assertEquals(
                    (4 + (END_KEY - key) + START_KEY + 0.01) * 999,
                    changeResultToDouble(result.get(j)),
                    delta);
                break;
              case 5:
                assertArrayEquals(getRandomStr(i, STRING_LEN).getBytes(), (byte[]) (result.get(j)));
                break;
              default:
                fail();
                break;
            }
          }
        }
      }

      // Test aggregate function for the delete

      List<String> dTDeleteAggrPaths =
          getPaths(currPath + 1, 4); // only the 1-4 columns are numbers

      SessionAggregateQueryDataSet dtDelColAvgDataSet =
          session.aggregateQuery(dTDeleteAggrPaths, START_KEY, END_KEY + 1, AggregateType.AVG);
      List<String> dtDelColAvgPaths = dtDelColAvgDataSet.getPaths();
      Object[] dtDelColAvgResult = dtDelColAvgDataSet.getValues();
      assertEquals(3, dtDelColAvgPaths.size());
      assertEquals(3, dtDelColAvgDataSet.getValues().length);
      for (int i = 0; i < 3; i++) {
        int currPathPos = getPathNum(dtDelColAvgPaths.get(i)) - currPath;
        switch (currPathPos) {
          case 1:
            assertEquals(
                (START_KEY + END_KEY) / 2.0 + 1, changeResultToDouble(dtDelColAvgResult[i]), delta);
            break;
          case 2:
            assertEquals((START_KEY + END_KEY) * 500.0 + 2000, dtDelColAvgResult[i]);
            break;
          case 3:
            assertEquals(
                (START_KEY + END_KEY) / 2.0 + 3 + 0.01,
                changeResultToDouble(dtDelColAvgResult[i]),
                delta * 10000);
            break;
          case 4:
            assertEquals(
                (START_KEY + END_KEY) * 999 / 2.0 + 4.01 * 999,
                changeResultToDouble(dtDelColAvgResult[i]),
                delta * 10000);
            break;
          default:
            fail();
            break;
        }
      }

      currPath += dataTypeLen;
    } else {
      currPath += dataTypeLen;
    }

    if (ifNeedCapExp) {
      // addSameTypeOfStorageEngineTest
      session.addStorageEngine("127.0.0.1", defaultPort2, storageEngineType, extraParams);

      int addStorageLen = 5;
      List<String> addStoragePaths = getPaths(currPath, addStorageLen);
      insertNumRecords(addStoragePaths);
      // query Test
      SessionQueryDataSet addStQueryDataSet =
          session.queryData(addStoragePaths, START_KEY, END_KEY + 1);
      int addStResLen = addStQueryDataSet.getKeys().length;
      List<String> addStResPaths = addStQueryDataSet.getPaths();
      assertEquals(addStorageLen, addStResPaths.size());
      assertEquals(KEY_PERIOD, addStResLen);
      assertEquals(KEY_PERIOD, addStQueryDataSet.getValues().size());
      for (int i = 0; i < addStorageLen; i++) {
        long key = addStQueryDataSet.getKeys()[i];
        assertEquals(i + START_KEY, key);
        List<Object> queryResult = addStQueryDataSet.getValues().get(i);
        for (int j = 0; j < addStorageLen; j++) {
          int pathNum = getPathNum(addStResPaths.get(j));
          assertNotEquals(pathNum, -1);
          assertEquals(key + pathNum, queryResult.get(j));
        }
      }
      // aggr Count
      SessionAggregateQueryDataSet addStCountDataSet =
          session.aggregateQuery(addStoragePaths, START_KEY, END_KEY + 1, AggregateType.COUNT);
      assertNull(addStCountDataSet.getKeys());
      List<String> addStCountResPaths = addStCountDataSet.getPaths();
      Object[] addStCountResult = addStCountDataSet.getValues();
      assertEquals(addStorageLen, addStCountResPaths.size());
      assertEquals(addStorageLen, addStCountDataSet.getValues().length);
      for (int i = 0; i < addStorageLen; i++) {
        assertEquals(KEY_PERIOD, addStCountResult[i]);
      }
      // aggr Avg
      SessionAggregateQueryDataSet addStAvgDataSet =
          session.aggregateQuery(addStoragePaths, START_KEY, END_KEY + 1, AggregateType.AVG);
      assertNull(addStAvgDataSet.getKeys());
      List<String> addStAvgResPaths = addStAvgDataSet.getPaths();
      Object[] addStAvgResult = addStAvgDataSet.getValues();
      assertEquals(addStorageLen, addStAvgResPaths.size());
      assertEquals(addStorageLen, addStAvgDataSet.getValues().length);
      for (int i = 0; i < addStorageLen; i++) {
        double avg = (START_KEY + END_KEY) / 2.0;
        int pathNum = getPathNum(addStAvgResPaths.get(i));
        assertNotEquals(pathNum, -1);
        assertEquals(avg + pathNum, changeResultToDouble(addStAvgResult[i]), delta);
      }
      // deletePartial, with query, aggr count and aggr Avg
      if (isAbleToDelete) {
        int stRemoveLen = 3;
        List<String> stDelPartPaths = getPaths(currPath, stRemoveLen);
        // ensure after delete there are still points in the timeseries

        // delete data
        session.deleteDataInColumns(stDelPartPaths, delStartKey, delEndKey);
        Thread.sleep(1000);
        SessionQueryDataSet stDelPartDataSet =
            session.queryData(addStoragePaths, START_KEY, END_KEY + 1);
        // query
        int stDelPartLen = stDelPartDataSet.getKeys().length;
        List<String> stDelPartResPaths = stDelPartDataSet.getPaths();
        assertEquals(addStorageLen, stDelPartResPaths.size());
        assertEquals(KEY_PERIOD, stDelPartDataSet.getKeys().length);
        assertEquals(KEY_PERIOD, stDelPartDataSet.getValues().size());
        for (int i = 0; i < stDelPartLen; i++) {
          long key = stDelPartDataSet.getKeys()[i];
          assertEquals(i + START_KEY, key);
          List<Object> result = stDelPartDataSet.getValues().get(i);
          if (delStartKey <= key && key < delEndKey) {
            for (int j = 0; j < addStorageLen; j++) {
              int pathNum = getPathNum(stDelPartResPaths.get(j));
              assertNotEquals(pathNum, -1);
              if (pathNum >= currPath + stRemoveLen) {
                assertEquals(key + pathNum, result.get(j));
              } else {
                assertNull(result.get(j));
              }
            }
          } else {
            for (int j = 0; j < addStorageLen; j++) {
              int pathNum = getPathNum(stDelPartResPaths.get(j));
              assertNotEquals(pathNum, -1);
              assertEquals(key + pathNum, result.get(j));
            }
          }
        }

        // Test avg for the delete
        SessionAggregateQueryDataSet stDelPartAvgDataSet =
            session.aggregateQuery(addStoragePaths, START_KEY, END_KEY + 1, AggregateType.AVG);
        List<String> stDelPartAvgResPaths = stDelPartAvgDataSet.getPaths();
        Object[] stDelPartAvgResult = stDelPartAvgDataSet.getValues();
        assertEquals(addStorageLen, stDelPartAvgResPaths.size());
        assertEquals(addStorageLen, stDelPartAvgDataSet.getValues().length);
        for (int i = 0; i < addStorageLen; i++) {
          int pathNum = getPathNum(stDelPartAvgResPaths.get(i));
          assertNotEquals(pathNum, -1);
          if (pathNum < currPath + stRemoveLen) { // Here is the removed rows
            assertEquals(deleteAvg + pathNum, changeResultToDouble(stDelPartAvgResult[i]), delta);
          } else {
            assertEquals(originAvg + pathNum, changeResultToDouble(stDelPartAvgResult[i]), delta);
          }
        }

        // Test count for the delete
        SessionAggregateQueryDataSet stDelPartCountDataSet =
            session.aggregateQuery(addStoragePaths, START_KEY, END_KEY + 1, AggregateType.COUNT);
        List<String> stDelPartCountResPaths = stDelPartCountDataSet.getPaths();
        Object[] stDelPartCountResult = stDelPartCountDataSet.getValues();
        assertEquals(addStorageLen, stDelPartCountResPaths.size());
        assertEquals(addStorageLen, stDelPartCountDataSet.getValues().length);
        for (int i = 0; i < addStorageLen; i++) {
          int pathNum = getPathNum(stDelPartAvgResPaths.get(i));
          assertNotEquals(pathNum, -1);
          if (pathNum < currPath + stRemoveLen) { // Here is the removed rows
            assertEquals(KEY_PERIOD - delKeyPeriod, stDelPartCountResult[i]);
          } else {
            assertEquals(KEY_PERIOD, stDelPartCountResult[i]);
          }
        }
      }
    }
    LOGGER.info("session test finished");
  }
}
