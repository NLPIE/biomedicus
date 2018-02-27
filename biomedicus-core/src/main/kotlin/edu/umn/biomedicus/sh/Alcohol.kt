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

package edu.umn.biomedicus.sh

import edu.umn.nlpengine.*

/**
 * A verb indicating dependants are related to alcohol usage.
 */
@LabelMetadata(versionId = "2_0", distinct = true)
data class AlcoholRelevant(override val startIndex: Int, override val endIndex: Int) : Label() {
    constructor(textRange: TextRange) : this(textRange.startIndex, textRange.endIndex)
}

/**
 * Detects [AlcoholRelevant] labels from [AlcoholCue] labels in text.
 */
class AlcoholRelevantLabeler : DocumentProcessor {
    override fun process(document: Document) {
        val relevants = document.findRelevantAncestors(document.labelIndex<AlcoholCue>())
                .map { AlcoholRelevant(it) }
        document.labelAll(relevants)
    }
}