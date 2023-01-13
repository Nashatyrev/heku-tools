/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.heku.util.config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.layout.PatternSelector;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;

public class FilePatternSelector implements PatternSelector {
  private static final String FILE_MESSAGE_FORMAT_OMIT =
          "%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | %t | %-5level | %c{1} | %msg %throwable{1}%n";
  private static final String FILE_MESSAGE_FORMAT =
          "%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | %t | %-5level | %c{1} | %msg%n";

  private final PatternFormatter[] omitStackTraceFormat;
  private final PatternFormatter[] stackTraceFormat;

  public FilePatternSelector(
      final AbstractConfiguration configuration) {

    final PatternParser patternParser = PatternLayout.createPatternParser(configuration);
    omitStackTraceFormat =
        patternParser
            .parse(
                    FILE_MESSAGE_FORMAT_OMIT,
                false,
                true)
            .toArray(PatternFormatter[]::new);

    stackTraceFormat =
            patternParser
                    .parse(
                            FILE_MESSAGE_FORMAT,
                            true,
                            true)
                    .toArray(PatternFormatter[]::new);
  }

  @Override
  public PatternFormatter[] getFormatters(final LogEvent event) {
    if (event.getLevel().isLessSpecificThan(Level.INFO)) {
      return omitStackTraceFormat;
    } else {
      return stackTraceFormat;
    }
  }
}
