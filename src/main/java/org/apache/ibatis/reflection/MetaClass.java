/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  /**
   * 根据类类型创建 MetaClass 实例
   * @param type 类类型
   * @param reflectorFactory 反射器工厂
   * @return MetaClass 实例
   */
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 根据属性(对象属性)创建MetaClass对象
   * @param name 属性名称
   * @return MetaClass 实例
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

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

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

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

  /**
   * 判断指定属性是否具有getter方法
   * @param name 属性名称
   * @return 是否
   */
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

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

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

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

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
