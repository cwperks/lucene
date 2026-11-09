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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import org.apache.lucene.luke.app.DirectoryHandler;
import org.apache.lucene.luke.app.DirectoryObserver;
import org.apache.lucene.luke.app.IndexHandler;
import org.apache.lucene.luke.app.IndexObserver;
import org.apache.lucene.luke.app.LukeState;
import org.apache.lucene.luke.app.desktop.MessageBroker;
import org.apache.lucene.luke.app.desktop.Preferences;
import org.apache.lucene.luke.app.desktop.PreferencesFactory;
import org.apache.lucene.luke.app.desktop.util.FontUtils;
import org.apache.lucene.luke.app.desktop.util.ImageUtils;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;
import org.apache.lucene.util.Version;

/** Provider of the root window */
public final class LukeWindowProvider implements LukeWindowOperator {

  private static final String WINDOW_TITLE =
      MessageUtils.getLocalizedMessage("window.title") + " - v" + Version.LATEST.toString();

  private final Preferences prefs;
  private final boolean experimentalUi;

  private final MessageBroker messageBroker;

  private final TabSwitcherProxy tabSwitcher;

  private final JMenuBar menuBar;

  private final JTabbedPane tabbedPane;

  private final JLabel messageLbl = new JLabel();
  private final JLabel indexContext = new JLabel();

  private final JLabel multiIcon = new JLabel();

  private final JLabel readOnlyIcon = new JLabel();

  private final JLabel noReaderIcon = new JLabel();

  private JFrame frame = new JFrame();

  public LukeWindowProvider() throws IOException {
    this(false);
  }

  public LukeWindowProvider(boolean experimentalUi) throws IOException {
    this.experimentalUi = experimentalUi;
    this.prefs = PreferencesFactory.getInstance();
    this.menuBar = new MenuBarProvider().get();
    this.tabbedPane = new TabbedPaneProvider(experimentalUi).get();
    this.messageBroker = MessageBroker.getInstance();
    this.tabSwitcher = TabSwitcherProxy.getInstance();

    ComponentOperatorRegistry.getInstance().register(LukeWindowOperator.class, this);
    Observer observer = new Observer();
    DirectoryHandler.getInstance().addObserver(observer);
    IndexHandler.getInstance().addObserver(observer);

    messageBroker.registerReceiver(new MessageReceiverImpl());
  }

  public JFrame get() {
    frame.setTitle(WINDOW_TITLE);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    var applicationIcon = ImageUtils.createApplicationIcon();
    frame.setIconImage(applicationIcon);
    if (java.awt.Taskbar.isTaskbarSupported()) {
      var taskbar = java.awt.Taskbar.getTaskbar();
      if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
        taskbar.setIconImage(applicationIcon);
      }
    }

    frame.setJMenuBar(menuBar);
    if (experimentalUi) {
      JPanel heading = new JPanel(new BorderLayout(12, 0));
      heading.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
      JLabel brand = new JLabel("LUKE");
      brand.setIcon(ImageUtils.createImageIcon("lucene-logo.png", "Apache Lucene", 143, 32));
      brand.setIconTextGap(20);
      brand.setFont(brand.getFont().deriveFont(java.awt.Font.BOLD, 22f));
      heading.add(brand, BorderLayout.WEST);
      heading.add(
          new JLabel(MessageUtils.getLocalizedMessage("explorer.app_subtitle")),
          BorderLayout.CENTER);
      heading.add(indexContext, BorderLayout.EAST);
      frame.add(heading, BorderLayout.NORTH);
    }
    frame.add(initMainPanel(), BorderLayout.CENTER);
    frame.add(initMessagePanel(), BorderLayout.PAGE_END);

    frame.setPreferredSize(experimentalUi ? new Dimension(1280, 850) : new Dimension(950, 680));
    frame.getContentPane().setBackground(prefs.getColorTheme().getBackgroundColor());

    return frame;
  }

  private JPanel initMainPanel() {
    JPanel panel = new JPanel(new GridLayout(1, 1));

    tabbedPane.setEnabledAt(TabbedPaneProvider.Tab.OVERVIEW.index(experimentalUi), false);
    if (experimentalUi)
      tabbedPane.setEnabledAt(TabbedPaneProvider.Tab.FIELDS.index(experimentalUi), false);
    tabbedPane.setEnabledAt(TabbedPaneProvider.Tab.DOCUMENTS.index(experimentalUi), false);
    tabbedPane.setEnabledAt(TabbedPaneProvider.Tab.SEARCH.index(experimentalUi), false);
    tabbedPane.setEnabledAt(TabbedPaneProvider.Tab.COMMITS.index(experimentalUi), false);

    panel.add(tabbedPane);

    panel.setOpaque(false);
    return panel;
  }

  private JPanel initMessagePanel() {
    JPanel panel = new JPanel(new GridLayout(1, 1));
    panel.setOpaque(false);
    panel.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 2));

    JPanel innerPanel = new JPanel(new GridBagLayout());
    innerPanel.setOpaque(false);
    if (experimentalUi) {
      innerPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
      innerPanel.setOpaque(true);
      innerPanel.setBackground(UIManager.getColor("Panel.background"));
    } else {
      innerPanel.setBorder(BorderFactory.createLineBorder(java.awt.Color.gray));
    }
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;

    JPanel msgPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    msgPanel.setOpaque(false);
    msgPanel.add(messageLbl);

    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.8;
    innerPanel.add(msgPanel, c);

    JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    iconPanel.setOpaque(false);

    multiIcon.setText(FontUtils.elegantIconHtml("&#xe08c;"));
    multiIcon.setToolTipText(MessageUtils.getLocalizedMessage("tooltip.multi_reader"));
    multiIcon.setVisible(false);
    iconPanel.add(multiIcon);

    readOnlyIcon.setText(FontUtils.elegantIconHtml("&#xe06c;"));
    readOnlyIcon.setToolTipText(MessageUtils.getLocalizedMessage("tooltip.read_only"));
    readOnlyIcon.setVisible(false);
    iconPanel.add(readOnlyIcon);

    noReaderIcon.setText(FontUtils.elegantIconHtml("&#xe077;"));
    noReaderIcon.setToolTipText(MessageUtils.getLocalizedMessage("tooltip.no_reader"));
    noReaderIcon.setVisible(false);
    iconPanel.add(noReaderIcon);

    JLabel luceneIcon = new JLabel(ImageUtils.createImageIcon("lucene.gif", "lucene", 16, 16));
    iconPanel.add(luceneIcon);

    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 0.2;
    innerPanel.add(iconPanel);
    panel.add(innerPanel);

    return panel;
  }

  @Override
  public void setColorTheme(Preferences.ColorTheme theme) {
    frame.getContentPane().setBackground(theme.getBackgroundColor());
  }

  private class Observer implements IndexObserver, DirectoryObserver {

    @Override
    public void openDirectory(LukeState state) {
      multiIcon.setVisible(false);
      readOnlyIcon.setVisible(false);
      noReaderIcon.setVisible(true);

      tabSwitcher.switchTab(TabbedPaneProvider.Tab.COMMITS);

      messageBroker.showStatusMessage(MessageUtils.getLocalizedMessage("message.directory_opened"));
    }

    @Override
    public void closeDirectory() {
      multiIcon.setVisible(false);
      readOnlyIcon.setVisible(false);
      noReaderIcon.setVisible(false);

      messageBroker.showStatusMessage(MessageUtils.getLocalizedMessage("message.directory_closed"));
    }

    @Override
    public void openIndex(LukeState state) {
      java.nio.file.Path path = java.nio.file.Path.of(state.getIndexPath());
      indexContext.setText(
          path.getFileName() == null ? path.toString() : path.getFileName().toString());
      indexContext.setToolTipText(state.getIndexPath());
      multiIcon.setVisible(!state.hasDirectoryReader());
      readOnlyIcon.setVisible(state.readOnly());
      noReaderIcon.setVisible(false);

      if (state.readOnly()) {
        messageBroker.showStatusMessage(
            MessageUtils.getLocalizedMessage("message.index_opened_ro"));
      } else if (!state.hasDirectoryReader()) {
        messageBroker.showStatusMessage(
            MessageUtils.getLocalizedMessage("message.index_opened_multi"));
      } else {
        messageBroker.showStatusMessage(MessageUtils.getLocalizedMessage("message.index_opened"));
      }
    }

    @Override
    public void closeIndex() {
      indexContext.setText("");
      indexContext.setToolTipText(null);
      multiIcon.setVisible(false);
      readOnlyIcon.setVisible(false);
      noReaderIcon.setVisible(false);

      messageBroker.showStatusMessage(MessageUtils.getLocalizedMessage("message.index_closed"));
    }
  }

  private class MessageReceiverImpl implements MessageBroker.MessageReceiver {

    @Override
    public void showStatusMessage(String message) {
      messageLbl.setText(message);
    }

    @Override
    public void showUnknownErrorMessage() {
      messageLbl.setText(MessageUtils.getLocalizedMessage("message.error.unknown"));
    }

    @Override
    public void clearStatusMessage() {
      messageLbl.setText("");
    }

    private MessageReceiverImpl() {}
  }
}
