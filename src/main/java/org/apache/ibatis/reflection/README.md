# Reflector
`org.apache.ibatis.reflection.Reflector` 反射器类，每个 Reflector 对应一个类。Reflector 会缓存反射操作需要的类的信息，例如：构造方法、属性名、setting / getting 方法等等。代码如下:

```java
public class Reflector {

  /**
   * 对应的类
   */
  private final Class<?> type;
  /**
   * 可读属性集合
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性集合
   */
  private final String[] writablePropertyNames;
  /**
   * 属性对应的setting方法映射
   *  key 属性名称
   *  value Invoker对象
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * 属性对应的getting方法映射
   *  key 属性名称
   *  value Invoker对象
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * 属性对应的setting方法的参数值类型
   * key 属性名称
   * value 返回值类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();

  /**
   * 属性对应的getting方法的返回值
   * key 属性名称
   * value 返回值类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 默认构造方法
   */
  private Constructor<?> defaultConstructor;

  /**
   * 不区分大小写的属性集合
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    // 设置对应的类
    type = clazz;
    // <1> 初始化 defaultConstructor
    addDefaultConstructor(clazz);
    // <2> // 初始化 getMethods 和 getTypes ，通过遍历 getting 方法
    addGetMethods(clazz);
    // <3> // 初始化 setMethods 和 setTypes ，通过遍历 setting 方法。
    addSetMethods(clazz);
    // <4> // 初始化 getMethods + getTypes 和 setMethods + setTypes ，通过遍历 fields 属性。
    addFields(clazz);
    // <5> 初始化 readablePropertyNames、writeablePropertyNames、caseInsensitivePropertyMap 属性
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    writablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  // ... 省略一些方法
```
- type 属性，每个Reflector对应的类
- defaultConstructor 属性，默认**无参**构造方法。在`<1>`处初始化
- getMethods、getTypes 属性，分别为属性对应的getter方法和返回值，在`<2>`处初始化
- setMethods、setTypes 属性，分别为属性对应的setter方法和返回值，在`<3>`处初始化
- `<4>`处，通过遍历 fiedls 属性初始化 `getMethods`+`getTypes`和`setMethods`+`setTypes`
- `<5>`处，初始化`readablePropertyNames`、`writablePropertyNames`、`caseInsensitivePropertyMap` 属性


## addDefaultConstructor
`#addDefaultConstructor(Class<?> clazz)`方法，查找默认无参构造方法，代码如下:
```java
  /**
   * 遍历构造方法，查找默认无参构造函数
   * @param clazz 类类型
   */
  private void addDefaultConstructor(Class<?> clazz) {
    // 获取所有构造方法
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    for (Constructor<?> constructor : consts) {
      if (constructor.getParameterTypes().length == 0) {  // 遍历所有构造方法查找无参构造方法
        this.defaultConstructor = constructor;
      }
    }
  }
```

## addGetMethods
`#addGetMethods(Class<?> cls)`方法，初始化`getMethods`和`getTypes`，通过遍历getter方法，代码如下:
```java
/**
   * 查找get方法
   * @param cls 类类型
   */
  private void addGetMethods(Class<?> cls) {
    // <1> 属性与其 getting 方法的映射。
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // <2> 获取所有方法
    Method[] methods = getClassMethods(cls);
    // <3> 遍历所有方法
    for (Method method : methods) {
      // <3.1> 参数大于0个，不是get方法，忽略
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      // <3.2> 以get或者is开头的方法，说明是get方法
      String name = method.getName();
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
        // <3.3> 获得属性
        name = PropertyNamer.methodToProperty(name);
        // <3.4> 添加到 conflictingGetters 中
        addMethodConflict(conflictingGetters, name, method);
      }
    }
    // <4> 解决 getter 冲突方法
    resolveGetterConflicts(conflictingGetters);
  }
```
 - `<1>`处，定义数组，用于保存含有冲突的所有getter方法。因为父类和子类可能拥有相同getter,所以value是数组
 - `<2>`处，调用`#getClassMethods(Class<?> cls)`方法获取所有方法
 - `<3>`处，遍历所有方法，挑选符合的getter方法，添加到conflictingGetters中
 	- `<3.1>`处，参数大于0的不是getter方法，忽略
 	- `<3.2>`处，使用get或者is开头的方法，是getter方法
 	- `<3.3>`处，调用`#PropertyNamer.methodToProperty(String name)`方法获得属性名
 	- `<3.4>`处，调用`addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method)`方法，将getter方法添加到`conflictingGetters`中
 - `<4>`处，调用`#resolveSetterConflicts(Map<String, List<Method>> conflictingSetters)`方法解决冲突

### getClassMethods
`#getClassMethods(Class<?> cls)`，获得所有方法，代码如下:
```java
  /**
   * 获得所有方法
   * @param cls 类类型
   * @return 方法数组
   */
  private Method[] getClassMethods(Class<?> cls) {
    // 每个方法签名与方法的映射
    Map<String, Method> uniqueMethods = new HashMap<>();
    // 循环类，直到父类为Object.
    Class<?> currentClass = cls;

    while (currentClass != null && currentClass != Object.class) {
      // <1> 记录当前类定义的方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods - 我们也需要查找接口中的方法
      // because the class may be abstract 因为这个类可能是抽象类
      // <2> 记录接口中定义的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      // 获得父类
      currentClass = currentClass.getSuperclass();
    }

    // 转换成数组返回
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }
```
 - `<1>`和`<2>`处，会调用`#addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods)`方法，添加方法到数组`uniqueMethods`中，代码如下:
```java
  /**
   * 添加方法数组到uniqueMethods
   * @param uniqueMethods uniqueMethods
   * @param methods 方法数组
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) {  // 忽略 bridge 方法，参见 https://www.zhihu.com/question/54895701/answer/141623158 文章
        // <3> 获取方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 当uniqueMethods 不存在 当前方法签名时，进行添加
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
```
 - `<3>`处，会调用`#getSignature(Method method)`方法获取方法的签名(唯一标识)
```java
 /**
   * 获取方法签名
   * @param method 方法
   * @return 签名 返回类型#方法名(:参数0,参数1)
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }
```

### resolveGetterConflicts
`#resolveGetterConflicts(Map<String, List<Method>> conflictingGetters)`方法，解决getter冲突，最终一个属性只有一个getter方法。代码如下：
```java
  /**
   * 解决getter冲突方法
   * @param conflictingGetters getter方法数组
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历每个属性，查找最匹配的方法，因为子类可覆写父类的方法，所以一个属性可以对应多个getter方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 最匹配的方法
      Method winner = null;
      String propName = entry.getKey();
      for (Method candidate : entry.getValue()) {
        // winner为空，说明candidate是最匹配的方法
        if (winner == null) {
          winner = candidate;
          continue;
        }
        // <1> 基于返回类型的比较
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        // 类型相同
        if (candidateType.equals(winnerType)) {
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        // 判断 candidate 的返回类型是否时 winner 返回类型相同或者是其超类或者超接口。是返回true
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        // 如果 winner 返回类型是 candidate 返回类型的 超类或者超接口,说明 candidate是子类 覆写了winner的getter方法.返回了更具体的返回类型。
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      // <2> 添加到 getMethod 和 getTypes 中
      addGetMethod(propName, winner);
    }
  }
```
 - `<1>`处，基于返回类型比较，因为子类复写父类方法时，可以返回更为具体的类型。所以使用返回值更为准确的getter方法
 - `<2>`处，调用`#addGetMethod(String name, Method method)`方法，填充getMethods和getTypes
```java
  /**
   * 添加Get方法到 getMethods 和 getTypes 中
   * @param name 属性
   * @param method 方法
   */
  private void addGetMethod(String name, Method method) {
    // <2.1> 判断方法名是否合法
    if (isValidPropertyName(name)) {
      // <2.2> 添加方法到 getMethod 中
      getMethods.put(name, new MethodInvoker(method));
      // <2.3> 添加到 getTypes 中
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      getTypes.put(name, typeToClass(returnType));
    }
  }
```
 - `<2.1>`处，调用`#isValidPropertyName(String name)`方法判断方法名是否合法,代码如下:
```java
  /**
   * 判断方法名是否合法
   * @param name 方法名称
   * @return 是否合法
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }
```
 - `<2.2>`处，添加到`getMethods`中,可以看到一个MethodInvoker类
 - `<2.3>`处，添加到`getTypes`中
 	- 此处可以看到一个`TypeParameterResolver`类
 	- `#typeToClass(Type src)`方法，获得`java.lang.reflect.Type`对应的类，代码如下:
```java
  /**
   * 获得 Type 对应的类
   * @param src type
   * @return 对应的类类型
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 普通类型，直接使用类
    if (src instanceof Class) {
      result = (Class<?>) src;
    // 泛型类型，使用泛型
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    // 泛型数组，获得具体类
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    // 都不符合，使用 Object 类
    if (result == null) {
      result = Object.class;
    }
    return result;
  }
```

## addSetMethods
`#addSetMethods(Class<?> cls)`方法，通过遍历setter方法，初始化setMethods和setTypes。代码如下:
```java
  /**
   * 初始化 setMethod 和 setTypes
   * @param cls 类类型
   */
  private void addSetMethods(Class<?> cls) {
    // 属性与其 setter 方法的映射
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获取所有方法
    Method[] methods = getClassMethods(cls);
    // 遍历方法
    for (Method method : methods) {
      String name = method.getName();
      // 方法名为set开头，且方法名长度大于3
      if (name.startsWith("set") && name.length() > 3) {
        // 方法只有一个参数
        if (method.getParameterTypes().length == 1) {
          // 获得属性
          name = PropertyNamer.methodToProperty(name);
          // 添加到 conflictingSetters 中
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    // <2> 解决 setter 冲突问题
    resolveSetterConflicts(conflictingSetters);
  }
```
 - 逻辑和`#addGetMethods`方法类似，差异主要在`<1>`和`<2>`处，`<1>`不再赘述，主要看`<2>`
```java
  /**
   * 解决 setter 方法冲突
   * @param conflictingSetters setter 方法集合
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    // 遍历每个属性，查找其最匹配的方法。因为子类可以覆写父类的方法，所以一个属性，可能对应多个 setting 方法
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      // <1> 遍历属性对应的 setting 方法
      for (Method setter : setters) {
        Class<?> paramType = setter.getParameterTypes()[0];
        // 和 getterType 相同，直接使用
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            // 选择一个更加匹配的
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      // <2> 添加到 setMethods 和 setTypes 中
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }
```
 - 其中，调用`#pickBetterSetter(Method setter1, Method setter2, String property)`方法，选出更加匹配的setter方法,代码如下:
```java
  /**
   * 获取更加匹配的 setter
   * @param setter1 setter1
   * @param setter2 setter2
   * @param property 属性
   * @return 更加匹配的方法
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    // 根据参数类型进行匹配
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      // 方法1的参数是方法2参数的父类，返回方法2
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }
```

## addFields
`#addFields(Class<?> clazz)` 方法，初始化 `getMethods` + `getTypes` 和 `setMethods` + `setTypes` ，通过遍历 fields 属性。实际上，它是 `#addGetMethods(...)` 和 `#addSetMethods(...)` 方法的补充，因为有些 field ，不存在对应的 setting 或 getting 方法，所以直接使用对应的 field ，而不是方法。代码如下：
```java
  /**
   * 添加属性
   * 有些 field 没有 setter 和 getter, 为这些field 补充
   * @param clazz 类类型
   */
  private void addFields(Class<?> clazz) {
    // 获取所有的 filed
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      // 如果 setMethods 中没有这个属性
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        // 获取字段修改权限
        int modifiers = field.getModifiers();
        // final 和 static属性 无法使用set赋值
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          // 添加到 setMethods 和 setTypes 中
          addSetField(field);
        }
      }
      // 如果 getMethods 中没有这个属性
      if (!getMethods.containsKey(field.getName())) {
        // 添加到 getMethods 和 getTypes 中
        addGetField(field);
      }
    }
    // 递归 处理父类
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }
```
 - `<1>`处，如果`setMethods`中没有这个属性，调用`#addGetField(Field field)`方法，添加到`getMethods`和`getTypes`中,代码如下:
```java
  /**
   * 添加属性字段 getter 方法
   * @param field 属性字段
   */
  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }
```
 - **注意**,此处创建的是`GetFieldInvoker`

 - `<2>`出，如果`getMethods`中没有这个属性，同理，添加之

## 其他方法
> Reflector 中还有其他方法，感兴趣自己阅读一下

# ReflectorFactory
`org.apache.ibatis.reflection.ReflectorFactory` ，Reflector 工厂接口，用于创建和缓存 Reflector 对象。代码如下：
```java
/**
 * 反射器工厂接口
 */
public interface ReflectorFactory {

  /**
   * @return 是否缓存 Reflector 对象
   */
  boolean isClassCacheEnabled();

  /**
   * 设置是否缓存 Reflector 对象
   * @param classCacheEnabled 是否缓存
   */
  void setClassCacheEnabled(boolean classCacheEnabled);

  /**
   * 获取 Reflector 对象
   * @param type 指定类
   * @return Reflector 对象
   */
  Reflector findForClass(Class<?> type);
}
```

## DefaultReflectorFactory
`org.apache.ibatis.reflection.DefaultReflectorFactory` ，实现 ReflectorFactory 接口，默认的 ReflectorFactory 实现类。代码如下：
```java
/**
 * Reflector 工厂的默认实现
 */
public class DefaultReflectorFactory implements ReflectorFactory {
  /**
   * 是否缓存
   */
  private boolean classCacheEnabled = true;

  /**
   * Reflector 的缓存映射
   *
   * key 类
   * value Reflector 对象
   */
  private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

  public DefaultReflectorFactory() {
  }

  @Override
  public boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }

  @Override
  public void setClassCacheEnabled(boolean classCacheEnabled) {
    this.classCacheEnabled = classCacheEnabled;
  }

  @Override
  public Reflector findForClass(Class<?> type) {
    // 开启缓存，从缓存中获取
    if (classCacheEnabled) {
      // synchronized (type) removed see issue #461
      return reflectorMap.computeIfAbsent(type, Reflector::new);
    } else {
      return new Reflector(type);
    }
  }

}
```

# Invoker
`org.apache.ibatis.reflection.invoker.Invoker` ，调用者接口。代码如下：
```java
public interface Invoker {
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  Class<?> getType();
}
```
 - `#invoke(Object target, Object[] args)`方法，执行一次调用，具体调用方法由子类完成

## GetFieldInvoker
`org.apache.ibatis.reflection.invoker.GetFieldInvoker`，实现 Invoker 接口，获得 Field 调用者。代码如下： 
```java
public class GetFieldInvoker implements Invoker {
  private final Field field;

  public GetFieldInvoker(Field field) {
    this.field = field;
  }

  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException {
    try {
      return field.get(target);
    } catch (IllegalAccessException e) {
      if (Reflector.canControlMemberAccessible()) {
        field.setAccessible(true);
        return field.get(target);
      } else {
        throw e;
      }
    }
  }

  @Override
  public Class<?> getType() {
    return field.getType();
  }
}
```

## SetFieldInvoker
`org.apache.ibatis.reflection.invoker.SetFieldInvoker` ，实现 Invoker 接口，设置 Field 调用者。代码如下：
```java
public class SetFieldInvoker implements Invoker {
  private final Field field;

  public SetFieldInvoker(Field field) {
    this.field = field;
  }

  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException {
    try {
      field.set(target, args[0]);
    } catch (IllegalAccessException e) {
      if (Reflector.canControlMemberAccessible()) {
        field.setAccessible(true);
        field.set(target, args[0]);
      } else {
        throw e;
      }
    }
    return null;
  }

  @Override
  public Class<?> getType() {
    return field.getType();
  }
}
```

## MethodInvoker
`org.apache.ibatis.reflection.invoker.MethodInvoker` ，实现 Invoker 接口，指定方法的调用器。代码如下：
```java
public class MethodInvoker implements Invoker {

  /**
   * 类型
   */
  private final Class<?> type;
  /**
   * 指定方法
   */
  private final Method method;

  public MethodInvoker(Method method) {
    this.method = method;

    // 方法参数为1,一般是setter方法,设置类型为方法参数类型
    if (method.getParameterTypes().length == 1) {
      type = method.getParameterTypes()[0];
    } else {
      // 否则,一般是getter方法,设置类型为返回类型
      type = method.getReturnType();
    }
  }

  /**
   * 执行指定方法
   * @param target 方法执行类
   * @param args 方法参数
   * @return 方法返回值
   */
  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
    try {
      return method.invoke(target, args);
    } catch (IllegalAccessException e) {
      if (Reflector.canControlMemberAccessible()) {
        method.setAccessible(true);
        return method.invoke(target, args);
      } else {
        throw e;
      }
    }
  }

  @Override
  public Class<?> getType() {
    return type;
  }
}
```

# ObjectFactory
`org.apache.ibatis.reflection.factory.ObjectFactory` ，Object 工厂接口，用于创建指定类的对象。代码如下：
```java
public interface ObjectFactory {

  /**
   * 设置属性
   * @param properties 配置属性
   */
  void setProperties(Properties properties);

  /**
   * 创建对象,使用默认无参构造方法
   * @param type 类类型
   * @param <T> 类型
   * @return 实例
   */
  <T> T create(Class<T> type);

  /**
   * 使用指定构造方法和参数
   * @param type 类类型
   * @param constructorArgTypes 构造方法的参数类型
   * @param constructorArgs 构造方法参数
   * @return 实例
   */
  <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

  /**
   * 判断指定类是否是集合类
   * @param type 类类型
   * @param <T> 类型
   * @return 是否
   */
  <T> boolean isCollection(Class<T> type);

}
```
## DefaultObjectFactory
`org.apache.ibatis.reflection.factory.DefaultObjectFactory` ，实现 ObjectFactory、Serializable 接口，默认 ObjectFactory 实现类。

### create
`#create(Class<T> type, ...)` 方法，创建指定类的对象
```java
  @Override
  public <T> T create(Class<T> type) {
    return create(type, null, null);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    // <1> 获得需要创建对象的类类型
    Class<?> classToCreate = resolveInterface(type);
    // <2> 创建指定类的对象
    return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
  }
```
 - `<1>`处，调用`#resolveInterface(Class<?> type)`方法，获得需要创建的类，代码如下:
  ```java
    /**
   * 获得对象对应的类型
   * @param type 对象类类型
   * @return 对象类型
   */
  protected Class<?> resolveInterface(Class<?> type) {
    Class<?> classToCreate;
    if (type == List.class || type == Collection.class || type == Iterable.class) {
      classToCreate = ArrayList.class;
    } else if (type == Map.class) {
      classToCreate = HashMap.class;
    } else if (type == SortedSet.class) { // issue #510 Collections Support
      classToCreate = TreeSet.class;
    } else if (type == Set.class) {
      classToCreate = HashSet.class;
    } else {
      classToCreate = type;
    }
    return classToCreate;
  }
  ```
 - `<2>`处，调用`#instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs)`方法创建实例
  ```java
  /**
   * 创建实例
   * @param type 类类型
   * @param constructorArgTypes 构造函数参数类型
   * @param constructorArgs 构造函数参数
   * @param <T> 类型
   * @return 实例
   */
  private  <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    try {
      Constructor<T> constructor;
      // <1.1> 使用默认无参构造函数创建实例
      if (constructorArgTypes == null || constructorArgs == null) {
        constructor = type.getDeclaredConstructor();
        try {
          return constructor.newInstance();
        } catch (IllegalAccessException e) {
          if (Reflector.canControlMemberAccessible()) {
            constructor.setAccessible(true);
            return constructor.newInstance();
          } else {
            throw e;
          }
        }
      }
      // <1.2> 使用特定的构造方法创建实例
      constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[constructorArgTypes.size()]));
      try {
        return constructor.newInstance(constructorArgs.toArray(new Object[constructorArgs.size()]));
      } catch (IllegalAccessException e) {
        if (Reflector.canControlMemberAccessible()) {
          constructor.setAccessible(true);
          return constructor.newInstance(constructorArgs.toArray(new Object[constructorArgs.size()]));
        } else {
          throw e;
        }
      }
    } catch (Exception e) {
      // 拼接 argTypes
      String argTypes = Optional.ofNullable(constructorArgTypes).orElseGet(Collections::emptyList)
          .stream().map(Class::getSimpleName).collect(Collectors.joining(","));
      // 拼接 argValues
      String argValues = Optional.ofNullable(constructorArgs).orElseGet(Collections::emptyList)
          .stream().map(String::valueOf).collect(Collectors.joining(","));
      // 抛出异常
      throw new ReflectionException("Error instantiating " + type + " with invalid types (" + argTypes + ") or values (" + argValues + "). Cause: " + e, e);
    }
  }
  ```

### isCollection
`#isCollection(Class<T> type)`方法，判断类型是否是集合,通过判断是否是Collection的子类,代码如下:
```java
  public <T> boolean isCollection(Class<T> type) {
    return Collection.class.isAssignableFrom(type);
  }
```

### setProperties
`#setProperties(Properties properties)`,设置系统属性，代码如下:
```java
  /**
   * 设置系统属性
   * @param properties 配置属性
   */
  @Override
  public void setProperties(Properties properties) {
    // no props for default
  }
```

# Property 工具类
`org.apache.ibatis.reflection.property` 包下，提供了 PropertyCopier、PropertyNamer、PropertyTokenizer 三个属性相关的工具类

## PropertyCopier
`org.apache.ibatis.reflection.property.PropertyCopier`,属性复制器，代码如下：
```java
public final class PropertyCopier {

  private PropertyCopier() {
    // Prevent Instantiation of Static Class
  }

  /**
   * 将　sourceBean中的属性复制到destinationBean中
   * @param type　类类型
   * @param sourceBean　源属性
   * @param destinationBean 目标属性
   */
  public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
    // 循环，从当前类开始，不断复制父类,直到父类不存在
    Class<?> parent = type;
    while (parent != null) {
      // 获取当前父类定义的属性
      final Field[] fields = parent.getDeclaredFields();
      for (Field field : fields) {
        try {
          try {
            // 复制属性
            field.set(destinationBean, field.get(sourceBean));
          } catch (IllegalAccessException e) {
            if (Reflector.canControlMemberAccessible()) {
              // 设置属性可访问并复制属性
              field.setAccessible(true);
              field.set(destinationBean, field.get(sourceBean));
            } else {
              throw e;
            }
          }
        } catch (Exception e) {
          // Nothing useful to do, will only fail on final fields, which will be ignored.
        }
      }
      // 获得父类
      parent = parent.getSuperclass();
    }
  }
}
```

### PropertyNamer
`org.apache.ibatis.reflection.property.PropertyNamer`,属性名工具，代码如下:
```java
public final class PropertyNamer {

  private PropertyNamer() {
    // Prevent Instantiation of Static Class
  }

  /**
   * 根据方法名，获得属性名称
   * @param name　方法名
   * @return  属性名
   */
  public static String methodToProperty(String name) {
    // is方法
    if (name.startsWith("is")) {
      name = name.substring(2);
    // get或者set方法
    } else if (name.startsWith("get") || name.startsWith("set")) {
      name = name.substring(3);
    } else {
      throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
    }

    // 首字母小写
    if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) {
      name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
    }

    return name;
  }

  /**
   * 判断是否是属性方法
   * @param name　方法名称
   * @return  是否
   */
  public static boolean isProperty(String name) {
    return name.startsWith("get") || name.startsWith("set") || name.startsWith("is");
  }

  /**
   * 判断是否是getter方法
   * @param name　方法名称
   * @return  是否
   */
  public static boolean isGetter(String name) {
    return name.startsWith("get") || name.startsWith("is");
  }

  /**
   * 判断是否是setter方法
   * @param name　方法名称
   * @return  是否
   */
  public static boolean isSetter(String name) {
    return name.startsWith("set");
  }

}
```
### PropertyTokenizer
`org.apache.ibatis.reflection.property.PropertyTokenizer`,实现了Iterator接口，属性分词器，支持迭代访问
举个例子，在访问 `"order[0].item[0].name"`时，我们希望拆分成 `"order[0]"`、`"item[0]"`、`"name"`三段，那么就可以通过 PropertyTokenizer 来实现。
#### 构造方法
```java
  /**
   * 当前字符串
   */
  private String name;
  /**
   * 索引的 {@link #name} ，因为 {@link #name} 如果存在 {@link #index} 会被更改
   */
  private final String indexedName;
  /**
   * 编号
   * 对于数组 name[0] 则　index = 0
   * 对于Map map[key] 则 index = key
   */
  private String index;
  /**
   * 剩余字符串
   */
  private final String children;

  /**
   * 构造方法
   * @param fullname　全名称
   */
  public PropertyTokenizer(String fullname) {
    // <1> 初始化 name,children字符串，使用"."分隔
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    // <2> 记录当前name
    indexedName = name;
    // 若存在"[", 则获取index,并修改name
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }
```
 - `<1>`处，初始化name,children字符串,使用`.`分隔
 - `<2>`处，如果是数组，记录编号，并修改name
#### next
`next()`方法，迭代你获取下一个PropertyTokenizer对象，代码如下:
```java
// PropertyTokenizer.java

@Override
public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
}
```

#### hasNext
`#hasNext()方法`,判断是否有下一个迭代元素，代码如下:
```java
// PropertyTokenizer.java

public String getChildren() {
    return children;
}
```

# MetaClass
`org.apache.ibatis.reflection.MetaClass`,类的元数据类，基于Reflector和PropertyTokenizer,实现对类的各种骚操作。

## 构造方法
```java
// MetaClass.java
  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }
```
 - 通过构造方法可以看出,一个MetaClass对应一个Class对象
目前有两个方法会涉及到构造方法:
 - ①`#forClass(Class<?> type, ReflectorFactory reflectorFactory)` 静态方法，创建指定类的 MetaClass 对象。代码如下：
 ```java
  /**
   * 根据类类型创建 MetaClass 实例
   * @param type 类类型
   * @param reflectorFactory 反射器工厂
   * @return MetaClass 实例
   */
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }
 ```

 - ②`#etaClassForProperty(String name)`,创建指定属性的类的MetaClass对象，代码如下:
 ```java
  /**
   * 根据属性(对象属性)创建MetaClass对象
   * @param name 属性名称
   * @return MetaClass 实例
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }
 ```

## findProperty

```java
  /**
   * 根据表达式，获取属性的值
   * @param name 表达式
   * @param useCamelCaseMapping 是否使用驼峰命名法
   * @return  属性值
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    // <1> 下划线转驼峰
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    // <2> 获得属性
    return findProperty(name);
  }
```
 - `<1>`处，下划线转驼峰时，将`_`替换成了空串
 - `<2>`处，调用`#findProperty(String name)`,获取属性，代码如下:
  ```java
  /**
   * 根据名称获取指定属性的值
   * @param name 属性名称
   * @return  属性值
   */
  public String findProperty(String name) {
    // <3> 构建属性
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }
  ```
 - `<3>`处，调用`#buildProperty(String name, StringBuilder builder)`构建属性，代码如下:
  ```java
  /**
   * 构建属性
   * @param name 表达式
   * @param builder 字符串 Builder
   * @return 构建结果
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 创建 PropertyTokenizer 对 name 进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // <4> 获得属性名，并添加到builder中
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        // 拼接属性名到 builder 中
        builder.append(propertyName);
        builder.append(".");
        // 创建 MetaClass 对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 递归解析子表达式children,并将结果加入到 builder 中
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    // 没有子表达式
    } else {
      // <4> 获得属性名,添加到 builder 中
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  // Reflector.java

  /**
   * 获取属性名称
   * @param name 不区分大小写属性名
   * @return 大写属性名称
   */
  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
  ```
  - `<4>`处，获取属性名时，通过Reflector的不区分大小写属性集合获取属性，所以非驼峰命名时，只要将`_`换成空串就可以找到属性。

## hasGetter
`#hasGetter(String name)` 方法，判断指定属性是否有 getting 方法。代码如下：
```java
  /**
   * 判断指定属性是否有 getter 方法
   * @param name 属性名称
   * @return 是否
   */
  public boolean hasGetter(String name) {
    // 创建 PropertyTokenizer对,对name 进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 判断是否有该属性的 getter 方法
      if (reflector.hasGetter(prop.getName())) {
        // <1> 创建 MetaClass 对象
        MetaClass metaProp = metaClassForProperty(prop);
        // 递归判断子表达式 children,是否有getter方法
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    // 没有子表达式
    } else {
      // 判断是否有该属性的 getter 方法
      return reflector.hasGetter(prop.getName());
    }
  }
```
 - `<1>`处，调用`#metaClassForProperty(PropertyTokenizer prop) `方法，创建 创建 MetaClass 对象。代码如下：
```java
  /**
   * 根据属性创建 MetaClass 实例
   * @param prop 属性对象
   * @return MetaClass 实例
   */
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    // 获取 getter 方法返回的类型
    Class<?> propType = getGetterType(prop);
    // 创建 MetaClass 对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 获取 getter 方法的返回类型
   * @param prop 属性分词器
   * @return 属性类型
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 获得返回类型
    Class<?> type = reflector.getGetterType(prop.getName());
    // 如果获取数组某个位置的元素,则获取其泛型.例如说：list[0].field ，那么就会解析 list 是什么类型，这样才好通过该类型，继续获得 field
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 获得返回的类型
      Type returnType = getGenericGetterType(prop.getName());
      if (returnType instanceof ParameterizedType) {
        // 如果是泛型,进行解析真正的类型
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) { // Collection<T> T只有一个,所以判断1
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  /**
   * 获取属性名返回类型
   * @param propertyName 属性名
   * @return 返回类型
   */
  private Type getGenericGetterType(String propertyName) {
    try {
      // 获得 Invoker 对象
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 如果是 MethodInvoker 对象,则说明是 getter 方法,解析方法返回类型
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      // 如果是 GetFieldInvoker对象,说明是field,直接访问
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }
```
## getGetterType
`#getGetterType(String name)` 方法，获得指定属性的getter方法的返回值类型

```java
  /**
   * 获取指定属性get方法的返回值类型
   * @param name 属性名
   * @return 返回值类型
   */
  public Class<?> getGetterType(String name) {
    // 创建 PropertyTokenizer 对象,对 name 进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 递归判断子表达式,获得返回值的类型
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    // 直接获得返回值的类型
    return getGetterType(prop);
  }
```

# ObjectWrapper
`org.apache.ibatis.reflection.wrapper.ObjectWrapper`,对象包装器接口，基于 MetaClass 工具类，定义对指定对象的各种操作。或者可以说，ObjectWrapper 是 MetaClass 的指定类的具象化。代码如下：
```java
public interface ObjectWrapper {

  /**
   * 获得属性值
   * @param prop 对象,相当于主键
   * @return 值
   */
  Object get(PropertyTokenizer prop);

  /**
   * 设置属性值
   * @param prop 对象
   * @param value 值
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * {@link MetaClass#findProperty(String, boolean)}
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * {@link MetaClass#getGetterNames()}
   */
  String[] getGetterNames();

  /**
   * {@link MetaClass#getSetterNames()}
   */
  String[] getSetterNames();

  /**
   * {@link MetaClass#getSetterType(String)}
   */
  Class<?> getSetterType(String name);

  /**
   * {@link MetaClass#getGetterType(String)}
   */
  Class<?> getGetterType(String name);

  /**
   * {@link MetaClass#hasSetter(String)}
   */
  boolean hasSetter(String name);

  /**
   * {@link MetaClass#hasGetter(String)}
   */
  boolean hasGetter(String name);

  /**
   * {@link MetaObject#forObject(Object, ObjectFactory, ObjectWrapperFactory, ReflectorFactory)}
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /**
   * 是否为集合
   */
  boolean isCollection();

  /**
   * 添加元素到集合
   */
  void add(Object element);

  /**
   * 添加多个元素到集合
   */
  <E> void addAll(List<E> element);

}
```
 - 从接口中，我们可以看到，主要是对MetaObject方法的调用
---
ObjectWrapper的子类实现如下图：
![TokenHandler](https://github.com/leithda/mybatis-3/blob/master/uml/ObjectWrapper.png?raw=true)

## BaseWrapper
`org.apache.ibatis.reflection.wrapper.BaseWrapper` ，实现 ObjectWrapper 接口，ObjectWrapper 抽象类，为子类 BeanWrapper 和 MapWrapper 提供属性值的获取和设置的公用方法。代码如下：
```java
public abstract class BaseWrapper implements ObjectWrapper {

  protected static final Object[] NO_ARGUMENTS = new Object[0];

  /**
   * MetaObject对象
   */
  protected final MetaObject metaObject;

  protected BaseWrapper(MetaObject metaObject) {
    this.metaObject = metaObject;
  }

  /**
   * 获得指定属性的值
   * @param prop PropertyTokenizer 对象
   * @param object 指定 Object 对象
   * @return 值
   */
  protected Object resolveCollection(PropertyTokenizer prop, Object object) {
    if ("".equals(prop.getName())) {
      return object;
    } else {
      return metaObject.getValue(prop.getName());
    }
  }

  /**
   * 获取集合中指定位置的值
   * @param prop PropertyTokenizer 对象
   * @param collection 集合
   * @return 值
   */
  protected Object getCollectionValue(PropertyTokenizer prop, Object collection) {
    if (collection instanceof Map) {
      return ((Map) collection).get(prop.getIndex());
    } else {
      int i = Integer.parseInt(prop.getIndex());
      if (collection instanceof List) {
        return ((List) collection).get(i);
      } else if (collection instanceof Object[]) {
        return ((Object[]) collection)[i];
      } else if (collection instanceof char[]) {
        return ((char[]) collection)[i];
      } else if (collection instanceof boolean[]) {
        return ((boolean[]) collection)[i];
      } else if (collection instanceof byte[]) {
        return ((byte[]) collection)[i];
      } else if (collection instanceof double[]) {
        return ((double[]) collection)[i];
      } else if (collection instanceof float[]) {
        return ((float[]) collection)[i];
      } else if (collection instanceof int[]) {
        return ((int[]) collection)[i];
      } else if (collection instanceof long[]) {
        return ((long[]) collection)[i];
      } else if (collection instanceof short[]) {
        return ((short[]) collection)[i];
      } else {
        throw new ReflectionException("The '" + prop.getName() + "' property of " + collection + " is not a List or Array.");
      }
    }
  }

  /**
   * 设置集合中指定位置的值
   * @param prop PropertyTokenizer 对象
   * @param collection 集合
   * @param value 值
   */
  protected void setCollectionValue(PropertyTokenizer prop, Object collection, Object value) {
    if (collection instanceof Map) {
      ((Map) collection).put(prop.getIndex(), value);
    } else {
      int i = Integer.parseInt(prop.getIndex());
      if (collection instanceof List) {
        ((List) collection).set(i, value);
      } else if (collection instanceof Object[]) {
        ((Object[]) collection)[i] = value;
      } else if (collection instanceof char[]) {
        ((char[]) collection)[i] = (Character) value;
      } else if (collection instanceof boolean[]) {
        ((boolean[]) collection)[i] = (Boolean) value;
      } else if (collection instanceof byte[]) {
        ((byte[]) collection)[i] = (Byte) value;
      } else if (collection instanceof double[]) {
        ((double[]) collection)[i] = (Double) value;
      } else if (collection instanceof float[]) {
        ((float[]) collection)[i] = (Float) value;
      } else if (collection instanceof int[]) {
        ((int[]) collection)[i] = (Integer) value;
      } else if (collection instanceof long[]) {
        ((long[]) collection)[i] = (Long) value;
      } else if (collection instanceof short[]) {
        ((short[]) collection)[i] = (Short) value;
      } else {
        throw new ReflectionException("The '" + prop.getName() + "' property of " + collection + " is not a List or Array.");
      }
    }
  }

}
```

### BeanWrapper
`org.apache.ibatis.reflection.wrapper.BeanWrapper` ，继承 BaseWrapper 抽象类，普通对象的 ObjectWrapper 实现类，例如 User、Order 这样的 POJO 类。属性如下：
```java
public class BeanWrapper extends BaseWrapper {

  /**
   * 普通对象
   */
  private final Object object;
  private final MetaClass metaClass;

  public BeanWrapper(MetaObject metaObject, Object object) {
    super(metaObject);
    this.object = object;
    // 创建 MetaClass 对象
    this.metaClass = MetaClass.forClass(object.getClass(), metaObject.getReflectorFactory());
  }
}
```
#### get
`#get(PropertyTokenizer prop)` 方法,获得指定属性的值。代码如下:
```java
  @Override
  public Object get(PropertyTokenizer prop) {
    // <1> 获得集合类型的属性的指定位置的值
    if (prop.getIndex() != null) {
      // 获得集合属性的类型
      Object collection = resolveCollection(prop, object);
      // 获得指定位置的值
      return getCollectionValue(prop, collection);
    // <2> 获得属性的值
    } else {
      return getBeanProperty(prop, object);
    }
  }
```
 - `<1>`处，获得集合类型的属性的指定位置的值。例如:User对象的list[0],所调用的方法，都是 BaseWrapper 所提供的公用方法。
 - `<2>`处，调用`#getBeanProperty(PropertyTokenizer prop, Object object)`方法，获得属性的值，代码如下:
 ```java
  /**
   * 获得属性的值
   * @param prop PropertyTokenizer 对象
   * @param object 目标对象
   * @return 属性值
   */
  private Object getBeanProperty(PropertyTokenizer prop, Object object) {
    try {
      Invoker method = metaClass.getGetInvoker(prop.getName());
      try {
        return method.invoke(object, NO_ARGUMENTS);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new ReflectionException("Could not get property '" + prop.getName() + "' from " + object.getClass() + ".  Cause: " + t.toString(), t);
    }
  }
 ```
#### set
`#set(PropertyTokenizer prop, Object value)`方法，设置指定属性的值，代码如下:
```java
  public void set(PropertyTokenizer prop, Object value) {
    // 设置集合类型的属性的指定位置的值
    if (prop.getIndex() != null) {
      // 获得集合类型的属性
      Object collection = resolveCollection(prop, object);
      // 设置指定位置的值
      setCollectionValue(prop, collection, value);
    } else {
      // 设置属性的值
      setBeanProperty(prop, object, value);
    }
  }
```
#### getGetterType
`#getGetterType(String name)`方法，获得指定属性的getter方法的返回值，代码如下:
```java
  @Override
  public Class<?> getGetterType(String name) {
    // 分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // <1> 创建 MetaObject 对象
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      // 如果 metaValue 为空, 则基于 MetaClass 获得返回类型
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return metaClass.getGetterType(name);
        // 如果 metaValue 非空, 则基于 metaValue 获得返回类型.
        // 例如：richType.richMap.nihao ，其中 richMap 是 Map 类型，而 nihao 的类型，需要获得到 nihao 的具体值，才能做真正的判断。
      } else {
        // 递归判断子表达式
        return metaValue.getGetterType(prop.getChildren());
      }
    // 无子表达式
    } else {
      // 直接获得返回值的类型
      return metaClass.getGetterType(name);
    }
  }
```
 - 逻辑同`MetaClass#getGetterType(String name)`方法一致，差异点主要在`<1>`处。
 - `<1>`处，基于当前属性，创建MetaObject对象。如果该属性对应的值为空，那么metaValue会等于 SystemMetaObject.NULL_META_OBJECT 。也因为为空，那么就不能基于 metaValue 去做递归，获取返回值的类型。
 - 关于 MetaObject 类，在后续详细解析
#### hasGetter
`#hasGetter(String name)`是否具有指定属性的getter方法
```java
  @Override
  public boolean hasSetter(String name) {
    // 创建 PropertyTokenizer 对象,对name进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 判断是否具有指定属性的getter方法
      if (metaClass.hasSetter(prop.getIndexedName())) {
        // 创建 MetaObject 对象
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        // 如果 metaValue 为空，则基于 metaClass 判断是否有该属性的 getting 方法
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasSetter(name);
        // 如果 metaValue 非空，则基于 metaValue 判断是否有 getting 方法。
        } else {
          // 递归判断子表达式 children ，判断是否有 getting 方法
          return metaValue.hasSetter(prop.getChildren());
        }
      } else {
        return false;
      }
    // 没有子表达式
    } else {
      return metaClass.hasSetter(name);
    }
  }
```

#### instantiatePropertyValue
`#instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory)` 方法，创建指定属性的值。代码如下：
```java
  @Override
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
    MetaObject metaValue;
    // 获得 setter 方法参数的类型
    Class<?> type = getSetterType(prop.getName());
    try {
      // 创建对象
      Object newObject = objectFactory.create(type);
      // 创建 MetaObject 对象
      metaValue = MetaObject.forObject(newObject, metaObject.getObjectFactory(), metaObject.getObjectWrapperFactory(), metaObject.getReflectorFactory());
      // <1> 设置当前对象的值
      set(prop, newObject);
    } catch (Exception e) {
      throw new ReflectionException("Cannot set value of property '" + name + "' because '" + name + "' is null and cannot be instantiated on instance of " + type.getName() + ". Cause:" + e.toString(), e);
    }
    return metaValue;
  }
```

#### isCollection
`#isCollection()` 方法，返回 false ，表示不是集合。代码如下：
```java
  @Override
  public boolean isCollection() {
    return false;
  }
```

***
所以，add以及addAll方法都是不支持的，直接抛出异常，代码如下:
```java
  @Override
  public void add(Object element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> void addAll(List<E> list) {
    throw new UnsupportedOperationException();
  }
```

### MapWrapper
`org.apache.ibatis.reflection.wrapper.MapWrapper`，继承 BaseWrapper 抽象类，Map 对象的 ObjectWrapper 实现类。

MapWrapper 和 BeanWrapper 的大体逻辑是一样的，差异点主要如下：
```java
// MapWrapper.java

// object 变成了 map 
private final Map<String, Object> map;

// 属性的操作变成了
map.put(prop.getName(), value);
map.get(prop.getName());
```

## CollectionWrapper
`org.apache.ibatis.reflection.wrapper.CollectionWrapper` ，实现 ObjectWrapper 接口，集合 ObjectWrapper 实现类。比较简单，直接看代码：
```java
public class CollectionWrapper implements ObjectWrapper {

  private final Collection<Object> object;

  public CollectionWrapper(MetaObject metaObject, Collection<Object> object) {
    this.object = object;
  }

  @Override
  public Object get(PropertyTokenizer prop) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void set(PropertyTokenizer prop, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String findProperty(String name, boolean useCamelCaseMapping) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getGetterNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getSetterNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Class<?> getSetterType(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Class<?> getGetterType(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasSetter(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasGetter(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCollection() {
    return true;
  }

  @Override
  public void add(Object element) {
    object.add(element);
  }

  @Override
  public <E> void addAll(List<E> element) {
    object.addAll(element);
  }

}
```
 - 代码比较简单，支持`add`和`addAll`元素操作

# ObjectWrapperFactory
`org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory`，ObjectWrapper 工厂接口。代码如下：
```java
public interface ObjectWrapperFactory {

  /**
   * 是否包装了指定对象
   * @param object 对象
   * @return 是否
   */
  boolean hasWrapperFor(Object object);

  /**
   * 获取指定对象的 ObjectWrapper 实例
   * @param metaObject  MetaObject 对象
   * @param object 指定对象
   * @return ObjectWrapper 对象
   */
  ObjectWrapper getWrapperFor(MetaObject metaObject, Object object);

}
```

## DefaultObjectWrapperFactory
`org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory` ，实现 `ObjectWrapperFactory` 接口，默认 `ObjectWrapperFactory` 实现类。代码如下：
```java
public class DefaultObjectWrapperFactory implements ObjectWrapperFactory {

  @Override
  public boolean hasWrapperFor(Object object) {
    return false;
  }

  @Override
  public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
    throw new ReflectionException("The DefaultObjectWrapperFactory should never be called to provide an ObjectWrapper.");
  }

}
```
 - 空的对象，默认情况下不使用默认实现
 
# MetaObject
`org.apache.ibatis.reflection.MetaObject` ，对象元数据，提供了对象的属性值的获得和设置等等方法。😈 可以理解成，对 BaseWrapper 操作的进一步增强。

## 构造方法
```java
public class MetaObject {

  // 封装过的 Object 对象
  private final Object originalObject;
  private final ObjectWrapper objectWrapper;
  private final ObjectFactory objectFactory;
  private final ObjectWrapperFactory objectWrapperFactory;
  private final ReflectorFactory reflectorFactory;

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;

    // <1>
    if (object instanceof ObjectWrapper) {
      this.objectWrapper = (ObjectWrapper) object;
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      // 创建 ObjectWrapper 对象
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
    } else if (object instanceof Map) {
      // 创建 MapWrapper 对象
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      // 创建 CollectionWrapper对象
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      // 创建 BeanWrapper 对象
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }
  
  // ...
}
```
 - `<1>`处，会根据object类型的不同，创建对应的ObjectWrapper对象
 - `<2>`处，我们可以看到 `ObjectWrapperFactory` 的使用，因为默认情况下的 DefaultObjectWrapperFactory 未实现任何逻辑，所以这块逻辑相当于暂时不起作用。如果想要起作用，需要自定义 ObjectWrapperFactory 的实现类。
## forObject
`#forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory)` 静态方法，创建 MetaObject 对象。代码如下：
```java
  /**
   * 创建 MetaObject 对象
   * @param object  原始 Object 对象
   * @param objectFactory 对象工厂类
   * @param objectWrapperFactory 对象装饰类对应工厂类
   * @param reflectorFactory 反射器工厂类
   * @return MetaObject 对象
   */
  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }
```
 - 如果 object 为空的情况下，返回 SystemMetaObject.NULL_META_OBJECT 。
## metaObjectForProperty
`#metaObjectForProperty(String name)` 方法，创建指定属性的 MetaObject 对象。代码如下：
```java
  /**
   * 创建指定属性的 MetaObject 对象
   * @param name 属性名
   * @return MetaObject 对象
   */
  public MetaObject metaObjectForProperty(String name) {
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }
```

## getValue
`#getValue(String name)` 方法，获得指定属性的值。代码如下：
```java
  /**
   * 获取指定属性的值
   * @param name 属性名
   * @return 属性值
   */
  public Object getValue(String name) {
    // 执行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 创建 MetaObject 对象
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      // <2> 递归判断子表达式 children ，获取值
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        return metaValue.getValue(prop.getChildren());
      }
    // 无子表达式
    } else {
      // <1> 获取值
      return objectWrapper.get(prop);
    }
  }
```
 - 对name 进行递归分词，直到`<1>`处，返回属性值
 
## setValue
`#setValue(String name, Object value)` 方法，设置指定属性的指定值。代码如下：
```java
  /**
   * 设置指定属性的值
   * @param name 属性名
   * @param value 属性值
   */
  public void setValue(String name, Object value) {
    // 分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 创建 MetaObject 对象
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      // 递归判断子表达式 children ，设置值
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null) {
          // don't instantiate child path if value is null
          return;
        } else {
          // <1> 创建值
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      // 设置值
      metaValue.setValue(prop.getChildren(), value);
      // 没有子表达式
    } else {
      objectWrapper.set(prop, value);
    }
  }
```
## isCollection
`#isCollection()`方法，判断是否为集合。代码如下：
```java
// MetaObject.java

public boolean isCollection() {
    return objectWrapper.isCollection();
}

public void add(Object element) {
    objectWrapper.add(element);
}

public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
}
```

# SystemMetaObject
`org.apache.ibatis.reflection.SystemMetaObject` ，系统级的 MetaObject 对象，主要提供了 ObjectFactory、ObjectWrapperFactory、空 MetaObject 的单例。代码如下：
```java
// SystemMetaObject.java

public final class SystemMetaObject {

    /**
     * ObjectFactory 的单例
     */
    public static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
    /**
     * ObjectWrapperFactory 的单例
     */
    public static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();

    /**
     * 空对象的 MetaObject 对象单例
     */
    public static final MetaObject NULL_META_OBJECT = MetaObject.forObject(NullObject.class, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, new DefaultReflectorFactory());

    private SystemMetaObject() {
        // Prevent Instantiation of Static Class
    }

    private static class NullObject {
    }

    /**
     * 创建 MetaObject 对象
     *
     * @param object 指定对象
     * @return MetaObject 对象
     */
    public static MetaObject forObject(Object object) {
        return MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, new DefaultReflectorFactory());
    }

}
```
 - 核心就是 `#forObject(Object object)` 方法，创建指定对象的 MetaObject 对象。

# ParamNameUtil
`org.apache.ibatis.reflection.ParamNameUtil` ，参数名工具类，获得构造方法、普通方法的参数列表。代码如下：
```java
public class ParamNameUtil {
  /**
   * 获得普通方法的参数列表
   *
   * @param method 普通方法
   * @return 参数集合
   */
  public static List<String> getParamNames(Method method) {
    return getParameterNames(method);
  }
  /**
   * 获得构造方法的参数列表
   *
   * @param constructor 构造方法
   * @return 参数集合
   */
  public static List<String> getParamNames(Constructor<?> constructor) {
    return getParameterNames(constructor);
  }

  /**
   * 获得方法的参数列表
   * @param executable 可执行实例,子类是method和constructor
   * @return 参数列表
   */
  private static List<String> getParameterNames(Executable executable) {
    return Arrays.stream(executable.getParameters()).map(Parameter::getName).collect(Collectors.toList());
  }

  private ParamNameUtil() {
    super();
  }
}
```

# ParamNameResolver
`org.apache.ibatis.reflection.ParamNameResolver` ，参数名解析器。

## 构造方法
```java

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   *
   * 参数名映射
   * KEY : 参数顺序
   * VALUE: 参数名称
   */
  private final SortedMap<Integer, String> names;

  /**
   * 是否有{@link Param} 注解的参数
   */
  private boolean hasParamAnnotation;

  /**
   * 构造函数
   * @param config 配置类
   * @param method 方法
   */
  public ParamNameResolver(Configuration config, Method method) {
    final Class<?>[] paramTypes = method.getParameterTypes();
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 如果是特殊参数,忽略
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      // 首先,从@Param注解中获取参数
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          name = ((Param) annotation).value();
          break;
        }
      }
      if (name == null) {
        // @Param was not specified.
        // 其次,获取真实的参数名
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        // 最差,使用map的顺序，作为编号
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      // 添加到map中
      map.put(paramIndex, name);
    }
    // 构建不可变集合
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }
  // ...
}
```

## getNamedParams
`#getNamedParams(Object[] args)` 方法，获得参数名与值的映射。代码如下：
```java
// ParamNameResolver.java

private static final String GENERIC_NAME_PREFIX = "param";

/**
 * <p>
 * A single non-special parameter is returned without a name.
 * Multiple parameters are named using the naming rule.
 * In addition to the default names, this method also adds the generic names (param1, param2,
 * ...).
 * </p>
 *
 * 获得参数名与值的映射
 */
public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    // 无参数，则返回 null
    if (args == null || paramCount == 0) {
        return null;
    // 只有一个非注解的参数，直接返回首元素
    } else if (!hasParamAnnotation && paramCount == 1) {
        return args[names.firstKey()];
    } else {
        // 集合。
        // 组合 1 ：KEY：参数名，VALUE：参数值
        // 组合 2 ：KEY：GENERIC_NAME_PREFIX + 参数顺序，VALUE ：参数值
        final Map<String, Object> param = new ParamMap<>();
        int i = 0;
        // 遍历 names 集合
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            // 组合 1 ：添加到 param 中
            param.put(entry.getValue(), args[entry.getKey()]);
            // add generic param names (param1, param2, ...)
            // 组合 2 ：添加到 param 中
            final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
            // ensure not to overwrite parameter named with @Param
            if (!names.containsValue(genericParamName)) {
                param.put(genericParamName, args[entry.getKey()]);
            }
            i++;
        }
        return param;
    }
}
```

# TypeParameterResolver
`org.apache.ibatis.reflection.TypeParameterResolver` ，工具类，`java.lang.reflect.Type` 参数解析器

## 暴露方法
`TypeParameterResolver` 暴露了三个 公用静态方法，分别用于解析 Field 类型、Method 返回类型、方法参数类型。代码如下：
```java
  /**
   * 解析属性类型
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    // 属性类型
    Type fieldType = field.getGenericType();
    // 定义的类
    Class<?> declaringClass = field.getDeclaringClass();
    // 解析类型
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * 解析方法返回类型
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    // 属性类型
    Type returnType = method.getGenericReturnType();
    // 定义的类
    Class<?> declaringClass = method.getDeclaringClass();
    // 解析类型
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * 解析方法参数的类型数组
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    // 获得方法参数类型数组
    Type[] paramTypes = method.getGenericParameterTypes();
    // 定义的类
    Class<?> declaringClass = method.getDeclaringClass();
    // 解析类型们
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }
```
 - 大体逻辑都类似，最终都会调用 `#resolveType(Type type, Type srcType, Class<?> declaringClass)` 方法，解析类型。

## resolveType
`#resolveType(Type type, Type srcType, Class<?> declaringClass)` 方法，解析 type 类型。代码如下：
```java
  /**
   * 解析类型
   * @param type 类型
   * @param srcType 来源类型
   * @param declaringClass 定义的类
   * @return 解析后的类型
   */
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    if (type instanceof TypeVariable) {
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
      return type;
    }
  }
```

### resolveParameterizedType
`#resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass)` 方法，解析 ParameterizedType 类型。代码如下：
```java
  /**
   * 解析 ParameterizedType 类型
   * @param parameterizedType ParameterizedType 类型
   * @param srcType 来源类型
   * @param declaringClass 定义的类
   * @return 解析后的类型
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    // 【1】解析 <> 中实际类型
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
    Type[] args = new Type[typeArgs.length];
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        args[i] = typeArgs[i];
      }
    }
    // 【2】
    return new ParameterizedTypeImpl(rawType, null, args);
  }
```
 - `【1】`处，解析 <> 中实际类型。
 - `【2】`处，创建 ParameterizedTypeImpl 对象。代码如下：
 ```java
   /**
   * ParameterizedType 实现类
   *
   * 参数化类型，即泛型。例如：List<T>、Map<K, V>等带有参数化的配置
   */
  static class ParameterizedTypeImpl implements ParameterizedType {

    // 以 List<T> 举例子

    /**
     * <> 前面实际类型
     *
     * 例如：List
     */
    private Class<?> rawType;

    /**
     * 如果这个类型是某个属性所有，则获取这个所有者类型；否则，返回 null
     */
    private Type ownerType;

    /**
     * <> 中实际类型
     *
     * 例如：T
     */
    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }
 ```
 
 ### resolveWildcardType
`#resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass)`方法，解析 WildcardType 类型。代码如下：
```java
  /**
   * 解析泛型类型
   * @param wildcardType 泛型类型
   * @param srcType 来源类型
   * @param declaringClass 定义的类
   * @return 解析后的类型
   */
  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    // <1.1> 解析泛型表达式下界（下限 super）
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    // <1.2> 解析泛型表达式上界（上限 extends）
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    // <2> 创建 WildcardTypeImpl 对象
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }
```
- `<1.1>`、`<1.2>` 处，解析泛型表达式下界（下限 super）和上界( 上限 extends )。
- `<2>` 创建 WildcardTypeImpl 对象。代码如下：
```java
  /**
   * WildcardType 实现类
   *
   * 泛型表达式（或者通配符表达式），即 ? extend Number、? super Integer 这样的表达式。
   * WildcardType 虽然是 Type 的子接口，但却不是 Java 类型中的一种。
   */
  static class WildcardTypeImpl implements WildcardType {

    /**
     * 泛型表达式下界（下限 super）
     */
    private Type[] lowerBounds;
    /**
     * 泛型表达式上界（上界 extends）
     */
    private Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }
```

### resolveGenericArrayType
```java
  /**
   * 解析数组类型
   * @param genericArrayType 数组类型
   * @param srcType 来源类型
   * @param declaringClass 定义的类
   * @return 解析后的类型
   */
  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    // 【1】解析 componentType
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType)
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    // 【2】创建 GenericArrayTypeImpl 对象
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }
```
 - `【1】` 处，解析 componentType 类型。
 - `【2】` 处，创建 GenericArrayTypeImpl 对象。代码如下：
 ```java
   /**
   * GenericArrayType 实现类
   *
   * 泛型数组类型，用来描述 ParameterizedType、TypeVariable 类型的数组；即 List<T>[]、T[] 等；
   */
  static class GenericArrayTypeImpl implements GenericArrayType {
    /**
     * 数组元素类型
     */
    private Type genericComponentType;

    GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
 ```
 
 ### resolveTypeVar
 - TODO,芋艿没看懂，我自觉看不懂，先放弃
 
 # ArrayUtil
 `org.apache.ibatis.reflection.ArrayUtil` ，数组工具类。代码如下：
 ```java
 public class ArrayUtil {

  /**
   * Returns a hash code for {@code obj}.
   *
   * @param obj
   *          The object to get a hash code for. May be an array or <code>null</code>.
   * @return A hash code of {@code obj} or 0 if {@code obj} is <code>null</code>
   */
  public static int hashCode(Object obj) {
    if (obj == null) {
      // for consistency with Arrays#hashCode() and Objects#hashCode()
      return 0;
    }
    // 普通类
    final Class<?> clazz = obj.getClass();
    if (!clazz.isArray()) {
      return obj.hashCode();
    }
    // 数组类型
    final Class<?> componentType = clazz.getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.hashCode((long[]) obj);
    } else if (int.class.equals(componentType)) {
      return Arrays.hashCode((int[]) obj);
    } else if (short.class.equals(componentType)) {
      return Arrays.hashCode((short[]) obj);
    } else if (char.class.equals(componentType)) {
      return Arrays.hashCode((char[]) obj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.hashCode((byte[]) obj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.hashCode((boolean[]) obj);
    } else if (float.class.equals(componentType)) {
      return Arrays.hashCode((float[]) obj);
    } else if (double.class.equals(componentType)) {
      return Arrays.hashCode((double[]) obj);
    } else {
      return Arrays.hashCode((Object[]) obj);
    }
  }

  /**
   * Compares two objects. Returns <code>true</code> if
   * <ul>
   * <li>{@code thisObj} and {@code thatObj} are both <code>null</code></li>
   * <li>{@code thisObj} and {@code thatObj} are instances of the same type and
   * {@link Object#equals(Object)} returns <code>true</code></li>
   * <li>{@code thisObj} and {@code thatObj} are arrays with the same component type and
   * equals() method of {@link Arrays} returns <code>true</code> (not deepEquals())</li>
   * </ul>
   *
   * @param thisObj
   *          The left hand object to compare. May be an array or <code>null</code>
   * @param thatObj
   *          The right hand object to compare. May be an array or <code>null</code>
   * @return <code>true</code> if two objects are equal; <code>false</code> otherwise.
   */
  public static boolean equals(Object thisObj, Object thatObj) {
    if (thisObj == null) {
      return thatObj == null;
    } else if (thatObj == null) {
      return false;
    }
    final Class<?> clazz = thisObj.getClass();
    if (!clazz.equals(thatObj.getClass())) {
      return false;
    }
    // 普通类
    if (!clazz.isArray()) {
      return thisObj.equals(thatObj);
    }
    // 数组类型
    final Class<?> componentType = clazz.getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.equals((long[]) thisObj, (long[]) thatObj);
    } else if (int.class.equals(componentType)) {
      return Arrays.equals((int[]) thisObj, (int[]) thatObj);
    } else if (short.class.equals(componentType)) {
      return Arrays.equals((short[]) thisObj, (short[]) thatObj);
    } else if (char.class.equals(componentType)) {
      return Arrays.equals((char[]) thisObj, (char[]) thatObj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.equals((byte[]) thisObj, (byte[]) thatObj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.equals((boolean[]) thisObj, (boolean[]) thatObj);
    } else if (float.class.equals(componentType)) {
      return Arrays.equals((float[]) thisObj, (float[]) thatObj);
    } else if (double.class.equals(componentType)) {
      return Arrays.equals((double[]) thisObj, (double[]) thatObj);
    } else {
      return Arrays.equals((Object[]) thisObj, (Object[]) thatObj);
    }
  }

  /**
   * If the {@code obj} is an array, toString() method of {@link Arrays} is called. Otherwise
   * {@link Object#toString()} is called. Returns "null" if {@code obj} is <code>null</code>.
   *
   * @param obj
   *          An object. May be an array or <code>null</code>.
   * @return String representation of the {@code obj}.
   */
  public static String toString(Object obj) {
    if (obj == null) {
      return "null";
    }
    // 普通类
    final Class<?> clazz = obj.getClass();
    if (!clazz.isArray()) {
      return obj.toString();
    }
    // 数组类型
    final Class<?> componentType = obj.getClass().getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.toString((long[]) obj);
    } else if (int.class.equals(componentType)) {
      return Arrays.toString((int[]) obj);
    } else if (short.class.equals(componentType)) {
      return Arrays.toString((short[]) obj);
    } else if (char.class.equals(componentType)) {
      return Arrays.toString((char[]) obj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.toString((byte[]) obj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.toString((boolean[]) obj);
    } else if (float.class.equals(componentType)) {
      return Arrays.toString((float[]) obj);
    } else if (double.class.equals(componentType)) {
      return Arrays.toString((double[]) obj);
    } else {
      return Arrays.toString((Object[]) obj);
    }
  }
}
 ```
 
# ExceptionUtil
`org.apache.ibatis.reflection.ExceptionUtil`，异常工具类。代码如下：
```java
public class ExceptionUtil {

  private ExceptionUtil() {
    // Prevent Instantiation
  }

  /**
   * 去掉异常的包装
   *
   * @param wrapped 被包装的异常
   * @return 去除包装后的异常
   */
  public static Throwable unwrapThrowable(Throwable wrapped) {
    Throwable unwrapped = wrapped;
    while (true) {
      if (unwrapped instanceof InvocationTargetException) {
        unwrapped = ((InvocationTargetException) unwrapped).getTargetException();
      } else if (unwrapped instanceof UndeclaredThrowableException) {
        unwrapped = ((UndeclaredThrowableException) unwrapped).getUndeclaredThrowable();
      } else {
        return unwrapped;
      }
    }
  }

```


***未完待续***