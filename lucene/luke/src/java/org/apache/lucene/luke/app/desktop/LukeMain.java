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

package org.apache.lucene.luke.app.desktop;

import static org.apache.lucene.luke.app.desktop.util.ExceptionHandler.handle;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.GraphicsEnvironment;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.UIManager;
import org.apache.lucene.luke.app.desktop.components.LukeWindowProvider;
import org.apache.lucene.luke.app.desktop.components.dialog.menubar.OpenIndexDialogFactory;
import org.apache.lucene.luke.app.desktop.util.DialogOpener;
import org.apache.lucene.luke.app.desktop.util.FontUtils;
import org.apache.lucene.luke.app.desktop.util.MessageUtils;
import org.apache.lucene.luke.util.LoggerFactory;

/** Entry class for desktop Luke */
public class LukeMain {

  static {
    LoggerFactory.initGuiLogging();
  }

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @SuppressWarnings("NonFinalStaticField")
  private static JFrame frame;

  public static JFrame getOwnerFrame() {
    return frame;
  }

  /**
   * @return Returns {@code true} if GUI startup and initialization was successful.
   */
  private static boolean createGUI(boolean experimentalUi) {
    // uncaught error handler
    MessageBroker messageBroker = MessageBroker.getInstance();
    try {
      Thread.setDefaultUncaughtExceptionHandler((_, cause) -> handle(cause, messageBroker));

      frame = new LukeWindowProvider(experimentalUi).get();
      frame.setLocation(200, 100);
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.pack();
      frame.setVisible(true);

      return true;
    } catch (Throwable e) {
      messageBroker.showUnknownErrorMessage();
      log.log(Level.SEVERE, "Cannot initialize components.", e);
      return false;
    }
  }

  public static void main(String[] args) throws Exception {
    boolean sanityCheck = Arrays.asList(args).contains("--sanity-check");
    boolean experimentalUi = Arrays.asList(args).contains("--experimental-ui");

    if (sanityCheck && GraphicsEnvironment.isHeadless()) {
      Logger.getGlobal().log(Level.SEVERE, "[Vader] Hello, Luke. Can't do much in headless mode.");
      Runtime.getRuntime().exit(0);
    }

    String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
    if (experimentalUi) {
      lookAndFeel = System.getProperty("swing.defaultlaf", FlatLightLaf.class.getName());
    } else if (!lookAndFeel.contains("AquaLookAndFeel")
        && !lookAndFeel.contains("PlasticXPLookAndFeel")) {
      lookAndFeel = "javax.swing.plaf.metal.MetalLookAndFeel";
    }
    if (experimentalUi && lookAndFeel.equals(FlatLightLaf.class.getName())) {
      UIManager.setLookAndFeel(experimentalLookAndFeel());
    } else {
      UIManager.setLookAndFeel(lookAndFeel);
    }

    GraphicsEnvironment genv = GraphicsEnvironment.getLocalGraphicsEnvironment();
    genv.registerFont(FontUtils.createElegantIconFont());

    var guiThreadResult = new SynchronousQueue<Boolean>();
    javax.swing.SwingUtilities.invokeLater(
        () -> {
          try {
            long _start = System.nanoTime();
            guiThreadResult.put(createGUI(experimentalUi));

            // Show the initial dialog.
            OpenIndexDialogFactory openIndexDialogFactory = OpenIndexDialogFactory.getInstance();
            new DialogOpener<>(openIndexDialogFactory)
                .open(
                    MessageUtils.getLocalizedMessage("openindex.dialog.title"),
                    600,
                    420,
                    (_) -> {});

            long _end = System.nanoTime() / 1_000_000;
            log.info(
                "Elapsed time for initializing GUI: "
                    + TimeUnit.NANOSECONDS.toMillis(_end - _start)
                    + " ms");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    if (Boolean.FALSE.equals(guiThreadResult.take())) {
      Logger.getGlobal().log(Level.SEVERE, "Luke could not start.");
      Runtime.getRuntime().exit(1);
    }

    if (sanityCheck) {
      // In sanity-check mode on non-headless displays, return success.
      Logger.getGlobal().log(Level.SEVERE, "[Vader] Hello, Luke. We seem to be fine.");
      Runtime.getRuntime().exit(0);
    }
  }

  private static FlatLightLaf experimentalLookAndFeel() {
    var theme = new FlatLightLaf();
    // Keep the palette scoped to this look and feel; classic Luke and overrides are untouched.
    theme.setExtraDefaults(
        Map.ofEntries(
            Map.entry("@background", "#F3F6FA"),
            Map.entry("@foreground", "#243247"),
            Map.entry("Label.disabledForeground", "#52647A"),
            Map.entry("@accentColor", "#0F766E"),
            Map.entry("@selectionBackground", "#CCFBF1"),
            Map.entry("@selectionForeground", "#134E4A"),
            Map.entry("Component.arc", "12"),
            Map.entry("Button.arc", "12"),
            Map.entry("TextComponent.arc", "12"),
            Map.entry("Component.focusWidth", "1"),
            Map.entry("Component.borderColor", "#CBD5E1"),
            Map.entry("TableHeader.background", "#E8EEF5"),
            Map.entry("TableHeader.foreground", "#334155"),
            Map.entry("Table.selectionInactiveBackground", "#CCFBF1"),
            Map.entry("Table.selectionInactiveForeground", "#134E4A"),
            Map.entry("List.selectionInactiveBackground", "#CCFBF1"),
            Map.entry("List.selectionInactiveForeground", "#134E4A"),
            Map.entry("List.selectionArc", "10"),
            Map.entry("List.selectionInsets", "2,4,2,4"),
            Map.entry("TabbedPane.selectedBackground", "#CCFBF1"),
            Map.entry("TabbedPane.underlineColor", "#0F766E"),
            Map.entry("ScrollBar.trackArc", "999"),
            Map.entry("ScrollBar.thumbArc", "999")));
    return theme;
  }
}
