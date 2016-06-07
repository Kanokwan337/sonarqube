/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.sonar.db.dialect.Dialect;
import org.sonar.db.dialect.MySql;
import org.sonar.db.dialect.Oracle;
import org.sonar.db.dialect.PostgreSql;

import static com.google.common.collect.Lists.newArrayList;

/**
 * Generate SQL queries to update multiple column types from a table.
 *
 * Note that this operation will not be re-entrant on:
 * <ul>
 *   <li>Oracle 11G (may raise {@code ORA-01442: column to be modified to NOT NULL is already NOT NULL} or
 *   {@code ORA-01451: column to be modified to NULL cannot be modified to NULL})</li>
 * </ul>
 */
public class AlterColumnsTypeBuilder {

  private static final String ALTER_TABLE = "ALTER TABLE ";
  private static final String ALTER_COLUMN = "ALTER COLUMN ";

  private final Dialect dialect;
  private final String tableName;
  private final List<ColumnDef> columnDefs = newArrayList();

  public AlterColumnsTypeBuilder(Dialect dialect, String tableName) {
    this.dialect = dialect;
    this.tableName = tableName;
  }

  public AlterColumnsTypeBuilder updateColumn(ColumnDef columnDef) {
    columnDefs.add(columnDef);
    return this;
  }

  public List<String> build() {
    if (columnDefs.isEmpty()) {
      throw new IllegalStateException("No column has been defined");
    }
    switch (dialect.getId()) {
      case PostgreSql.ID:
        return createPostgresQuery();
      case MySql.ID:
        return createMySqlQuery();
      case Oracle.ID:
        return createOracleQuery();
      default:
        return createMsSqlAndH2Queries();
    }
  }

  private List<String> createPostgresQuery() {
    StringBuilder sql = new StringBuilder(ALTER_TABLE + tableName + " ");
    for (Iterator<ColumnDef> columnDefIterator = columnDefs.iterator(); columnDefIterator.hasNext();) {
      ColumnDef columnDef = columnDefIterator.next();
      sql.append(ALTER_COLUMN);
      addColumn(sql, columnDef, "TYPE ", false);
      sql.append(", ");
      sql.append(ALTER_COLUMN);
      sql.append(columnDef.getName());
      sql.append(' ').append(columnDef.isNullable() ? "DROP" : "SET").append(" NOT NULL");
      if (columnDefIterator.hasNext()) {
        sql.append(", ");
      }
    }
    return Collections.singletonList(sql.toString());
  }

  private List<String> createMySqlQuery() {
    StringBuilder sql = new StringBuilder(ALTER_TABLE + tableName + " ");
    addColumns(sql, "MODIFY COLUMN ", "", true);
    return Collections.singletonList(sql.toString());
  }

  private List<String> createOracleQuery() {
    StringBuilder sql = new StringBuilder(ALTER_TABLE + tableName + " ").append("MODIFY (");
    addColumns(sql, "", "", true);
    sql.append(")");
    return Collections.singletonList(sql.toString());
  }

  private List<String> createMsSqlAndH2Queries() {
    List<String> sqls = new ArrayList<>();
    for (ColumnDef columnDef : columnDefs) {
      StringBuilder defaultQuery = new StringBuilder(ALTER_TABLE + tableName + " ");
      defaultQuery.append(ALTER_COLUMN);
      addColumn(defaultQuery, columnDef, "", true);
      sqls.add(defaultQuery.toString());
    }
    return sqls;
  }

  private void addColumns(StringBuilder sql, String updateKeyword, String typePrefix, boolean addNotNullableProperty) {
    for (Iterator<ColumnDef> columnDefIterator = columnDefs.iterator(); columnDefIterator.hasNext();) {
      sql.append(updateKeyword);
      addColumn(sql, columnDefIterator.next(), typePrefix, addNotNullableProperty);
      if (columnDefIterator.hasNext()) {
        sql.append(", ");
      }
    }
  }

  private void addColumn(StringBuilder sql, ColumnDef columnDef, String typePrefix, boolean addNotNullableProperty) {
    sql.append(columnDef.getName())
      .append(" ")
      .append(typePrefix)
      .append(columnDef.generateSqlType(dialect));
    if (addNotNullableProperty) {
      sql.append(columnDef.isNullable() ? " NULL" : " NOT NULL");
    }
  }

}
