/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.pf4j.Extension;
import org.pf4j.ExtensionDescriptor;
import org.pf4j.ExtensionFinder;
import org.pf4j.ExtensionWrapper;

/**
 * This {@link ExtensionFinder} tries to load extensions using {@link ServiceLoader}.
 *
 * <p>Right now it doesn't support loading extensions from plugins, it only loads extensions from
 * classpath.
 *
 * <p>The extensions are the classes annotated with the {@link Extension} annotation.
 */
class BuckExtensionFinder implements ExtensionFinder {

  @Override
  public <T> List<ExtensionWrapper<T>> find(Class<T> type) {
    ServiceLoader<T> serviceLoader = ServiceLoader.load(type);

    List<ExtensionWrapper<T>> extensions = new ArrayList<>();
    serviceLoader.forEach(extension -> extensions.add(createExtensionWrapper(extension)));
    return extensions;
  }

  @Override
  public <T> List<ExtensionWrapper<T>> find(Class<T> type, String pluginId) {
    throw new UnsupportedOperationException();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public List<ExtensionWrapper> find(String pluginId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> findClassNames(String pluginId) {
    throw new UnsupportedOperationException();
  }

  private <T> ExtensionWrapper<T> createExtensionWrapper(T extension) {
    int ordinal = 0;
    Class<?> extensionClass = extension.getClass();
    if (extensionClass.isAnnotationPresent(Extension.class)) {
      ordinal = extensionClass.getAnnotation(Extension.class).ordinal();
    }
    ExtensionDescriptor descriptor = new ExtensionDescriptor(ordinal, extensionClass);
    return new ExtensionWrapper<T>(descriptor, (cls) -> extension);
  }
}
