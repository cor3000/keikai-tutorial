package io.keikai.tutorial.persistence;

import org.hsqldb.cmdline.*;

import java.io.*;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.*;

/**
 * Create adn close a connection for every query.
 */
public class WorkflowDao {
    /**
     * http://hsqldb.org/doc/guide/dbproperties-chapt.html
     * shutdown=true, Automatic Shutdown, shut down the database when the last connection is closed
     */
    public static final String HSQLDB_CONNECTION_STRING = "jdbc:hsqldb:file:database/tutorial;shutdown=true";
    static String TABLE_NAME = "workflow";

    static public void initDatabase() {
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
            try (Connection con = createConnection();) {
                initializeTable(con);
                System.out.println("-> initialized table " + TABLE_NAME);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Connection createConnection() {
        try {
            return DriverManager.getConnection(HSQLDB_CONNECTION_STRING, "SA", "");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    static private void initializeTable(Connection con) throws IOException, URISyntaxException, SqlToolError, SQLException {
        File inputFile = new File(WorkflowDao.class.getResource("/workflow.sql").toURI());
        SqlFile file = new SqlFile(inputFile);
        file.setConnection(con);
        file.execute();
    }


    static public void insertSubmission() {
        String sql = "INSERT INTO " + TABLE_NAME + " (category, quantity, subtotal) VALUES( ?, ?, ?)";
        try (Connection con = createConnection();
             PreparedStatement statement = con.prepareStatement(sql);
        ) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
