/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.keys.SupportsInputBasedRuleKey;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.FileScrubberStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;

/**
 * A {@link com.facebook.buck.rules.BuildRule} which builds an "ar" archive from input files
 * represented as {@link com.facebook.buck.rules.SourcePath}.
 */
public class Archive extends AbstractBuildRule implements SupportsInputBasedRuleKey {

  @AddToRuleKey
  private final Archiver archiver;
  @AddToRuleKey
  private final Tool ranlib;
  @AddToRuleKey
  private final Contents contents;
  @AddToRuleKey(stringify = true)
  private final Path output;
  @AddToRuleKey
  private final ImmutableList<SourcePath> inputs;

  private Archive(
      BuildRuleParams params,
      SourcePathResolver resolver,
      Archiver archiver,
      Tool ranlib,
      Contents contents,
      Path output,
      ImmutableList<SourcePath> inputs) {
    super(params, resolver);
    Preconditions.checkState(
        contents == Contents.NORMAL || archiver.supportsThinArchives(),
        "%s: archive tool for this platform does not support thin archives",
        getBuildTarget());
    this.archiver = archiver;
    this.ranlib = ranlib;
    this.contents = contents;
    this.output = output;
    this.inputs = inputs;
  }

  /**
   * Construct an {@link com.facebook.buck.cxx.Archive} from a
   * {@link com.facebook.buck.rules.BuildRuleParams} object representing a target
   * node.  In particular, make sure to trim dependencies to *only* those that
   * provide the input {@link com.facebook.buck.rules.SourcePath}.
   */
  public static Archive from(
      BuildTarget target,
      BuildRuleParams baseParams,
      SourcePathResolver resolver,
      Archiver archiver,
      Tool ranlib,
      Contents contents,
      Path output,
      ImmutableList<SourcePath> inputs) {

    // Convert the input build params into ones specialized for this archive build rule.
    // In particular, we only depend on BuildRules directly from the input file SourcePaths.
    BuildRuleParams archiveParams =
        baseParams.copyWithChanges(
            target,
            Suppliers.ofInstance(ImmutableSortedSet.<BuildRule>of()),
            Suppliers.ofInstance(
                ImmutableSortedSet.<BuildRule>naturalOrder()
                    .addAll(resolver.filterBuildRuleInputs(inputs))
                    .addAll(archiver.getDeps(resolver))
                    .build()));

    return new Archive(
        archiveParams,
        resolver,
        archiver,
        ranlib,
        contents,
        output,
        inputs);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {

    // Cache the archive we built.
    buildableContext.recordArtifact(output);

    // We only support packaging inputs that use the same filesystem root as the output, as thin
    // archives embed relative paths from output to input inside the archive.  If this becomes a
    // limitation, we could make this rule uncacheable and allow thin archives to embed absolute
    // paths.
    for (SourcePath input : inputs) {
      Preconditions.checkState(
          getResolver().getFilesystem(input).getRootPath()
              .equals(getProjectFilesystem().getRootPath()));
    }

    return ImmutableList.of(
        new MkdirStep(getProjectFilesystem(), output.getParent()),
        new RmStep(getProjectFilesystem(), output, /* shouldForceDeletion */ true),
        new ArchiveStep(
            getProjectFilesystem(),
            archiver.getEnvironment(getResolver()),
            archiver.getCommandPrefix(getResolver()),
            contents,
            output,
            FluentIterable.from(inputs)
                .transform(getResolver().getRelativePathFunction())
                .toList()),
        new ShellStep(getProjectFilesystem().getRootPath()) {
          @Override
          protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
            return ImmutableList.<String>builder()
                .addAll(ranlib.getCommandPrefix(getResolver()))
                .add(output.toString())
                .build();
          }

          @Override
          public String getShortName() {
            return "ranlib";
          }
        },
        new FileScrubberStep(getProjectFilesystem(), output, archiver.getScrubbers()));
  }

  /**
   * @return the {@link Arg} to use when using this archive.  When thin archives are used, this will
   *     ensure that the inputs are also propagated as build time deps to whatever rule uses this
   *     archive.
   */
  public Arg toArg() {
    SourcePath archive = new BuildTargetSourcePath(getBuildTarget());
    return contents == Contents.NORMAL ?
        new SourcePathArg(getResolver(), archive) :
        ThinArchiveArg.of(getResolver(), archive, inputs);
  }

  @Override
  public Path getPathToOutput() {
    return output;
  }

  public Contents getContents() {
    return contents;
  }

  /**
   * How this archive packages its contents.
   */
  public enum Contents {

    /**
     * This archive packages a copy of its inputs and can be used independently of its inputs.
     */
    NORMAL,

    /**
     * This archive only packages the relative paths to its inputs and so can only be used when its
     * inputs are available.
     */
    THIN,

  }

}
