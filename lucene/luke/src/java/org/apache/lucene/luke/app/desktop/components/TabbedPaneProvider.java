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

import java.io.IOException;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.apache.lucene.luke.app.DirectoryHandler;
import org.apache.lucene.luke.app.DirectoryObserver;
import org.apache.lucene.luke.app.IndexHandler;
import org.apache.lucene.luke.app.IndexObserver;
import org.apache.lucene.luke.app.LukeState;
import org.apache.lucene.luke.app.desktop.MessageBroker;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;

/** Provider of the Tabbed pane */
public final class TabbedPaneProvider implements TabSwitcherProxy.TabSwitcher {

  private final MessageBroker messageBroker;

  private final JTabbedPane tabbedPane;

  private final JPanel overviewPanel;
  private final JPanel fieldsPanel;

  private final JPanel documentsPanel;

  private final JPanel searchPanel;

  private final JPanel analysisPanel;

  private final JPanel commitsPanel;

  private final JPanel logsPanel;

  public TabbedPaneProvider() throws IOException {
    this.tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
    tabbedPane.setOpaque(true);
    tabbedPane.setBackground(javax.swing.UIManager.getColor("Panel.background"));
    tabbedPane.putClientProperty("JTabbedPane.minimumTabWidth", 170);
    tabbedPane.putClientProperty("JTabbedPane.tabHeight", 48);
    tabbedPane.putClientProperty("JTabbedPane.tabAlignment", "leading");
    tabbedPane.putClientProperty("JTabbedPane.tabType", "card");
    tabbedPane.putClientProperty("JTabbedPane.showContentSeparator", false);

    this.overviewPanel = new OverviewPanelProvider().get();
    this.fieldsPanel = new FieldsPanelProvider().get();
    this.documentsPanel = new DocumentsPanelProvider().get();
    this.searchPanel = new SearchPanelProvider().get();
    this.analysisPanel = new AnalysisPanelProvider().get();
    this.commitsPanel = new CommitsPanelProvider().get();
    this.logsPanel = new LogsPanelProvider().get();

    this.messageBroker = MessageBroker.getInstance();

    TabSwitcherProxy.getInstance().set(this);

    Observer observer = new Observer();
    IndexHandler.getInstance().addObserver(observer);
    DirectoryHandler.getInstance().addObserver(observer);
  }

  public JTabbedPane get() {
    tabbedPane.addTab(MessageUtils.getLocalizedMessage("navigation.overview"), overviewPanel);
    tabbedPane.addTab(MessageUtils.getLocalizedMessage("navigation.fields"), fieldsPanel);
    tabbedPane.addTab(MessageUtils.getLocalizedMessage("navigation.documents"), documentsPanel);
    tabbedPane.addTab(MessageUtils.getLocalizedMessage("navigation.search"), searchPanel);
    tabbedPane.addTab(MessageUtils.getLocalizedMessage("navigation.analysis"), analysisPanel);
    tabbedPane.addTab(MessageUtils.getLocalizedMessage("navigation.commits"), commitsPanel);
    tabbedPane.addTab(MessageUtils.getLocalizedMessage("navigation.logs"), logsPanel);

    return tabbedPane;
  }

  @Override
  public void switchTab(Tab tab) {
    tabbedPane.setSelectedIndex(tab.index());
    tabbedPane.setVisible(false);
    tabbedPane.setVisible(true);
    messageBroker.clearStatusMessage();
  }

  private class Observer implements IndexObserver, DirectoryObserver {

    @Override
    public void openDirectory(LukeState state) {
      tabbedPane.setEnabledAt(Tab.COMMITS.index(), true);
    }

    @Override
    public void closeDirectory() {
      tabbedPane.setEnabledAt(Tab.FIELDS.index(), false);
      tabbedPane.setEnabledAt(Tab.OVERVIEW.index(), false);
      tabbedPane.setEnabledAt(Tab.DOCUMENTS.index(), false);
      tabbedPane.setEnabledAt(Tab.SEARCH.index(), false);
      tabbedPane.setEnabledAt(Tab.COMMITS.index(), false);
    }

    @Override
    public void openIndex(LukeState state) {
      tabbedPane.setEnabledAt(Tab.FIELDS.index(), true);
      tabbedPane.setEnabledAt(Tab.OVERVIEW.index(), true);
      tabbedPane.setEnabledAt(Tab.DOCUMENTS.index(), true);
      tabbedPane.setEnabledAt(Tab.SEARCH.index(), true);
      tabbedPane.setEnabledAt(Tab.COMMITS.index(), true);
      switchTab(Tab.FIELDS);
    }

    @Override
    public void closeIndex() {
      tabbedPane.setEnabledAt(Tab.FIELDS.index(), false);
      tabbedPane.setEnabledAt(Tab.OVERVIEW.index(), false);
      tabbedPane.setEnabledAt(Tab.DOCUMENTS.index(), false);
      tabbedPane.setEnabledAt(Tab.SEARCH.index(), false);
      tabbedPane.setEnabledAt(Tab.COMMITS.index(), false);
    }
  }

  /** tabs in the main frame */
  public enum Tab {
    OVERVIEW(0),
    FIELDS(1),
    DOCUMENTS(2),
    SEARCH(3),
    ANALYZER(4),
    COMMITS(5);

    private int tabIdx;

    Tab(int tabIdx) {
      this.tabIdx = tabIdx;
    }

    int index() {
      return tabIdx;
    }
  }
}
