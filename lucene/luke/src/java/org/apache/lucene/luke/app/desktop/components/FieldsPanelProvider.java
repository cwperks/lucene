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

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.luke.app.IndexHandler;
import org.apache.lucene.luke.app.IndexObserver;
import org.apache.lucene.luke.app.LukeState;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;
import org.apache.lucene.luke.util.BytesRefUtils;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

/** Read-only, bounded field-to-document explorer. */
public final class FieldsPanelProvider implements IndexObserver {
  static final int TERM_LIMIT = 100;
  static final int DOCUMENT_LIMIT = 50;
  private static final int VALUE_PREVIEW_LENGTH = 1000;

  private final DefaultListModel<String> fieldNames = new DefaultListModel<>();
  private final JList<String> fields = new JList<>(fieldNames);
  private final JTextField filter = new JTextField();
  private final JLabel title = new JLabel(message("explorer.select_field"));
  private final JLabel metadata = new JLabel(" ");
  private final JLabel status = new JLabel(message("explorer.select_field"));
  private final JTable terms = table("explorer.term", "explorer.frequency");
  private final JTable documents = table("explorer.document_id");
  private final JTable values = table("explorer.stored_field", "explorer.stored_value");
  private List<String> allFields = List.of();
  private List<TermRow> termRows = List.of();
  private IndexReader reader;
  private long generation;

  public FieldsPanelProvider() {
    fields.setName("explorerFields");
    terms.setName("explorerTerms");
    terms.getColumnModel().getColumn(0).setPreferredWidth(450);
    terms.getColumnModel().getColumn(1).setPreferredWidth(140);
    documents.setName("explorerDocuments");
    values.setName("explorerValues");
    IndexHandler.getInstance().addObserver(this);
    fields.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fields.setFixedCellHeight(36);
    fields.addListSelectionListener(
        event -> {
          if (!event.getValueIsAdjusting()) selectField();
        });
    terms
        .getSelectionModel()
        .addListSelectionListener(
            event -> {
              if (!event.getValueIsAdjusting()) selectTerm();
            });
    documents
        .getSelectionModel()
        .addListSelectionListener(
            event -> {
              if (!event.getValueIsAdjusting()) selectDocument();
            });
    filter
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                filterFields();
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                filterFields();
              }

              @Override
              public void changedUpdate(DocumentEvent event) {
                filterFields();
              }
            });
  }

  public JPanel get() {
    JPanel panel = new JPanel(new BorderLayout(0, 20));
    panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 16, 24));
    JPanel heading = new JPanel(new BorderLayout(0, 6));
    JLabel label = new JLabel(message("explorer.title"));
    label.setFont(label.getFont().deriveFont(Font.BOLD, 28f));
    heading.add(label, BorderLayout.NORTH);
    heading.add(new JLabel(message("explorer.subtitle")), BorderLayout.SOUTH);
    panel.add(heading, BorderLayout.NORTH);

    JPanel browser = new JPanel(new BorderLayout(0, 10));
    filter.putClientProperty("JTextField.placeholderText", message("overview.filter_hint"));
    filter.putClientProperty("JTextField.showClearButton", true);
    filter.getAccessibleContext().setAccessibleName(message("overview.filter_hint"));
    browser.add(filter, BorderLayout.NORTH);
    browser.add(new JScrollPane(fields), BorderLayout.CENTER);

    JPanel inspector = new JPanel(new BorderLayout(0, 16));
    inspector.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
    JPanel fieldHeading = new JPanel(new BorderLayout(0, 8));
    title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
    fieldHeading.add(title, BorderLayout.NORTH);
    fieldHeading.add(metadata, BorderLayout.CENTER);
    inspector.add(fieldHeading, BorderLayout.NORTH);

    JSplitPane results =
        split(
            section("explorer.documents", documents), section("explorer.values", values), 0.3, 150);
    JSplitPane exploration =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, section("explorer.terms", terms), results);
    exploration.setResizeWeight(0.5);
    exploration.setDividerLocation(240);
    exploration.setBorder(BorderFactory.createEmptyBorder());
    inspector.add(exploration, BorderLayout.CENTER);
    panel.add(split(browser, inspector, 0.25, 260), BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    return panel;
  }

  private static JSplitPane split(
      java.awt.Component left, java.awt.Component right, double weight, int location) {
    JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
    pane.setResizeWeight(weight);
    pane.setDividerLocation(location);
    pane.setBorder(BorderFactory.createEmptyBorder());
    return pane;
  }

  private static JPanel section(String key, JTable table) {
    JPanel section = new JPanel(new BorderLayout(0, 8));
    section.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
    section.add(new JLabel(message(key)), BorderLayout.NORTH);
    section.add(new JScrollPane(table), BorderLayout.CENTER);
    return section;
  }

  private static JTable table(String... keys) {
    String[] columns =
        java.util.Arrays.stream(keys).map(FieldsPanelProvider::message).toArray(String[]::new);
    JTable table =
        new JTable(
            new DefaultTableModel(columns, 0) {
              @Override
              public boolean isCellEditable(int row, int column) {
                return false;
              }
            });
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(32);
    table.setShowGrid(false);
    table.setFillsViewportHeight(true);
    return table;
  }

  private static void clear(JTable table) {
    ((DefaultTableModel) table.getModel()).setRowCount(0);
  }

  private void filterFields() {
    fields.clearSelection();
    fieldNames.clear();
    String query = filter.getText().toLowerCase(Locale.ROOT);
    allFields.stream()
        .filter(name -> name.toLowerCase(Locale.ROOT).contains(query))
        .forEach(fieldNames::addElement);
    if (fieldNames.isEmpty()) status.setText(message("explorer.no_fields"));
  }

  private void selectField() {
    generation++;
    termRows = List.of();
    clear(terms);
    clear(documents);
    clear(values);
    String field = fields.getSelectedValue();
    title.setText(field == null ? message("explorer.select_field") : field);
    title.setToolTipText(field);
    metadata.setText(" ");
    if (field == null || reader == null) {
      status.setText(message("explorer.select_field"));
      return;
    }
    var info = FieldInfos.getMergedFieldInfos(reader).fieldInfo(field);
    metadata.setText(
        MessageUtils.getLocalizedMessage(
            "explorer.metadata",
            info.getIndexOptions(),
            info.getDocValuesType(),
            info.getPointDimensionCount(),
            info.getVectorDimension()));
    IndexReader current = reader;
    load(
        () -> readTerms(current, field),
        rows -> {
          termRows = rows;
          var model = (DefaultTableModel) terms.getModel();
          for (TermRow row : rows)
            model.addRow(new Object[] {BytesRefUtils.decode(row.term().bytes()), row.frequency()});
          status.setText(message(rows.isEmpty() ? "explorer.no_terms" : "explorer.terms_ready"));
        });
  }

  private void selectTerm() {
    generation++;
    clear(documents);
    clear(values);
    int row = terms.getSelectedRow();
    if (row < 0 || row >= termRows.size() || reader == null) return;
    Term term = termRows.get(row).term();
    IndexReader current = reader;
    load(
        () -> findDocuments(current, term),
        ids -> {
          var model = (DefaultTableModel) documents.getModel();
          for (int id : ids) model.addRow(new Object[] {id});
          status.setText(
              message(ids.isEmpty() ? "explorer.no_documents" : "explorer.documents_ready"));
        });
  }

  private void selectDocument() {
    generation++;
    clear(values);
    int row = documents.getSelectedRow();
    if (row < 0 || reader == null) return;
    int id = (Integer) documents.getValueAt(row, 0);
    IndexReader current = reader;
    load(
        () -> {
          List<Object[]> result = new ArrayList<>();
          for (var field : current.storedFields().document(id)) {
            String value =
                field.binaryValue() != null
                    ? BytesRefUtils.decode(field.binaryValue())
                    : field.numericValue() != null
                        ? field.numericValue().toString()
                        : field.stringValue();
            if (value != null && value.length() > VALUE_PREVIEW_LENGTH)
              value = value.substring(0, VALUE_PREVIEW_LENGTH) + "…";
            result.add(new Object[] {field.name(), value});
          }
          return result;
        },
        rows -> {
          var model = (DefaultTableModel) values.getModel();
          rows.forEach(model::addRow);
          status.setText(
              message(rows.isEmpty() ? "explorer.no_stored_values" : "explorer.values_ready"));
        });
  }

  private <T> void load(Callable<T> task, Consumer<T> display) {
    IndexReader current = reader;
    long request = generation;
    if (current == null || !current.tryIncRef()) return;
    status.setText(message("explorer.loading"));
    new SwingWorker<T, Void>() {
      @Override
      protected T doInBackground() throws Exception {
        try {
          return task.call();
        } finally {
          current.decRef();
        }
      }

      @Override
      protected void done() {
        if (request != generation || current != reader) return;
        try {
          display.accept(get());
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
          status.setText(message("explorer.interrupted"));
        } catch (ExecutionException e) {
          status.setText(
              MessageUtils.getLocalizedMessage(
                  "explorer.error", e.getCause().getClass().getSimpleName()));
        }
      }
    }.execute();
  }

  static List<TermRow> readTerms(IndexReader reader, String field) throws IOException {
    List<TermRow> result = new ArrayList<>();
    var fieldTerms = MultiTerms.getTerms(reader, field);
    if (fieldTerms == null) return result;
    var iterator = fieldTerms.iterator();
    BytesRef bytes;
    while (result.size() < TERM_LIMIT && (bytes = iterator.next()) != null) {
      result.add(new TermRow(new Term(field, BytesRef.deepCopyOf(bytes)), iterator.docFreq()));
    }
    return result;
  }

  static List<Integer> findDocuments(IndexReader reader, Term term) throws IOException {
    var hits = new IndexSearcher(reader).search(new TermQuery(term), DOCUMENT_LIMIT);
    List<Integer> result = new ArrayList<>();
    for (var hit : hits.scoreDocs) result.add(hit.doc);
    return result;
  }

  record TermRow(Term term, int frequency) {}

  @Override
  public void openIndex(LukeState state) {
    closeIndex();
    reader = state.getIndexReader();
    List<String> names = new ArrayList<>();
    for (var info : FieldInfos.getMergedFieldInfos(reader)) names.add(info.name);
    allFields = names.stream().sorted().toList();
    filterFields();
  }

  @Override
  public void closeIndex() {
    generation++;
    reader = null;
    allFields = List.of();
    filter.setText("");
    filterFields();
    termRows = List.of();
    clear(terms);
    clear(documents);
    clear(values);
    title.setText(message("explorer.select_field"));
    metadata.setText(" ");
    status.setText(message("explorer.select_field"));
  }

  private static String message(String key) {
    return MessageUtils.getLocalizedMessage(key);
  }
}
