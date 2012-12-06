package de.caluga.test.mongo.suite;

import de.caluga.morphium.MorphiumSingleton;
import de.caluga.morphium.annotations.*;
import de.caluga.morphium.annotations.caching.NoCache;
import de.caluga.morphium.query.Query;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Stephan Bösebeck
 * Date: 22.11.12
 * Time: 08:30
 * <p/>
 */
public class GeoSearchTests extends MongoTest {

    @Test
    public void nearTest() throws Exception {
        MorphiumSingleton.get().dropCollection(Place.class);
        ArrayList<Place> toStore = new ArrayList<Place>();
//        MorphiumSingleton.get().ensureIndicesFor(Place.class);
        for (int i = 0; i < 1000; i++) {
            Place p = new Place();
            List<Double> pos = new ArrayList<Double>();
            pos.add((Math.random() * 180) - 90);
            pos.add((Math.random() * 180) - 90);
            p.setName("P" + i);
            p.setPosition(pos);
            toStore.add(p);
        }
        MorphiumSingleton.get().storeList(toStore);

        Query<Place> q = MorphiumSingleton.get().createQueryFor(Place.class).f("position").near(0, 0, 10);
        long cnt = q.countAll();
        log.info("Found " + cnt + " places around 0,0 (10)");
        List<Place> lst = q.asList();
        for (Place p : lst) {
            log.info("Position: " + p.getPosition().get(0) + " / " + p.getPosition().get(1));
        }
    }

    @Index("position:2d")
    @NoCache
    @WriteSafety(level = SafetyLevel.MAJORITY)
    @Entity
    public static class Place {
        @Id
        private ObjectId id;

        public List<Double> position;
        public String name;

        public ObjectId getId() {
            return id;
        }

        public void setId(ObjectId id) {
            this.id = id;
        }

        public List<Double> getPosition() {
            return position;
        }

        public void setPosition(List<Double> position) {
            this.position = position;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}