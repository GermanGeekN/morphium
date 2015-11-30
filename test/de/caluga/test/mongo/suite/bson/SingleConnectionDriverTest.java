package de.caluga.test.mongo.suite.bson;/**
 * Created by stephan on 30.11.15.
 */

import de.caluga.morphium.ObjectMapperImpl;
import de.caluga.morphium.driver.singleconnect.SingleConnection;
import de.caluga.test.mongo.suite.data.UncachedObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO: Add Documentation here
 **/
public class SingleConnectionDriverTest {

    @Test
    public void crudTest() throws Exception {
        SingleConnection drv = new SingleConnection();
        drv.setHostSeed("localhost:27017");
        drv.connect();
        List<Map<String, Object>> lst = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            UncachedObject uc = new UncachedObject();
            uc.setCounter(i);
            uc.setValue("V:" + i);
            lst.add(new ObjectMapperImpl().marshall(uc));
        }
        drv.insert("morphium_test", "tst", lst, null);

        lst = drv.find("morphium_test", "tst", new HashMap<String, Object>(), null, null, 0, 0, 1000, null, null);
        System.out.println("List: " + lst.size());
    }
}
