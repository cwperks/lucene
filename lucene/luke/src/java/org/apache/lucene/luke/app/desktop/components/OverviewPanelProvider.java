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
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import org.apache.lucene.luke.app.IndexHandler;
import org.apache.lucene.luke.app.IndexObserver;
import org.apache.lucene.luke.app.LukeState;
import org.apache.lucene.luke.app.desktop.MessageBroker;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;
import org.apache.lucene.luke.app.desktop.util.StyleConstants;
import org.apache.lucene.luke.app.desktop.util.TableUtils;
import org.apache.lucene.luke.models.overview.Overview;
import org.apache.lucene.luke.models.overview.OverviewFactory;
import org.apache.lucene.luke.models.overview.TermCountsOrder;
import org.apache.lucene.luke.models.overview.TermStats;

/** Provider of the Overview panel */
public final class OverviewPanelProvider {
  private final boolean experimentalUi;

  private final OverviewFactory overviewFactory = new OverviewFactory();

  private final ComponentOperatorRegistry operatorRegistry;

  private final TabSwitcherProxy tabSwitcher;

  private final MessageBroker messageBroker;

  private final JPanel panel = new JPanel();

  private final JLabel indexPathLbl = new JLabel();

  private final JLabel numFieldsLbl = new JLabel();

  private final JLabel numDocsLbl = new JLabel();

  private final JLabel numTermsLbl = new JLabel();

  private final JLabel delOptLbl = new JLabel();

  private final JLabel indexVerLbl = new JLabel();

  private final JLabel indexFmtLbl = new JLabel();

  private final JLabel dirImplLbl = new JLabel();

  private final JLabel commitPointLbl = new JLabel();

  private final JLabel commitUserDataLbl = new JLabel();

  private final JTable termCountsTable = new JTable();
  private final JTextField fieldFilter = new JTextField();
  private final JLabel fieldCount = new JLabel();
  private final JLabel termsHint = new JLabel();

  private final JTextField selectedField = new JTextField();

  private final JButton showTopTermsBtn = new JButton();

  private final JSpinner numTopTermsSpnr = new JSpinner();

  private final JTable topTermsTable = new JTable();

  private final JPopupMenu topTermsContextMenu = new JPopupMenu();

  private final ListenerFunctions listeners = new ListenerFunctions();

  private Overview overviewModel;

  public OverviewPanelProvider() {
    this(false);
  }

  public OverviewPanelProvider(boolean experimentalUi) {
    this.experimentalUi = experimentalUi;
    this.messageBroker = MessageBroker.getInstance();
    this.operatorRegistry = ComponentOperatorRegistry.getInstance();
    this.tabSwitcher = TabSwitcherProxy.getInstance();

    termCountsTable
        .getSelectionModel()
        .addListSelectionListener(
            event -> {
              if (!event.getValueIsAdjusting()) {
                selectField();
              }
            });

    IndexHandler.getInstance().addObserver(new Observer());
  }

  public JPanel get() {
    if (!experimentalUi) return classicOverview();
    panel.setLayout(new BorderLayout(0, 16));
    panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    panel.add(initHeader(), BorderLayout.NORTH);

    JSplitPane workspace =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, initTermCountsPanel(), initTopTermsPanel());
    workspace.setResizeWeight(0.35);
    workspace.setDividerLocation(340);
    workspace.setBorder(BorderFactory.createEmptyBorder());
    panel.add(workspace, BorderLayout.CENTER);
    setUpTopTermsContextMenu();
    return panel;
  }

  private JPanel classicOverview() {
    panel.setOpaque(false);
    panel.setLayout(new GridLayout(1, 1));
    panel.setBorder(BorderFactory.createLineBorder(java.awt.Color.gray));
    JPanel details = new JPanel(new java.awt.GridBagLayout());
    details.setOpaque(false);
    String[] keys = {
      "index_path",
      "num_fields",
      "num_docs",
      "num_terms",
      "del_opt",
      "index_version",
      "index_format",
      "dir_impl",
      "commit_point",
      "commit_userdata"
    };
    JLabel[] values = {
      indexPathLbl,
      numFieldsLbl,
      numDocsLbl,
      numTermsLbl,
      delOptLbl,
      indexVerLbl,
      indexFmtLbl,
      dirImplLbl,
      commitPointLbl,
      commitUserDataLbl
    };
    var constraints = new java.awt.GridBagConstraints();
    constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    constraints.insets = new java.awt.Insets(2, 10, 2, 2);
    for (int row = 0; row < keys.length; row++) {
      constraints.gridy = row;
      constraints.gridx = 0;
      constraints.weightx = 0.1;
      details.add(
          new JLabel(MessageUtils.getLocalizedMessage("overview.label." + keys[row]), JLabel.RIGHT),
          constraints);
      constraints.gridx = 1;
      constraints.weightx = 0.9;
      values[row].setText("?");
      details.add(values[row], constraints);
    }
    JPanel lower = new JPanel(new BorderLayout());
    lower.setOpaque(false);
    lower.add(
        new JLabel(MessageUtils.getLocalizedMessage("overview.label.select_fields")),
        BorderLayout.NORTH);
    var fieldsAndTerms =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, initTermCountsPanel(), initTopTermsPanel());
    fieldsAndTerms.setDividerLocation(320);
    fieldsAndTerms.setOpaque(false);
    fieldsAndTerms.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    lower.add(fieldsAndTerms, BorderLayout.CENTER);
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, details, lower);
    split.setDividerLocation(0.4);
    split.setOpaque(false);
    panel.add(split);
    setUpTopTermsContextMenu();
    return panel;
  }

  private JPanel initHeader() {
    JPanel header = new JPanel(new BorderLayout(0, 12));
    JLabel title = new JLabel(MessageUtils.getLocalizedMessage("overview.title"));
    title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 24f));
    JPanel heading = new JPanel(new BorderLayout(0, 4));
    heading.add(title, BorderLayout.NORTH);
    indexPathLbl.setForeground(UIManager.getColor("Label.disabledForeground"));
    heading.add(indexPathLbl, BorderLayout.CENTER);
    header.add(heading, BorderLayout.NORTH);

    JPanel statistics = new JPanel(new GridLayout(1, 3, 16, 0));
    statistics.add(statistic("overview.documents", numDocsLbl));
    statistics.add(statistic("overview.fields", numFieldsLbl));
    statistics.add(statistic("overview.terms", numTermsLbl));

    JPanel details = new JPanel(new GridLayout(0, 2, 16, 6));
    addDetail(details, "overview.label.del_opt", delOptLbl);
    addDetail(details, "overview.label.index_version", indexVerLbl);
    addDetail(details, "overview.label.index_format", indexFmtLbl);
    addDetail(details, "overview.label.dir_impl", dirImplLbl);
    addDetail(details, "overview.label.commit_point", commitPointLbl);
    addDetail(details, "overview.label.commit_userdata", commitUserDataLbl);
    details.setVisible(false);

    javax.swing.JToggleButton toggle =
        new javax.swing.JToggleButton(MessageUtils.getLocalizedMessage("overview.details"));
    toggle.addActionListener(
        _ -> {
          details.setVisible(toggle.isSelected());
          panel.revalidate();
        });
    JPanel summary = new JPanel(new BorderLayout(0, 12));
    summary.add(statistics, BorderLayout.NORTH);
    JPanel disclosure = new JPanel(new BorderLayout(0, 8));
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    controls.add(toggle);
    disclosure.add(controls, BorderLayout.NORTH);
    disclosure.add(details, BorderLayout.CENTER);
    summary.add(disclosure, BorderLayout.CENTER);
    header.add(summary, BorderLayout.CENTER);
    return header;
  }

  private JPanel statistic(String key, JLabel value) {
    JPanel card = new JPanel(new BorderLayout(0, 6));
    card.setBackground(UIManager.getColor("Table.background"));
    card.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)));
    value.setFont(value.getFont().deriveFont(java.awt.Font.BOLD, 22f));
    card.add(new JLabel(MessageUtils.getLocalizedMessage(key)), BorderLayout.NORTH);
    card.add(value, BorderLayout.CENTER);
    return card;
  }

  private void addDetail(JPanel details, String key, JLabel value) {
    details.add(new JLabel(MessageUtils.getLocalizedMessage(key)));
    details.add(value);
  }

  private JPanel initTermCountsPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(false);

    JLabel label =
        new JLabel(
            MessageUtils.getLocalizedMessage(
                experimentalUi
                    ? "overview.label.available_fields"
                    : "overview.legacy.available_fields"));
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    JPanel heading = new JPanel(new BorderLayout(0, 8));
    heading.add(label, BorderLayout.NORTH);
    label.setLabelFor(fieldFilter);
    fieldFilter.setName("fieldFilter");
    fieldFilter.putClientProperty(
        "JTextField.placeholderText", MessageUtils.getLocalizedMessage("overview.filter_hint"));
    fieldFilter.putClientProperty("JTextField.showClearButton", true);
    fieldFilter
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
    heading.add(fieldFilter, BorderLayout.CENTER);
    heading.add(fieldCount, BorderLayout.SOUTH);
    panel.add(experimentalUi ? heading : label, BorderLayout.PAGE_START);

    TableUtils.setupTable(
        termCountsTable,
        ListSelectionModel.SINGLE_SELECTION,
        new TermCountsTableModel(),
        null,
        TermCountsTableModel.Column.NAME.getColumnWidth(),
        TermCountsTableModel.Column.TERM_COUNT.getColumnWidth());
    if (experimentalUi) {
      termCountsTable.setFont(UIManager.getFont("Table.font"));
      termCountsTable.setRowHeight(
          termCountsTable.getFontMetrics(termCountsTable.getFont()).getHeight() + 10);
      termCountsTable.setShowGrid(false);
    }
    JScrollPane scrollPane = new JScrollPane(termCountsTable);
    panel.add(scrollPane, BorderLayout.CENTER);

    panel.setOpaque(false);
    return panel;
  }

  private JPanel initTopTermsPanel() {
    JPanel termsPanel = new JPanel(new BorderLayout(0, 12));
    termsPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
    JPanel header = new JPanel(new BorderLayout(0, 8));
    selectedField.setEditable(false);
    if (experimentalUi)
      selectedField.setFont(selectedField.getFont().deriveFont(java.awt.Font.BOLD, 16f));
    selectedField.putClientProperty(
        "JTextField.placeholderText", MessageUtils.getLocalizedMessage("overview.select_hint"));
    selectedField
        .getAccessibleContext()
        .setAccessibleName(MessageUtils.getLocalizedMessage("overview.label.selected_field"));
    header.add(selectedField, BorderLayout.NORTH);

    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
    numTopTermsSpnr.setModel(new SpinnerNumberModel(50, 0, 1000, 1));
    JLabel limit =
        new JLabel(
            MessageUtils.getLocalizedMessage(
                experimentalUi ? "overview.label.num_top_terms" : "overview.legacy.num_top_terms"));
    limit.setLabelFor(numTopTermsSpnr);
    toolbar.add(limit);
    toolbar.add(numTopTermsSpnr);
    showTopTermsBtn.setText(MessageUtils.getLocalizedMessage("overview.button.show_terms"));
    showTopTermsBtn.addActionListener(listeners::showTopTerms);
    showTopTermsBtn.setEnabled(false);
    toolbar.add(showTopTermsBtn);
    header.add(toolbar, BorderLayout.CENTER);
    termsPanel.add(header, BorderLayout.NORTH);

    TableUtils.setupTable(
        topTermsTable,
        ListSelectionModel.SINGLE_SELECTION,
        new TopTermsTableModel(),
        null,
        TopTermsTableModel.Column.RANK.getColumnWidth(),
        TopTermsTableModel.Column.FREQ.getColumnWidth());
    if (experimentalUi) {
      topTermsTable.setFont(UIManager.getFont("Table.font"));
      topTermsTable.setRowHeight(
          topTermsTable.getFontMetrics(topTermsTable.getFont()).getHeight() + 12);
      topTermsTable.setShowGrid(false);
    }
    topTermsTable.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            listeners.showTopTermsContextMenu(event);
          }
        });
    if (!experimentalUi) {
      JPanel controls = new JPanel();
      controls.setOpaque(false);
      controls.setLayout(new javax.swing.BoxLayout(controls, javax.swing.BoxLayout.PAGE_AXIS));
      controls.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
      selectedField.setColumns(20);
      selectedField.setPreferredSize(new java.awt.Dimension(100, 30));
      selectedField.setFont(StyleConstants.FONT_MONOSPACE_LARGE);
      selectedField.setBackground(java.awt.Color.white);
      showTopTermsBtn.setPreferredSize(new java.awt.Dimension(170, 40));
      showTopTermsBtn.setFont(StyleConstants.FONT_BUTTON_LARGE);
      numTopTermsSpnr.setPreferredSize(new java.awt.Dimension(80, 30));
      for (java.awt.Component component :
          new java.awt.Component[] {
            new JLabel(MessageUtils.getLocalizedMessage("overview.label.selected_field")),
            selectedField,
            showTopTermsBtn,
            limit,
            numTopTermsSpnr
          }) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING));
        row.setOpaque(false);
        row.add(component);
        controls.add(row);
      }
      JPanel controlsPanel = new JPanel(new BorderLayout());
      controlsPanel.setOpaque(false);
      controlsPanel.add(controls, BorderLayout.NORTH);
      JPanel tablePanel = new JPanel(new BorderLayout());
      tablePanel.setOpaque(false);
      tablePanel.add(
          new JLabel(MessageUtils.getLocalizedMessage("overview.label.top_terms")),
          BorderLayout.NORTH);
      tablePanel.add(new JScrollPane(topTermsTable), BorderLayout.CENTER);
      var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsPanel, tablePanel);
      split.setDividerLocation(180);
      split.setOpaque(false);
      split.setBorder(BorderFactory.createEmptyBorder());
      JPanel result = new JPanel(new GridLayout(1, 1));
      result.setOpaque(false);
      result.add(split);
      return result;
    }
    termsPanel.add(new JScrollPane(topTermsTable), BorderLayout.CENTER);
    termsPanel.add(termsHint, BorderLayout.SOUTH);
    termsHint.setText(MessageUtils.getLocalizedMessage("overview.select_hint"));
    return termsPanel;
  }

  private void setUpTopTermsContextMenu() {
    JMenuItem item1 =
        new JMenuItem(MessageUtils.getLocalizedMessage("overview.toptermtable.menu.item1"));
    item1.addActionListener(listeners::browseByTerm);
    topTermsContextMenu.add(item1);

    JMenuItem item2 =
        new JMenuItem(MessageUtils.getLocalizedMessage("overview.toptermtable.menu.item2"));
    item2.addActionListener(listeners::searchByTerm);
    topTermsContextMenu.add(item2);
  }

  // control methods

  private void filterFields() {
    if (termCountsTable.getRowSorter() instanceof TableRowSorter<?> sorter) {
      termCountsTable.clearSelection();
      String query = fieldFilter.getText().toLowerCase(Locale.ROOT);
      sorter.setRowFilter(
          new RowFilter<javax.swing.table.TableModel, Integer>() {
            @Override
            public boolean include(
                Entry<? extends javax.swing.table.TableModel, ? extends Integer> entry) {
              return entry.getStringValue(0).toLowerCase(Locale.ROOT).contains(query);
            }
          });
    }
    fieldCount.setText(
        MessageUtils.getLocalizedMessage(
            "overview.field_count",
            termCountsTable.getRowCount(),
            termCountsTable.getModel().getRowCount()));
  }

  private void selectField() {
    String field = termCountsTable.getSelectedRow() < 0 ? "" : getSelectedField();
    selectedField.setText(field);
    showTopTermsBtn.setEnabled(termCountsTable.getSelectedRow() >= 0);
    topTermsTable.setModel(new TopTermsTableModel());
    termsHint.setText(
        MessageUtils.getLocalizedMessage(
            termCountsTable.getSelectedRow() < 0 ? "overview.select_hint" : "overview.load_hint"));
  }

  private void showTopTerms() {
    String field = getSelectedField();
    int numTerms = (int) numTopTermsSpnr.getModel().getValue();
    List<TermStats> termStats = overviewModel.getTopTerms(field, numTerms);

    // update top terms table
    topTermsTable.setModel(new TopTermsTableModel(termStats, numTerms));
    topTermsTable
        .getColumnModel()
        .getColumn(TopTermsTableModel.Column.RANK.getIndex())
        .setMaxWidth(TopTermsTableModel.Column.RANK.getColumnWidth());
    topTermsTable
        .getColumnModel()
        .getColumn(TopTermsTableModel.Column.FREQ.getIndex())
        .setMaxWidth(TopTermsTableModel.Column.FREQ.getColumnWidth());
    messageBroker.clearStatusMessage();
    termsHint.setText(
        MessageUtils.getLocalizedMessage(
            termStats.isEmpty() || numTerms == 0 ? "overview.no_terms" : "overview.terms_hint"));
  }

  private void browseByTerm() {
    String field = getSelectedField();
    String term = getSelectedTerm();
    operatorRegistry
        .get(DocumentsTabOperator.class)
        .ifPresent(
            operator -> {
              operator.browseTerm(field, term);
              tabSwitcher.switchTab(TabbedPaneProvider.Tab.DOCUMENTS);
            });
  }

  private void searchByTerm() {
    String field = getSelectedField();
    String term = getSelectedTerm();
    operatorRegistry
        .get(SearchTabOperator.class)
        .ifPresent(
            operator -> {
              operator.searchByTerm(field, term);
              tabSwitcher.switchTab(TabbedPaneProvider.Tab.SEARCH);
            });
  }

  private String getSelectedField() {
    int selected = termCountsTable.getSelectedRow();
    // need to convert selected row index to underlying model index
    // https://docs.oracle.com/javase/8/docs/api/javax/swing/table/TableRowSorter.html
    int row = termCountsTable.convertRowIndexToModel(selected);
    if (row < 0 || row >= termCountsTable.getModel().getRowCount()) {
      throw new IllegalStateException("Field is not selected.");
    }
    return (String)
        termCountsTable.getModel().getValueAt(row, TermCountsTableModel.Column.NAME.getIndex());
  }

  private String getSelectedTerm() {
    int rowTerm = topTermsTable.getSelectedRow();
    if (rowTerm < 0 || rowTerm >= topTermsTable.getRowCount()) {
      throw new IllegalStateException("Term is not selected.");
    }
    return (String)
        topTermsTable.getModel().getValueAt(rowTerm, TopTermsTableModel.Column.TEXT.getIndex());
  }

  private class ListenerFunctions {

    void showTopTerms(ActionEvent e) {
      OverviewPanelProvider.this.showTopTerms();
    }

    void showTopTermsContextMenu(MouseEvent e) {
      if (e.getClickCount() == 2 && !e.isConsumed()) {
        int row = topTermsTable.rowAtPoint(e.getPoint());
        if (row < 0) return;
        if (row != topTermsTable.getSelectedRow()) {
          topTermsTable.changeSelection(row, topTermsTable.getSelectedColumn(), false, false);
        }
        topTermsContextMenu.show(e.getComponent(), e.getX(), e.getY());
      }
    }

    void browseByTerm(ActionEvent e) {
      OverviewPanelProvider.this.browseByTerm();
    }

    void searchByTerm(ActionEvent e) {
      OverviewPanelProvider.this.searchByTerm();
    }
  }

  private class Observer implements IndexObserver {

    @Override
    public void openIndex(LukeState state) {
      overviewModel = overviewFactory.newInstance(state.getIndexReader(), state.getIndexPath());

      indexPathLbl.setText(overviewModel.getIndexPath());
      indexPathLbl.setToolTipText(overviewModel.getIndexPath());
      numFieldsLbl.setText(Integer.toString(overviewModel.getNumFields()));
      numDocsLbl.setText(Integer.toString(overviewModel.getNumDocuments()));
      numTermsLbl.setText(Long.toString(overviewModel.getNumTerms()));
      String del =
          overviewModel.hasDeletions()
              ? String.format(Locale.ENGLISH, "Yes (%d)", overviewModel.getNumDeletedDocs())
              : "No";
      String opt = overviewModel.isOptimized().map(b -> b ? "Yes" : "No").orElse("?");
      delOptLbl.setText(del + " / " + opt);
      indexVerLbl.setText(overviewModel.getIndexVersion().map(v -> Long.toString(v)).orElse("?"));
      indexFmtLbl.setText(overviewModel.getIndexFormat().orElse(""));
      dirImplLbl.setText(overviewModel.getDirImpl().orElse(""));
      commitPointLbl.setText(overviewModel.getCommitDescription().orElse("---"));
      commitUserDataLbl.setText(overviewModel.getCommitUserData().orElse("---"));

      // term counts table
      Map<String, Long> termCounts = overviewModel.getSortedTermCounts(TermCountsOrder.COUNT_DESC);
      long numTerms = overviewModel.getNumTerms();
      termCountsTable.setModel(new TermCountsTableModel(numTerms, termCounts));
      termCountsTable.setRowSorter(new TableRowSorter<>(termCountsTable.getModel()));
      fieldFilter.setText("");
      filterFields();
      termCountsTable
          .getColumnModel()
          .getColumn(TermCountsTableModel.Column.NAME.getIndex())
          .setPreferredWidth(TermCountsTableModel.Column.NAME.getColumnWidth());
      termCountsTable
          .getColumnModel()
          .getColumn(TermCountsTableModel.Column.TERM_COUNT.getIndex())
          .setMaxWidth(TermCountsTableModel.Column.TERM_COUNT.getColumnWidth());
      DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
      rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
      termCountsTable
          .getColumnModel()
          .getColumn(TermCountsTableModel.Column.RATIO.getIndex())
          .setCellRenderer(rightRenderer);

      // top terms table
      topTermsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      topTermsTable
          .getColumnModel()
          .getColumn(TopTermsTableModel.Column.RANK.getIndex())
          .setMaxWidth(TopTermsTableModel.Column.RANK.getColumnWidth());
      topTermsTable
          .getColumnModel()
          .getColumn(TopTermsTableModel.Column.FREQ.getIndex())
          .setMaxWidth(TopTermsTableModel.Column.FREQ.getColumnWidth());
      topTermsTable.getColumnModel().setColumnMargin(StyleConstants.TABLE_COLUMN_MARGIN_DEFAULT);
    }

    @Override
    public void closeIndex() {
      indexPathLbl.setText("");
      numFieldsLbl.setText("");
      numDocsLbl.setText("");
      numTermsLbl.setText("");
      delOptLbl.setText("");
      indexVerLbl.setText("");
      indexFmtLbl.setText("");
      dirImplLbl.setText("");
      commitPointLbl.setText("");
      commitUserDataLbl.setText("");

      selectedField.setText("");
      showTopTermsBtn.setEnabled(false);

      termCountsTable.setRowSorter(null);
      termCountsTable.setModel(new TermCountsTableModel());
      fieldFilter.setText("");
      filterFields();
      topTermsTable.setModel(new TopTermsTableModel());
    }
  }

  static final class TermCountsTableModel extends TableModelBase<TermCountsTableModel.Column> {

    enum Column implements TableColumnInfo {
      NAME("Name", 0, String.class, 150),
      TERM_COUNT("Term count", 1, Long.class, 100),
      RATIO("%", 2, String.class, Integer.MAX_VALUE);

      private final String colName;
      private final int index;
      private final Class<?> type;
      private final int width;

      Column(String colName, int index, Class<?> type, int width) {
        this.colName = colName;
        this.index = index;
        this.type = type;
        this.width = width;
      }

      @Override
      public String getColName() {
        return colName;
      }

      @Override
      public int getIndex() {
        return index;
      }

      @Override
      public Class<?> getType() {
        return type;
      }

      @Override
      public int getColumnWidth() {
        return width;
      }
    }

    TermCountsTableModel() {
      super();
    }

    TermCountsTableModel(double numTerms, Map<String, Long> termCounts) {
      super(termCounts.size());
      int i = 0;
      for (Map.Entry<String, Long> e : termCounts.entrySet()) {
        String term = e.getKey();
        Long count = e.getValue();
        data[i++] =
            new Object[] {
              term, count, String.format(Locale.ENGLISH, "%.2f %%", count / numTerms * 100)
            };
      }
    }

    @Override
    protected Column[] columnInfos() {
      return Column.values();
    }
  }

  static final class TopTermsTableModel extends TableModelBase<TopTermsTableModel.Column> {

    enum Column implements TableColumnInfo {
      RANK("Rank", 0, Integer.class, 50),
      FREQ("Freq", 1, Integer.class, 80),
      TEXT("Text", 2, String.class, Integer.MAX_VALUE);

      private final String colName;
      private final int index;
      private final Class<?> type;
      private final int width;

      Column(String colName, int index, Class<?> type, int width) {
        this.colName = colName;
        this.index = index;
        this.type = type;
        this.width = width;
      }

      @Override
      public String getColName() {
        return colName;
      }

      @Override
      public int getIndex() {
        return index;
      }

      @Override
      public Class<?> getType() {
        return type;
      }

      @Override
      public int getColumnWidth() {
        return width;
      }
    }

    TopTermsTableModel() {
      super();
    }

    TopTermsTableModel(List<TermStats> termStats, int numTerms) {
      super(Math.min(numTerms, termStats.size()));
      for (int i = 0; i < data.length; i++) {
        int rank = i + 1;
        int freq = termStats.get(i).getDocFreq();
        String termText = termStats.get(i).getDecodedTermText();
        data[i] = new Object[] {rank, freq, termText};
      }
    }

    @Override
    protected Column[] columnInfos() {
      return Column.values();
    }
  }
}
