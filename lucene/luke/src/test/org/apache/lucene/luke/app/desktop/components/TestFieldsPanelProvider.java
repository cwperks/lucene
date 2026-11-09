/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.luke.app.desktop.components;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestFieldsPanelProvider extends LuceneTestCase {
  public void testPreviewLimitsAndFieldsWithoutTerms() throws Exception {
    try (var directory = newDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      for (int i = 0; i < 120; i++) {
        var document = new Document();
        document.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        document.add(new StringField("shared", "value", Field.Store.NO));
        document.add(new NumericDocValuesField("number", i));
        writer.addDocument(document);
      }
      try (var reader = DirectoryReader.open(writer)) {
        var terms = FieldsPanelProvider.readTerms(reader, "id");
        assertEquals(FieldsPanelProvider.TERM_LIMIT, terms.size());
        for (int i = 1; i < terms.size(); i++) {
          assertTrue(terms.get(i - 1).term().bytes().compareTo(terms.get(i).term().bytes()) < 0);
        }
        assertTrue(FieldsPanelProvider.readTerms(reader, "number").isEmpty());
        assertTrue(FieldsPanelProvider.readTerms(reader, "missing").isEmpty());
        assertEquals(
            FieldsPanelProvider.DOCUMENT_LIMIT,
            FieldsPanelProvider.findDocuments(reader, new Term("shared", "value")).size());
      }
    }
  }

  public void testExactBinaryTermAndDeletedDocuments() throws Exception {
    BytesRef bytes = new BytesRef(new byte[] {(byte) 0xff, 0, 1});
    try (var directory = newDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      for (String id : new String[] {"keep", "delete"}) {
        var document = new Document();
        document.add(new StringField("id", id, Field.Store.YES));
        document.add(new StringField("binary", bytes, Field.Store.NO));
        writer.addDocument(document);
      }
      writer.deleteDocuments(new Term("id", "delete"));
      try (var reader = DirectoryReader.open(writer)) {
        var terms = FieldsPanelProvider.readTerms(reader, "binary");
        assertEquals(1, terms.size());
        assertEquals(bytes, terms.getFirst().term().bytes());
        var ids = FieldsPanelProvider.findDocuments(reader, terms.getFirst().term());
        assertEquals(1, ids.size());
        assertEquals("keep", reader.storedFields().document(ids.getFirst()).get("id"));
        assertTrue(FieldsPanelProvider.findDocuments(reader, new Term("id", "absent")).isEmpty());
      }
    }
  }
}
