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
package org.apache.ibatis.reflection.property;

import java.lang.reflect.Field;

import org.apache.ibatis.reflection.Reflector;

/**
 * @author Clinton Begin
 */
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
