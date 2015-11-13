package de.caluga.morphium.driver.bulk;/**
 * Created by stephan on 13.11.15.
 */

import java.util.Map;

/**
 * TODO: Add Documentation here
 **/
public abstract class BulkRequestContext {
    private boolean odererd = false;

    public boolean isOdererd() {
        return odererd;
    }

    public void setOdererd(boolean odererd) {
        this.odererd = odererd;
    }

    public abstract void addRequest(BulkRequest br);

    public abstract Map<String, Object> execute();

}