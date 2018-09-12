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

    static public void initDatabase(List<InputStream> formFileList) {
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

    static public List queryAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME;
        try (Connection con = createConnection();
             Statement statement = con.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery(sql);
        ) {
            LinkedList list = new LinkedList<>();
            while (resultSet.next()) {
            }
            return list;
        }
    }

    static public void insert(Expense expense) {
        String sql = "INSERT INTO " + TABLE_NAME + " (category, quantity, subtotal) VALUES( ?, ?, ?)";
        try (Connection con = createConnection();
             PreparedStatement statement = con.prepareStatement(sql);
        ) {
            statement.setString(1, expense.getCategory());
            statement.setInt(2, expense.getQuantity());
            statement.setInt(3, expense.getSubtotal());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
