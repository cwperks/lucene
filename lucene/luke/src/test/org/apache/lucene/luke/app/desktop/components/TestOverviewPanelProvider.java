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

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Component;
import java.awt.Container;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.luke.app.IndexHandler;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;

@LuceneTestCase.SuppressFileSystems("ExtrasFS")
public class TestOverviewPanelProvider extends LuceneTestCase {

  private static LookAndFeel previousLookAndFeel;

  public void testExperimentalContentDoesNotInheritDarkNavigationBackground() throws Exception {
    org.apache.lucene.luke.util.LoggerFactory.initGuiLogging();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            var tabs = new TabbedPaneProvider(true).get();
            var nestedTabs = new javax.swing.JTabbedPane();
            var originalUI = nestedTabs.getUI();
            org.apache.lucene.luke.app.desktop.util.TabUtils.forceTransparent(nestedTabs);
            assertSame(originalUI, nestedTabs.getUI());
            for (int i = 0; i < tabs.getTabCount(); i++) {
              var content = (javax.swing.JPanel) tabs.getComponentAt(i);
              assertTrue(tabs.getTitleAt(i), content.isOpaque());
              assertEquals(UIManager.getColor("Panel.background"), content.getBackground());
              assertNotEquals(tabs.getBackground(), content.getBackground());
            }
          } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        });
    var icon = org.apache.lucene.luke.app.desktop.util.ImageUtils.createApplicationIcon();
    assertEquals(256, icon.getWidth(null));
    assertEquals(256, icon.getHeight(null));
  }

  public void testDefaultLayoutAndNavigationIndices() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var panel = new OverviewPanelProvider().get();
          assertNull(
              findComponent(
                  panel, JTextField.class, field -> "fieldFilter".equals(field.getName())));
          assertTrue(panel.getComponent(0) instanceof javax.swing.JSplitPane);
          assertEquals(
              javax.swing.JSplitPane.VERTICAL_SPLIT,
              ((javax.swing.JSplitPane) panel.getComponent(0)).getOrientation());
        });
    assertEquals(0, TabbedPaneProvider.Tab.OVERVIEW.index(false));
    assertEquals(1, TabbedPaneProvider.Tab.DOCUMENTS.index(false));
    assertEquals(2, TabbedPaneProvider.Tab.SEARCH.index(false));
    assertEquals(3, TabbedPaneProvider.Tab.ANALYZER.index(false));
    assertEquals(4, TabbedPaneProvider.Tab.COMMITS.index(false));
    assertEquals(1, TabbedPaneProvider.Tab.FIELDS.index(true));
    assertEquals(2, TabbedPaneProvider.Tab.DOCUMENTS.index(true));
    expectThrows(IllegalArgumentException.class, () -> TabbedPaneProvider.Tab.FIELDS.index(false));
  }

  @BeforeClass
  public static void installTheme() throws Exception {
    previousLookAndFeel = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new FlatLightLaf());
  }

  @AfterClass
  public static void restoreTheme() throws Exception {
    UIManager.setLookAndFeel(previousLookAndFeel);
    previousLookAndFeel = null;
  }

  public void testFieldSelectionWithoutMouseClick() throws Exception {
    var indexPath = createTempDir();
    try (var directory = FSDirectory.open(indexPath);
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      var document = new Document();
      document.add(new StringField("city", "Toronto", Field.Store.NO));
      document.add(new StringField("country", "Canada", Field.Store.NO));
      writer.addDocument(document);
    }
    SwingUtilities.invokeAndWait(
        () -> {
          var panel = new OverviewPanelProvider(true).get();
          var table = findTermCountsTable(panel);
          var selectedField = findComponent(panel, JTextField.class, field -> !field.isEditable());
          var showTerms =
              findComponent(
                  panel,
                  JButton.class,
                  button ->
                      MessageUtils.getLocalizedMessage("overview.button.show_terms")
                          .equals(button.getText()));
          var handler = IndexHandler.getInstance();
          try {
            handler.open(indexPath.toString(), FSDirectory.class.getName());
            assertFalse(showTerms.isEnabled());
            var filter =
                findComponent(
                    panel, JTextField.class, field -> "fieldFilter".equals(field.getName()));
            table.getRowSorter().toggleSortOrder(0);
            filter.setText("COUNTRY");
            assertEquals(1, table.getRowCount());
            table.setRowSelectionInterval(0, 0);
            assertEquals("country", selectedField.getText());
            showTerms.doClick();
            var filteredTerms =
                findComponent(
                    panel,
                    JTable.class,
                    candidate ->
                        candidate.getModel() instanceof OverviewPanelProvider.TopTermsTableModel);
            assertEquals("Canada", filteredTerms.getValueAt(0, 2));
            filter.setText("[");
            assertEquals(0, table.getRowCount());
            assertEquals("", selectedField.getText());
            assertFalse(showTerms.isEnabled());
            filter.setText("");
            assertEquals(2, table.getRowCount());
            table.setRowSelectionInterval(0, 0);
            assertEquals(table.getValueAt(0, 0), selectedField.getText());
            assertTrue(showTerms.isEnabled());
            var terms =
                findComponent(
                    panel,
                    JTable.class,
                    candidate ->
                        candidate.getModel() instanceof OverviewPanelProvider.TopTermsTableModel);
            showTerms.doClick();
            assertEquals(1, terms.getRowCount());
            // Use the same action as the Down arrow, without dispatching a mouse click.
            table
                .getActionMap()
                .get("selectNextRow")
                .actionPerformed(new java.awt.event.ActionEvent(table, 0, "selectNextRow"));
            assertEquals(1, table.getSelectedRow());
            assertEquals(table.getValueAt(1, 0), selectedField.getText());
            assertEquals(0, terms.getRowCount());
            table.clearSelection();
            assertEquals("", selectedField.getText());
            assertFalse(showTerms.isEnabled());
            filter.setText("no match");
            handler.reOpen();
            assertEquals("", filter.getText());
            assertEquals(2, table.getRowCount());
          } finally {
            handler.close();
          }
        });
  }

  private static <T extends Component> T findComponent(
      Component component, Class<T> type, Predicate<T> matches) {
    if (type.isInstance(component) && matches.test(type.cast(component))) {
      return type.cast(component);
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        T found = findComponent(child, type, matches);
        if (found != null) return found;
      }
    }
    return null;
  }

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
          var panel = new OverviewPanelProvider(true).get();
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
