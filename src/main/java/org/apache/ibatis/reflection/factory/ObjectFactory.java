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
package org.apache.ibatis.reflection.factory;

import java.util.List;
import java.util.Properties;

/**
 * MyBatis uses an ObjectFactory to create all needed new Objects.
 *
 * @author Clinton Begin
 */
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
