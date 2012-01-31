package com.mapr.storm;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This Storm bolt counts things that it receives.  As items are received, they are also logged into
 * a recovery log.
 * <p/>
 * At fixed intervals, all counts are emitted and reset back to zero.  This also causes the snapshot
 * to be set to the current position in the log.  The snapshot contains nothing more than a
 * reference to the log.
 * <p/>
 * On startup, if we see one or more recovery logs and a snapshot, we look at the snapshot and read
 * items from the log starting where the snapshot indicates before accepting new items. If we see
 * logs but no snapshot, we don't need to read any logs before starting.
 * <p/>
 * All log files are named in a manner that allows them to be read in order.  Snapshots contain a
 * reference to a file and an offset so that we know where to start reading the log files.  A
 * snapshot may refer to a log that is not the latest.  If so, we need to read all logs up to the
 * latest in addition to the log specified in the snapshot.
 */
public class SnappedCounter implements IRichBolt {
  private static final transient Logger logger = Logger.getLogger(SnappedCounter.class);
  
  private AtomicInteger count = new AtomicInteger();


  // we flush and acknowledge pending tuples when we have either seen maxBufferedTuples tuples
  // when logFlushInterval ms have passed.
  private final long reportingInterval;
  private int maxBufferedTuples;

  // all pending tuples are kept with an atomic reference so we can atomically switch to a
  // clean table
  private AtomicReference<Set<Tuple>> ackables = new AtomicReference<Set<Tuple>>(new HashSet<Tuple>());

  private OutputCollector outputCollector;

  // when did we last record output?
  private long lastRecordOutput = 0;

  public SnappedCounter() throws FileNotFoundException {
    this(10 * 1000, 100000);
  }

  public SnappedCounter(long reportingInterval, int maxBufferedTuples) throws FileNotFoundException {
    this.reportingInterval = reportingInterval;
    this.maxBufferedTuples = maxBufferedTuples;
  }

  @Override
  public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
    this.outputCollector = outputCollector;
  }

  /**
   * The input tuple consists of a key and a value.  The key selects which counter table we need to
   * increment and the value is the value to be counted.
   *
   * @param tuple The (key,value) data to count.
   */
  @Override
  public void execute(Tuple tuple) {
    ackables.get().add(tuple);
    recordCounts(false);
  }

  /**
   * Records and then clears all pending counts if we have crossed a window boundary
   * or have a bunch of data accumulated.
   * @param force  If true, then windows and such are ignored and the data is pushed out regardless
   */
  private void recordCounts(boolean force) {
    long currentRecordWindowStart = (System.currentTimeMillis() / reportingInterval) * reportingInterval;
    if (lastRecordOutput == 0) {
      lastRecordOutput = currentRecordWindowStart;
    }

    final int bufferedTuples = ackables.get().size();
    if (force || currentRecordWindowStart - lastRecordOutput > reportingInterval || bufferedTuples > maxBufferedTuples) {
      if (force) {
        logger.info("Forced recording");
      } else if (bufferedTuples > maxBufferedTuples) {
        logger.info("Recording due to max tuples");
      } else {
        logger.info("Recording due to time");
      }

      // atomic get and set avoids the need to locks and still avoids races
      Set<Tuple> oldAckables = ackables.getAndSet(new HashSet<Tuple>());

      Multiset<String> counts = HashMultiset.create();
      for (Tuple tuple : oldAckables) {
        counts.add(tuple.getString(0) + "\t" + tuple.getString(1));
      }

      // record all keys
      for (String keyValue : counts.elementSet()) {
        final int n = counts.count(keyValue);
        outputCollector.emit(oldAckables, new Values(keyValue, n));
        count.addAndGet(n);
      }

      for (Tuple tuple : oldAckables) {
        outputCollector.ack(tuple);
      }
      lastRecordOutput = currentRecordWindowStart;
    }
  }

  @Override
  public void cleanup() {
    recordCounts(true);
    logger.warn(String.format("Total = %d\n", count.get()));
  }

  @Override
  public void declareOutputFields(OutputFieldsDeclarer declarer) {
    declarer.declare(new Fields("keyValue", "count"));
  }
}
