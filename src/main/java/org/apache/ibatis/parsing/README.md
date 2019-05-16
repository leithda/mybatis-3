# 解析器

## 概述

> 解析器模块主要提供两个功能
>
> - 对XPath进行封装，为mybatis初始化时解`mybatis-config.xml`配置文件及映射文件提供支持
> - 处理动态sql中的占位符

## `XPathParser`

> 基于Java XPath解析器，用于解析xml文件。属性如下：

```java
// XPathParser.java

/**
 * XML Document 对象
 */
private final Document document;
/**
 * 是否校验
 */
private boolean validation;
/**
 * XML 实体解析器
 */
private EntityResolver entityResolver;
/**
 * 变量 Properties 对象
 */
private Properties variables;
/**
 * Java XPath 对象
 */
private XPath xpath;
```

- `document` 	xml解析后生成的`org.w3c.dom.Document`对象

- `validation`    是否校验xml，一般情况下为`true`

- `entityResolver`    `org.xml.sax.EntityResolver` 对象，XML 实体解析器。默认情况下，对 XML 进行校验时，会基于 XML 文档开始位置指定的 DTD 文件或 XSD 文件。例如说，解析 `mybatis-config.xml` 配置文件时，会加载 `http://mybatis.org/dtd/mybatis-3-config.dtd` 这个 DTD 文件。但是，如果每个应用启动都从网络加载该 DTD 文件，势必在弱网络下体验非常下，甚至说应用部署在无网络的环境下，还会导致下载不下来，那么就会出现 XML 校验失败的情况。所以，在实际场景下，MyBatis 自定义了 EntityResolver 的实现，达到使用**本地** DTD 文件，从而避免下载**网络** DTD 文件的效果

- `xpath`    `javax.xml.xpath.XPath`对象，用于查询xml中的节点和元素。相关学习资料： [Java XPath解析器 - 解析XML文档 - Java XML教程™](https://www.yiibai.com/java_xml/java_xpath_parse_document.html)

- `variables`    `Properties`对象，用来替换需要动态配置的属性值。例如：

  ```xml
  <dataSource type="POOLED">
    <property name="driver" value="${driver}"/>
    <property name="url" value="${url}"/>
    <property name="username" value="${username}"/>
    <property name="password" value="${password}"/>
  </dataSource>
  ```

  - `variables`的来源，可以在常用的Java Properties文件中配置，也可以使用`mybatis`的`<property />`标签中配置。例如：

    ```xml
    <properties resource="org/mybatis/example/config.properties">
      <property name="username" value="dev_user"/>
      <property name="password" value="F2Fa3!33TYyg"/>
    </properties>
    ```

### 构造方法

> XPath的构造方法有很多，大多相似，查看其中的一个

```java
  /**
   * 构造方法
   * @param xml XML文件路径
   * @param validation 是否校验
   * @param variables 变量 Properties 对象
   * @param entityResolver XML 实体解析器
   */
  public XPathParser(String xml, boolean validation, Properties variables, EntityResolver entityResolver) {
    commonConstructor(validation, variables, entityResolver);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }
```

- 调用`#commonConstructor(validation, variables, entityResolver);`方法，公共的构造方法逻辑，代码如下

  ```java
    /**
     * 公共构造方法
     * @param validation 是否校验
     * @param variables Properties 变量
     * @param entityResolver XML 实体解析器
     */
    private void commonConstructor(boolean validation, Properties variables, EntityResolver entityResolver) {
      this.validation = validation;
      this.entityResolver = entityResolver;
      this.variables = variables;
      XPathFactory factory = XPathFactory.newInstance();
      this.xpath = factory.newXPath();
    }
  ```

- 调用`#createDocument(InputSource inputSource)` 方法，将 XML 文件解析成 Document 对象。代码如下：

  ````java
  /**
     * 创建 Document 对象
     * @param inputSource  XML 的 InputSource 对象
     * @return org.w3c.dom.Document
     */
    private Document createDocument(InputSource inputSource) {
      // important: this must only be called AFTER common constructor
      try {
        // 1. 创建 DocumentBuilderFactory 对象
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(validation);  // 设置是否校验xml
  
        factory.setNamespaceAware(false);
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(false);
        factory.setCoalescing(false);
        factory.setExpandEntityReferences(true);
  
        // 2. 创建 DocumentBuilder 对象
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(entityResolver);  // 设置实体解析器
        builder.setErrorHandler(new ErrorHandler() {
          @Override
          public void error(SAXParseException exception) throws SAXException {
            throw exception;
          }
  
          @Override
          public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
          }
  
          @Override
          public void warning(SAXParseException exception) throws SAXException {
          }
        });
        // 3. 解析xml文件
        return builder.parse(inputSource);
      } catch (Exception e) {
        throw new BuilderException("Error creating document instance.  Cause: " + e, e);
      }
    }
  ````

  

### eval*方法族

> XPathParser 提供了一系列的 `#eval*` 方法，用于获得 Boolean、Short、Integer、Long、Float、Double、String、Node 类型的元素或节点的“值”。当然，虽然方法很多，但是都是基于 `#evaluate(String expression, Object root, QName returnType)` 方法，代码如下：

```java
// XPathParser.java

/**
* 获得指定元素或节点的值
*
* @param expression 表达式
* @param root 指定节点
* @param returnType 返回类型
* @return 值
*/
private Object evaluate(String expression, Object root, QName returnType) {
  try {
      return xpath.evaluate(expression, root, returnType);
  } catch (Exception e) {
      throw new BuilderException("Error evaluating XPath.  Cause: " + e, e);
  }
}
```

  

#### eval元素

> eval元素的方法，用于获得 Boolean、Short、Integer、Long、Float、Double、String 类型的**元素**的值。我们以 `#evalString(Object root, String expression)` 方法为例子，代码如下：

```java
// XPathParser.java

public String evalString(Object root, String expression) {
    // <1> 获得值
    String result = (String) evaluate(expression, root, XPathConstants.STRING);
    // <2> 基于 variables 替换动态值，如果 result 为动态值
    result = PropertyParser.parse(result, variables);
    return result;
}
```

- `<1>`处，调用`#evaluate(String expression, Object root, QName returnType)`方法获得值，其中，`returnType`传入的是`XPathConstants.STRING`，表示返回的是`String`类型。
- `<2>`处，调用`#PropertyParser#parse(String string, Properties variables)`方法，基于`variables`替换**动态值**。这就是`mybatis`实现动态值替换的方法

#### eval节点

> eval元素的方法，用户获取`Node`类型的**节点**的值，代码如下：

```java
// XPathParser.java

public List<XNode> evalNodes(String expression) { // Node 数组
    return evalNodes(document, expression);
}

public List<XNode> evalNodes(Object root, String expression) { // Node 数组
    // <1> 获得 Node 数组
    NodeList nodes = (NodeList) evaluate(expression, root, XPathConstants.NODESET);
    // <2> 封装成 XNode 数组
    List<XNode> xnodes = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++) {
        xnodes.add(new XNode(this, nodes.item(i), variables));
    }
    return xnodes;
}

public XNode evalNode(String expression) { // Node 对象
    return evalNode(document, expression);
}

public XNode evalNode(Object root, String expression) { // Node 对象
    // <1> 获得 Node 对象
    Node node = (Node) evaluate(expression, root, XPathConstants.NODE);
    if (node == null) {
        return null;
    }
    // <2> 封装成 XNode 对象
    return new XNode(this, node, variables);
}
```

- `<1>`处，返回结果有Node**对象**和**数组**两种情况，根据参数`expression`需要，获取的节点不同

- `<2>`处，最终结果会将Node封装成`org.apache.ibatis.parsing.XNode`对象，主要为了**动态值替换**.例如：

  ```java
  // XNode.java
  
  public String evalString(String expression) {
      return xpathParser.evalString(node, expression);
  }
  ```

## XMLMapperEntityResolver

> `org.apache.ibatis.builder.xml.XMLMapperEntityResolver` ，实现 `EntityResolver` 接口，myBatis 自定义 `EntityResolver `实现类，用于加载本地的 `mybatis-3-config.dtd` 和 `mybatis-3-mapper.dtd` 这两个 DTD 文件。代码如下：

```java
// XMLMapperEntityResolver.java

public class XMLMapperEntityResolver implements EntityResolver {

    private static final String IBATIS_CONFIG_SYSTEM = "ibatis-3-config.dtd";
    private static final String IBATIS_MAPPER_SYSTEM = "ibatis-3-mapper.dtd";
    private static final String MYBATIS_CONFIG_SYSTEM = "mybatis-3-config.dtd";
    private static final String MYBATIS_MAPPER_SYSTEM = "mybatis-3-mapper.dtd";

    /**
     * 本地 mybatis-config.dtd 文件
     */
    private static final String MYBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/mybatis-3-config.dtd";
    /**
     * 本地 mybatis-mapper.dtd 文件
     */
    private static final String MYBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd";

    /**
     * Converts a public DTD into a local one
     *
     * @param publicId The public id that is what comes after "PUBLIC"
     * @param systemId The system id that is what comes after the public id.
     * @return The InputSource for the DTD
     *
     * @throws org.xml.sax.SAXException If anything goes wrong
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
        try {
            if (systemId != null) {
                String lowerCaseSystemId = systemId.toLowerCase(Locale.ENGLISH);
                // 本地 mybatis-config.dtd 文件
                if (lowerCaseSystemId.contains(MYBATIS_CONFIG_SYSTEM) || lowerCaseSystemId.contains(IBATIS_CONFIG_SYSTEM)) {
                    return getInputSource(MYBATIS_CONFIG_DTD, publicId, systemId);
                // 本地 mybatis-mapper.dtd 文件
                } else if (lowerCaseSystemId.contains(MYBATIS_MAPPER_SYSTEM) || lowerCaseSystemId.contains(IBATIS_MAPPER_SYSTEM)) {
                    return getInputSource(MYBATIS_MAPPER_DTD, publicId, systemId);
                }
            }
            return null;
        } catch (Exception e) {
            throw new SAXException(e.toString());
        }
    }

    private InputSource getInputSource(String path, String publicId, String systemId) {
        InputSource source = null;
        if (path != null) {
            try {
                // 创建 InputSource 对象
                InputStream in = Resources.getResourceAsStream(path);
                source = new InputSource(in);
                // 设置  publicId、systemId 属性
                source.setPublicId(publicId);
                source.setSystemId(systemId);
            } catch (IOException e) {
                // ignore, null is ok
            }
        }
        return source;
    }
}
```

## GenericTokenParser

> `org.apache.ibatis.parsing.GenericTokenParser`，通用的Token解析器，代码如下：

```java
//GenericTokenParser.java

public class GenericTokenParser {

  // 开始的Token字符串
  private final String openToken;
  // 结束的Token字符串
  private final String closeToken;
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 解析 Token 字符串<br>
   *
   * 解析以 openToken 开始，以 closeToken 结束的 Token ，并提交给 handler 进行处理
   * @param text 字符串
   * @return Token
   */
  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    // 寻找开始的Token字符串
    int start = text.indexOf(openToken);
    if (start == -1) { // 找不到直接返回
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0; // 起始查找位置
    final StringBuilder builder = new StringBuilder();  // 保存结果
    StringBuilder expression = null;  // 匹配到 openToken 和 closeToken 之间的表达式

    // 循环匹配
    while (start > -1) {
      // 转义字符
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        // 因为 openToken 前面一个位置是 \ 转义字符，所以忽略 \
        // 添加 [offset, start - offset - 1] 和 openToken 的内容，添加到 builder 中
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      // 非转义字符
      } else {
        // found open token. let's search close token.
        // 创建 expression 对象
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        // 添加 offset 和 openToken 之间的内容，添加到 builder 中
        builder.append(src, offset, start - offset);
        // 修改 offset
        offset = start + openToken.length();
        // 寻找结束的 closeToken 的位置
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          // 转义
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            // 因为 endToken 前面一个位置是 \ 转义字符，所以忽略 \
            // 添加 [offset, end - offset - 1] 和 endToken 的内容，添加到 builder 中
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          // 非转义
          } else {
            // 添加 [offset, end - offset] 的内容，添加到 builder 中
            expression.append(src, offset, end - offset);
            offset = end + closeToken.length();
            break;
          }
        }
        // 拼接内容
        if (end == -1) {
          // close token was not found.
          // token 未找到,直接拼接
          builder.append(src, start, src.length - start);
          // 修改offset
          offset = src.length;
        } else {
          // <x> closeToken 找到，将 expression 提交给 handler 处理 ，并将处理结果添加到 builder 中
          builder.append(handler.handleToken(expression.toString()));
          // 修改 offset
          offset = end + closeToken.length();
        }
      }
      // 继续，寻找开始的 openToken 的位置
      start = text.indexOf(openToken, offset);
    }
    // 拼接剩余的部分
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
```

- 方法只有一个，就是`#parse(String text)`，循环处理，解析以`openToken`开头，以`closeToken`结束的Token，交给handle进行处理。
- 关于`hadnle`这个`TokenHandle`，TokenHandle是一个接口，`handle`对token进行特定处理，所以这个类叫做`通用`

## PropertyParser

> `org.apache.ibatis.parsing.PropertyParser` ，动态属性解析器。代码如下：

```java
// PropertyParser.java

public class PropertyParser {

    // ... 省略部分无关的

    private PropertyParser() { // <1>
        // Prevent Instantiation
    }

    public static String parse(String string, Properties variables) { // <2>
        // <2.1> 创建 VariableTokenHandler 对象
        VariableTokenHandler handler = new VariableTokenHandler(variables);
        // <2.2> 创建 GenericTokenParser 对象
        GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
        // <2.3> 执行解析
        return parser.parse(string);
    }
}
```

- `<1>`，构造私有，因为这个类是一个静态方法的工具类。
- `<2>`，基于`variables`变量，替换`string`字符串中的动态属性，并返回结果
  - `<2.1>`， 创建`VariablesTokenHandle`对象
  - `<2.2>`，创建`GenericTokenParser`对象
    - 可以看到，`openToken`=`${`，`closeToken`=`}`，就是上述的`${username}`和`${password}`
  - `<2.3>`，调用`GenericTokenParser#parse(String text)`方法，执行解析。

## TokenHandler

> `org.apache.ibatis.parsing.TokenHandler` ，Token 处理器接口。代码如下：

```java
// TokenHandler.java

public interface TokenHandler {

    /**
     * 处理 Token
     *
     * @param content Token 字符串
     * @return 处理后的结果
     */
    String handleToken(String content);

}
```

TokenHandler有四个子类实现，如下图所示

![TokenHandler](https://github.com/leithda/mybatis-3/blob/master/uml/TokenHandler.png?raw=true)

### VariableTokenHandler

> VariableTokenHandler，是`PropertyParser`的静态内部类

#### 构造方法

```java
// PropertyParser.java

private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
/**
 * The special property key that indicate whether enable a default value on placeholder.
 * <p>
 *   The default value is {@code false} (indicate disable a default value on placeholder)
 *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
 * </p>
 * @since 3.4.2
 */
public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

/**
 * The special property key that specify a separator for key and default value on placeholder.
 * <p>
 *   The default separator is {@code ":"}.
 * </p>
 * @since 3.4.2
 */
public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

private static final String ENABLE_DEFAULT_VALUE = "false";
private static final String DEFAULT_VALUE_SEPARATOR = ":";

// VariableTokenHandler 类里

/**
 * 变量 Properties 对象
 */
private final Properties variables;
/**
 * 是否开启默认值功能。默认为 {@link #ENABLE_DEFAULT_VALUE}
 */
private final boolean enableDefaultValue;
/**
 * 默认值的分隔符。默认为 {@link #KEY_DEFAULT_VALUE_SEPARATOR} ，即 ":" 。
 */
private final String defaultValueSeparator;

private VariableTokenHandler(Properties variables) {
    this.variables = variables;
    this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
    this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
}

private String getPropertyValue(String key, String defaultValue) {
    return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
}
```

- `variables`属性，变量`Properties`对象
- `enableDefaultValue`属性，是否开启默认值功能，默认为`ENABLE_DEFAULT_VALUE`，即**不开启**

，想要开启，可以使用如下配置：

```xml
<properties resource="org/mybatis/example/config.properties">
  <!-- ... -->
  <property name="org.apache.ibatis.parsing.PropertyParser.enable-default-value" value="true"/> <!-- Enable this feature -->
</properties>
```

- `defaultValueSeparator`属性，默认值的分隔符。默认为`DEFAULT_VALUE_SEPARATOR`即`:`,想要修改，可以做如下配置

```xml
<properties resource="org/mybatis/example/config.properties">
  <!-- ... -->
  <property name="org.apache.ibatis.parsing.PropertyParser.default-value-separator" value="?:"/> <!-- Change default value of separator -->
</properties>
```

#### handleToken方法

```java
/**
 * 对 Token 字符串的特定处理
 * @param content Token 字符串
 * @return 处理后的结果
 */
@Override
public String handleToken(String content) {
  if (variables != null) {
    String key = content;
    // 如果开启默认值功能
    if (enableDefaultValue) {
      // 寻找默认值
      final int separatorIndex = content.indexOf(defaultValueSeparator);
      String defaultValue = null;
      if (separatorIndex >= 0) {
        key = content.substring(0, separatorIndex);
        defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
      }
      // 如果有默认值,优先替换
      if (defaultValue != null) {
        return variables.getProperty(key, defaultValue);
      }
    }
    // 未开启默认值功能，直接替换
    if (variables.containsKey(key)) {
      return variables.getProperty(key);
    }
  }
  // 无 variables 直接返回
  return "${" + content + "}";
}
```