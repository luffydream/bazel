// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.analysis.actions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.AbstractAction;
import com.google.devtools.build.lib.actions.ActionExecutionContext;
import com.google.devtools.build.lib.actions.ActionExecutionException;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ExecException;
import com.google.devtools.build.lib.actions.Executor;
import com.google.devtools.build.lib.cmdline.Label;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Abstract Action to write to a file.
 */
public abstract class AbstractFileWriteAction extends AbstractAction {

  protected final boolean makeExecutable;

  /**
   * Creates a new AbstractFileWriteAction instance.
   *
   * @param owner the action owner.
   * @param inputs the Artifacts that this Action depends on
   * @param output the Artifact that will be created by executing this Action.
   * @param makeExecutable iff true will change the output file to be
   *   executable.
   */
  public AbstractFileWriteAction(ActionOwner owner,
      Iterable<Artifact> inputs, Artifact output, boolean makeExecutable) {
    // There is only one output, and it is primary.
    super(owner, inputs, ImmutableList.of(output));
    this.makeExecutable = makeExecutable;
  }

  public boolean makeExecutable() {
    return makeExecutable;
  }

  @Override
  public final void execute(ActionExecutionContext actionExecutionContext)
      throws ActionExecutionException, InterruptedException {
    try {
      getStrategy(actionExecutionContext.getExecutor()).exec(this, actionExecutionContext);
    } catch (ExecException e) {
      throw e.toActionExecutionException(
          "Writing file for rule '" + Label.print(getOwner().getLabel()) + "'",
          actionExecutionContext.getExecutor().getVerboseFailures(), this);
    }
    afterWrite(actionExecutionContext.getExecutor());
  }

  /**
   * Produce a DeterministicWriter that can write the file to an OutputStream deterministically.
   *
   * @param ctx context for use with creating the writer.  
   */
  public abstract DeterministicWriter newDeterministicWriter(ActionExecutionContext ctx)
      throws IOException, InterruptedException, ExecException;
  
  /**
   * This hook is called after the File has been successfully written to disk.
   *
   * @param executor the Executor.
   */
  protected void afterWrite(Executor executor) {
  }

  @Override
  public String getMnemonic() {
    return "FileWrite";
  }

  @Override
  protected String getRawProgressMessage() {
    return "Writing " + (makeExecutable ? "script " : "file ")
        + Iterables.getOnlyElement(getOutputs()).prettyPrint();
  }

  /**
   * Whether the file write can be generated remotely. If the file is consumed in Blaze
   * unconditionally, it doesn't make sense to run remotely.
   */
  public boolean isRemotable() {
    return true;
  }

  private FileWriteActionContext getStrategy(Executor executor) {
    return executor.getContext(FileWriteActionContext.class);
  }

  /**
   * A deterministic writer writes bytes to an output stream. The same byte stream is written
   * on every invocation of writeOutputFile().
   */
  public interface DeterministicWriter {
    public void writeOutputFile(OutputStream out) throws IOException;
  }
}
