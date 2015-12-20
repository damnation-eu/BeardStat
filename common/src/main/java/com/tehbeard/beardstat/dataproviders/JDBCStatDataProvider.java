package com.tehbeard.beardstat.dataproviders;

import com.tehbeard.beardstat.BeardStatRuntimeException;
import com.tehbeard.beardstat.DatabaseConfiguration;
import com.tehbeard.beardstat.DbPlatform;
import com.tehbeard.beardstat.containers.EntityStatBlob;
import com.tehbeard.beardstat.containers.IStat;
import com.tehbeard.beardstat.containers.StatBlobRecord;
import com.tehbeard.beardstat.containers.documents.docfile.DocumentFile;
import com.tehbeard.beardstat.containers.documents.docfile.DocumentFileRef;
import com.tehbeard.beardstat.containers.meta.CategoryPointer;
import com.tehbeard.beardstat.containers.meta.DomainPointer;
import com.tehbeard.beardstat.containers.meta.StatPointer;
import com.tehbeard.beardstat.containers.meta.WorldPointer;
import com.tehbeard.utils.sql.DBVersion;
import com.tehbeard.utils.sql.JDBCDataSource;
import com.tehbeard.utils.sql.PostUpgrade;
import com.tehbeard.utils.sql.SQLInitScript;
import com.tehbeard.utils.sql.SQLScript;
import com.tehbeard.utils.uuid.MojangWebAPI;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.UUID;
import java.util.logging.Level;


/**
 * base class for JDBC based data providers Allows easy development of data
 * providers that make use of JDBC
 *
 * @author James
 *
 */
@SQLInitScript("sql/maintenence/create.tables")
public abstract class JDBCStatDataProvider extends JDBCDataSource implements IStatDataProvider {

    public Connection getConnection(){
        return connection;
    }

    //Entity scripts
    public static final String SQL_SAVE_ENTITY = "sql/entity/saveEntity";
    public static final String SQL_UPDATE_ENTITY = "sql/entity/updateEntityName";
    public static final String SQL_SAVE_STAT = "sql/entity/saveStat";
    public static final String SQL_LOAD_ENTITY_DATA = "sql/entity/getEntityData";
    //Component scripts
    public static final String SQL_LOAD_DOMAINS = "sql/components/load/getDomains";
    public static final String SQL_LOAD_WORLDS = "sql/components/load/getWorlds";
    public static final String SQL_LOAD_CATEGORIES = "sql/components/load/getCategories";
    public static final String SQL_LOAD_STATISTICS = "sql/components/load/getStatistics";
    public static final String SQL_SAVE_DOMAIN = "sql/components/save/saveDomain";
    public static final String SQL_SAVE_WORLD = "sql/components/save/saveWorld";
    public static final String SQL_SAVE_CATEGORY = "sql/components/save/saveCategory";
    public static final String SQL_SAVE_STATISTIC = "sql/components/save/saveStatistic";
    public static final String SQL_SAVE_UUID = "sql/save/setUUID";
    
    //Maintenence scripts
    public static final String SQL_METADATA_CATEGORY = "sql/maintenence/metadata/category";
    public static final String SQL_METADATA_STATISTIC = "sql/maintenence/metadata/statistic";
    public static final String SQL_METADATA_STATIC_STATS = "sql/maintenence/metadata/staticstats";
    public static final String SQL_METADATA_STATIC_FIXNULL = "sql/maintenence/metadata/fixnull";
    public static final String SQL_KEEP_ALIVE = "sql/maintenence/keepAlive";

    //Entity scripts
    //Component scripts
    // Load components
    @SQLScript(SQL_LOAD_DOMAINS)
    protected PreparedStatement loadDomainsList;
    @SQLScript(SQL_LOAD_WORLDS)
    protected PreparedStatement loadWorldsList;
    @SQLScript(SQL_LOAD_CATEGORIES)
    protected PreparedStatement loadCategoriesList;
    @SQLScript(SQL_LOAD_STATISTICS)
    protected PreparedStatement loadStatisticsList;
    // save components
    @SQLScript(value = SQL_SAVE_DOMAIN,flags = Statement.RETURN_GENERATED_KEYS)
    protected PreparedStatement saveDomain;
    @SQLScript(value = SQL_SAVE_WORLD,flags = Statement.RETURN_GENERATED_KEYS)
    protected PreparedStatement saveWorld;
    @SQLScript(value = SQL_SAVE_CATEGORY,flags = Statement.RETURN_GENERATED_KEYS)
    protected PreparedStatement saveCategory;
    @SQLScript(value = SQL_SAVE_STATISTIC,flags = Statement.RETURN_GENERATED_KEYS)
    protected PreparedStatement saveStatistic;
    // Load data from db
    @SQLScript(SQL_LOAD_ENTITY_DATA)
    protected PreparedStatement loadEntityData;
    // save to db
    @SQLScript(value=SQL_SAVE_ENTITY, flags = Statement.RETURN_GENERATED_KEYS)
    protected PreparedStatement saveEntity;
    @SQLScript(value=SQL_UPDATE_ENTITY)
    protected PreparedStatement updateEntityName;
    @SQLScript(SQL_SAVE_STAT)
    protected PreparedStatement saveEntityData;
    // Maintenance
    @SQLScript(SQL_KEEP_ALIVE)
    protected PreparedStatement keepAlive;
    protected PreparedStatement deleteEntity;
    //Create script
    
    //Utility
    @SQLScript(SQL_SAVE_UUID)
    protected PreparedStatement setUUID;
    // Write queue
    private HashMap<Integer, StatBlobRecord> writeCache = new HashMap<Integer, StatBlobRecord>();
    //Configuration/env
    protected DbPlatform platform;
    protected DatabaseConfiguration config;

    public JDBCStatDataProvider(DbPlatform platform, String scriptSuffix, String driverClass, DatabaseConfiguration config) throws ClassNotFoundException {
        super(scriptSuffix, driverClass, platform.getLogger());
        this.connectionProperties.put("allowMultiQuery", "true");
        this.config = config;
        this.platform = platform;
    }

    /**
     * Boots up the data provider, this entails:
     * <ol>
     * <li>Open connection</li>
     * <li>Migration check</li>
     * <li>Create tables</li>
     * <li>Load SQL statements</li>
     * <li>Update metadata tables</li>
     * <li>Cache data as needed</li>
     * </ol>
     *
     * @throws BeardStatRuntimeException
     */
    protected void initialise() throws BeardStatRuntimeException {

        try {
            setup();
            doMigration(getDataSourceVersion());
            executeScript(SQL_METADATA_CATEGORY);
            executeScript(SQL_METADATA_STATISTIC);
            executeScript(SQL_METADATA_STATIC_STATS);
            executeScript(SQL_METADATA_STATIC_FIXNULL);
            cacheComponents();
        } catch (SQLException e) {
            throw new BeardStatRuntimeException("Failed to initialize database", e, false);
        }

    }

    /**
     * Cache entries for quicker resolvement on our end.
     */
    public void cacheComponents() {
        ResultSet rs;
        try {
            //Domains
            rs = loadDomainsList.executeQuery();
            while (rs.next()) {
                DomainPointer
                        .get(rs.getString("domain"))
                        .setId(rs.getInt("domainId"));
            }
            rs.close();
        } catch (SQLException e) {
            this.platform.mysqlError(e, SQL_LOAD_DOMAINS);
        }
        try {
            //Worlds
            rs = loadWorldsList.executeQuery();
            while (rs.next()) {
                WorldPointer
                        .get(rs.getString("world"))
                        .setId(rs.getInt("worldId"));
                //TODO - set world human name + outputStr
            }
            rs.close();
        } catch (SQLException e) {
            this.platform.mysqlError(e, SQL_LOAD_WORLDS);
        }
        try {
            //Worlds
            rs = loadCategoriesList.executeQuery();
            while (rs.next()) {
                CategoryPointer
                        .get(rs.getString("category"))
                        .setId(rs.getInt("categoryId"));
                
                        //rs.getString("statwrapper"));
                //TODO - set outputStr
            }
            rs.close();
        } catch (SQLException e) {
            this.platform.mysqlError(e, SQL_LOAD_CATEGORIES);
        }
        try {
            //Worlds
            rs = loadStatisticsList.executeQuery();
            while (rs.next()) {
                StatPointer s = StatPointer.get(rs.getString("statistic"));
                s.setHumanName(rs.getString("name"));
                s.setId(rs.getInt("statisticId"));
                //TODO - Formatting.valueOf(rs.getString("formatting")));
            }
            rs.close();

        } catch (SQLException e) {
            this.platform.mysqlError(e, SQL_LOAD_STATISTICS);
        }

    }

    @Override
    public ProviderQueryResult[] queryDatabase(ProviderQuery query) {
        if ( 
                (query.name == null || query.noNameChk) && 
                query.type == null && 
                query.getUUIDString() == null
            ) {
            throw new IllegalStateException("Invalid ProviderQuery passed.");
        }
        String sql = "SELECT `entityId`,`name`,`type`,`uuid` FROM `${PREFIX}_entity` WHERE ";
        boolean addAnd = false;

        //Search by UUID if provided, or fall back to player name, NEVER DO BOTH
        if (query.getUUIDString() != null) {
            if (addAnd) {
                sql += "AND ";
            }
            sql += "`uuid`=? ";
            addAnd = true;
        } else if (query.name != null && !query.noNameChk) {
            if (query.likeName) {
                sql += "`name` LIKE ? ";
                addAnd = true;
            } else {
                sql += "`name`=? ";
                addAnd = true;
            }
        }

        if (query.type != null) {
            if (addAnd) {
                sql += "AND ";
            }
            sql += "`type`=? ";
            addAnd = true;
        }

        try {
            PreparedStatement qryStmt = connection.prepareStatement(processSQL(sql));
            int colId = 1;
            if (query.getUUIDString() != null) {
                qryStmt.setString(colId, query.getUUIDString());
                colId++;
            } else if (query.name != null) {
                String sqlName = (query.likeName ? "%" : "") + query.name + (query.likeName ? "%" : "");
                qryStmt.setString(colId, sqlName);
                colId++;
            }
            if (query.type != null) {
                qryStmt.setString(colId, query.type);
                colId++;
            }

            ResultSet rs = qryStmt.executeQuery();
            List<ProviderQueryResult> results = new ArrayList<ProviderQueryResult>();
            while (rs.next()) {
                results.add(new ProviderQueryResult(
                        rs.getInt("entityId"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("uuid") == null ? null : MojangWebAPI.expandUUID(rs.getString("uuid"))));
            }
            rs.close();
            return results.toArray(new ProviderQueryResult[0]);

        } catch (SQLException e) {
            platform.mysqlError(e, "AUTOGEN: " + sql);
        }
        return new ProviderQueryResult[0];
    }

    @Override
    public EntityStatBlob pullEntityBlob(ProviderQuery query) {
        try {
            if (!checkConnection()) {
                platform.getLogger().severe("Database connection error!");
                return null;
            }
            platform.getLogger().log(Level.INFO, "Requesting data for {0}", query);
            long t1 = (new Date()).getTime();
            ProviderQueryResult result = getSingleEntity(query);
            platform.getLogger().log(Level.INFO, "Entry {0}found", (result == null ? "not ":""));
            EntityStatBlob esb = null;
            ResultSet rs;

            if (result != null) {
                if(!result.name.equals(query.name)){
                    updateEntityName.setString(1,query.name);
                    updateEntityName.setString(2,result.asProviderQuery().getUUIDString());
                    updateEntityName.execute();
                }
                esb = new EntityStatBlob(query.name, result.dbid, result.type, result.uuid, this);//Create the damn esb
                // load all stats data
                loadEntityData.setInt(1, esb.getEntityID());
                rs = loadEntityData.executeQuery();

                while (rs.next()) {
                    // `domain`,`world`,`category`,`statistic`,`value`
                    IStat ps = esb.getStat(
                            DomainPointer.get(rs.getInt("domainId")),
                            WorldPointer.get(rs.getInt("worldId")),
                            CategoryPointer.get(rs.getInt("categoryId")),
                            StatPointer.get(rs.getInt("statisticId"))
                    );
                    ps.setValue(rs.getInt("value"));
                    ps.clearArchive();
                }
                rs.close();
            } else if (result == null && query.create) {
                try{
                    saveEntity.setString(1, query.name);
                    saveEntity.setString(2, query.type);
                    saveEntity.setString(3, query.getUUIDString());
                    saveEntity.executeUpdate();
                    rs = saveEntity.getGeneratedKeys();
                    rs.next();// load player id
                    // make the player object, close out result set.
                    esb = new EntityStatBlob(query.name, rs.getInt(1), query.type, query.getUUID(), this);
                    rs.close();
                }catch(SQLException e){
                    platform.mysqlError(e, SQL_SAVE_ENTITY);
                }
            }
            //Didn't get a esb, kill it.
            if (esb == null) {
                return null;
            }

            platform.loadEvent(esb);
            platform.getLogger().log(Level.CONFIG, "time taken to retrieve: {0} Milliseconds", ((new Date()).getTime() - t1));
            return esb;
        } catch (SQLException e) {
            platform.mysqlError(e, SQL_LOAD_ENTITY_DATA);
        }
        return null;
    }

    protected ProviderQueryResult getSingleEntity(ProviderQuery query) throws IllegalStateException {
        ProviderQueryResult[] results = queryDatabase(query);
        if (results.length > 1) {
            throw new IllegalStateException("Invalid Query provided, more than one entity returned.");
        }
        return results.length == 1 ? results[0] : null;
    }

    @Override
    public boolean hasEntityBlob(ProviderQuery query) {
        return queryDatabase(query).length > 1;
    }

    @Override
    public boolean deleteEntityBlob(EntityStatBlob blob) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void pushEntityBlob(EntityStatBlob player) {

        synchronized (this.writeCache) {

            StatBlobRecord copy = player.cloneForArchive();

            if (!this.writeCache.containsKey(player.getName())) {
                this.writeCache.put(copy.entityId, copy);
            }
        }

    }
    /**
     * Runner used to flush to database async.
     */
    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            synchronized (writeCache) {
                try {
                    keepAlive.execute();
                } catch (SQLException e1) {
                }

                if (!checkConnection()) {
                    platform.getLogger().warning("Could not restablish connection, will try again later, WARNING: CACHE WILL GROW WHILE THIS HAPPENS");
                } else {
                    platform.getLogger().config("Saving to database");
                    for (Entry<Integer, StatBlobRecord> entry : writeCache
                            .entrySet()) {

                        StatBlobRecord updateRecord = entry.getValue();
                        IStat stat = null;
                        try {
                            saveEntityData.clearBatch();
                            for (Iterator<IStat> it = updateRecord.stats.iterator(); it.hasNext();) {
                                stat = it.next();
                                saveEntityData.setInt(1, updateRecord.entityId);
                                saveEntityData.setInt(2, stat.getDomain().getId());
                                saveEntityData.setInt(3, stat.getWorld().getId());
                                saveEntityData.setInt(4, stat.getCategory().getId());
                                saveEntityData.setInt(5, stat.getStatistic().getId());
                                saveEntityData.setInt(6, stat.getValue());
                                saveEntityData.addBatch();
                            }
                            saveEntityData.executeBatch();

                            for (DocumentFileRef ref : updateRecord.files) {
                                try {
                                    DocumentFile newDoc = pushDocument(updateRecord.entityId, ref.getRef());
                                    ref.getRef().invalidateDocument();
                                    ref.setRef(newDoc);
                                } catch (RevisionMismatchException ex) {
                                    ref.invalidateRef();
                                    platform.getLogger().log(Level.SEVERE, "Document {0}:{1} failed to save.", new Object[]{ref.getRef().getDomain(), ref.getRef().getKey()});
                                    platform.getLogger().severe("Another process has stored a new revision at this address.");
                                    platform.getLogger().severe("No Revision Merge strategy found. Changes not saved.");
                                } catch (DocumentTooLargeException e) {
                                    platform.getLogger().log(Level.SEVERE, "Document {0}:{1} failed to save.", new Object[]{ref.getRef().getDomain(), ref.getRef().getKey()});
                                    platform.getLogger().severe("The document was too large to save to the database.");
                                }
                            }

                        } catch (SQLException e) {
                            platform.getLogger().log(Level.WARNING, "entity id: {0}}", updateRecord.entityId);
                            platform.getLogger().log(Level.WARNING, "domain: {0}", stat.getDomain());
                            platform.getLogger().log(Level.WARNING, "world: {0}", stat.getWorld());
                            platform.getLogger().log(Level.WARNING, "category: {0}", stat.getCategory());
                            platform.getLogger().log(Level.WARNING, "statistic: {0}", stat.getStatistic());
                            platform.getLogger().log(Level.WARNING, "Value: {0}", stat.getValue());
                            platform.mysqlError(e, SQL_SAVE_STAT);
                            checkConnection();
                        }
                    }
                    platform.getLogger().config("Clearing write cache");
                    writeCache.clear();
                }
            }

        }
    };

    @Override
    public void flushSync() {
        this.platform.getLogger().info("Flushing in main thread! Game will lag!");
        this.flush.run();
        this.platform.getLogger().info("Flushed!");
    }

    @Override
    public void flush() {

        new Thread(this.flush).start();
    }

    public final static int MAX_UUID_REQUESTS_PER = 2 * 64;

    /**
     * Add UUIDS for all players
     *
     * @throws SQLException
     */
    @PostUpgrade
    @DBVersion(6)
    public void upgradeWriteUUIDS() throws SQLException, Exception {
        platform.getLogger().log(Level.INFO, "Updating all UUIDs based on Mojang Web API");
        PreparedStatement stmt = connection.prepareStatement(processSQL("UPDATE `${PREFIX}_entity` SET `uuid`=? WHERE `name`=? and `type`=?"));

        stmt.setString(3, IStatDataProvider.PLAYER_TYPE);
        platform.getLogger().log(Level.INFO, "Generating list of players to checks");
        ProviderQueryResult[] result = queryDatabase(ProviderQuery.ALL_PLAYERS);

        List<String> toGet = new ArrayList<String>(result.length);
        for (ProviderQueryResult res : result) {
            toGet.add(res.name);
        }

        platform.getLogger().log(Level.INFO, "Querying Mojang Web API");
        Map<String, UUID> map = MojangWebAPI.lookupUUIDS(toGet);

        platform.getLogger().log(Level.INFO, "Applying Name->UUID mapping.");
        for (Entry<String, UUID> e : map.entrySet()) {
            stmt.setString(2, e.getKey());
            stmt.setString(1, e.getValue().toString().replaceAll("-", ""));
            stmt.executeUpdate();
            //System.out.println(e.getKey() + " = " + e.getValue());
        }
        platform.getLogger().log(Level.INFO, "Updated {0} entries", map.size());

    }

    /**
     * Utility method to load SQL commands from files in JAR
     *
     * @param type extension of file to load, if not found will try load sql
     * type (which is the type for MySQL syntax)
     * @param filename file to load, minus extension
     * @param prefix table prefix, replaces ${PREFIX} in loaded files
     * @return SQL commands loaded from file.
     */
    public String readSQLFile(String type, String filename) {
        platform.getLogger().fine("Loading SQL: " + filename);
        InputStream is = platform.getResource(filename + "." + type);
        if (is == null) {
            is = platform.getResource(filename + ".sql");
        }
        if (is == null) {
            throw new IllegalArgumentException("No SQL file found with name " + filename + "." + scriptSuffix);
        }
        Scanner scanner = new Scanner(is);
        String sql = scanner.useDelimiter("\\Z").next().replaceAll("\\Z", "").replaceAll("\\n|\\r", "");
        scanner.close();
        return processSQL(sql);

    }

    public String byteArrayToHexString(byte[] b) {
        String result = "";
        for (int i = 0; i < b.length; i++) {
            result
                    += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }

    public void setUUID(String player, String uuid) {
        try {
            setUUID.setString(1, uuid);
            setUUID.setString(2, player);
            setUUID.setString(3, IStatDataProvider.PLAYER_TYPE);
            setUUID.executeUpdate();
        } catch (SQLException e) {
            platform.mysqlError(e, SQL_SAVE_UUID);
        }

    }

    public void runExternalScript(File file) throws SQLException {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (is == null) {
            return;
        }
        Scanner scanner = new Scanner(is);
        String sql = scanner.useDelimiter("\\Z").next().replaceAll("\\Z", "").replaceAll("\\n|\\r", "");
        scanner.close();
        
        connection.prepareStatement(processSQL(sql)).execute();

    }

    @Override
    protected String getMigrationScriptPath(int toVersion) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getDataSourceVersion() {
        return config.latestVersion; //To change body of generated methods, choose Tools | Templates.
    }

}