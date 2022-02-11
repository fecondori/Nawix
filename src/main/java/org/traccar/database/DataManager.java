/*
 * Copyright 2012 - 2021 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.database;

import com.sun.jna.platform.win32.WinBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.*;
import org.traccar.model.Calendar;

import javax.sql.DataSource;
import javax.ws.rs.QueryParam;
import java.beans.Introspector;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class DataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataManager.class);

    public static final String ACTION_SELECT_ALL = "selectAll";
    public static final String ACTION_SELECT = "select";
    public static final String ACTION_INSERT = "insert";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_DELETE = "delete";

    private final Config config;

    private DataSource dataSource;

    public DataSource getDataSource() {
        return dataSource;
    }

    private boolean generateQueries;

    private final boolean forceLdap;

    public DataManager(Config config) throws Exception {
        this.config = config;

        forceLdap = config.getBoolean(Keys.LDAP_FORCE);

        initDatabase();
        initDatabaseSchema();
    }

    private void initDatabase() throws Exception {

        String driverFile = config.getString(Keys.DATABASE_DRIVER_FILE);
        if (driverFile != null) {
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            try {
                Method method = classLoader.getClass().getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                method.invoke(classLoader, new File(driverFile).toURI().toURL());
            } catch (NoSuchMethodException e) {
                Method method = classLoader.getClass()
                        .getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
                method.setAccessible(true);
                method.invoke(classLoader, driverFile);
            }
        }

        String driver = config.getString(Keys.DATABASE_DRIVER);
        if (driver != null) {
            Class.forName(driver);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(driver);
        hikariConfig.setJdbcUrl(config.getString(Keys.DATABASE_URL));
        hikariConfig.setUsername(config.getString(Keys.DATABASE_USER));
        hikariConfig.setPassword(config.getString(Keys.DATABASE_PASSWORD));
        hikariConfig.setConnectionInitSql(config.getString(Keys.DATABASE_CHECK_CONNECTION));
        hikariConfig.setIdleTimeout(600000);

        int maxPoolSize = config.getInteger(Keys.DATABASE_MAX_POOL_SIZE);
        if (maxPoolSize != 0) {
            hikariConfig.setMaximumPoolSize(maxPoolSize);
        }

        generateQueries = config.getBoolean(Keys.DATABASE_GENERATE_QUERIES);

        dataSource = new HikariDataSource(hikariConfig);
    }

    public static String constructObjectQuery(String action, Class<?> clazz, boolean extended) {
        switch (action) {
            case ACTION_INSERT:
            case ACTION_UPDATE:
                StringBuilder result = new StringBuilder();
                StringBuilder fields = new StringBuilder();
                StringBuilder values = new StringBuilder();

                Set<Method> methods = new HashSet<>(Arrays.asList(clazz.getMethods()));
                methods.removeAll(Arrays.asList(Object.class.getMethods()));
                methods.removeAll(Arrays.asList(BaseModel.class.getMethods()));
                for (Method method : methods) {
                    boolean skip;
                    if (extended) {
                        skip = !method.isAnnotationPresent(QueryExtended.class);
                    } else {
                        skip = method.isAnnotationPresent(QueryIgnore.class)
                                || method.isAnnotationPresent(QueryExtended.class) && !action.equals(ACTION_INSERT);
                    }
                    if (!skip && method.getName().startsWith("get") && method.getParameterTypes().length == 0) {
                        String name = Introspector.decapitalize(method.getName().substring(3));
                        if (action.equals(ACTION_INSERT)) {
                            fields.append(name).append(", ");
                            values.append(":").append(name).append(", ");
                        } else {
                            fields.append(name).append(" = :").append(name).append(", ");
                        }
                    }
                }
                fields.setLength(fields.length() - 2);
                if (action.equals(ACTION_INSERT)) {
                    values.setLength(values.length() - 2);
                    result.append("INSERT INTO ").append(getObjectsTableName(clazz)).append(" (");
                    result.append(fields).append(") ");
                    result.append("VALUES (").append(values).append(")");
                } else {
                    result.append("UPDATE ").append(getObjectsTableName(clazz)).append(" SET ");
                    result.append(fields);
                    result.append(" WHERE id = :id");
                }
                return result.toString();
            case ACTION_SELECT_ALL:
                return "SELECT * FROM " + getObjectsTableName(clazz);
            case ACTION_SELECT:
                return "SELECT * FROM " + getObjectsTableName(clazz) + " WHERE id = :id";
            case ACTION_DELETE:
                return "DELETE FROM " + getObjectsTableName(clazz) + " WHERE id = :id";
            default:
                throw new IllegalArgumentException("Unknown action");
        }
    }

    public static String constructPermissionQuery(String action, Class<?> owner, Class<?> property) {
        switch (action) {
            case ACTION_SELECT_ALL:
                return "SELECT " + makeNameId(owner) + ", " + makeNameId(property) + " FROM "
                        + getPermissionsTableName(owner, property);
            case ACTION_INSERT:
                return "INSERT INTO " + getPermissionsTableName(owner, property)
                        + " (" + makeNameId(owner) + ", " + makeNameId(property) + ") VALUES (:"
                        + makeNameId(owner) + ", :" + makeNameId(property) + ")";
            case ACTION_DELETE:
                return "DELETE FROM " + getPermissionsTableName(owner, property)
                        + " WHERE " + makeNameId(owner) + " = :" + makeNameId(owner)
                        + " AND " + makeNameId(property) + " = :" + makeNameId(property);
            default:
                throw new IllegalArgumentException("Unknown action");
        }
    }

    private String getQuery(String key) {
        String query = config.getString(key);
        if (query == null) {
            LOGGER.info("Query not provided: " + key);
        }
        return query;
    }

    public String getQuery(String action, Class<?> clazz) {
        return getQuery(action, clazz, false);
    }

    public String getQuery(String action, Class<?> clazz, boolean extended) {
        String queryName;
        if (action.equals(ACTION_SELECT_ALL)) {
            queryName = "database.select" + clazz.getSimpleName() + "s";
        } else {
            queryName = "database." + action.toLowerCase() + clazz.getSimpleName();
            if (extended) {
                queryName += "Extended";
            }
        }
        String query = config.getString(queryName);
        if (query == null) {
            if (generateQueries) {
                query = constructObjectQuery(action, clazz, extended);
            } else {
                LOGGER.info("Query not provided: " + queryName);
            }
        }
        return query;
    }

    public String getQuery(String action, Class<?> owner, Class<?> property) {
        String queryName;
        switch (action) {
            case ACTION_SELECT_ALL:
                queryName = "database.select" + owner.getSimpleName() + property.getSimpleName() + "s";
                break;
            case ACTION_INSERT:
                queryName = "database.link" + owner.getSimpleName() + property.getSimpleName();
                break;
            default:
                queryName = "database.unlink" + owner.getSimpleName() + property.getSimpleName();
                break;
        }
        String query = config.getString(queryName);
        if (query == null) {
            if (generateQueries) {
                query = constructPermissionQuery(
                        action, owner, property.equals(User.class) ? ManagedUser.class : property);
            } else {
                LOGGER.info("Query not provided: " + queryName);
            }
        }
        return query;
    }

    private static String getPermissionsTableName(Class<?> owner, Class<?> property) {
        String propertyName = property.getSimpleName();
        if (propertyName.equals("ManagedUser")) {
            propertyName = "User";
        }
        return "tc_" + Introspector.decapitalize(owner.getSimpleName())
                + "_" + Introspector.decapitalize(propertyName);
    }

    private static String getObjectsTableName(Class<?> clazz) {
        String result = "tc_" + Introspector.decapitalize(clazz.getSimpleName());
        // Add "s" ending if object name is not plural already
        if (!result.endsWith("s")) {
            result += "s";
        }
        return result;
    }

    private void initDatabaseSchema() throws LiquibaseException {

        if (config.hasKey(Keys.DATABASE_CHANGELOG)) {

            ResourceAccessor resourceAccessor = new FileSystemResourceAccessor(new File("."));

            Database database = DatabaseFactory.getInstance().openDatabase(
                    config.getString(Keys.DATABASE_URL),
                    config.getString(Keys.DATABASE_USER),
                    config.getString(Keys.DATABASE_PASSWORD),
                    config.getString(Keys.DATABASE_DRIVER),
                    null, null, null, resourceAccessor);

            Liquibase liquibase = new Liquibase(
                    config.getString(Keys.DATABASE_CHANGELOG), resourceAccessor, database);

            liquibase.clearCheckSums();

            liquibase.update(new Contexts());
        }
    }

    public User login(String email, String password) throws SQLException {
        User user = QueryBuilder.create(dataSource, getQuery("database.loginUser"))
                .setString("email", email.trim())
                .executeQuerySingle(User.class);
        LdapProvider ldapProvider = Context.getLdapProvider();
        if (user != null) {
            if (ldapProvider != null && user.getLogin() != null && ldapProvider.login(user.getLogin(), password)
                    || !forceLdap && user.isPasswordValid(password)) {
                return user;
            }
        } else {
            if (ldapProvider != null && ldapProvider.login(email, password)) {
                user = ldapProvider.getUser(email);
                Context.getUsersManager().addItem(user);
                return user;
            }
        }
        return null;
    }

    public void updateDeviceStatus(Device device) throws SQLException {
        QueryBuilder.create(dataSource, getQuery(ACTION_UPDATE, Device.class, true))
                .setObject(device)
                .executeUpdate();
    }

    public Collection<Position> getPositions(long deviceId, Date from, Date to) throws SQLException {
        return QueryBuilder.create(dataSource, getQuery("database.selectPositions"))
                .setLong("deviceId", deviceId)
                .setDate("from", from)
                .setDate("to", to)
                .executeQuery(Position.class);
    }

    public Position getPrecedingPosition(long deviceId, Date date) throws SQLException {
        return QueryBuilder.create(dataSource, getQuery("database.selectPrecedingPosition"))
                .setLong("deviceId", deviceId)
                .setDate("time", date)
                .executeQuerySingle(Position.class);
    }

    public void updateLatestPosition(Position position) throws SQLException {
        QueryBuilder.create(dataSource, getQuery("database.updateLatestPosition"))
                .setDate("now", new Date())
                .setObject(position)
                .executeUpdate();
    }

    public Collection<Position> getLatestPositions() throws SQLException {
        return QueryBuilder.create(dataSource, getQuery("database.selectLatestPositions"))
                .executeQuery(Position.class);
    }

    public Server getServer() throws SQLException {
        return QueryBuilder.create(dataSource, getQuery(ACTION_SELECT_ALL, Server.class))
                .executeQuerySingle(Server.class);
    }

    public Collection<Event> getEvents(long deviceId, Date from, Date to) throws SQLException {
        return QueryBuilder.create(dataSource, getQuery("database.selectEvents"))
                .setLong("deviceId", deviceId)
                .setDate("from", from)
                .setDate("to", to)
                .executeQuery(Event.class);
    }

    public Collection<Event> getEvents(long deviceId, String type, Date from, Date to) throws SQLException {
        return QueryBuilder.create(dataSource, getQuery("database.selectEventsWithType"))
                .setLong("deviceId", deviceId)
                .setDate("from", from)
                .setDate("to", to)
                .setString("type", type)
                .executeQuery(Event.class);
    }


    private boolean isEmptyList(List<Long> list){
        return list == null || list.isEmpty();
    }

    private String createInClause(String column, List<Long> list){
        if(isEmptyList(list)) return null;
        return String.format("%s in (%s)", column, String.join(",", list.stream().map(String::valueOf).collect(Collectors.toList())));
    }

    private String formatSpeedRange(String column, double min, double max){
        if(max > 0){
            return String.format("%s BETWEEN %f AND %f", column, min, max);
        }
        else{
            return String.format("%s >= %f", column, min);
        }
    }

    public Collection<ExtendedEvent> getOverspeedEvents(
            List<Long> groupIds,
            List<Long> deviceIds,
            Date from,
            Date to,
            List<Long> geofences,
            boolean includeOutsideGeofences,
            double minDeviceSpeed,
            double maxDeviceSpeed,
            double minDeviceSpeedLimit,
            double maxDeviceSpeedLimit,
            double minGeofenceSpeedLimit,
            double maxGeofenceSpeedLimit)
            throws SQLException {

        StringBuilder query = new StringBuilder();
        query.append("SELECT tc_events.*, tc_geofences.name as geofenceName, tc_positions.latitude, tc_positions.longitude, tc_positions.altitude, tc_devices.groupid as groupid" +
                " FROM tc_events" +
                " join tc_devices on tc_devices.id = tc_events.deviceid" +
                " join tc_positions on tc_positions.id = tc_events.positionid" +
                " left join tc_geofences on tc_geofences.id = tc_events.geofenceid WHERE ");

        // All clauses will be joined with an "AND" as delimiter
        List<String> clauses = new ArrayList<>();
        if(!isEmptyList(groupIds) && !isEmptyList(deviceIds))
        {
            clauses.add(String.format("(%s OR %s)",
                    createInClause("groupid", groupIds),
                    createInClause("tc_events.deviceId", deviceIds)));
        }
        else if(!isEmptyList(groupIds)){
            clauses.add(createInClause("groupid", groupIds));
        }
        else if(!isEmptyList(deviceIds))
        {
            clauses.add(createInClause("tc_events.deviceId", deviceIds));
        }

        clauses.add("tc_events.type = 'deviceOverspeed'");

        if(!isEmptyList(geofences)){
            String inClause = createInClause("geofenceid", geofences);
            if(includeOutsideGeofences)
                clauses.add(String.format("(%s OR geofenceid is NULL)", inClause));
            else
                clauses.add(inClause);
        }

        if(!includeOutsideGeofences){
            clauses.add("geofenceid IS NOT NULL");
        }

        clauses.add(formatSpeedRange("tc_positions.speed", minDeviceSpeed, maxDeviceSpeed));
        if(minDeviceSpeedLimit != 0 || maxDeviceSpeedLimit != 0)
            clauses.add(formatSpeedRange("CAST (tc_devices.attributes::jsonb->'speedLimit' as FLOAT)", minDeviceSpeedLimit, maxDeviceSpeedLimit));

        String geofenceSpeedLimit = formatSpeedRange("CAST (tc_geofences.attributes::jsonb->'speedLimit' as FLOAT)", minGeofenceSpeedLimit, maxGeofenceSpeedLimit);
        if(includeOutsideGeofences)
            geofenceSpeedLimit += " OR geofenceid IS NULL";

        clauses.add(geofenceSpeedLimit);
        clauses.add("eventTime between :from AND :to");

        query.append(String.format("(%s)", String.join(") AND (", clauses))).append(" ORDER BY eventTime");

        Collection<ExtendedEvent> result = QueryBuilder.create(dataSource, query.toString())
                .setDate("from", from)
                .setDate("to", to)
                .executeQuery(ExtendedEvent.class);
        //if(speedUnit == )

        return result;
    }

    public Collection<Statistics> getStatistics(Date from, Date to) throws SQLException {
        return QueryBuilder.create(dataSource, getQuery("database.selectStatistics"))
                .setDate("from", from)
                .setDate("to", to)
                .executeQuery(Statistics.class);
    }

    public static Class<?> getClassByName(String name) throws ClassNotFoundException {
        switch (name.toLowerCase().replace("id", "")) {
            case "device":
                return Device.class;
            case "group":
                return Group.class;
            case "user":
                return User.class;
            case "manageduser":
                return ManagedUser.class;
            case "geofence":
                return Geofence.class;
            case "driver":
                return Driver.class;
            case "attribute":
                return Attribute.class;
            case "calendar":
                return Calendar.class;
            case "command":
                return Command.class;
            case "maintenance":
                return Maintenance.class;
            case "notification":
                return Notification.class;
            case "order":
                return Order.class;
            default:
                throw new ClassNotFoundException();
        }
    }

    private static String makeNameId(Class<?> clazz) {
        String name = clazz.getSimpleName();
        return Introspector.decapitalize(name) + (!name.contains("Id") ? "Id" : "");
    }

    public Collection<Permission> getPermissions(Class<? extends BaseModel> owner, Class<? extends BaseModel> property)
            throws SQLException, ClassNotFoundException {
        return QueryBuilder.create(dataSource, getQuery(ACTION_SELECT_ALL, owner, property))
                .executePermissionsQuery();
    }

    public void linkObject(Class<?> owner, long ownerId, Class<?> property, long propertyId, boolean link)
            throws SQLException {
        QueryBuilder.create(dataSource, getQuery(link ? ACTION_INSERT : ACTION_DELETE, owner, property))
                .setLong(makeNameId(owner), ownerId)
                .setLong(makeNameId(property), propertyId)
                .executeUpdate();
    }

    public <T extends BaseModel> T getObject(Class<T> clazz, long entityId) throws SQLException {
        return QueryBuilder.create(dataSource, getQuery(ACTION_SELECT, clazz))
                .setLong("id", entityId)
                .executeQuerySingle(clazz);
    }

    public <T extends BaseModel> Collection<T> getObjects(Class<T> clazz) throws SQLException {
        return QueryBuilder.create(dataSource, getQuery(ACTION_SELECT_ALL, clazz))
                .executeQuery(clazz);
    }

    public void addObject(BaseModel entity) throws SQLException {
        entity.setId(QueryBuilder.create(dataSource, getQuery(ACTION_INSERT, entity.getClass()), true)
                .setObject(entity)
                .executeUpdate());
    }

    public void updateObject(BaseModel entity) throws SQLException {
        QueryBuilder.create(dataSource, getQuery(ACTION_UPDATE, entity.getClass()))
                .setObject(entity)
                .executeUpdate();
        if (entity instanceof User && ((User) entity).getHashedPassword() != null) {
            QueryBuilder.create(dataSource, getQuery(ACTION_UPDATE, User.class, true))
                    .setObject(entity)
                    .executeUpdate();
        }
    }

    public void removeObject(Class<? extends BaseModel> clazz, long entityId) throws SQLException {
        QueryBuilder.create(dataSource, getQuery(ACTION_DELETE, clazz))
                .setLong("id", entityId)
                .executeUpdate();
    }

}
