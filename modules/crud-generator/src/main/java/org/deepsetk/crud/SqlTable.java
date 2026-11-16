package org.deepsetk.crud;

import java.util.ArrayList;
import java.util.List;

/** Tabla y columnas detectadas en un CREATE TABLE de MySQL. */
final class SqlTable {

    final String name;
    final List<Column> columns;

    SqlTable(String name, List<Column> columns) {
        this.name = name;
        this.columns = List.copyOf(columns);
    }

    List<Column> primaryKeys() {
        List<Column> keys = new ArrayList<>();
        for (Column column : columns) {
            if (column.primaryKey) {
                keys.add(column);
            }
        }
        return keys;
    }

    boolean hasPrimaryKey() {
        for (Column column : columns) {
            if (column.primaryKey) {
                return true;
            }
        }
        return false;
    }

    static final class Column {

        final String sqlName;
        final String javaName;
        final String sqlType;
        final String javaType;
        final boolean nullable;
        final boolean autoIncrement;
        final boolean primaryKey;

        Column(String sqlName, String javaName, String sqlType, String javaType,
                boolean nullable, boolean autoIncrement, boolean primaryKey) {
            this.sqlName = sqlName;
            this.javaName = javaName;
            this.sqlType = sqlType;
            this.javaType = javaType;
            this.nullable = nullable;
            this.autoIncrement = autoIncrement;
            this.primaryKey = primaryKey;
        }
    }
}
