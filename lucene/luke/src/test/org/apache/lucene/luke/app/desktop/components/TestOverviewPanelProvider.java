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

import java.awt.Component;
import java.awt.Container;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.luke.app.IndexHandler;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestOverviewPanelProvider extends LuceneTestCase {

  public void testFieldNameColumnCanGrowAfterOpeningIndex() throws Exception {
    var indexPath = createTempDir();
    String fieldName = "provider.address.city.country.name";
    try (var directory = FSDirectory.open(indexPath);
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      var document = new Document();
      document.add(new StringField(fieldName, "test", Field.Store.NO));
      writer.addDocument(document);
    }

    SwingUtilities.invokeAndWait(
        () -> {
          var panel = new OverviewPanelProvider().get();
          var table = findTermCountsTable(panel);
          assertNotNull(table);
          var handler = IndexHandler.getInstance();
          try {
            // Opening another index rebuilds the table columns, so check both opens.
            for (int i = 0; i < 2; i++) {
              handler.open(indexPath.toString(), FSDirectory.class.getName());
              assertEquals(fieldName, table.getValueAt(0, 0));
              var column = table.getColumnModel().getColumn(0);
              int width = table.getFontMetrics(table.getFont()).stringWidth(fieldName) + 20;
              assertTrue(width > 150);
              column.setWidth(width);
              assertEquals(width, column.getWidth());
            }
          } finally {
            handler.close();
          }
        });
  }

  private static JTable findTermCountsTable(Component component) {
    if (component instanceof JTable table
        && table.getModel() instanceof OverviewPanelProvider.TermCountsTableModel) {
      return table;
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        JTable table = findTermCountsTable(child);
        if (table != null) {
          return table;
        }
      }
    }
    return null;
  }
}
