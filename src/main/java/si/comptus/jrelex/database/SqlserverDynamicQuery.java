////////////////////////////////////////////////////////////////////////////////////////////////////
// JRelEx: Java application is intended for searching data using database relations.
// Copyright (C) 2015 tomazst <tomaz.stefancic@gmail.com>.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
////////////////////////////////////////////////////////////////////////////////////////////////////

package si.comptus.jrelex.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.SortType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import si.comptus.jrelex.Common;
import si.comptus.jrelex.container.CColumn;
import si.comptus.jrelex.container.CReference;
import si.comptus.jrelex.container.CReferenceData;
import si.comptus.jrelex.container.CSettings;
import si.comptus.jrelex.container.CTable;

import com.panemu.tiwulfx.common.TableCriteria;
import com.panemu.tiwulfx.dialog.MessageDialogBuilder;

/**
 *
 * @author tomaz
 */
public class SqlserverDynamicQuery<T> extends DynamicQueryAbstract<T> {
    private Connection conn;
    private static final Logger log = LoggerFactory
            .getLogger(MysqlDynamicQuery.class);

    public SqlserverDynamicQuery(Connection conn) {
        this.conn = conn;
    }

    public Connection getConn() {
        return conn;
    }

    public void setConn(Connection conn) {
        this.conn = conn;
    }

    public PreparedStatement getPrepStmtTableData(CTable table, String databaseName,
            String storedDatabaseName, List<TableCriteria<T>> filteredColumns,
            List<String> sortedColumns,
            List<TableColumn.SortType> sortingOrders, int startIndex,
            int maxResult) throws SQLException
    {
        String orderby = null;

        String firstColName = table.getColumnNames().get(0);
        orderby = " ORDER BY " + firstColName;

        if (sortedColumns != null) {
            if (sortedColumns.size() != 0) {
                orderby = sorts(sortedColumns, sortingOrders);
            }
        }

        ArrayList<String> cols = new ArrayList<String>();
        for (CColumn col : table.getColumns()) {
            cols.add(col.getName());
        }
        String columns = StringUtils.join(cols, ",");

        String sql1 = "SELECT " + columns + " FROM (";
        String sql2 = "select ROW_NUMBER() over (" + orderby
                + ") as r, * FROM [" + databaseName + "].dbo."
                + table.getName() + " " + getWhere(table, filteredColumns);

        if (maxResult == 0) {
            maxResult = Common.getInstance().getDbstore().getAppSettings().getNumberRowsToDisplay();
        }
        int maxIndex = startIndex + maxResult;

        String sql3 = ") AS rt WHERE r >=" + startIndex + " AND r <= "
                + maxIndex + orderby;

        String sql = sql1 + sql2 + sql3;

        PreparedStatement stmt = this.getPrepStmtWithValues(sql, filteredColumns);

        log.debug(sql);
        return stmt;
    }

    public PreparedStatement getPrepStmtTableData(CTable table, String databaseName,
            String storedDatabaseName, List<TableCriteria<T>> filteredColumns) throws SQLException {
        return getPrepStmtTableData(table, databaseName, storedDatabaseName,
                filteredColumns, null, null, 0, 0);
    }

    private String sorts(List<String> sortedColumns,
            List<TableColumn.SortType> sortingOrders) {

        String orderby = "";

        for (int i = 0; i < sortedColumns.size(); i++) {
            if (sortingOrders.get(i).equals(SortType.DESCENDING)) {
                orderby += sortedColumns.get(i) + " desc";
            } else {
                orderby += sortedColumns.get(i) + " asc";
            }

            if (sortedColumns.size() - 1 != i) {
                orderby += ",";
            }
        }

        if (!orderby.isEmpty()) {
            orderby = " ORDER BY " + orderby;
        }

        return orderby;
    }

    public int getRecordCount(CTable table, String databaseName,
            List<TableCriteria<T>> filteredColumns) {

        String sql = "SELECT COUNT(*) as c FROM [" + databaseName + "].dbo."
                + table.getName() + " " + getWhere(table, filteredColumns);
        log.debug(sql);
        int count = 0;
        try (PreparedStatement stmt = this.getPrepStmtWithValues(sql, filteredColumns);
                ResultSet rs = stmt.executeQuery();) {
            if (rs != null) {
                rs.next();
                count = rs.getInt("c");
            }
        } catch (SQLException e) {
            log.error("Error: ", e);
            MessageDialogBuilder.error(e).show(null);
        }
        return count;
    }

    private String getWhere(CTable table, List<TableCriteria<T>> filteredColumns) {
        String where = "";
        ArrayList<String> conditions = this.conditions(table, filteredColumns);
        where = StringUtils.join(conditions, " and ");

        if (!where.isEmpty()) {
            where = " WHERE " + where;
        }

        return where;
    }
/*
    private ArrayList<String> conditions(CTable table,
            List<TableCriteria<T>> filteredColumns) {

        ArrayList<String> conditions = new ArrayList<>(filteredColumns.size());

        for (TableCriteria<? extends Object> filteredColumn : filteredColumns) {

            String condition = "";

            Operator operator = filteredColumn.getOperator();
            String fieldValue = filteredColumn.getValue().toString();
            String fieldName = filteredColumn.getAttributeName();

            String fieldValueWithApostrof = fieldValue;

            if (!this.isNumericType(table.getColumnByName(fieldName).getType())) {
                fieldValueWithApostrof = "'" + fieldValue + "'";
            }

            switch (operator) {
            case eq:
                condition += fieldName + " = " + fieldValueWithApostrof;
                break;
            case ne:
                condition += fieldName + " != " + fieldValueWithApostrof;
                break;
            case le:
                condition += fieldName + " <= " + fieldValueWithApostrof;
                break;
            case lt:
                condition += fieldName + " < " + fieldValueWithApostrof;
                break;
            case ge:
                condition += fieldName + " >= " + fieldValueWithApostrof;
                break;
            case gt:
                condition += fieldName + " > " + fieldValueWithApostrof;
                break;
            case like_begin:
                condition += fieldName + " LIKE '" + fieldValue + "%'";
                break;
            case like_anywhere:
                condition += fieldName + " LIKE " + "'%" + fieldValue + "%'";
                break;
            case like_end:
                condition += fieldName + " LIKE " + "'%" + fieldValue + "'";
                break;
            case ilike_begin:
                condition += fieldName + " LIKE '" + fieldValue + "%'";
                break;
            case ilike_anywhere:
                condition += fieldName + " LIKE " + "'%" + fieldValue + "%'";
                break;
            case ilike_end:
                condition += fieldName + " LIKE " + "'%" + fieldValue + "'";
                break;
            case is_null:
                condition += fieldName + " IS NULL";
                break;
            case is_not_null:
                condition += fieldName + " IS NOT NULL";
                break;
            case in:
                condition += fieldName + " IN (" + fieldValueWithApostrof + ")";
                break;
            case not_in:
                condition += fieldName + " NOT IN (" + fieldValueWithApostrof
                        + ")";
                break;
            default:
            }
            conditions.add(condition);
        }
        return conditions;
    }
*/
/*
    private void displaySql(String sql) {
        TextField txtField = new TextField();
        // where.orderBy(this.sorts(sortedColumns, sortingOrders)).getSQL();
        txtField.setText(sql);
        txtField.setEditable(false);
        // Common.getInstance().getBrowserController().titlePaneSQLView.setContent(txtField);
    }
*/
    @Override
    public HashMap<String, CReferenceData> getReferencedData(
            String storedDatabaseName, String databaseName, String tableName,
            CColumn column, String value) {
        // If value is null or empty string we don't search for values in other
        // tables
        CSettings settings = Common.getInstance().getDbstore().getAppSettings();

        if(!settings.isShowRefToNullData()) {
            if(value == null){
                return new HashMap<>(0);
            }
        }

        if(!settings.isShowRefToEmptyData()){
            if(value.trim() == ""){
                return new HashMap<>(0);
            }
        }

        int initialSize = column.getReferences().size() + 1;
        HashMap<String, CReferenceData> referencedColumnData = new HashMap<>(
                initialSize);
        // ArrayList<String> subQrys = new ArrayList<>(); // for subquerys
        String[] subQrys = new String[initialSize];

        CReferenceData rdata = new CReferenceData();
        rdata.setStoredDatabase(storedDatabaseName);
        rdata.setDatabaseName(databaseName);
        rdata.setTableName(tableName);
        rdata.setColumnName(column.getName());
        setKeys(storedDatabaseName, rdata);
        rdata.setValue(value);
        String id = getRefId(tableName, column.getName(), tableName,
                column.getName());
        rdata.setId(id);
        referencedColumnData.put(id, rdata);

        String fieldValue = value;
        if (!this.isNumericType(column.getType())) {
            fieldValue = "'" + value + "'";
        }

        int i = 0;
        subQrys[i] = " (SELECT COUNT(*) FROM " + tableName + " WHERE "
                + column.getName() + "=" + fieldValue + ") AS " + id;

        for (CReference reference : column.getReferences()) {

            CReferenceData rdata2 = new CReferenceData();
            rdata2.setStoredDatabase(storedDatabaseName);
            rdata2.setDatabaseName(databaseName);
            rdata2.setTableName(reference.getReferencedTable());
            rdata2.setColumnName(reference.getReferencedColumn());
            rdata2.setValue(value);
            id = getRefId(reference.getTable(), reference.getColumn(),
                    reference.getReferencedTable(),
                    reference.getReferencedColumn());
            rdata2.setId(id);
            setKeys(storedDatabaseName, rdata2);
            referencedColumnData.put(rdata2.getId(), rdata2);

            fieldValue = value;
            if (!this.isNumericType(column.getType())) {
                fieldValue = "'" + value + "'";
            }

            i++;
            subQrys[i] = " (SELECT COUNT(*) FROM "
                    + reference.getReferencedTable() + " WHERE "
                    + reference.getReferencedColumn() + "=" + fieldValue
                    + ") AS " + id;

        }

        try {
            Statement stmt = conn.createStatement();
            String sql = "SELECT " + StringUtils.join(subQrys, ",");
            log.debug(sql);
            ResultSet rs = stmt.executeQuery(sql);
            rs.next();

            Iterator<String> iterator = referencedColumnData.keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                CReferenceData referenceData = (CReferenceData) referencedColumnData
                        .get(key);
                referenceData.setStrikes((int) rs.getInt(key));
            }

        } catch (SQLException e) {
            log.error("Erorr while getting data", e);
            MessageDialogBuilder.error(e).show(null);
        }

        return referencedColumnData;
    }

}
