来自 [芋道源码](http://www.iocoder.cn/?github)
- - -
> MyBatis 对数据库中的事务进行了抽象，其自身提供了相应的事务接口和简单实现。

> 在很多场景中，MyBatis 会与 Spring 框架集成，并由 Spring 框架管理事务。
<!-- more -->


# 概述
本文涉及类图如下:
{% asset_img Transaction.png Transaction UML图 %}

# Transaction
`org.apache.ibatis.transaction.Transaction` ，事务接口。代码如下：
```java
public interface Transaction {

  /**
   * 获得数据库连接
   * @return DataBase connection
   * @throws SQLException
   */
  Connection getConnection() throws SQLException;

  /**
   * 提交事务
   * @throws SQLException
   */
  void commit() throws SQLException;

  /**
   * 回滚事务
   * @throws SQLException
   */
  void rollback() throws SQLException;

  /**
   * 关闭数据库连接
   * @throws SQLException
   */
  void close() throws SQLException;

  /**
   * 获得事务超时时间
   * @throws SQLException
   */
  Integer getTimeout() throws SQLException;

}
```

## JdbcTransaction
`org.apache.ibatis.transaction.jdbc.JdbcTransaction` ，实现 Transaction 接口，基于 JDBC 的事务实现类。代码如下：
```java
public class JdbcTransaction implements Transaction {

  private static final Log log = LogFactory.getLog(JdbcTransaction.class);

  /**
   * Connection 对象
   */
  protected Connection connection;
  /**
   * DataSource 对象
   */
  protected DataSource dataSource;
  /**
   * 事务隔离级别
   */
  protected TransactionIsolationLevel level;
  /**
   * 自动提交标志
   */
  protected boolean autoCommit;

  public JdbcTransaction(DataSource ds, TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit) {
    dataSource = ds;
    level = desiredLevel;
    autoCommit = desiredAutoCommit;
  }

  public JdbcTransaction(Connection connection) {
    this.connection = connection;
  }

  @Override
  public Connection getConnection() throws SQLException {
    // 如果为空，创建连接
    if (connection == null) {
      openConnection();
    }
    return connection;
  }

  @Override
  public void commit() throws SQLException {
    // 非自动提交，执行提交事务
    if (connection != null && !connection.getAutoCommit()) {
      if (log.isDebugEnabled()) {
        log.debug("Committing JDBC Connection [" + connection + "]");
      }
      connection.commit();
    }
  }

  @Override
  public void rollback() throws SQLException {
    // 非自动提交，执行回滚
    if (connection != null && !connection.getAutoCommit()) {
      if (log.isDebugEnabled()) {
        log.debug("Rolling back JDBC Connection [" + connection + "]");
      }
      connection.rollback();
    }
  }

  @Override
  public void close() throws SQLException {
    // 重置为自动提交
    if (connection != null) {
      resetAutoCommit();
      if (log.isDebugEnabled()) {
        log.debug("Closing JDBC Connection [" + connection + "]");
      }
      // 关闭连接
      connection.close();
    }
  }
  /**
   * 设置指定的 autoCommit 属性
   *
   * @param desiredAutoCommit 指定的 autoCommit 属性
   */
  protected void setDesiredAutoCommit(boolean desiredAutoCommit) {
    try {
      if (connection.getAutoCommit() != desiredAutoCommit) {
        if (log.isDebugEnabled()) {
          log.debug("Setting autocommit to " + desiredAutoCommit + " on JDBC Connection [" + connection + "]");
        }
        connection.setAutoCommit(desiredAutoCommit);
      }
    } catch (SQLException e) {
      // Only a very poorly implemented driver would fail here,
      // and there's not much we can do about that.
      throw new TransactionException("Error configuring AutoCommit.  "
          + "Your driver may not support getAutoCommit() or setAutoCommit(). "
          + "Requested setting: " + desiredAutoCommit + ".  Cause: " + e, e);
    }
  }

  /**
   * 重置 autoCommit 属性
   */
  protected void resetAutoCommit() {
    try {
      if (!connection.getAutoCommit()) {
        // MyBatis does not call commit/rollback on a connection if just selects were performed.
        // Some databases start transactions with select statements
        // and they mandate a commit/rollback before closing the connection.
        // A workaround is setting the autocommit to true before closing the connection.
        // Sybase throws an exception here.
        if (log.isDebugEnabled()) {
          log.debug("Resetting autocommit to true on JDBC Connection [" + connection + "]");
        }
        connection.setAutoCommit(true);
      }
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Error resetting autocommit to true "
            + "before closing the connection.  Cause: " + e);
      }
    }
  }

  /**
   * 获得 Connection 对象
   *
   * @throws SQLException 获得失败
   */
  protected void openConnection() throws SQLException {
    if (log.isDebugEnabled()) {
      log.debug("Opening JDBC Connection");
    }
    // 获得连接
    connection = dataSource.getConnection();
    if (level != null) {
      // 设置事务隔离级别
      connection.setTransactionIsolation(level.getLevel());
    }
    // 设置自动提交属性
    setDesiredAutoCommit(autoCommit);
  }

  @Override
  public Integer getTimeout() throws SQLException {
    return null;
  }
}
```

## ManagedTransaction
`org.apache.ibatis.transaction.managed.ManagedTransaction` ，实现 Transaction 接口，基于容器管理的事务实现类。代码如下：
```java
public class ManagedTransaction implements Transaction {

  private static final Log log = LogFactory.getLog(ManagedTransaction.class);

  /**
   * DataSource 对象
   */
  private DataSource dataSource;
  /**
   * 事务隔离级别
   */
  private TransactionIsolationLevel level;
  /**
   * 数据库连接
   */
  private Connection connection;
  /**
   * 是否关闭数据库连接
   */
  private final boolean closeConnection;

  public ManagedTransaction(Connection connection, boolean closeConnection) {
    this.connection = connection;
    this.closeConnection = closeConnection;
  }

  public ManagedTransaction(DataSource ds, TransactionIsolationLevel level, boolean closeConnection) {
    this.dataSource = ds;
    this.level = level;
    this.closeConnection = closeConnection;
  }

  @Override
  public Connection getConnection() throws SQLException {
    // 连接为空，创建连接
    if (this.connection == null) {
      openConnection();
    }
    return this.connection;
  }

  @Override
  public void commit() throws SQLException {
    // Does nothing
  }

  @Override
  public void rollback() throws SQLException {
    // Does nothing
  }

  @Override
  public void close() throws SQLException {
    // 开启关闭连接并且连接不为空的时候关闭连接
    if (this.closeConnection && this.connection != null) {
      if (log.isDebugEnabled()) {
        log.debug("Closing JDBC Connection [" + this.connection + "]");
      }
      this.connection.close();
    }
  }

  protected void openConnection() throws SQLException {
    if (log.isDebugEnabled()) {
      log.debug("Opening JDBC Connection");
    }
    // 获取连接
    this.connection = this.dataSource.getConnection();
    // 设置隔离级别
    if (this.level != null) {
      this.connection.setTransactionIsolation(this.level.getLevel());
    }
  }

  @Override
  public Integer getTimeout() throws SQLException {
    return null;
  }
}
```

## SpringManagedTransaction
org.mybatis.spring.transaction.SpringManagedTransaction ，实现 Transaction 接口，基于 Spring 管理的事务实现类。实际真正在使用的，本文暂时不分享，感兴趣的胖友可以自己先愁一愁 [SpringManagedTransaction](https://github.com/eddumelendez/mybatis-spring/blob/master/src/main/java/org/mybatis/spring/transaction/SpringManagedTransaction.java) 。

# TransactionFactory
`org.apache.ibatis.transaction.TransactionFactory` ，Transaction 工厂接口。代码如下：
```java
public interface TransactionFactory {

  /**
   * Sets transaction factory custom properties.
   *
   * 设置工厂属性
   * 
   * @param props
   */
  void setProperties(Properties props);

  /**
   * Creates a {@link Transaction} out of an existing connection.
   *
   * 创建 Transaction 事务
   *
   * @param conn Existing database connection
   * @return Transaction
   * @since 3.1.0
   */
  Transaction newTransaction(Connection conn);

  /**
   * Creates a {@link Transaction} out of a datasource.
   *
   * 创建 Transaction 事务
   *
   * @param dataSource DataSource to take the connection from
   * @param level Desired isolation level
   * @param autoCommit Desired autocommit
   * @return Transaction
   * @since 3.1.0
   */
  Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit);

}
```

## JdbcTransactionFactory
`org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory `，实现 TransactionFactory 接口，JdbcTransaction 工厂实现类。代码如下：
```java
public class JdbcTransactionFactory implements TransactionFactory {

  @Override
  public void setProperties(Properties props) {
  }

  @Override
  public Transaction newTransaction(Connection conn) {
    // // 创建 JdbcTransaction 对象
    return new JdbcTransaction(conn);
  }

  @Override
  public Transaction newTransaction(DataSource ds, TransactionIsolationLevel level, boolean autoCommit) {
    // 创建 JdbcTransaction 对象
    return new JdbcTransaction(ds, level, autoCommit);
  }
}
```

## ManagedTransactionFactory
`org.apache.ibatis.transaction.managed.ManagedTransactionFactory `，实现TransactionFactory 接口，ManagedTransaction 工厂实现类。代码如下：
```java
public class ManagedTransactionFactory implements TransactionFactory {

  /**
   * 是否关闭连接
   */
  private boolean closeConnection = true;

  @Override
  public void setProperties(Properties props) {
    // 获得是否关闭连接属性
    if (props != null) {
      String closeConnectionProperty = props.getProperty("closeConnection");
      if (closeConnectionProperty != null) {
        closeConnection = Boolean.valueOf(closeConnectionProperty);
      }
    }
  }

  @Override
  public Transaction newTransaction(Connection conn) {
    // 创建 ManagedTransaction 对象
    return new ManagedTransaction(conn, closeConnection);
  }

  @Override
  public Transaction newTransaction(DataSource ds, TransactionIsolationLevel level, boolean autoCommit) {
    // Silently ignores autocommit and isolation level, as managed transactions are entirely
    // controlled by an external manager.  It's silently ignored so that
    // code remains portable between managed and unmanaged configurations.
    // 创建 ManagedTransaction 对象
    return new ManagedTransaction(ds, level, closeConnection);
  }
}
```

## SpringManagedTransactionFactory
org.mybatis.spring.transaction.SpringManagedTransactionFactory ，实现 TransactionFactory 接口，SpringManagedTransaction 工厂实现类。实际真正在使用的，本文暂时不分享，感兴趣的胖友可以自己先看一看[SpringManagedTransactionFactory](https://github.com/eddumelendez/mybatis-spring/blob/c5834f93bd4a5879f86854fe188957787e56ef95/src/main/java/org/mybatis/spring/transaction/SpringManagedTransactionFactory.java)。