/**
 *    Copyright 2009-2019 the original author or authors.
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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * @author Clinton Begin
 */
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
    } else if (objectWrapperFactory.hasWrapperFor(object)) {  // <2>
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

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

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

  /**
   * 创建指定属性的 MetaObject 对象
   * @param name 属性名
   * @return MetaObject 对象
   */
  public MetaObject metaObjectForProperty(String name) {
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

  public void add(Object element) {
    objectWrapper.add(element);
  }

  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
