package com.redislabs.redistimeseries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.redislabs.redistimeseries.information.Info;
import com.redislabs.redistimeseries.information.Rule;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;

public class RedisTimeSeriesTest {

  private final JedisPool pool = new JedisPool();
  private final RedisTimeSeries client = new RedisTimeSeries(); 

  @Before
  public void testClient() {
    try (Jedis conn = pool.getResource()) {
      conn.flushAll();
    }      
  }

  @Test
  public void testCreate() {
    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");

    Assert.assertTrue(client.create("series1", 10/*retentionTime*/, labels));
    try (Jedis conn = pool.getResource()) {
      Assert.assertEquals("TSDB-TYPE", conn.type("series1"));
    }          

    Assert.assertTrue(client.create("series2", labels));
    try (Jedis conn = pool.getResource()) {
      Assert.assertEquals("TSDB-TYPE", conn.type("series2"));
    }

    Assert.assertTrue(client.create("series3", 10/*retentionTime*/));
    try (Jedis conn = pool.getResource()) {
      Assert.assertEquals("TSDB-TYPE", conn.type("series3"));
    }

    Assert.assertTrue(client.create("series4"));
    try (Jedis conn = pool.getResource()) {
      Assert.assertEquals("TSDB-TYPE", conn.type("series4"));
    }

    try {
      Assert.assertTrue(client.create("series1", 10/*retentionTime*/, labels));
      Assert.fail();
    } catch(JedisDataException e) {
    }

    try {
      Assert.assertTrue(client.create("series1", labels));
      Assert.fail();
    } catch(JedisDataException e) {
    }

    try {
      Assert.assertTrue(client.create("series1", 10));
      Assert.fail();
    } catch(JedisDataException e) {
    }

    try {
      Assert.assertTrue(client.create("series1"));
      Assert.fail();
    } catch(JedisDataException e) {
    }

  }

  @Test
  public void testRule() {
    Assert.assertTrue(client.create("source"));
    Assert.assertTrue(client.create("dest", 10/*retentionTime*/));

    Assert.assertTrue(client.createRule("source", Aggregation.AVG, 100, "dest"));

    try {
      Assert.assertFalse(client.createRule("source", Aggregation.COUNT, 100, "dest"));
      Assert.fail();
    } catch(JedisDataException e) {
      // Error on creating same rule twice
    }

    Assert.assertTrue(client.deleteRule("source", "dest"));
    Assert.assertTrue(client.createRule("source", Aggregation.COUNT, 100, "dest"));

    try {
      Assert.assertTrue(client.deleteRule("source", "dest1"));
      Assert.fail();
    } catch(JedisDataException e) {
      // Error on creating same rule twice
    }
  }

  @Test
  public void testAdd() {
    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");    
    Assert.assertTrue(client.create("seriesAdd", 10000L/*retentionTime*/, labels));

    Assert.assertEquals(1000L, client.add("seriesAdd", 1000L, 1.1, 10000, null));
    Assert.assertEquals(2000L, client.add("seriesAdd", 2000L, 3.2, null));
    Assert.assertEquals(3200L, client.add("seriesAdd", 3200L, 3.2, 10000));
    Assert.assertEquals(4500L, client.add("seriesAdd", 4500L, -1.2));

    Value[] values = client.range("seriesAdd", 800L, 3000L);
    Assert.assertEquals(2, values.length);
    
    values = client.range("seriesAdd", 1200L, 4600L, Aggregation.COUNT, 1);
    Assert.assertEquals(3, values.length);

    Range[] ranges = client.mrange(500L, 4600L, Aggregation.COUNT, 1, "l1=v1");
    Assert.assertEquals(1, ranges.length);

    Range range = ranges[0];
    Assert.assertEquals("seriesAdd", range.getKey());
    Assert.assertEquals(labels, range.getLables());

    Value[] rangeValues = range.getValues();
    Assert.assertEquals(4, rangeValues.length);
    Assert.assertEquals( new Value(1000, 1), rangeValues[0]);
    Assert.assertNotEquals( new Value(1000, 1.1), rangeValues[0]);
    Assert.assertEquals( 2000L, rangeValues[1].getTime());
    Assert.assertEquals( "(2000:1.0)", rangeValues[1].toString());
    Assert.assertEquals( 1072695248, rangeValues[1].hashCode());
    Assert.assertFalse(rangeValues[1].equals("(2000:1.0)"));
  
    // Add with labels
    Map<String, String> labels2 = new HashMap<>();
    labels2.put("l3", "v3");
    labels2.put("l4", "v4");    
    Assert.assertEquals(client.add("seriesAdd2", 1000L, 1.1, 10000, labels2), 1000L);
    Range[] ranges2 = client.mrange(500L, 4600L, Aggregation.COUNT, 1, "l4=v4");
    Assert.assertEquals(1, ranges2.length);
    
    Map<String, String> labels3 = new HashMap<>();
    labels3.put("l3", "v33");
    labels3.put("l4", "v4");    
    Assert.assertEquals(1000L, client.add("seriesAdd3", 1000L, 1.1, labels3));
    Range[] ranges3 = client.mrange(500L, 4600L, Aggregation.COUNT, 1, "l4=v4");
    Assert.assertEquals(2, ranges3.length);

    // Failure cases
    try {
      client.add("seriesAdd", 800L, 1.1);
      Assert.fail();
    } catch(JedisDataException e) {
      // Error on creating same rule twice
    }
    
    try {
      client.add("seriesAdd", 800L, 1.1, 10000);
      Assert.fail();
    } catch(JedisDataException e) {
      // Error on creating same rule twice
    }
    
    try {
      client.add("seriesAdd", 800L, 1.1, 10000, null);
      Assert.fail();
    } catch(JedisDataException e) {
      // Error on creating same rule twice
    }

    try {
      client.range("seriesAdd1", 500L, 4000L, Aggregation.COUNT, 1);
      Assert.fail();
    } catch(JedisDataException e) {
      // Error on creating same rule twice
    }
  }
  
  @Test
  public void testAddStar() throws InterruptedException {
    Map<String, String> labels = new HashMap<>();
    labels.put("l11", "v11");
    labels.put("l22", "v22");    
    Assert.assertTrue(client.create("seriesAdd2", 10000L/*retentionTime*/, labels));

    long startTime = System.currentTimeMillis();
    Thread.sleep(1);
    long add1 = client.add("seriesAdd2", 0, 1.1, 10000, null);
    Assert.assertTrue(add1>startTime);
    Thread.sleep(1);
    long add2 = client.add("seriesAdd2", 0, 3.2, null);
    Assert.assertTrue(add2>add1);
    Thread.sleep(1);
    long add3 = client.add("seriesAdd2", 0, 3.2, 10000);
    Assert.assertTrue(add3>add2);
    Thread.sleep(1);
    long add4 = client.add("seriesAdd2", 0, -1.2);
    Assert.assertTrue(add4>add3);
    Thread.sleep(1);
    long endTime = System.currentTimeMillis();
    Assert.assertTrue(System.currentTimeMillis()>add4);

    Value[] values = client.range("seriesAdd2", startTime, add3);
    Assert.assertEquals(3, values.length);
  }
  
  @Test
  public void testMadd() {
    Map<String, String> labels = new HashMap<>();
    labels.put("l1", "v1");
    labels.put("l2", "v2");    
    Assert.assertTrue(client.create("seriesAdd1", 10000L/*retentionTime*/, labels));
    Assert.assertTrue(client.create("seriesAdd2", 10000L/*retentionTime*/, labels));

    long now = System.currentTimeMillis();
    List<Object> result = client.madd( 
        new Measurement("seriesAdd1", 0L, 1.1), // System time
        new Measurement("seriesAdd2", 2000L, 3.2),
        new Measurement("seriesAdd1", 1500L, 2.67), // Should return an error
        new Measurement("seriesAdd2", 3200L, 54.2));
    
    Assert.assertTrue(now <= (Long)result.get(0) && now+5 > (Long)result.get(0));
    Assert.assertEquals(2000L, result.get(1));
    Assert.assertTrue( result.get(2) instanceof JedisDataException);
    Assert.assertEquals(3200L, result.get(3));
    
    Value[] values1 = client.range("seriesAdd1", 0, Long.MAX_VALUE);
    Assert.assertEquals(1, values1.length);
    Assert.assertEquals(1.1, values1[0].getValue(), 0.001);

    Value[] values2 = client.range("seriesAdd2", 0, Long.MAX_VALUE);
    Assert.assertEquals(2, values2.length);
    Assert.assertEquals(3.2, values2[0].getValue(), 0.001);
    Assert.assertEquals(54.2, values2[1].getValue(), 0.001);
  }

  @Test
  public void testIncDec() {
    Assert.assertTrue(client.create("seriesIncDec", 100*1000/*100sec retentionTime*/));   
    Assert.assertTrue( client.add("seriesIncDec", -1, 1, 10000, null)-System.currentTimeMillis() < 100 );
    Assert.assertTrue(client.incrBy("seriesIncDec", 3, 1000));
    Assert.assertTrue(client.decrBy("seriesIncDec", 2, 1000));

    Value[] values = client.range("seriesIncDec", 1L, Long.MAX_VALUE);
    Assert.assertEquals(1, values.length);
    Assert.assertEquals(2, values[0].getValue(), 0);
  }

  @Test
  public void testInfo() {
    Assert.assertTrue(client.create("seriesInfo", 10/*retentionTime*/, null));   

    Info info = client.info("seriesInfo");
    Assert.assertEquals( (Long)10L, info.getProperty("retentionTime"));
    Assert.assertEquals( null, info.getLabel(""));
    Rule rule = info.getRule("");
    //    Assert.assertEquals( "", rule);
    //    Assert.assertEquals( "", rule.getTarget());
    //    Assert.assertEquals( "", rule.getValue());
    //    Assert.assertEquals( Aggregation.AVG, rule.getAggregation());


    try {
      client.info("seriesInfo1");
      Assert.fail();
    } catch(JedisDataException e) {
      // Error on creating same rule twice
    }
  }
}
