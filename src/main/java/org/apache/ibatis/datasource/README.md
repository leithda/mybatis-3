教程来自 [芋道源码](http://www.iocoder.cn/?github)
- - -
> 数据源是实际开发中常用的组件之一。现在开源的数据源都提供了比较丰富的功能，例如，连接池功能、检测连接状态等，选择性能优秀的数据源组件对于提升 ORM 框架乃至整个应用的性能都是非常重要的。

> `MyBatis` 自身提供了相应的数据源实现，当然 `MyBatis` 也提供了与第三方数据源集成的接口，这些功能都位于数据源模块之中。

<!-- More -->
# 概述
涉及的类如图:
![DataSource](https://github.com/leithda/mybatis-3/blob/master/uml/DataSource.png?raw=true)


# DataSourceFactory
`org.apache.ibatis.datasource.DataSourceFactory` ，`javax.sql.DataSource` 工厂接口。代码如下：
```java
public interface DataSourceFactory {


  /**
   * 设置 DataSource 对象的属性
   *
   * @param props 属性
   */
  void setProperties(Properties props);

  /**
   * 获得 DataSource 对象
   *
   * @return DataSource 对象
   */
  DataSource getDataSource();
}
```

## UnpooledDataSourceFactory
`org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory` ，实现 `DataSourceFactory` 接口，非池化的 `DataSourceFactory` 实现类。

### 构造方法
```java
  /**
   * 数据源对象
   */
  protected DataSource dataSource;

  public UnpooledDataSourceFactory() {
    this.dataSource = new UnpooledDataSource();
  }
```

### setProperties
`#setProperties(Properties properties)` 方法，将 `properties` 的属性，初始化到 `dataSource` 中。代码如下：
```java
  @Override
  public void setProperties(Properties properties) {
    Properties driverProperties = new Properties();
    // 创建 DataSource 对应的 MetaObject对象
    MetaObject metaDataSource = SystemMetaObject.forObject(dataSource);
    // 遍历 properties 属性，初始化到 driverProperties 和 MetaObject 中
    for (Object key : properties.keySet()) {
      String propertyName = (String) key;
      // 初始化到 driverProperties 中
      if (propertyName.startsWith(DRIVER_PROPERTY_PREFIX)) { // 以 "driver." 开头的配置
        String value = properties.getProperty(propertyName);
        driverProperties.setProperty(propertyName.substring(DRIVER_PROPERTY_PREFIX_LENGTH), value);
      // 初始化到 MetaObject 中
      } else if (metaDataSource.hasSetter(propertyName)) {
        String value = (String) properties.get(propertyName);
        // <1> 转化属性
        Object convertedValue = convertValue(metaDataSource, propertyName, value);
        metaDataSource.setValue(propertyName, convertedValue);
      } else {
        throw new DataSourceException("Unknown DataSource property: " + propertyName);
      }
    }
    // 设置 driverProperties 到 MetaObject 中
    if (driverProperties.size() > 0) {
      metaDataSource.setValue("driverProperties", driverProperties);
    }
  }
```
 - `<1>` 处，调用 #convertValue(MetaObject metaDataSource, String propertyName, String value) 方法，将字符串转化成对应属性的类型。代码如下：
```java
  /**
   * 转换属性
   * @param metaDataSource 数据源 MetaObject 对象
   * @param propertyName 属性名
   * @param value 属性值
   * @return 转换后属性值
   */
  private Object convertValue(MetaObject metaDataSource, String propertyName, String value) {
    Object convertedValue = value;
    Class<?> targetType = metaDataSource.getSetterType(propertyName);
    if (targetType == Integer.class || targetType == int.class) {
      convertedValue = Integer.valueOf(value);
    } else if (targetType == Long.class || targetType == long.class) {
      convertedValue = Long.valueOf(value);
    } else if (targetType == Boolean.class || targetType == boolean.class) {
      convertedValue = Boolean.valueOf(value);
    }
    return convertedValue;
  }
```

## PooledDataSourceFactory
`org.apache.ibatis.datasource.pooled.PooledDataSourceFactory ，继承 UnpooledDataSourceFactory` 类，池化的 DataSourceFactory 实现类。
> FROM 《MyBatis 文档 —— XML 映射配置文件》
	POOLED– 这种数据源的实现利用“池”的概念将 JDBC 连接对象组织起来，避免了创建新的连接实例时所必需的初始化和认证时间。 这是一种使得并发 Web 应用快速响应请求的流行处理方式。
	除了上述提到 UNPOOLED 下的属性外，还有更多属性用来配置 POOLED 的数据源：
   - poolMaximumActiveConnections – 在任意时间可以存在的活动（也就是正在使用）连接数量，默认值：10
   - poolMaximumIdleConnections – 任意时间可能存在的空闲连接数。
   - poolMaximumCheckoutTime – 在被强制返回之前，池中连接被检出（checked out）时间，默认值：20000 毫秒（即 20 秒）
   - poolTimeToWait – 这是一个底层设置，如果获取连接花费了相当长的时间，连接池会打印状态日志并重新尝试获取一个连接（避免在误配置的情况下一直安静的失败），默认值：20000 毫秒（即 20 秒）。
   - poolMaximumLocalBadConnectionTolerance – 这是一个关于坏连接容忍度的底层设置， 作用于每一个尝试从缓存池获取连接的线程. 如果这个线程获取到的是一个坏的连接，那么这个数据源允许这个线程尝试重新获取一个新的连接，但是这个重新尝试的次数不应该超过 poolMaximumIdleConnections 与 poolMaximumLocalBadConnectionTolerance 之和。 默认值：3 (新增于 3.4.5)
   - poolPingQuery – 发送到数据库的侦测查询，用来检验连接是否正常工作并准备接受请求。默认是“NO PING QUERY SET”，这会导致多数数据库驱动失败时带有一个恰当的错误消息。
   - poolPingEnabled – 是否启用侦测查询。若开启，需要设置 poolPingQuery 属性为一个可执行的 SQL 语句（最好是一个速度非常快的 SQL 语句），默认值：false。
   - poolPingConnectionsNotUsedFor – 配置 poolPingQuery 的频率。可以被设置为和数据库连接超时时间一样，来避免不必要的侦测，默认值：0（即所有连接每一时刻都被侦测 — 当然仅当 poolPingEnabled 为 true 时适用）。
    
 - PooledDataSource 比 UnpooledDataSource 的配置项多很多。
代码如下:
```java
// PooledDataSourceFactory.java

public class PooledDataSourceFactory extends UnpooledDataSourceFactory {

    public PooledDataSourceFactory() {
        this.dataSource = new PooledDataSource();
    }

}
```
 - 默认创建了 `PooledDataSource` 对象。其它方法，在父类中 UnpooledDataSourceFactory 中已经实现。所以，真正的池化逻辑，在 PooledDataSource 对象中。
 
## JndiDataSourceFactory
`org.apache.ibatis.datasource.jndi.JndiDataSourceFactory` ，实现 DataSourceFactory 接口，基于 JNDI 的 DataSourceFactory 实现类。
> FROM 《MyBatis 文档 —— XML 映射配置文件》

> **JNDI** – 这个数据源的实现是为了能在如 EJB 或应用服务器这类容器中使用，容器可以集中或在外部配置数据源，然后放置一个 JNDI 上下文的引用。这种数据源配置只需要两个属性：
- initial_context – 这个属性用来在 InitialContext 中寻找上下文（即，initialContext.lookup(initial_context)）。这是个可选属性，如果忽略，那么 data_source 属性将会直接从 InitialContext 中寻找。
- data_source – 这是引用数据源实例位置的上下文的路径。提供了 initial_context 配置时会在其返回的上下文中进行查找，没有提供时则直接在 InitialContext 中查找。
> 和其他数据源配置类似，可以通过添加前缀“env.”直接把属性传递给初始上下文。比如：
- env.encoding=UTF8
这就会在初始上下文（InitialContext）实例化时往它的构造方法传递值为 UTF8 的 encoding 属性。

### setProperties
`#setProperties(Properties properties)` 方法，从上下文中，获得 DataSource 对象。代码如下：
```java
  public static final String INITIAL_CONTEXT = "initial_context";
  public static final String DATA_SOURCE = "data_source";
  public static final String ENV_PREFIX = "env.";

  @Override
  public void setProperties(Properties properties) {
    try {
      InitialContext initCtx;
      // <1> 获得系统 Properties 对象
      Properties env = getEnvProperties(properties);
      // 创建 InitialContext 对象
      if (env == null) {
        initCtx = new InitialContext();
      } else {
        initCtx = new InitialContext(env);
      }

      // 从 InitialContext 上下文中，获取 DataSource 对象
      if (properties.containsKey(INITIAL_CONTEXT)
          && properties.containsKey(DATA_SOURCE)) {
        Context ctx = (Context) initCtx.lookup(properties.getProperty(INITIAL_CONTEXT));
        dataSource = (DataSource) ctx.lookup(properties.getProperty(DATA_SOURCE));
      } else if (properties.containsKey(DATA_SOURCE)) {
        dataSource = (DataSource) initCtx.lookup(properties.getProperty(DATA_SOURCE));
      }

    } catch (NamingException e) {
      throw new DataSourceException("There was an error configuring JndiDataSourceTransactionPool. Cause: " + e, e);
    }
  }
```
 - `<1>` 处，调用 #getEnvProperties(Properties allProps) 方法，获得系统 Properties 对象。代码如下：
 ```java
   /**
   * 获取系统属性实例
   * @param allProps 全部属性
   * @return 系统 Properties 实例
   */
  private static Properties getEnvProperties(Properties allProps) {
    final String PREFIX = ENV_PREFIX;
    Properties contextProperties = null;
    for (Entry<Object, Object> entry : allProps.entrySet()) {
      String key = (String) entry.getKey();
      String value = (String) entry.getValue();
      if (key.startsWith(PREFIX)) {
        if (contextProperties == null) {
          contextProperties = new Properties();
        }
        contextProperties.put(key.substring(PREFIX.length()), value);
      }
    }
    return contextProperties;
  }
 ```
 
# DataSource
`javax.sql.DataSource` 是个神奇的接口，在其上可以衍生出数据连接池、分库分表、读写分离等等功能。
 
## UnpooledDataSource
`org.apache.ibatis.datasource.unpooled.UnpooledDataSource` ，实现 DataSource 接口，非池化的 DataSource 对象。
 
### 构造方法

```java
  /**
   * Driver 类加载器
   */
  private ClassLoader driverClassLoader;

  /**
   * Driver 属性
   */
  private Properties driverProperties;

  /**
   * 已注册的 Driver 映射
   *
   * KEY：Driver 类名
   * VALUE：Driver 对象
   */
  private static Map<String, Driver> registeredDrivers = new ConcurrentHashMap<>();

  /**
   * Driver 类名
   */
  private String driver;
  /**
   * 数据库 URL
   */
  private String url;
  /**
   * 数据库用户名
   */
  private String username;
  /**
   * 数据库密码
   */
  private String password;
  /**
   * 是否自动提交事务
   */
  private Boolean autoCommit;
  /**
   * 默认事务隔离级别
   */
  private Integer defaultTransactionIsolationLevel;
  private Integer defaultNetworkTimeout;

  static {
    // 初始化 registeredDrivers
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      registeredDrivers.put(driver.getClass().getName(), driver);
    }
  }

  public UnpooledDataSource() {
  }

  public UnpooledDataSource(String driver, String url, String username, String password) {
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(String driver, String url, Properties driverProperties) {
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }
```

### getConnection
`#getConnection(...)` 方法，获得 Connection 连接。代码如下：
```java
  @Override
  public Connection getConnection() throws SQLException {
    return doGetConnection(username, password);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return doGetConnection(username, password);
  }
```
 - 都是调用 #doGetConnection(String username, String password) 方法，获取 Connection 连接。代码如下：
 ```java
 
  /**
   * 获取数据库连接实际操作
   * @param username 用户名
   * @param password 密码
   * @return 数据库连接
   * @throws SQLException 异常
   */
  private Connection doGetConnection(String username, String password) throws SQLException {
    Properties props = new Properties();
    if (driverProperties != null) {
      props.putAll(driverProperties);
    }
    if (username != null) {
      props.setProperty("user", username);
    }
    if (password != null) {
      props.setProperty("password", password);
    }
    return doGetConnection(props);
  }

  /**
   * 获取数据库连接
   * @param properties Properties实例
   * @return 数据库连接
   * @throws SQLException 异常
   */
  private Connection doGetConnection(Properties properties) throws SQLException {
    // <1> 初始化 Driver
    initializeDriver();
    // <2> 获得 Connection 实例
    Connection connection = DriverManager.getConnection(url, properties);
    // <3> 配置 Connection 实例
    configureConnection(connection);
    return connection;
  }
 ```
 - `<1>` 处，调用 #initializeDriver() 方法，初始化 Driver 。
 - `<2>` 处，调用 java.sql.DriverManager#getConnection(String url, Properties info) 方法，获得 Connection 对象。
 - `<3>` 处，调用 #configureConnection(Connection conn) 方法，配置 Connection 对象。

#### initializeDriver
`#initializeDriver()` 方法，初始化 Driver 。代码如下：
```java
  /**
   * 初始化 Driver
   * @throws SQLException 异常
   */
  private synchronized void initializeDriver() throws SQLException { // <1>
    // 判断 registeredDrivers 是否已经存在该 driver ，若不存在，进行初始化
    if (!registeredDrivers.containsKey(driver)) {
      Class<?> driverType;
      try {
        // <2> 获得 driver 类
        if (driverClassLoader != null) {
          driverType = Class.forName(driver, true, driverClassLoader);
        } else {
          driverType = Resources.classForName(driver);
        }
        // <3> 创建 Driver 对象
        // DriverManager requires the driver to be loaded via the system ClassLoader.
        // http://www.kfu.com/~nsayer/Java/dyn-jdbc.html
        Driver driverInstance = (Driver)driverType.newInstance();
        // 创建 DriverProxy 对象，并注册到 DriverManager 中
        DriverManager.registerDriver(new DriverProxy(driverInstance));
        // 添加到 registeredDrivers 中
        registeredDrivers.put(driver, driverInstance);
      } catch (Exception e) {
        throw new SQLException("Error setting driver on UnpooledDataSource. Cause: " + e);
      }
    }
  }
```
 - `<1>`处，synchronized 锁的粒度太大，可以减小到基于 registeredDrivers 来同步，并且很多时候，不需要加锁。
 - `<2>`处,获得 driver 类，实际上，就是我们常见的 "Class.forName("com.mysql.jdbc.Driver")" 。
 - `<3>`处，创建 `Driver` 对象，并注册到 DriverManager 中，以及添加到 registeredDrivers 中。为什么此处会有使用 DriverProxy 呢？DriverProxy 的代码如下：
```java
// UnpooledDataSource.java 的内部私有静态类

private static class DriverProxy implements Driver {
    private Driver driver;

    DriverProxy(Driver d) {
        this.driver = d;
    }

    @Override
    public boolean acceptsURL(String u) throws SQLException {
        return this.driver.acceptsURL(u);
    }

    @Override
    public Connection connect(String u, Properties p) throws SQLException {
        return this.driver.connect(u, p);
    }

    @Override
    public int getMajorVersion() {
        return this.driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return this.driver.getMinorVersion();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
        return this.driver.getPropertyInfo(u, p);
    }

    @Override
    public boolean jdbcCompliant() {
        return this.driver.jdbcCompliant();
    }

    // @Override only valid jdk7+
    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); // <4>
    }
} 
```
 - 因为 `<4>` 处，使用 MyBatis 自定义的 Logger 对象
 
#### configureConnection
`#configureConnection(Connection conn)` 方法，配置 Connection 对象。代码如下：
```java
  /**
   * 配置数据库连接
   * @param conn 连接对象
   * @throws SQLException 异常
   */
  private void configureConnection(Connection conn) throws SQLException {
    // 设置超时时间
    if (defaultNetworkTimeout != null) {
      conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), defaultNetworkTimeout);
    }
    // 设置自动提交
    if (autoCommit != null && autoCommit != conn.getAutoCommit()) {
      conn.setAutoCommit(autoCommit);
    }
    // 设置事务隔离级别
    if (defaultTransactionIsolationLevel != null) {
      conn.setTransactionIsolation(defaultTransactionIsolationLevel);
    }
  }
```

### 其他方法
 - UnpooledDataSource 还实现了 DataSource 的其它方法,自行观看
 
## PooledDataSource
`org.apache.ibatis.datasource.pooled.PooledDataSource` ，实现 DataSource 接口，池化的 DataSource 实现类。
- 实际场景下，我们基本不用 MyBatis 自带的数据库连接池的实现。所以，本文更多的目的，是让胖友们对数据库连接池的实现，有个大体的理解。

### 构造方法
```java
public class PooledDataSource implements DataSource {

  private static final Log log = LogFactory.getLog(PooledDataSource.class);

  /**
   * PoolState 实例， 记录池化的状态
   */
  private final PoolState state = new PoolState(this);

  /**
   * UnpooledDataSource 实例
   */
  private final UnpooledDataSource dataSource;

  // OPTIONAL CONFIGURATION FIELDS
  /**
   * 在任意时间可以存在的活动（也就是正在使用）连接数量
   */
  protected int poolMaximumActiveConnections = 10;

  /**
   * 任意时间可能存在的空闲连接数
   */
  protected int poolMaximumIdleConnections = 5;

  /**
   * 在被强制返回之前，池中连接被检出（checked out）时间。单位：毫秒
   */
  protected int poolMaximumCheckoutTime = 20000;

  /**
   * 这是一个底层设置，如果获取连接花费了相当长的时间，连接池会打印状态日志并重新尝试获取一个连接（避免在误配置的情况下一直安静的失败）。单位：毫秒
   */
  protected int poolTimeToWait = 20000;

  /**
   * 这是一个关于坏连接容忍度的底层设置，作用于每一个尝试从缓存池获取连接的线程. 如果这个线程获取到的是一个坏的连接，那么这个数据源允许这个线程尝试重新获取一个新的连接，但是这个重新尝试的次数不应该超过 poolMaximumIdleConnections 与 poolMaximumLocalBadConnectionTolerance 之和。
   */
  protected int poolMaximumLocalBadConnectionTolerance = 3;

  /**
   * 发送到数据库的侦测查询，用来检验连接是否正常工作并准备接受请求。
   */
  protected String poolPingQuery = "NO PING QUERY SET";

  /**
   * 是否启用侦测查询。若开启，需要设置 poolPingQuery 属性为一个可执行的 SQL 语句（最好是一个速度非常快的 SQL 语句）
   */
  protected boolean poolPingEnabled;

  /**
   * 配置 poolPingQuery 的频率。可以被设置为和数据库连接超时时间一样，来避免不必要的侦测，默认值：0（即所有连接每一时刻都被侦测 — 当然仅当 poolPingEnabled 为 true 时适用）
   */
  protected int poolPingConnectionsNotUsedFor;

  /**
   * 期望 Connection 的类型编码，通过 {@link #assembleConnectionTypeCode(String, String, String)} 计算。
   */
  private int expectedConnectionTypeCode;

  public PooledDataSource() {
    dataSource = new UnpooledDataSource();
  }

  public PooledDataSource(UnpooledDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public PooledDataSource(String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    // 创建 UnpooledDataSource 对象
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
    // 计算  expectedConnectionTypeCode 的值
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }
  // ...
}
```
 - `dataSource` 属性，UnpooledDataSource 对象。这样，就能重用 UnpooledDataSource 的代码了。说白了，获取真正连接的逻辑，还是在 UnpooledDataSource 中实现。
 - `expectedConnectionTypeCode` 属性，调用 `#assembleConnectionTypeCode(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties)` 方法，计算 expectedConnectionTypeCode 的值。代码如下：
 ```java
   /**
   * 计算 ConnectionTypeCode 的值
   * @param url 数据库连接
   * @param username 数据库用户名
   * @param password 数据库密码
   * @return ConnectionTypeCode 值
   */
  private int assembleConnectionTypeCode(String url, String username, String password) {
    return ("" + url + username + password).hashCode();
  }
 ```
 - `state` 属性，PoolState 对象，记录池化的状态
 
### getConnection
`#getConnection(...)` 方法，获得 Connection 连接。代码如下：
```java
  @Override
  public Connection getConnection() throws SQLException {
    return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return popConnection(username, password).getProxyConnection();
  }
```
 - 调用 `#popConnection(String username, String password)` 方法，获取 `org.apache.ibatis.datasource.pooled.PooledConnection`对象，这是一个池化的连接。非常关键的一个方法
 - 调用 `PooledConnection#getProxyConnection()` 方法，返回代理的 Connection 对象。这样，每次对数据库的操作，才能被 PooledConnection 的 「invoke」 代理拦截。

#### popConnection
`#popConnection(String username, String password)` 方法，获取 PooledConnection 对象
整体流程如下图:
> From [Mybatis技术内幕](https://item.jd.com/12125531.html)
![getConnection](https://github.com/leithda/mybatis-3/blob/master/uml/getConnection.png?raw=true)


代码如下：
```java
  /**
   * 获取池化数据库连接
   * @param username 用户名
   * @param password 密码
   * @return PooledConnection 实例
   * @throws SQLException 异常
   */
  private PooledConnection popConnection(String username, String password) throws SQLException {
    boolean countedWait = false;  // 标记，获取连接时，是否进行了等待
    PooledConnection conn = null; // 最终获取到的链接对象
    long t = System.currentTimeMillis();  // 记录当前时间
    int localBadConnectionCount = 0;  // 记录当前方法，获取到坏连接的次数

    // 循环，获取可用的 Connection 连接
    while (conn == null) {
      synchronized (state) {
        // 空闲连接非空
        if (!state.idleConnections.isEmpty()) {
          // Pool has available connection
          // 通过移除的方式，获得首个空闲的连接
          conn = state.idleConnections.remove(0);
          if (log.isDebugEnabled()) {
            log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
          }
        // 无空闲连接
        } else {
          // Pool does not have available connection
          // 激活的连接数小于 poolMaximumActiveConnections
          if (state.activeConnections.size() < poolMaximumActiveConnections) {
            // Can create new connection
            // 创建新的 PooledConnection 连接对象
            conn = new PooledConnection(dataSource.getConnection(), this);
            if (log.isDebugEnabled()) {
              log.debug("Created connection " + conn.getRealHashCode() + ".");
            }
          } else {
            // Cannot create new connection
            // 获得首个激活的 PooledConnection 对象
            PooledConnection oldestActiveConnection = state.activeConnections.get(0);
            // 检查该连接是否超时
            long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
            if (longestCheckoutTime > poolMaximumCheckoutTime) {  // 检查到超时
              // Can claim overdue connection
              // 对连接超时的时间的统计
              state.claimedOverdueConnectionCount++;
              state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
              state.accumulatedCheckoutTime += longestCheckoutTime;
              // 从活跃的连接集合中移除
              state.activeConnections.remove(oldestActiveConnection);
              // 如果非自动提交的，需要进行回滚。即将原有执行中的事务，全部回滚。
              if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                try {
                  oldestActiveConnection.getRealConnection().rollback();
                } catch (SQLException e) {
                  /*
                     Just log a message for debug and continue to execute the following
                     statement like nothing happened.
                     Wrap the bad connection with a new PooledConnection, this will help
                     to not interrupt current executing thread and give current thread a
                     chance to join the next competition for another valid/good database
                     connection. At the end of this loop, bad {@link @conn} will be set as null.
                   */
                  log.debug("Bad connection. Could not roll back");
                }
              }
              // 创建新的 PooledConnection 连接对象
              conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
              conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
              conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());
              // 设置 oldestActiveConnection 为无效
              oldestActiveConnection.invalidate();
              if (log.isDebugEnabled()) {
                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
              }
            } else { // 检查到未超时
              // Must wait
              try {
                // 对等待连接进行统计。通过 countedWait 标识，在这个循环中，只记录一次。
                if (!countedWait) {
                  state.hadToWaitCount++;
                  countedWait = true;
                }
                if (log.isDebugEnabled()) {
                  log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                }
                // 记录当前时间
                long wt = System.currentTimeMillis();
                // 等待，直到超时，或 pingConnection 方法中归还连接时的唤醒
                state.wait(poolTimeToWait);
                // 统计等待连接的时间 
                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
              } catch (InterruptedException e) {
                break;
              }
            }
          }
        }
        // 获取到连接
        if (conn != null) {
          // ping to server and check the connection is valid or not
          // 通过 ping 来测试连接是否有效
          if (conn.isValid()) {
            // 如果非自动提交的，需要进行回滚。即将原有执行中的事务，全部回滚。
            // 这里又执行了一次，有点奇怪。目前猜测，是不是担心上一次适用方忘记提交或回滚事务 TODO 1001 芋艿
            if (!conn.getRealConnection().getAutoCommit()) {
              conn.getRealConnection().rollback();
            }
            // 设置获取连接的属性
            conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
            conn.setCheckoutTimestamp(System.currentTimeMillis());
            conn.setLastUsedTimestamp(System.currentTimeMillis());
            // 添加到活跃的连接集合
            state.activeConnections.add(conn);
            // 对获取成功连接的统计
            state.requestCount++;
            state.accumulatedRequestTime += System.currentTimeMillis() - t;
          } else {
            if (log.isDebugEnabled()) {
              log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
            }
            // 统计获取到坏的连接的次数
            state.badConnectionCount++;
            // 记录获取到坏的连接的次数【本方法】
            localBadConnectionCount++;
            // 将 conn 置空，那么可以继续获取
            conn = null;
            // 如果超过最大次数，抛出 SQLException 异常
            // 为什么次数要包含 poolMaximumIdleConnections 呢？相当于把激活的连接，全部遍历一次。
            if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
              if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Could not get a good connection to the database.");
              }
              throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
            }
          }
        }
      }

    }

    // 获取不到连接，抛出 SQLException 异常
    if (conn == null) {
      if (log.isDebugEnabled()) {
        log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
      }
      throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }
    return conn;
  }
```
 - 连接的情况分为以下四种：
  1. 空闲连接非空，此时使用remove获取首个空闲连接
  2. 空闲连接为空，激活的连接数小于 poolMaximumActiveConnections，创建新的连接
  3. 空闲连接为空，激活连接数大于配置，获得首个激活的连接，超时的话处理事务，创建新的连接，将超时连接置为无效
  4. 空闲连接为空，激活连接数大于配置，首个激活连接未超时，此时等待
  
### pushConnection
`#pushConnection(PooledConnection conn)` 方法，将使用完的连接，添加回连接池中。
整体流程如下图:
> From [Mybatis技术内幕](https://item.jd.com/12125531.html) 
![pushConnection](https://github.com/leithda/mybatis-3/blob/master/uml/pushConnection.png?raw=true)

代码如下:
```java
  /**
   * 将使用完的连接放回连接池
   * @param conn 连接
   * @throws SQLException 异常
   */
  protected void pushConnection(PooledConnection conn) throws SQLException {

    synchronized (state) {
      // 从激活的连接集合中移除该连接
      state.activeConnections.remove(conn);
      // 通过 ping 来测试连接是否有效
      if (conn.isValid()) { // 有效
        // 判断是否超过空闲连接上限，并且和当前连接池的标识匹配
        if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
          // 统计连接使用时长
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          // 回滚事务，避免使用方未提交或者回滚事务
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          // 创建 PooledConnection 对象，并添加到空闲的链接集合中
          PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
          state.idleConnections.add(newConn);
          newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
          newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
          // 设置原连接失效
          // 为什么这里要创建新的 PooledConnection 对象呢？避免使用方还在使用 conn ，通过将它设置为失效，万一再次调用，会抛出异常
          conn.invalidate();
          if (log.isDebugEnabled()) {
            log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
          }
          // 唤醒正在等待连接的线程
          state.notifyAll();
        } else {
          // 统计连接使用时长
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          // 回滚事务，避免使用方未提交或者回滚事务
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          // 关闭真正的数据库连接
          conn.getRealConnection().close();
          if (log.isDebugEnabled()) {
            log.debug("Closed connection " + conn.getRealHashCode() + ".");
          }
          // 设置原连接失效
          conn.invalidate();
        }
      } else {  // 失效
        if (log.isDebugEnabled()) {
          log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
        }
        // 统计获取到坏的连接的次数
        state.badConnectionCount++;
      }
    }
  }
```
 - 方法会被 `PooledConnection` 的 「invoke」 在 `methodName = close` 方法的情况下时被调用。

### forceCloseAll
`#forceCloseAll()` 方法，关闭所有的 activeConnections 和 idleConnections 的连接。代码如下：
```java
  /**
   * Closes all active and idle connections in the pool.
   */
  public void forceCloseAll() {
    synchronized (state) {
      // 计算 expectedConnectionTypeCode
      expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
      // 遍历 activeConnections ，进行关闭
      for (int i = state.activeConnections.size(); i > 0; i--) {
        try {
          // 设置为失效
          PooledConnection conn = state.activeConnections.remove(i - 1);
          conn.invalidate();
          // 回滚事务，如果有事务未提交或回滚
          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          // 关闭真实的连接
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
      // 遍历 idleConnections ，进行关闭
      for (int i = state.idleConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.idleConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource forcefully closed/removed all connections.");
    }
  }
```
 - 该方法会被 `#finalize()` 方法所调用，即当前 PooledDataSource 对象被释放时。代码如下：
 ```java
   /**
   * 类似C++的析构函数
   * @throws Throwable 异常
   */
  protected void finalize() throws Throwable {
    forceCloseAll();
    super.finalize();
  }
 ```
 
### unwrapConnection
`#unwrapConnection(Connection conn)` 方法，获取真实的数据库连接。代码如下：
```java
  /**
   * Unwraps a pooled connection to get to the 'real' connection
   *
   * @param conn - the pooled connection to unwrap
   * @return The 'real' connection
   */
  public static Connection unwrapConnection(Connection conn) {
    // 如果传入的是被代理对象
    if (Proxy.isProxyClass(conn.getClass())) {
      // 获取 InvocationHandler 对象
      InvocationHandler handler = Proxy.getInvocationHandler(conn);
      // 如果是 PooledConnection 对象，则获取真实的连接
      if (handler instanceof PooledConnection) {
        return ((PooledConnection) handler).getRealConnection();
      }
    }
    return conn;
  }
```

<br/><br/><br/><br/>
**未完待续**