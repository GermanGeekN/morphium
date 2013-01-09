package de.caluga.morphium.query;

import com.mongodb.BasicDBList;
import com.mongodb.DBRef;
import de.caluga.morphium.FilterExpression;
import de.caluga.morphium.MongoType;
import de.caluga.morphium.ObjectMapper;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Reference;
import org.bson.types.ObjectId;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User: Stpehan Bösebeck
 * Date: 26.03.12
 * Time: 15:18
 * <p/>
 * default implementation of a MongoField
 *
 * @see MongoField
 */
@SuppressWarnings("UnusedDeclaration")
public class MongoFieldImpl<T> implements MongoField<T> {
    private Query<T> query;
    private ObjectMapper mapper;
    private String fldStr;

    private FilterExpression fe;

    public MongoFieldImpl() {
    }

    public MongoFieldImpl(Query<T> q, ObjectMapper map) {
        query = q;
        mapper = map;
    }

    @Override
    public String getFieldString() {
        return fldStr;
    }

    @Override
    public void setFieldString(String fldStr) {
        fe = new FilterExpression();
        fe.setField(fldStr);
        this.fldStr = fldStr;
    }

    @Override
    public ObjectMapper getMapper() {
        return mapper;
    }

    @Override
    public void setMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Query<T> eq(Object val) {
        // checking for Ids in references...
        if (val != null) {
            Class<?> cls = val.getClass();
            if (mapper.getMorphium().isAnnotationPresentInHierarchy(cls, Entity.class) || val instanceof ObjectId) {
                Field field = mapper.getField(query.getType(), fldStr);
                if (field.isAnnotationPresent(Reference.class)) {
                    ObjectId id;
                    if (val instanceof ObjectId) {
                        id = (ObjectId) val;
                    } else {
                        id = mapper.getId(val);
                    }
                    if (field.getType().isAssignableFrom(List.class)) {
                        // list of references, this should be part of
                        //
                        // need to compare DBRefs
                        val = new DBRef(null, val.getClass().getName(), id);
                    } else {
                        // Reference
                        // here - query value is an entity AND it is referenced by the query type
                        // => we need to compeare ID's

                        val = id;
                    }
                }

            }
        }

        fe.setValue(val);
        fe.setField(mapper.getFieldName(query.getType(), fldStr));
        query.addChild(fe);
        return query;  // To change body of implemented methods use File | Settings | File Templates.
    }

    private void add(String op, Object value) {
        fe.setField(mapper.getFieldName(query.getType(), fldStr));
        FilterExpression child = new FilterExpression();
        child.setField(op);
        child.setValue(value);
        fe.addChild(child);

        query.addChild(fe);
    }

    private void add(List<FilterExpression> expressionList) {
        fe.setField(mapper.getFieldName(query.getType(), fldStr));
        fe.setChildren(expressionList);
        query.addChild(fe);
    }

    @Override
    public Query<T> ne(Object val) {
        add("$ne", val);
        return query;
    }

    @Override
    public Query<T> lt(Object val) {
        add("$lt", val);
        return query;
    }

    @Override
    public Query<T> lte(Object val) {
        add("$lte", val);
        return query;
    }

    @Override
    public Query<T> gt(Object val) {
        add("$gt", val);
        return query;
    }

    @Override
    public Query<T> gte(Object val) {
        add("$gte", val);
        return query;
    }

    @Override
    public Query<T> exists() {
        add("$exists", true);
        return query;
    }

    @Override
    public Query<T> notExists() {
        add("$exists", false);
        return query;
    }

    @Override
    public Query<T> mod(int base, int val) {
        BasicDBList lst = new BasicDBList();
        lst.add(base);
        lst.add(val);
        add("$mod", lst);
        return query;
    }

    @Override
    public Query<T> matches(Pattern p) {
        // $regex : 'acme.*corp', $options: 'i'
        add("$regex", p.toString());
        return query;
    }

    @Override
    public Query<T> matches(String ptrn) {
        return matches(Pattern.compile(ptrn));
    }

    @Override
    public Query<T> type(MongoType t) {
        add("$type", t.getNumber());
        return query;
    }

    @Override
    public Query<T> in(Collection<?> vals) {
        BasicDBList lst = new BasicDBList();
        lst.addAll(vals);
        add("$in", lst);
        return query;
    }

    @Override
    public Query<T> nin(Collection<?> vals) {
        BasicDBList lst = new BasicDBList();
        lst.addAll(vals);
        add("$nin", lst);
        return query;
    }

    @Override
    public Query<T> near(double x, double y) {
        BasicDBList lst = new BasicDBList();
        lst.add(x);
        lst.add(y);
        add("$near", lst);
        return query;
    }

    @Override
    public Query<T> near(double x, double y, double maxDistance) {
        BasicDBList location = new BasicDBList();
        location.add(x);
        location.add(y);

        List<FilterExpression> expressionList = new ArrayList<FilterExpression>();

        FilterExpression nearExpression = new FilterExpression();
        nearExpression.setField("$near");
        nearExpression.setValue(location);
        expressionList.add(nearExpression);

        FilterExpression maxDistanceExpression = new FilterExpression();
        maxDistanceExpression.setField("$maxDistance");
        maxDistanceExpression.setValue(maxDistance);
        expressionList.add(maxDistanceExpression);

        add(expressionList);
        return query;
    }

    @Override
    public Query<T> getQuery() {
        return query;
    }

    @Override
    public void setQuery(Query<T> q) {
        query = q;
    }

}
