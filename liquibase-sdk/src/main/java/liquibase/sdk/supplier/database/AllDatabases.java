package liquibase.sdk.supplier.database;

import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.sdk.Context;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.experimental.theories.ParameterSupplier;
import org.junit.experimental.theories.PotentialAssignment;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.*;

public class AllDatabases extends ParameterSupplier {

    private Map<String, Boolean> connectionsAttempted = new HashMap<String, Boolean>();
    private Map<String, DatabaseConnection> connectionsByUrl = new HashMap<String, DatabaseConnection>();

    @Override
    public List<PotentialAssignment> getValueSources(ParameterSignature sig) {
        List<PotentialAssignment> returnList = new ArrayList<PotentialAssignment>();
        for (Database database :  DatabaseFactory.getInstance().getImplementedDatabases()) {
            database.setConnection(openConnection(database));
            returnList.add(PotentialAssignment.forValue(database.getShortName(), database));
        }

        return returnList;
    }

    protected DatabaseConnection openConnection(Database database) {
        try {
            final String url = getConnectionUrl(database);
            if (connectionsAttempted.containsKey(url)) {
                JdbcConnection connection = (JdbcConnection) connectionsByUrl.get(url);
                if (connection == null) {
                    return null;
                } else if (connection.getUnderlyingConnection().isClosed()){
                    connectionsByUrl.put(url, openDatabaseConnection(url));
                }
                return connectionsByUrl.get(url);
            }
            connectionsAttempted.put(url, Boolean.TRUE);


            final DatabaseConnection connection = openDatabaseConnection(url);
            if (connection == null) {
                return null;
            }

//            try {
//                if (url.startsWith("jdbc:hsql")) {
//                    ((JdbcConnection) databaseConnection).getUnderlyingConnection().createStatement().execute("CREATE SCHEMA " + ALT_SCHEMA + " AUTHORIZATION DBA");
//                } else if (url.startsWith("jdbc:sqlserver")
//                        || url.startsWith("jdbc:postgresql")
//                        || url.startsWith("jdbc:h2")) {
//                    ((JdbcConnection) databaseConnection).getUnderlyingConnection().createStatement().execute("CREATE SCHEMA " + ALT_SCHEMA);
//                }
//                if (!databaseConnection.getAutoCommit()) {
//                    databaseConnection.commit();
//                }
//            } catch (SQLException e) {
////            e.printStackTrace();
//                ; //schema already exists
//            } finally {
//                try {
//                    databaseConnection.rollback();
//                } catch (DatabaseException e) {
//                    if (database instanceof DB2Database) {
////                    expected, there is a problem with it
//                    } else {
//                        throw e;
//                    }
//                }
//            }

            connectionsByUrl.put(url, connection);

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        try {
                            if (!((JdbcConnection) connection).getUnderlyingConnection().getAutoCommit()) {
                                ((JdbcConnection) connection).getUnderlyingConnection().rollback();
                            }
                        } catch (SQLException e) {
                            ;
                        }


                        ((JdbcConnection) connection).getUnderlyingConnection().close();
                    } catch (SQLException e) {
                        System.out.println("Could not close " + url);
                        e.printStackTrace();
                    }
                }
            }));

            return connectionsByUrl.get(url);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    protected String getConnectionUrl(Database database) {
        if (database.getShortName().equals("h2")) {
            return "jdbc:h2:mem:liquibase";
        }
        return null;  //To change body of created methods use File | Settings | File Templates.
    }

    protected String getUsername(String url) {
        return "liquibase";
    }

    protected String getPassword(String url) {
        return "liquibase";
    }


    public DatabaseConnection openDatabaseConnection(String url) throws Exception {
        if (url == null) {
            System.out.println("Cannot open connection for null url");
            return null;
        }

        String username = getUsername(url);
        String password = getPassword(url);


        JDBCDriverClassLoader jdbcDriverLoader = new JDBCDriverClassLoader();
        final Driver driver;
        try {
            String defaultDriver = DatabaseFactory.getInstance().findDefaultDriver(url);
            driver = (Driver) Class.forName(defaultDriver, true, jdbcDriverLoader).newInstance();
        } catch (Exception e) {
            System.out.println("Error finding default driver for " + url + ": Will not test against.  " + e.getMessage());
            return null; //could not connect
        }

        Properties info = new Properties();
        info.put("user", username);
        if (password != null) {
            info.put("password", password);
        }
        info.put("retrieveMessagesFromServerOnGetMessage", "true"); //for db2


        Connection connection;
        try {
            connection = driver.connect(url, info);
        } catch (SQLException e) {
            System.out.println("Could not connect to " + url + ": Will not test against.  " + e.getMessage());
            return null; //could not connect
        }
        if (connection == null) {
            throw new DatabaseException("Connection could not be created to " + url + " with driver " + driver.getClass().getName() + ".  Possibly the wrong driver for the given database URL");
        }

        return new JdbcConnection(connection);
    }
}
