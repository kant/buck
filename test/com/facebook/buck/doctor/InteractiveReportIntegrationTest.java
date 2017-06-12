/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.doctor;

import static org.junit.Assert.assertThat;

import com.facebook.buck.doctor.config.BuildLogEntry;
import com.facebook.buck.doctor.config.DoctorConfig;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class InteractiveReportIntegrationTest {

  private static final String BUILD_PATH =
      "buck-out/log/" + "2016-06-21_16h16m24s_buildcommand_ac8bd626-6137-4747-84dd-5d4f215c876c/";
  private static final String DEPS_PATH =
      "buck-out/log/"
          + "2016-06-21_16h18m51s_autodepscommand_d09893d5-b11e-4e3f-a5bf-70c60a06896e/";
  private static final String SERVER_PATH =
      "buck-out/log/" + "2017-05-02_12h22m53s_servercommand_c0d07334-7c18-4b91-93d0-214c52bbd373";
  private static final ImmutableMap<String, String> TIMESTAMPS =
      ImmutableMap.of(
          BUILD_PATH, "2016-06-21T16:16:24.00Z",
          DEPS_PATH, "2016-06-21T16:18:51.00Z",
          SERVER_PATH, "2017-05-02T12:22:53.00Z");

  private ProjectWorkspace traceWorkspace;
  private String tracePath1;
  private String tracePath2;

  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Before
  public void setUp() throws Exception {
    tracePath1 = BUILD_PATH + "file.trace";
    tracePath2 = DEPS_PATH + "file.trace";
    traceWorkspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "report-all", temporaryFolder);
    traceWorkspace.setUp();
    traceWorkspace.writeContentsToPath(new String(new char[32 * 1024]), tracePath1);
    traceWorkspace.writeContentsToPath(new String(new char[64 * 1024]), tracePath2);

    ProjectFilesystem filesystem = traceWorkspace.asCell().getFilesystem();
    for (Map.Entry<String, String> timestampEntry : TIMESTAMPS.entrySet()) {
      for (Path path : filesystem.getDirectoryContents(Paths.get(timestampEntry.getKey()))) {
        filesystem.setLastModifiedTime(
            path, FileTime.from(Instant.parse(timestampEntry.getValue())));
      }
    }
  }

  @Test
  public void testReport() throws Exception {
    UserInputFixture userInputFixture = new UserInputFixture("0");
    DoctorConfig doctorConfig = DoctorConfig.of(traceWorkspace.asCell().getBuckConfig());
    DoctorReportHelper helper =
        DoctorTestUtils.createDoctorHelper(
            traceWorkspace, userInputFixture.getUserInput(), doctorConfig);
    BuildLogHelper buildLogHelper = new BuildLogHelper(traceWorkspace.asCell().getFilesystem());
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    DefectSubmitResult report =
        DoctorTestUtils.createDefectReport(
            traceWorkspace,
            ImmutableSet.of(entry.get()),
            userInputFixture.getUserInput(),
            doctorConfig);
    Path reportFile =
        traceWorkspace.asCell().getFilesystem().resolve(report.getReportSubmitLocation().get());

    ZipInspector zipInspector = new ZipInspector(reportFile);
    zipInspector.assertFileExists("report.json");
    zipInspector.assertFileExists("buckconfig.local");
    zipInspector.assertFileExists(DEPS_PATH + "buck-machine-log");
    zipInspector.assertFileExists(DEPS_PATH + "buck.log");
    zipInspector.assertFileExists(DEPS_PATH + "file.trace");
  }

  @Test
  public void testTraceInReport() throws Exception {
    UserInputFixture userInputFixture = new UserInputFixture("1");
    DoctorConfig doctorConfig = DoctorConfig.of(traceWorkspace.asCell().getBuckConfig());
    DoctorReportHelper helper =
        DoctorTestUtils.createDoctorHelper(
            traceWorkspace, userInputFixture.getUserInput(), doctorConfig);
    BuildLogHelper buildLogHelper = new BuildLogHelper(traceWorkspace.asCell().getFilesystem());
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    DefectSubmitResult report =
        DoctorTestUtils.createDefectReport(
            traceWorkspace,
            ImmutableSet.of(entry.get()),
            userInputFixture.getUserInput(),
            doctorConfig);
    Path reportFile =
        traceWorkspace.asCell().getFilesystem().resolve(report.getReportSubmitLocation().get());

    ZipInspector zipInspector = new ZipInspector(reportFile);
    zipInspector.assertFileExists(tracePath1);
  }

  @Test
  public void testTraceRespectReportSize() throws Exception {
    UserInputFixture userInputFixture = new UserInputFixture("0");
    DoctorConfig doctorConfig = DoctorConfig.of(traceWorkspace.asCell().getBuckConfig());
    DoctorReportHelper helper =
        DoctorTestUtils.createDoctorHelper(
            traceWorkspace, userInputFixture.getUserInput(), doctorConfig);
    BuildLogHelper buildLogHelper = new BuildLogHelper(traceWorkspace.asCell().getFilesystem());
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    DefectSubmitResult report =
        DoctorTestUtils.createDefectReport(
            traceWorkspace,
            ImmutableSet.of(entry.get()),
            userInputFixture.getUserInput(),
            doctorConfig);
    Path reportFile =
        traceWorkspace.asCell().getFilesystem().resolve(report.getReportSubmitLocation().get());

    ZipInspector zipInspector = new ZipInspector(reportFile);
    // The second command was more recent, so its file should be included.
    zipInspector.assertFileExists(tracePath2);
    zipInspector.assertFileDoesNotExist(tracePath1);
  }

  @Test
  public void testLocalConfigurationReport() throws Exception {
    UserInputFixture userInputFixture = new UserInputFixture("0");
    DoctorConfig doctorConfig = DoctorConfig.of(traceWorkspace.asCell().getBuckConfig());
    DoctorReportHelper helper =
        DoctorTestUtils.createDoctorHelper(
            traceWorkspace, userInputFixture.getUserInput(), doctorConfig);
    BuildLogHelper buildLogHelper = new BuildLogHelper(traceWorkspace.asCell().getFilesystem());
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    DefectSubmitResult report =
        DoctorTestUtils.createDefectReport(
            traceWorkspace,
            ImmutableSet.of(entry.get()),
            userInputFixture.getUserInput(),
            doctorConfig);
    Path reportFile =
        traceWorkspace.asCell().getFilesystem().resolve(report.getReportSubmitLocation().get());

    ZipInspector zipInspector = new ZipInspector(reportFile);
    assertThat(
        zipInspector.getZipFileEntries(),
        Matchers.hasItems("buckconfig.local", "bucklogging.local.properties"));
  }

  @Test
  public void testWatchmanDiagReport() throws Exception {
    UserInputFixture userInputFixture = new UserInputFixture("1\n\n\ny");
    DoctorConfig doctorConfig = DoctorConfig.of(traceWorkspace.asCell().getBuckConfig());
    DoctorReportHelper helper =
        DoctorTestUtils.createDoctorHelper(
            traceWorkspace, userInputFixture.getUserInput(), doctorConfig);
    BuildLogHelper buildLogHelper = new BuildLogHelper(traceWorkspace.asCell().getFilesystem());
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    DefectSubmitResult report =
        DoctorTestUtils.createDefectReport(
            traceWorkspace,
            ImmutableSet.of(entry.get()),
            userInputFixture.getUserInput(),
            doctorConfig);
    Path reportFile =
        traceWorkspace.asCell().getFilesystem().resolve(report.getReportSubmitLocation().get());

    ZipInspector zipInspector = new ZipInspector(reportFile);
    assertThat(
        zipInspector.getZipFileEntries(),
        Matchers.hasItem(Matchers.stringContainsInOrder("watchman-diag-report")));
  }
}