package de.caluga.morphium.driver.singleconnect;

import de.caluga.morphium.Logger;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.driver.MorphiumDriverException;
import de.caluga.morphium.driver.WriteConcern;
import de.caluga.morphium.driver.bulk.*;
import de.caluga.morphium.driver.mongodb.DriverHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Stephan Bösebeck
 * Date: 06.12.15
 * Time: 23:14
 * <p>
 * TODO: Add documentation here
 */
public class BulkContext extends BulkRequestContext {
    private Logger log = new Logger(BulkContext.class);
    private DriverBase driver;
    private boolean ordered;
    private String db;
    private String collection;
    private WriteConcern wc;

    private List<BulkRequest> requests;

    public BulkContext(Morphium m, String db, String collection, DriverBase driver, boolean ordered, int batchSize, WriteConcern wc) {
        super(m);
        this.driver = driver;
        this.ordered = ordered;
        this.db = db;
        this.collection = collection;
        this.wc = wc;
        setBatchSize(batchSize);

        requests = new ArrayList<>();
    }

    public void addRequest(BulkRequest br) {
        requests.add(br);
    }

    @Override
    public UpdateBulkRequest addUpdateBulkRequest() {
        UpdateBulkRequest up = new UpdateBulkRequest();
        addRequest(up);
        return up;
    }

    @Override
    public InsertBulkRequest addInsertBulkReqpest(List<Map<String, Object>> toInsert) {
        InsertBulkRequest in = new InsertBulkRequest(toInsert);
        addRequest(in);
        return in;
    }

    @Override
    public StoreBulkRequest addStoreBulkRequest(List<Map<String, Object>> toStore) {
        StoreBulkRequest store = new StoreBulkRequest(toStore);
        addRequest(store);
        return store;
    }

    @Override
    public DeleteBulkRequest addDeleteBulkRequest() {
        DeleteBulkRequest del = new DeleteBulkRequest();
        addRequest(del);
        return del;
    }


    @Override
    public Map<String, Object> execute() throws MorphiumDriverException {


//        List<WriteModel<? extends Document>> lst = new ArrayList<>();
        int count = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        DriverHelper helper = new DriverHelper();
        List<Map<String, Object>> inserts = new ArrayList<>();
        List<Map<String, Object>> stores = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();

        //TODO - add result data
        for (BulkRequest br : requests) {
            if (br instanceof StoreBulkRequest) {
                stores.addAll(((StoreBulkRequest) br).getToInsert());
                if (stores.size() >= driver.getMaxWriteBatchSize()) {
                    driver.store(db, collection, stores, wc);
                    stores.clear();
                }
            } else if (br instanceof InsertBulkRequest) {
//                //Insert...
                InsertBulkRequest ib = (InsertBulkRequest) br;
                inserts.addAll(((InsertBulkRequest) br).getToInsert());
                if (inserts.size() >= driver.getMaxWriteBatchSize()) {
                    driver.insert(db, collection, inserts, wc);
                    inserts.clear();
                }
            } else if (br instanceof DeleteBulkRequest) {
                //no real bulk operation here
                driver.delete(db, collection, ((DeleteBulkRequest) br).getQuery(), ((DeleteBulkRequest) br).isMultiple(), wc);
            } else {
//                //update
                UpdateBulkRequest up = (UpdateBulkRequest) br;
                Map<String, Object> cmd = new HashMap<>();
                cmd.put("q", up.getQuery());
                cmd.put("u", up.getCmd());
                cmd.put("upsert", up.isUpsert());
                cmd.put("multi", up.isMultiple());
                updates.add(cmd);
                if (updates.size() >= driver.getMaxWriteBatchSize()) {
                    Map<String, Object> result = null;
                    result = driver.update(db, collection, updates, ordered, wc);
                    updates.clear();
                }
            }
            count++;
        }
        if (inserts.size() != 0) {
            driver.insert(db, collection, inserts, wc);
        }

        if (stores.size() != 0) {
            driver.store(db, collection, stores, wc);
        }

        if (updates.size() != 0) {
            Map<String, Object> result = null;
            result = driver.update(db, collection, updates, ordered, wc);
        }

//
//
        Map<String, Object> res = new HashMap<>();
//
        int delCount = 0;
        int matchedCount = 0;
        int insertCount = 0;
        int modifiedCount = 0;
        int upsertCount = 0;
        for (Map<String, Object> r : results) {
            //TODO - get metadata
//            delCount += r.getDeletedCount();
//            matchedCount += r.getMatchedCount();
//            insertCount += r.getInsertedCount();
//            modifiedCount += r.getModifiedCount();
//            upsertCount += r.getUpserts().size();
        }
//
//        res.put("num_del", delCount);
//        res.put("num_matched", matchedCount);
//        res.put("num_insert", insertCount);
//        res.put("num_modified", modifiedCount);
//        res.put("num_upserts", upsertCount);
//        return res;
        return null;
    }

}
