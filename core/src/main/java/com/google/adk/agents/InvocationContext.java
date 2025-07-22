/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.agents;

import com.google.adk.artifacts.BaseArtifactService;
import com.google.adk.exceptions.LlmCallsLimitExceededException;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/** The context for an agent invocation. */
public class InvocationContext {

  private final BaseSessionService sessionService;
  private final BaseArtifactService artifactService;
  private final Optional<LiveRequestQueue> liveRequestQueue;

  private Optional<String> branch;
  private final String invocationId;
  private BaseAgent agent;
  private final Session session;

  private final Optional<Content> userContent;
  private final RunConfig runConfig;
  private boolean endInvocation;
  private final InvocationCostManager invocationCostManager = new InvocationCostManager();

  private InvocationContext(
      BaseSessionService sessionService,
      BaseArtifactService artifactService,
      Optional<LiveRequestQueue> liveRequestQueue,
      Optional<String> branch,
      String invocationId,
      BaseAgent agent,
      Session session,
      Optional<Content> userContent,
      RunConfig runConfig,
      boolean endInvocation) {
    this.sessionService = sessionService;
    this.artifactService = artifactService;
    this.liveRequestQueue = liveRequestQueue;
    this.branch = branch;
    this.invocationId = invocationId;
    this.agent = agent;
    this.session = session;
    this.userContent = userContent;
    this.runConfig = runConfig;
    this.endInvocation = endInvocation;
  }

  public static InvocationContext create(
      BaseSessionService sessionService,
      BaseArtifactService artifactService,
      String invocationId,
      BaseAgent agent,
      Session session,
      Content userContent,
      RunConfig runConfig) {
    return new InvocationContext(
        sessionService,
        artifactService,
        Optional.empty(),
        /* branch= */ Optional.empty(),
        invocationId,
        agent,
        session,
        Optional.ofNullable(userContent),
        runConfig,
        false);
  }

  public static InvocationContext create(
      BaseSessionService sessionService,
      BaseArtifactService artifactService,
      BaseAgent agent,
      Session session,
      LiveRequestQueue liveRequestQueue,
      RunConfig runConfig) {
    return new InvocationContext(
        sessionService,
        artifactService,
        Optional.ofNullable(liveRequestQueue),
        /* branch= */ Optional.empty(),
        InvocationContext.newInvocationContextId(),
        agent,
        session,
        Optional.empty(),
        runConfig,
        false);
  }

  public static InvocationContext copyOf(InvocationContext other) {
    return new InvocationContext(
        other.sessionService,
        other.artifactService,
        other.liveRequestQueue,
        other.branch,
        other.invocationId,
        other.agent,
        other.session,
        other.userContent,
        other.runConfig,
        other.endInvocation);
  }

  public BaseSessionService sessionService() {
    return sessionService;
  }

  public BaseArtifactService artifactService() {
    return artifactService;
  }

  public Optional<LiveRequestQueue> liveRequestQueue() {
    return liveRequestQueue;
  }

  public String invocationId() {
    return invocationId;
  }

  public void branch(@Nullable String branch) {
    this.branch = Optional.ofNullable(branch);
  }

  public Optional<String> branch() {
    return branch;
  }

  public BaseAgent agent() {
    return agent;
  }

  public void agent(BaseAgent agent) {
    this.agent = agent;
  }

  public Session session() {
    return session;
  }

  public Optional<Content> userContent() {
    return userContent;
  }

  public RunConfig runConfig() {
    return runConfig;
  }

  public boolean endInvocation() {
    return endInvocation;
  }

  public void setEndInvocation(boolean endInvocation) {
    this.endInvocation = endInvocation;
  }

  public String appName() {
    return session.appName();
  }

  public String userId() {
    return session.userId();
  }

  public static String newInvocationContextId() {
    return "e-" + UUID.randomUUID();
  }

  public void incrementLlmCallsCount() throws LlmCallsLimitExceededException {
    this.invocationCostManager.incrementAndEnforceLlmCallsLimit(this.runConfig);
  }

  private static class InvocationCostManager {
    private int numberOfLlmCalls = 0;

    public void incrementAndEnforceLlmCallsLimit(RunConfig runConfig)
        throws LlmCallsLimitExceededException {
      this.numberOfLlmCalls++;

      if (runConfig != null
          && runConfig.maxLlmCalls() > 0
          && this.numberOfLlmCalls > runConfig.maxLlmCalls()) {
        throw new LlmCallsLimitExceededException(
            "Max number of llm calls limit of " + runConfig.maxLlmCalls() + " exceeded");
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InvocationContext that)) {
      return false;
    }
    return endInvocation == that.endInvocation
        && Objects.equals(sessionService, that.sessionService)
        && Objects.equals(artifactService, that.artifactService)
        && Objects.equals(liveRequestQueue, that.liveRequestQueue)
        && Objects.equals(branch, that.branch)
        && Objects.equals(invocationId, that.invocationId)
        && Objects.equals(agent, that.agent)
        && Objects.equals(session, that.session)
        && Objects.equals(userContent, that.userContent)
        && Objects.equals(runConfig, that.runConfig);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sessionService,
        artifactService,
        liveRequestQueue,
        branch,
        invocationId,
        agent,
        session,
        userContent,
        runConfig,
        endInvocation);
  }
}
