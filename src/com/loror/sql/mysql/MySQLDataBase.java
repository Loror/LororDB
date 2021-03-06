package com.loror.sql.mysql;

import com.loror.sql.ModelData;
import com.loror.sql.ModelDataList;
import com.loror.sql.SQLDataBase;
import com.mysql.jdbc.Statement;

import java.sql.*;
import java.util.List;

abstract class MySQLDataBase implements SQLDataBase {

    public interface OnGetPst {
        void getPst(PreparedStatement pst) throws SQLException;
    }

    private Connection conn;

    public MySQLDataBase(String url, String name, String password) {
        try {
            Class.forName("com.mysql.jdbc.Driver");// 指定连接类型
            conn = DriverManager.getConnection(url, name, password);// 获取连接
        } catch (SQLException | ClassNotFoundException e) {
            onException(new com.loror.sql.SQLException(e));
        }
    }

    public Connection getConn() {
        return conn;
    }

    public PreparedStatement getPst(String sql, boolean returnKey) throws SQLException {
        if (conn == null || conn.isClosed()) {
            return null;
        }
        onSql(true, sql);
        if (returnKey) {
            return conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);// 准备执行语句
        } else {
            return conn.prepareStatement(sql);// 准备执行语句
        }
    }

    public void getPst(String sql, boolean returnKey, OnGetPst onGetPst) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = getPst(sql, returnKey);
        } catch (SQLException e) {
            onException(new com.loror.sql.SQLException(e));
        }
        if (preparedStatement == null) {
            return;
        }
        try {
            if (onGetPst != null) {
                onGetPst.getPst(preparedStatement);
            } else {
                preparedStatement.execute();
            }
        } catch (SQLException e) {
            onException(new com.loror.sql.SQLException(e));
        } finally {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public abstract void onException(com.loror.sql.SQLException e);

    public abstract void onSql(boolean connect, String sql);

    public abstract ModelDataList beforeQuery(String sql);

    public abstract void beforeExecute(String sql);

    @Override
    public ModelDataList executeQuery(String sql) {
        ModelDataList cache = beforeQuery(sql);
        if (cache != null) {
            onSql(false, sql);
            return cache;
        }
        ModelDataList entitys = new ModelDataList();
        getPst(sql, false, pst -> {
            ResultSet cursor = pst.executeQuery();
            List<ModelData> modelResults = MySQLResult.find(cursor);
            cursor.close();
            entitys.addAll(modelResults);
        });
        return entitys;
    }

    @Override
    public boolean execute(String sql) {
        beforeExecute(sql);
        boolean[] execute = new boolean[1];
        getPst(sql, false, pst -> {
            execute[0] = pst.execute();
        });
        return execute[0];
    }

    @Override
    public ModelData executeByReturnKeys(String sql) {
        beforeExecute(sql);
        ModelData result = new ModelData();
        getPst(sql, true, pst -> {
            pst.execute();
            // 在执行更新后获取自增长列
            ResultSet cursor = pst.getGeneratedKeys();
            List<ModelData> modelResults = MySQLResult.find(cursor);
            cursor.close();
            if (modelResults.size() > 0) {
                ModelData query = modelResults.get(0);
                result.addAll(query);
            }
        });
        return result;
    }

    @Override
    public int executeUpdate(String sql) {
        beforeExecute(sql);
        int[] updates = new int[1];
        getPst(sql, false, pst -> {
            updates[0] = pst.executeUpdate();
        });
        return updates[0];
    }

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
            conn = null;
        }
    }

    public boolean isClosed() {
        try {
            return conn == null || conn.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }
}
