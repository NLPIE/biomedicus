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

package edu.umn.biomedicus.concepts;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import edu.umn.biomedicus.annotations.Setting;
import edu.umn.biomedicus.common.dictionary.StringsBag;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DataLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ConceptDictionaryLoader extends DataLoader<ConceptDictionary> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConceptDictionaryLoader.class);

  private final Path dbPath;

  private final boolean inMemory;

  @Inject
  ConceptDictionaryLoader(@Setting("concepts.db.asDataPath") Path dbPath,
      @Setting("concepts.inMemory") boolean inMemory) {
    this.dbPath = dbPath;
    this.inMemory = inMemory;
  }

  @Override
  protected ConceptDictionary loadModel() throws BiomedicusException {
    RocksDB.loadLibrary();

    try (Options options = new Options().setInfoLogLevel(InfoLogLevel.ERROR_LEVEL)) {
      LOGGER.info("Opening concepts dictionary: {}. inMemory = {}.", dbPath, inMemory);

      RocksDB phrasesDB = RocksDB.openReadOnly(options, dbPath.resolve("phrases").toString());
      RocksDB lowercaseDB = RocksDB.openReadOnly(options, dbPath.resolve("lowercase").toString());
      RocksDB normsDB = RocksDB.openReadOnly(options, dbPath.resolve("norms").toString());
      Map<Integer, String> sources = new HashMap<>();

      Files.lines(dbPath.resolve("sources.txt")).forEach(s -> sources.put(sources.size(), s));


      if (inMemory) {
        LOGGER.info("Loading concepts phrases into memory.");
        final Map<String, List<ConceptRow>> phrases = new HashMap<>();
        dumpToMap(phrasesDB, phrases, String::new);

        LOGGER.info("Loading concepts lowercases into memory.");
        final Map<String, List<ConceptRow>> lowercasePhrases = new HashMap<>();
        dumpToMap(lowercaseDB, lowercasePhrases, String::new);

        LOGGER.info("Loading concepts norms into memory.");
        final Map<StringsBag, List<ConceptRow>> normDictionary = new HashMap<>();
        dumpToMap(normsDB, normDictionary, StringsBag::new);

        LOGGER.info("Done loading concepts into memory.");

        return new ConceptDictionary () {
          @Override
          @Nullable
          public List<ConceptRow> forPhrase(String phrase) {
            return phrases.get(phrase);
          }

          @Override
          @Nullable
          public List<ConceptRow> forLowercasePhrase(String phrase) {
            return lowercasePhrases.get(phrase);
          }

          @Override
          @Nullable
          public List<ConceptRow> forNorms(StringsBag norms) {
            if (norms.uniqueTerms() == 0) {
              return null;
            }
            return normDictionary.get(norms);
          }

          @Override
          public String source(int identifier) {
            return sources.get(identifier);
          }
        };

      }

      LOGGER.info("Done opening concepts dictionary.");

      return new RocksDbConceptDictionary(phrasesDB, lowercaseDB, normsDB, sources);
    } catch (RocksDBException | IOException e) {
      throw new BiomedicusException(e);
    }
  }

  private static <T> void dumpToMap(RocksDB db, Map<T, List<ConceptRow>> suiCuiTuis,
      Function<byte[], T> keyMapper) {
    try (RocksIterator rocksIterator = db.newIterator()) {
      rocksIterator.seekToFirst();
      while (rocksIterator.isValid()) {
        byte[] keyBytes = rocksIterator.key();
        T key = keyMapper.apply(keyBytes);
        suiCuiTuis.put(key, RocksDbConceptDictionary.toList(rocksIterator.value()));
        rocksIterator.next();
      }
    }

    db.close();
  }
}
