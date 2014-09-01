package de.caluga.morphium.messaging;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumSingleton;
import de.caluga.morphium.async.AsyncOperationCallback;
import de.caluga.morphium.async.AsyncOperationType;
import de.caluga.morphium.query.MorphiumIterator;
import de.caluga.morphium.query.Query;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * User: Stephan Bösebeck
 * Date: 26.05.12
 * Time: 15:48
 * <p/>
 * Messaging implements a simple, threadsafe and messaging api. Used for cache synchronization.
 */
@SuppressWarnings({"ConstantConditions", "unchecked", "UnusedDeclaration"})
public class Messaging extends Thread {
    private static Logger log = Logger.getLogger(Messaging.class);

    private Morphium morphium;
    private boolean running;
    private int pause = 5000;
    private String id;
    private boolean autoAnswer = false;
    private String hostname;
    private boolean processMultiple = false;

    private List<MessageListener> listeners;

    private Map<String, List<MessageListener>> listenerByName;
    private String queueName;

    private ThreadPoolExecutor threadPool;

    private boolean multithreadded = false;
    private int prefetchdWindows = 1;
    private int windowSize = 1000;


    /**
     * attaches to the default queue named "msg"
     *
     * @param m               - morphium
     * @param pause           - pause between checks
     * @param processMultiple - process multiple messages at once, if false, only ony by one
     */
    public Messaging(Morphium m, int pause, boolean processMultiple) {
        this(m, null, pause, processMultiple);
    }

    public Messaging(Morphium m, int pause, boolean processMultiple, boolean multithreadded, int windowSize, int prefetchdWindows) {
        this(m, null, pause, processMultiple, multithreadded, windowSize, prefetchdWindows);
    }

    public long getMessageCount() {
        return morphium.createQueryFor(Msg.class).countAll();
    }

    public Messaging(Morphium m, String queueName, int pause, boolean processMultiple) {
        this(m, queueName, pause, processMultiple, false, 1000, 1);
    }

    public Messaging(Morphium m, String queueName, int pause, boolean processMultiple, boolean multithreadded, int windowSize, int prefetchdWindows) {
        this.multithreadded = multithreadded;
        this.windowSize = windowSize;
        this.prefetchdWindows = prefetchdWindows;


        threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100));

        this.queueName = queueName;
        morphium = m;
        running = true;
        this.pause = pause;
        this.processMultiple = processMultiple;
        id = UUID.randomUUID().toString();
        hostname = System.getenv("HOSTNAME");
        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
            }
        }
        if (hostname == null) {
            hostname = "unknown host";
        }

        m.ensureIndicesFor(Msg.class, queueName);
//        try {
//            m.ensureIndex(Msg.class, Msg.Fields.lockedBy, Msg.Fields.timestamp);
//            m.ensureIndex(Msg.class, Msg.Fields.lockedBy, Msg.Fields.processedBy);
//            m.ensureIndex(Msg.class, Msg.Fields.timestamp);
//        } catch (Exception e) {
//            log.error("Could not ensure indices", e);
//        }

        listeners = new Vector<MessageListener>();
        listenerByName = new Hashtable<String, List<MessageListener>>();
    }

    public void run() {
        if (log.isDebugEnabled()) {
            log.debug("Messaging " + id + " started");
        }
        Map<String, Object> values = new HashMap<String, Object>();
        while (running) {

            try {
                Query<Msg> q = morphium.createQueryFor(Msg.class);
                q.setCollectionName(getCollectionName());
//                //removing all outdated stuff
//                q = q.where("this.ttl<" + System.currentTimeMillis() + "-this.timestamp");
//                if (log.isDebugEnabled() && q.countAll() > 0) {
//                    log.debug("Deleting outdate messages: " + q.countAll());
//                }
//                morphium.remove(q);
//                q = q.q();
                //locking messages...
                q.or(q.q().f(Msg.Fields.sender).ne(id).f(Msg.Fields.lockedBy).eq(null).f(Msg.Fields.processedBy).ne(id).f(Msg.Fields.recipient).eq(null),
                        q.q().f(Msg.Fields.sender).ne(id).f(Msg.Fields.lockedBy).eq(null).f(Msg.Fields.processedBy).ne(id).f(Msg.Fields.recipient).eq(id));
                values.put("locked_by", id);
                values.put("locked", System.currentTimeMillis());
                morphium.set(q, values, false, processMultiple);
                q = q.q();
                q.or(q.q().f(Msg.Fields.lockedBy).eq(id),
                        q.q().f(Msg.Fields.lockedBy).eq("ALL").f(Msg.Fields.processedBy).ne(id).f(Msg.Fields.recipient).eq(id),
                        q.q().f(Msg.Fields.lockedBy).eq("ALL").f(Msg.Fields.processedBy).ne(id).f(Msg.Fields.recipient).eq(null));
                q.sort(Msg.Fields.timestamp);

//                List<Msg> messages = q.asList();
                MorphiumIterator<Msg> messages = q.asIterable(windowSize, prefetchdWindows);
                messages.setMultithreaddedAccess(multithreadded);
                final List<Msg> toStore = new Vector<Msg>();
                final List<Runnable> toExec = new Vector<Runnable>();

                for (final Msg m : messages) {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            final Msg msg = morphium.reread(m, getCollectionName()); //make sure it's current version in DB
                            if (msg == null) return; //was deleted
                            if (!msg.getLockedBy().equals(id) && !msg.getLockedBy().equals("ALL")) {
                                //over-locked by someone else
                                return;
                            }
                            if (msg.getTtl() < System.currentTimeMillis() - msg.getTimestamp()) {
                                //Delete outdated msg!
                                log.info("Found outdated message - deleting it!");
                                morphium.delete(msg, getCollectionName());
                                return;
                            }
                            try {
                                for (MessageListener l : listeners) {
                                    Msg answer = l.onMessage(Messaging.this, msg);
                                    if (autoAnswer && answer == null) {
                                        answer = new Msg(msg.getName(), "received", "");
                                    }
                                    if (answer != null) {
                                        msg.sendAnswer(Messaging.this, answer);
                                    }
                                }

                                if (listenerByName.get(msg.getName()) != null) {
                                    for (MessageListener l : listenerByName.get(msg.getName())) {
                                        Msg answer = l.onMessage(Messaging.this, msg);
                                        if (autoAnswer && answer == null) {
                                            answer = new Msg(msg.getName(), "received", "");
                                        }
                                        if (answer != null) {
                                            msg.setDeleteAt(new Date(System.currentTimeMillis() + msg.getTtl()));
                                            msg.sendAnswer(Messaging.this, answer);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
//                        msg.addAdditional("Processing of message failed by "+getSenderId()+": "+t.getMessage());
                                log.error("Processing failed", t);
                            }

//                            if (msg.getType().equals(MsgType.SINGLE)) {
//                                //removing it
//                                morphium.delete(msg, getCollectionName());
//                            }
                            //updating it to be processed by others...
                            if (msg.getLockedBy().equals("ALL")) {
                                toExec.add(new Runnable() {
                                    @Override
                                    public void run() {
                                        Query<Msg> idq = MorphiumSingleton.get().createQueryFor(Msg.class);
                                        idq.setCollectionName(getCollectionName());
                                        idq.f(Msg.Fields.msgId).eq(msg.getMsgId());

                                        MorphiumSingleton.get().push(idq, Msg.Fields.processedBy, id);
                                    }
                                });

                            } else {
                                //Exclusive message
                                msg.addProcessedId(id);
                                msg.setLockedBy(null);
                                msg.setLocked(0);
                                toStore.add(msg);
                            }
                        }
                    };

                    if (multithreadded) {
                        boolean queued = false;
                        while (!queued) {
                            try {
                                threadPool.execute(r);
                            } catch (Throwable t) {
                            }
                        }
                    } else {
                        r.run();
                    }
                }
                //wait for all threads to finish
                while (threadPool.getActiveCount() > 0) {
                    Thread.sleep(100);
                }
                morphium.storeList(toStore, getCollectionName());
                for (Runnable r : toExec) {
                    if (multithreadded) {
                        boolean queued = false;
                        while (!queued) {
                            try {
                                threadPool.execute(r);
                            } catch (Throwable t) {
                            }
                        }
                    } else {
                        r.run();
                    }
                }
                while (morphium.getWriteBufferCount() > 0) {
                    Thread.sleep(100);
                }
            } catch (Throwable e) {
                log.error("Unhandled exception " + e.getMessage(), e);
            } finally {
                try {
                    sleep(pause);
                } catch (InterruptedException ignored) {
                }
            }


        }
        if (log.isDebugEnabled()) {
            log.debug("Messaging " + id + " stopped!");
        }
        if (!running) {
            listeners.clear();
            listenerByName.clear();
        }
    }

    public String getCollectionName() {
        if (queueName == null || queueName.isEmpty()) {
            return "msg";
        }
        return "mmsg_" + queueName;

    }

    public void addListenerForMessageNamed(String n, MessageListener l) {
        if (listenerByName.get(n) == null) {
            listenerByName.put(n, new ArrayList<MessageListener>());
        }
        listenerByName.get(n).add(l);
    }

    public void removeListenerForMessageNamed(String n, MessageListener l) {
//        l.setMessaging(null);
        if (listenerByName.get(n) == null) {
            return;
        }
        listenerByName.get(n).remove(l);

    }

    public String getSenderId() {
        return id;
    }

    public void setSenderId(String id) {
        this.id = id;
    }

    public int getPause() {
        return pause;
    }

    public void setPause(int pause) {
        this.pause = pause;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;

    }

    public void addMessageListener(MessageListener l) {
        listeners.add(l);
    }

    public void removeMessageListener(MessageListener l) {
        listeners.remove(l);
    }

    public void queueMessage(final Msg m) {
        storeMsg(m, true);
    }

    public void storeMessage(Msg m) {
        storeMsg(m, false);
    }

    private void storeMsg(Msg m, boolean async) {
        AsyncOperationCallback cb = null;
        if (async) {
            cb = new AsyncOperationCallback() {
                @Override
                public void onOperationSucceeded(AsyncOperationType type, Query q, long duration, List result, Object entity, Object... param) {
                }

                @Override
                public void onOperationError(AsyncOperationType type, Query q, long duration, String error, Throwable t, Object entity, Object... param) {
                }
            };
        }
        m.setDeleteAt(new Date(System.currentTimeMillis() + m.getTtl()));
        m.setSender(id);
        m.addProcessedId(id);
        m.setLockedBy(null);
        m.setLocked(0);
        m.setSenderHost(hostname);
        if (m.getTo() != null && m.getTo().size() > 0) {
            for (String recipient : m.getTo()) {
                Msg msg = m.getCopy();
                msg.setRecipient(recipient);
                morphium.storeNoCache(msg, getCollectionName(), cb);
            }
        } else {
            morphium.storeNoCache(m, getCollectionName(), cb);
        }
    }

    public boolean isAutoAnswer() {
        return autoAnswer;
    }

    public void setAutoAnswer(boolean autoAnswer) {
        this.autoAnswer = autoAnswer;
    }
}
