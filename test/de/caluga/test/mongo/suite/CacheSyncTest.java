package de.caluga.test.mongo.suite;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.StatisticKeys;
import de.caluga.morphium.annotations.SafetyLevel;
import de.caluga.morphium.annotations.WriteSafety;
import de.caluga.morphium.annotations.caching.Cache;
import de.caluga.morphium.annotations.caching.WriteBuffer;
import de.caluga.morphium.cache.CacheSyncVetoException;
import de.caluga.morphium.cache.MessagingCacheSyncListener;
import de.caluga.morphium.cache.MessagingCacheSynchronizer;
import de.caluga.morphium.cache.WatchingCacheSynchronizer;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.morphium.query.Query;
import de.caluga.test.mongo.suite.data.CachedObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * User: Stephan Bösebeck
 * Date: 12.06.12
 * Time: 16:40
 * <p/>
 */
@SuppressWarnings("AssertWithSideEffects")
public class CacheSyncTest extends MongoTest {
    private boolean preSendClear = false;
    private boolean postSendClear = false;
    private boolean preClear = false;
    private boolean postclear = false;

    @Test
    public void sendClearMsgTest() throws Exception {
        morphium.dropCollection(Msg.class);
        Messaging msg = new Messaging(morphium, 100, true);
        msg.start();
        MessagingCacheSynchronizer cs = new MessagingCacheSynchronizer(msg, morphium);

        Query<Msg> q = morphium.createQueryFor(Msg.class);
        long cnt = q.countAll();
        assert (cnt == 0) : "Already a message?!?! " + cnt;

        cs.sendClearMessage(CachedObject.class, "test");
        Thread.sleep(2000);
        waitForWrites();
        cnt = q.countAll();
        assert (cnt == 1) : "there should be one msg, there are " + cnt;
        msg.terminate();
        cs.detach();
    }

    @Test
    public void removeFromCacheTest() throws Exception {

        for (int i = 0; i < 100; i++) {
            CachedObject o = new CachedObject();
            o.setCounter(i);
            o.setValue("a value");
            morphium.store(o);
        }
        waitForWrites();
        for (int i = 0; i < 100; i++) {
            Query<CachedObject> c = morphium.createQueryFor(CachedObject.class);
            c = c.f("counter").eq(i);
            c.asList();
        }
        assert (morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name()) != null) : "Cache entries not set?";
        assert (morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name()) > 0) : "Cache entries not set? " + morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name());
        Thread.sleep(2500);
        Query<CachedObject> c = morphium.createQueryFor(CachedObject.class);
        c = c.f("counter").eq(10);
        MorphiumId id = c.get().getId();
        Double cnt = morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name());
        morphium.getCache().removeEntryFromCache(CachedObject.class, id);
        Double cnt2 = morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name());
        assert (morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name()) <= cnt - 1) : "Cache entries not set?";
        log.info("Count 1: " + cnt + " ---> " + cnt2);
    }

    @Test
    public void clearCacheTest() throws Exception {

        Messaging msg1 = new Messaging(morphium, 100, true);
        msg1.start();
        Messaging msg2 = new Messaging(morphium, 100, true);
        msg2.start();
        MessagingCacheSynchronizer cs1 = new MessagingCacheSynchronizer(msg1, morphium);
        MessagingCacheSynchronizer cs2 = new MessagingCacheSynchronizer(msg2, morphium);


        for (int i = 0; i < 100; i++) {
            CachedObject o = new CachedObject();
            o.setCounter(i);
            o.setValue("a value");
            morphium.store(o);
        }
        waitForWrites();
        for (int i = 0; i < 100; i++) {
            Query<CachedObject> c = morphium.createQueryFor(CachedObject.class);
            c = c.f("counter").eq(i);
            c.asList();
        }
        System.out.println("Stats " + morphium.getStatistics().toString());
        assert (morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name()) != null) : "Cache entries not set?";
        cs1.sendClearAllMessage("test");
        Thread.sleep(5500);
        if ((morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name()) != 0)) {
            throw new AssertionError("Cache entries set? Entries: " + morphium.getStatistics().get(StatisticKeys.CACHE_ENTRIES.name()));
        }
        msg1.terminate();
        msg2.terminate();
        cs1.detach();
        cs2.detach();
    }

    @Test
    public void idCacheTest() throws Exception {
        morphium.dropCollection(Msg.class);
        morphium.dropCollection(IdCachedObject.class);
        //Making sure, indices are only created once...
        IdCachedObject o = new IdCachedObject();
        o.setCounter(0);
        o.setValue("a value");
        morphium.store(o);
        waitForAsyncOperationToStart(1000000);
        waitForWrites();
        Thread.sleep(2000);
        long start = System.currentTimeMillis();
        for (int i = 1; i < 100; i++) {
            o = new IdCachedObject();
            o.setCounter(i);
            o.setValue("a value");
            morphium.store(o);
        }
        waitForWriteToStart(1000000);
        waitForWrites();
        long dur = System.currentTimeMillis() - start;
        log.info("Storing without synchronizer: " + dur + " ms");

        start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            Query<IdCachedObject> q = morphium.createQueryFor(IdCachedObject.class);
            IdCachedObject obj = q.f("counter").eq(i).get();
            obj.setCounter(i + 1000);
            morphium.store(obj);
        }
        waitForWriteToStart(1000000);
        waitForWrites();
        dur = System.currentTimeMillis() - start;
        log.info("Updating without synchronizer: " + dur + " ms");


        morphium.clearCollection(IdCachedObject.class);
        Messaging msg1 = new Messaging(morphium, 100, true);
        msg1.start();

        MessagingCacheSynchronizer cs1 = new MessagingCacheSynchronizer(msg1, morphium);
        start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            o = new IdCachedObject();
            o.setCounter(i);
            o.setValue("a value");
            morphium.store(o);
        }
        waitForWrites();
        dur = System.currentTimeMillis() - start;
        log.info("Storing with synchronizer: " + dur + " ms");

        Thread.sleep(15000);
        start = System.currentTimeMillis();
        int notFoundCounter = 0;
        for (int i = 0; i < 100; i++) {
            Query<IdCachedObject> q = morphium.createQueryFor(IdCachedObject.class);
            q = q.f("counter").eq(i);
            IdCachedObject obj = q.get();
            if (obj == null) {
                log.info("Object not found... waiting");
                Thread.sleep(1550); //wait a moment
                obj = q.get();
            }
            if (obj == null) {
                notFoundCounter++;
                continue;
            } else {
                obj.setCounter(i + 2000);
            }
            assert (notFoundCounter < 10) : "too many objects not found";
            morphium.store(obj);
        }
        dur = System.currentTimeMillis() - start;
        log.info("Updates queued... " + dur + "ms");
        waitForWrites();
        dur = System.currentTimeMillis() - start;
        log.info("Updating with synchronizer: " + dur + " ms");


        msg1.terminate();
        cs1.detach();

    }

    private void waitForWriteToStart(int max) {
        int cnt = 0;
        while (morphium.getWriteBufferCount() == 0) {
            //wait for things to get started...
            Thread.yield();
            cnt++;
            if (cnt > max) {
                return;
            }
        }
    }

    @Test
    public void testListeners() throws Exception {
        morphium.dropCollection(IdCachedObject.class);
        final Messaging msg1 = new Messaging(morphium, 100, true);
        msg1.start();
        final Messaging msg2 = new Messaging(morphium, 100, true);
        msg2.start();


        final MessagingCacheSynchronizer cs1 = new MessagingCacheSynchronizer(msg1, morphium);
        cs1.addSyncListener(new MessagingCacheSyncListener() {
            @Override
            public void preClear(Class cls) {
            }

            @Override
            public void postClear(Class cls) {
            }

            @Override
            public void preSendClearMsg(Class cls, Msg m) {
                log.info("in preSendClearMsg");
                preSendClear = true;
            }

            @Override
            public void postSendClearMsg(Class cls, Msg m) {
                log.info("in postSendClearMsg");
                postSendClear = true;
            }
        });

        final MessagingCacheSynchronizer cs2 = new MessagingCacheSynchronizer(msg2, morphium);
        cs2.addSyncListener(new MessagingCacheSyncListener() {
            @Override
            public void preClear(Class cls) {
                log.info("in preClear");
                preClear = true;
            }

            @Override
            public void postClear(Class cls) {
                log.info("In postClear");
                postclear = true;
            }

            @Override
            public void preSendClearMsg(Class cls, Msg m) {
            }

            @Override
            public void postSendClearMsg(Class cls, Msg m) {
            }
        });


        new Thread() {
            @Override
            public void run() {
                morphium.store(new CachedObject());
                waitForWrites();
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                cs1.detach();
                cs2.detach();
                msg1.terminate();
                msg2.terminate();

            }
        }.start();

        while (cs1.isAttached()) {
            log.info("still attached - waiting");
            Thread.sleep(500);
        }
        assert (preClear);
        assert (postclear);
        assert (preSendClear);
        assert (postSendClear);

    }

    @Test
    public void cacheSyncVetoTestMessaging() throws Exception {
        morphium.dropCollection(Msg.class);
        createCachedObjects(1000);

        Morphium m1 = morphium;
        MorphiumConfig cfg2 = new MorphiumConfig();
        cfg2.setHostSeed(m1.getConfig().getHostSeed());
        cfg2.setDatabase(m1.getConfig().getDatabase());

        Morphium m2 = new Morphium(cfg2);
        Messaging msg1 = new Messaging(m1, 200, true);
        Messaging msg2 = new Messaging(m2, 200, true);

        msg1.start();
        msg2.start();

        MessagingCacheSynchronizer cs1 = new MessagingCacheSynchronizer(msg1, m1);
        MessagingCacheSynchronizer cs2 = new MessagingCacheSynchronizer(msg2, m2);
        waitForWrites();
        cs1.addSyncListener(new MessagingCacheSyncListener() {
            @Override
            public void preSendClearMsg(Class cls, Msg m) throws CacheSyncVetoException {
                throw new CacheSyncVetoException("not sending");
            }

            @Override
            public void postSendClearMsg(Class cls, Msg m) {

            }

            @Override
            public void preClear(Class cls) throws CacheSyncVetoException {
                throw new CacheSyncVetoException("not clearing");
            }

            @Override
            public void postClear(Class cls) {

            }
        });

        //fill caches
        log.info("Filling caches...");
        fillCache(m1, m2);

        for (Morphium m : new Morphium[]{m1, m2}) {
            printstats(m);
        }
        assert (m1.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") > 90);
        assert (m2.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") > 90);

        log.info("Storing to m1 - should trigger veto, no clear on m2");
        m1.store(new CachedObject("value", 100000));
        waitForWrites();
        assert (m2.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") != 0);
        assert (m1.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") == 0);


        fillCache(m1, m2);
        log.info("Storing to m2 - should trigger veto, no clear on m1");
        m2.store(new CachedObject("value2", 102828));
        waitForWrites();
        assert (m2.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") == 0);
        assert (m1.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") != 0);

        cs1.detach();
        cs2.detach();
        msg1.terminate();
        msg2.terminate();
        m2.close();
    }

    @Test
    public void simpleSyncTest() throws Exception {
        morphium.dropCollection(Msg.class);
        createCachedObjects(1000);

        Morphium m1 = morphium;
        MorphiumConfig cfg2 = new MorphiumConfig();
        cfg2.setHostSeed(m1.getConfig().getHostSeed());
        cfg2.setDatabase(m1.getConfig().getDatabase());

        Morphium m2 = new Morphium(cfg2);
        Messaging msg1 = new Messaging(m1, 200, true);
        Messaging msg2 = new Messaging(m2, 200, true);

        msg1.start();
        msg2.start();

        MessagingCacheSynchronizer cs1 = new MessagingCacheSynchronizer(msg1, m1);
        MessagingCacheSynchronizer cs2 = new MessagingCacheSynchronizer(msg2, m2);
        waitForWrites();

        //fill caches
        log.info("Filling caches...");
        fillCache(m1, m2);

        for (Morphium m : new Morphium[]{m1, m2}) {
            printstats(m);
        }
        assert (m1.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") > 90);
        assert (m2.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") > 90);

        log.info("Storing to m1 - waiting for m2's cache to be cleared...");
        m1.store(new CachedObject("value", 100000));
        waitForWrites();
        checkForClearedCache(m1, m2);

        log.info("Filling cache again...");
        fillCache(m1, m2);
        log.info("other way round");
        m2.store(new CachedObject("value", 100200));
        waitForWrites();
        checkForClearedCache(m1, m2);


        log.info("Filling cache again...");
        fillCache(m1, m2);
        log.info("doing a delete");
        Query<CachedObject> q = m1.createQueryFor(CachedObject.class).f(CachedObject.Fields.counter).lt(10);
        m1.delete(q);
        waitForWrites();
        checkForClearedCache(m1, m2);

        log.info("Filling cache again...");
        fillCache(m1, m2);
        log.info("doing an update");
        q = m1.createQueryFor(CachedObject.class).f(CachedObject.Fields.counter).lt(15);
        m1.set(q, CachedObject.Fields.value, 9999);
        waitForWrites();
        checkForClearedCache(m1, m2);


        cs1.detach();
        cs2.detach();
        msg1.terminate();
        msg2.terminate();
        m2.close();
    }

    private void checkForClearedCache(Morphium m1, Morphium m2) throws InterruptedException {
        printstats(m1, "X-Entries for:.*");
        assert (m1.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") == 0);
        Thread.sleep(1000);
        printstats(m1, "X-Entries for:.*");
        assert (m2.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") == 0);
    }

    private void fillCache(Morphium m1, Morphium m2) {
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 100; i++) {
                m1.createQueryFor(CachedObject.class).f("counter").lte(i + 10).asList(); //fill cache
                m2.createQueryFor(CachedObject.class).f("counter").lte(i + 10).asList(); //fill cache
            }
        }
    }

    private void printstats(Morphium m) {
        printstats(m, null);
    }

    private void printstats(Morphium m, String pattern) {

        Pattern p = null;

        if (pattern != null) {
            p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        }
        log.info("Starting output....");
        for (String k : m.getStatistics().keySet()) {
            if (p == null || p.matcher(k).matches()) {
                log.info(" - stats: " + k + ":" + m.getStatistics().get(k));
            }
        }
        log.info("Finished...");
    }

    @Test
    public void cacheSyncTest() throws Exception {
        morphium.dropCollection(Msg.class);
        createCachedObjects(1000);

        Morphium m1 = morphium;
        MorphiumConfig cfg2 = new MorphiumConfig();
        cfg2.setHostSeed(m1.getConfig().getHostSeed());
        cfg2.setDatabase(m1.getConfig().getDatabase());

        Morphium m2 = new Morphium(cfg2);
        Messaging msg1 = new Messaging(m1, 200, true);
        Messaging msg2 = new Messaging(m2, 200, true);

        msg1.start();
        msg2.start();

        MessagingCacheSynchronizer cs1 = new MessagingCacheSynchronizer(msg1, m1);
        MessagingCacheSynchronizer cs2 = new MessagingCacheSynchronizer(msg2, m2);
        waitForWrites();

        //fill caches
        log.info("Filling caches...");
        for (int i = 0; i < 100; i++) {
            m1.createQueryFor(CachedObject.class).f("counter").lte(i + 10).asList(); //fill cache
            m2.createQueryFor(CachedObject.class).f("counter").lte(i + 10).asList(); //fill cache
        }
        //1 always sends to 2....
        log.info("done.");


        CachedObject o = m1.createQueryFor(CachedObject.class).f("counter").eq(155).get();
        cs2.addSyncListener(CachedObject.class, new MessagingCacheSyncListener() {
            @Override
            public void preClear(Class cls) {
                log.info("Should clear cache");
                preClear = true;
            }

            @Override
            public void postClear(Class cls) {
                log.info("did clear cache");
                postclear = true;
            }

            @Override
            public void preSendClearMsg(Class cls, Msg m) {
                log.info("will send clear message");
                preSendClear = true;
            }

            @Override
            public void postSendClearMsg(Class cls, Msg m) {
                log.info("just sent clear message");
                postSendClear = true;
            }
        });
        msg2.addMessageListener((msg, m) -> {
            log.info("Got message " + m.getName());
            return null;
        });
        log.info("resetting...");
        preSendClear = false;
        preClear = false;
        postclear = false;
        postSendClear = false;
        o.setValue("changed it");
        log.info("Storing..");
        m1.store(o);
        log.info("done.");

        Thread.sleep(3000);
        log.info("sleep finished " + postclear);
        assert (!preSendClear);
        assert (!postSendClear);
        assert (postclear);
        assert (preClear);
        log.info("Waiting a minute for msg to be cleared... ");
        Thread.sleep(62000);

        long l = m1.createQueryFor(Msg.class).countAll();
        assert (l <= 1) : "too many messages? " + l;

        //        createCachedObjects(50);


        //        Thread.sleep(90000); //wait for messages to be cleared
        //        assert(m1.createQueryFor(Msg.class).countAll()==0);
        cs1.detach();
        cs2.detach();
        msg1.terminate();
        msg2.terminate();
        m2.close();

    }

    @Test
    public void testWatchingCacheSynchronizer() throws Exception {
        morphium.dropCollection(CachedObject.class);

        WatchingCacheSynchronizer sync = new WatchingCacheSynchronizer(morphium);
        sync.start();

        createCachedObjects(100);
        waitForWriteToStart(1000);
        waitForWrites();

        //filling cache
        for (int i = 0; i < 10; i++) {
            morphium.createQueryFor(CachedObject.class).f("counter").lte(i * 10).asList();
        }

        assert (morphium.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") >= 10);
        List<Map<String, Object>> writings = new ArrayList<>();
        Map<String, Object> obj = new HashMap<>();
        obj.put("counter", 123);
        obj.put(CachedObject.Fields.value.name(), "test");
        writings.add(obj);
        morphium.getDriver().store(morphium.getConfig().getDatabase(), morphium.getMapper().getCollectionName(CachedObject.class), writings, morphium.getWriteConcernForClass(CachedObject.class));
        //stored some object avoiding cache handling in morphium
        //now cache should be empty
        waitForWriteToStart(1000);
        waitForWrites();
        assert (morphium.getStatistics().get("X-Entries for: resultCache|de.caluga.test.mongo.suite.data.CachedObject") <= 1);
        sync.terminate();
        Thread.sleep(1000);
    }




    @Cache(syncCache = Cache.SyncCacheStrategy.UPDATE_ENTRY)
    @WriteBuffer(timeout = 1000)
    @WriteSafety(waitForJournalCommit = true, level = SafetyLevel.WAIT_FOR_ALL_SLAVES)
    public static class IdCachedObject extends CachedObject {

    }

}
