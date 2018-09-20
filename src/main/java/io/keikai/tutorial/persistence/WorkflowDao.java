package io.keikai.tutorial.persistence;

import org.hsqldb.cmdline.*;

import java.io.*;
import java.net.URISyntaxException;
import java.sql.*;
import java.time.LocalDateTime;
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
    static final String TABLE_NAME = "workflow";

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

    synchronized static public void insert(Submission submission) {
        String sql = "INSERT INTO " + TABLE_NAME + " (form, formName, state, lastUpdate) VALUES( ?, ?, ?, ?)";
        try (Connection con = createConnection();
             PreparedStatement statement = con.prepareStatement(sql);
        ) {
            Blob form = con.createBlob();
            form.setBytes(1, submission.getForm().toByteArray());
            statement.setBlob(1, form);
            statement.setString(2, submission.getFormName());
            statement.setString(3, submission.getState().name());
            statement.setTimestamp(4, Timestamp.valueOf(submission.getLastUpdate()));
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    synchronized static public List<Submission> queryAll() {
        String sql = "SELECT * FROM " + TABLE_NAME;
        LinkedList<Submission> list = new LinkedList<>();
        try (Connection con = createConnection();
             PreparedStatement statement = con.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery();
        ) {
            while (resultSet.next()) {
                Submission submission = new Submission();
                submission.setId(resultSet.getInt("id"));
                submission.setFormName(resultSet.getString("formName"));
                submission.setState(Submission.State.valueOf(resultSet.getString("state")));
                submission.setLastUpdate(resultSet.getTimestamp("lastUpdate").toLocalDateTime());
                Blob formBlob = resultSet.getBlob("form");
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                outputStream.write(formBlob.getBytes(1, (int) formBlob.length()));
                submission.setForm(outputStream);
                list.add(submission);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

}
