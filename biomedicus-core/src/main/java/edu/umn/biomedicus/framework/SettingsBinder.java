/*
 * Copyright (c) 2018 Regents of the University of Minnesota.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umn.biomedicus.framework;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds a set of settings to a Guice module.
 *
 */
class SettingsBinder {

  private static final Logger LOGGER = LoggerFactory.getLogger(SettingsBinder.class);
  private final Path dataPath;
  private final Path homePath;
  private final Path confPath;
  private final Map<String, Object> settings = new HashMap<>();

  private Binder binder;

  private SettingsBinder(Path dataPath, Path confPath, Path homePath) {
    this.dataPath = dataPath;
    this.homePath = homePath;
    this.confPath = confPath;
  }

  static SettingsBinder create(Path dataPath, Path confPath, Path homePath) {
    return new SettingsBinder(dataPath, confPath, homePath);
  }

  void addSettings(Map<?, ?> settings) {
    settings.forEach(this::addSetting);
  }

  void addSetting(Object key, Object value) {
    if (key instanceof String) {
      this.settings.put((String) key, value);
    } else {
      throw new IllegalStateException("Setting key without String type: " + key);
    }
  }

  private void performBindings(Binder binder) {
    this.binder = binder;
    binder.bind(new TypeLiteral<Map<String, Object>>() {
    }).annotatedWith(Names.named("globalSettings")).toInstance(settings);

    binder.bind(new TypeLiteral<Map<String, ?>>() {
    }).annotatedWith(Names.named("globalSettings")).toInstance(settings);

    binder.bind(Path.class).annotatedWith(new SettingImpl("paths.data")).toInstance(dataPath);
    binder.bind(Path.class).annotatedWith(new SettingImpl("paths.home")).toInstance(homePath);
    binder.bind(Path.class).annotatedWith(new SettingImpl("paths.conf")).toInstance(confPath);

    SettingsTransformer settingsTransformer = new SettingsTransformer(dataPath);
    settingsTransformer.setAnnotationFunction(SettingImpl::new);
    settingsTransformer.addAll(settings);
    settingsTransformer.getSettings().forEach(this::bindSetting);
  }

  private <T> void bindSetting(Key<T> key, Object value) {
    if (value instanceof Key) {
      @SuppressWarnings("unchecked")
      Key<T> valueKey = (Key<T>) value;
      binder.bind(key).to(valueKey);
    } else {
      TypeLiteral<T> typeLiteral = key.getTypeLiteral();
      Class<? super T> rawType = typeLiteral.getRawType();
      @SuppressWarnings("unchecked")
      T cast = (T) rawType.cast(value);
      binder.bind(key).toInstance(cast);
    }
  }

  Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        performBindings(binder());
      }
    };
  }
}
