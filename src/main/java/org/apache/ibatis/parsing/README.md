## 解析器模块

### 类和说明

| 类                                                           | 说明                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [`XPathParser`](https://github.com/leithda/mybatis-3/blob/master/src/main/java/org/apache/ibatis/parsing/XPathParser.java) | **解析器对象**,主要用于解析`mybatis`的配置以及`mapper`的`xml`文件 |
| [`XMLMapperEntityResolver`](https://github.com/leithda/mybatis-3/blob/master/src/main/java/org/apache/ibatis/builder/xml/XMLMapperEntityResolver.java) | **EntityResolver实现类**,实现了`EntityResolver`接口，用于加载本地的`mybatis-3-config.dtd` 和 `mybatis-3-mapper.dtd` 这两个 DTD 文件,避免弱网络状况下使用`mybatis`加载问题 |
| [`GenericTokenParser`](https://github.com/leithda/mybatis-3/blob/master/src/main/java/org/apache/ibatis/parsing/GenericTokenParser.java) | **通用Token解析器**,解析特定占位符交给`TokenHandler`进行特定处理 |
| [`PropertyParser`](https://github.com/leithda/mybatis-3/blob/master/src/main/java/org/apache/ibatis/parsing/PropertyParser.java) | **动态属性解析器**,使用`VariableTokenHandler`对动态属性进行处理 |
| [`TokenHandler`](https://github.com/leithda/mybatis-3/blob/master/src/main/java/org/apache/ibatis/parsing/TokenHandler.java) | **TokenHandler**,Token处理接口，定义了`handleToken`方法对`toekn`进行处理。 |
| [`VariableTokenHandler`](<https://github.com/leithda/mybatis-3/blob/master/src/main/java/org/apache/ibatis/parsing/PropertyParser.java>) | **动态变量Token处理器**,`PropertyParser`内部类，实现了`handleToken`对`token`进行处理 |

### 主要方法

#### `XPathPrser`

- `XPathPrser$commonConstructor`

  公共的构造方法，初始化变量、通过`XPathFactory`生成`XPath`对象

- `XPathPrser$createDocument`

  创建`Document`对象

  1.  创建 `DocumentBuilderFactory` 对象
  2.  创建 `DocumentBuilder` 对象
  3.  解析`xml`文件，生成`Document`对象

- `XPathPrser$eval*`

  获取`xml`节点或者元素的值,其中`evalString`会基于`variables`替换动态值。底层是`XPath`的`evaluate`方法

#### `XmlMapperEntityResolver`

- `XMLMapperEntityResolver$resolveEntity`

  覆写`EntityResolver`方法，将下载`dtd`文件转换为本地加载

#### `GenericTokenParser`

- `GenericTokenParser#parse`

  查找`Token`并使用`TokenHandle`进行对`Token`的处理

#### `PropertyParser`

- `PropertyParser#parse`

  调用`GenericTokenParser#parse`进行`token`提取，注入`VariableTokenHandler`对`token`进行处理

#### `VariableTokenHandler`

- `VariableTokenHandler#handleToken`

  `Token`处理方法，根据`variables`对`token`(如*`${name}`*)进行变量替换