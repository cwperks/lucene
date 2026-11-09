<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# Luke

Integrated desktop GUI tool: a utility for browsing, searching and maintaining indexes and documents.

## Experimental interface (fork prototype)

The classic interface remains the default. Opt in at startup to try the FlatLaf
light theme, sidebar navigation, redesigned Overview, and read-only Fields explorer:

The experimental palette uses cool slate surfaces and teal accents, with rounded
inputs and persistent selection highlights to keep the field-to-document trail
visible as focus moves between panes. It reuses Swing and FlatLaf components;
no browser or additional component library is needed.

```sh
./gradlew :lucene:luke:run -Pluke.experimentalUi=true
```

For the standalone distribution, append `--experimental-ui` after the JAR name:

```sh
java -jar lucene-luke-<version>-standalone.jar --experimental-ui
```

The modular launcher also accepts the flag after `-m org.apache.lucene.luke`.
Omit the flag (or the Gradle property) to return to the classic interface.
The choice is not saved in preferences and requires restarting Luke; there is no
live theme/layout switch. The distribution still contains FlatLaf in both modes.

In the experimental interface, the sidebar toggle switches between labels and an
icon-only rail. Hover over an icon for its screen name. This sidebar preference
is saved in Luke's existing `~/.luke.d/luke.ini` configuration and does not affect
the classic interface.

The Fields explorer previews up to 100 terms in byte order, 50 matching live
documents, and stored values truncated to 1,000 characters. These are previews,
not pagination. The existing Documents screen remains available for full values
and doc values. Large-index responsiveness and cross-platform testing remain
follow-ups before considering an upstream proposal.

A possible later fork experiment is a React frontend in a browser backed by a
local Java service. That would require a separately reviewed local-API security
design and another build lifecycle. No web server, React, or Electron is included
or required by this prototype.

## Older releases

Older releases of Luke (prior to 8.1) can be found at <https://github.com/DmitryKey/luke>
