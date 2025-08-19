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

package com.google.adk.tools.mcp;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.JsonBaseModel;
import com.google.adk.tools.ToolContext;
import com.google.common.collect.ImmutableMap;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO(b/413489523): Add support for auth. This is a TODO for Python as well.

/**
 * Initializes a MCP tool.
 *
 * <p>This wraps a MCP Tool interface and an active MCP Session. It invokes the MCP Tool through
 * executing the tool from remote MCP Session.
 */
public final class McpAsyncTool extends AbstractMcpTool<McpAsyncClient> {

  private static final Logger logger = LoggerFactory.getLogger(McpAsyncTool.class);

  /**
   * Creates a new McpAsyncTool with the default ObjectMapper.
   *
   * @param mcpTool The MCP tool to wrap.
   * @param mcpSession The MCP session to use to call the tool.
   * @param mcpSessionManager The MCP session manager to use to create new sessions.
   * @throws IllegalArgumentException If mcpTool or mcpSession are null.
   */
  public McpAsyncTool(
      Tool mcpTool, McpAsyncClient mcpSession, McpSessionManager mcpSessionManager) {
    super(mcpTool, mcpSession, mcpSessionManager, JsonBaseModel.getMapper());
  }

  /**
   * Creates a new McpAsyncTool
   *
   * @param mcpTool The MCP tool to wrap.
   * @param mcpSession The MCP session to use to call the tool.
   * @param mcpSessionManager The MCP session manager to use to create new sessions.
   * @param objectMapper The ObjectMapper to use to convert JSON schemas.
   * @throws IllegalArgumentException If mcpTool or mcpSession are null.
   */
  public McpAsyncTool(
      Tool mcpTool,
      McpAsyncClient mcpSession,
      McpSessionManager mcpSessionManager,
      ObjectMapper objectMapper) {
    super(mcpTool, mcpSession, mcpSessionManager, objectMapper);
  }

  private Single<McpSchema.InitializeResult> reinitializeSession() {
    McpAsyncClient client = this.mcpSessionManager.createAsyncSession();
    return Single.fromCompletionStage(
        client
            .initialize()
            .doOnSuccess(
                initResult -> {
                  logger.debug("Initialize McpAsyncClient Result: {}", initResult);
                })
            .doOnError(
                e -> {
                  logger.error("Initialize McpAsyncClient Failed: {}", e.getMessage(), e);
                })
            .doOnNext(
                _initResult -> {
                  this.mcpSession = client;
                })
            .toFuture());
  }

  @Override
  public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
    return Single.defer(
            () ->
                Maybe.fromCompletionStage(
                        this.mcpSession
                            .callTool(new CallToolRequest(this.name(), ImmutableMap.copyOf(args)))
                            .toFuture())
                    .map(callResult -> wrapCallResult(this.objectMapper, this.name(), callResult))
                    .switchIfEmpty(
                        Single.fromCallable(
                            () -> wrapCallResult(this.objectMapper, this.name(), null))))
        .retryWhen(
            errors ->
                errors
                    .delay(100, MILLISECONDS)
                    .take(3)
                    .doOnNext(
                        error ->
                            logger.error("Retrying callTool due to: {}", error.getMessage(), error))
                    .flatMapSingle(_ignore -> this.reinitializeSession()));
  }
}
