package org.deepsetk.crud;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** Datos de conexion a MySQL y creacion de conexiones JDBC. */
final class DbConfig {

    final String host;
    final String port;
    final String user;
    final String password;
    final String database;

    DbConfig(String host, String port, String user, String password, String database) {
        this.host = blank(host) ? "localhost" : host.trim();
        this.port = blank(port) ? "3306" : port.trim();
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
        this.database = database == null ? "" : database.trim();
    }

    String url() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8";
    }

    Connection connect() throws SQLException {
        try {
            com.mysql.cj.jdbc.Driver driver = new com.mysql.cj.jdbc.Driver();
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            Connection connection = driver.connect(url(), props);
            if (connection == null) {
                throw new SQLException("El driver MySQL no acepto la URL: " + url());
            }
            return connection;
        } catch (SQLException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
