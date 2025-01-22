package org.apache.hadoop.hbase.regionserver;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.coprocessor.CoprocessorHost;
import org.apache.hadoop.hbase.coprocessor.BaseRegionObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.util.JVMClusterUtil.RegionServerThread;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.FSUtils;
import org.apache.hadoop.hbase.util.FSVisitor;
import org.apache.hadoop.hbase.util.TestTableName;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(LargeTests.class)
public class TestLargeRowOOM {
    private static final Log LOG = LogFactory.getLog(TestScannerRetriableFailure.class);

    private static final HBaseTestingUtility UTIL = new HBaseTestingUtility();

    private static final String FAMILY_NAME_STR = "f";
    private static final byte[] FAMILY_NAME = Bytes.toBytes(FAMILY_NAME_STR);

    @Rule public TestTableName TEST_TABLE = new TestTableName();

    private static int ROW_SIZE=1024*1024;


    private static void setupConf(Configuration conf) {
        conf.setLong("hbase.hstore.compaction.min", 20);
        conf.setLong("hbase.hstore.compaction.max", 39);
        conf.setLong("hbase.hstore.blockingStoreFiles", 40);
        conf.setLong("hbase.client.keyvalue.maxsize", 1024 * 1024 * 1024);
    }

    @BeforeClass
    public static void setup() throws Exception {
        setupConf(UTIL.getConfiguration());
        UTIL.startMiniCluster(1);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        try {
            UTIL.shutdownMiniCluster();
        } catch (Exception e) {
            LOG.warn("failure shutting down cluster", e);
        }
    }

    @Test(timeout=180000)
    public void testLargeRowOOM() throws Exception {
        TableName tableName = TEST_TABLE.getTableName();
        Table table = UTIL.createTable(tableName, FAMILY_NAME);
        try {
            final int NUM_ROWS = 10000;
            loadTable(table);
            checkTableRow(table);
        } finally {
            table.close();
        }
    }

    // ==========================================================================
    //  Helpers
    // ==========================================================================
    private FileSystem getFileSystem() {
        return UTIL.getHBaseCluster().getMaster().getMasterFileSystem().getFileSystem();
    }

    private Path getRootDir() {
        return UTIL.getHBaseCluster().getMaster().getMasterFileSystem().getRootDir();
    }

    public void loadTable(final Table table) throws IOException {
        byte[] value = new byte[1024 * 1024];
        new Random().nextBytes(value);

        Put put = new Put(Bytes.toBytes("wide-row"));
        for (int i = 0; i < 1; i++) {
            put.addColumn(FAMILY_NAME, Bytes.toBytes("col" + i), value);
        }
        table.put(put);
        System.out.println("Inserted data for the wide row");
    }

    private void checkTableRow(final Table table) throws Exception {
        Scan scan = new Scan();
        scan.setRowPrefixFilter(Bytes.toBytes("wide-row"));
        scan.setCaching(1);
        scan.setCacheBlocks(false);
        ResultScanner scanner = table.getScanner(scan);
        try {
            Result result = scanner.next();
            assertTrue(result != null);
            int count = result.size();
            result = scanner.next();
        } finally {
            scanner.close();
        }
    }
}

