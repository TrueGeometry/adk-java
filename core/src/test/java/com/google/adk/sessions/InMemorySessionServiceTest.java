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
package com.google.adk.sessions;

import static com.google.common.truth.Truth.assertThat;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import io.reactivex.rxjava3.core.Single;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link InMemorySessionService}. */
@RunWith(JUnit4.class)
public final class InMemorySessionServiceTest {

  @Test
  public void lifecycle_noSession() {
    InMemorySessionService sessionService = new InMemorySessionService();

    assertThat(
            sessionService
                .getSession("app-name", "user-id", "session-id", Optional.empty())
                .blockingGet())
        .isNull();

    assertThat(sessionService.listSessions("app-name", "user-id").blockingGet().sessions())
        .isEmpty();

    assertThat(
            sessionService.listEvents("app-name", "user-id", "session-id").blockingGet().events())
        .isEmpty();
  }

  @Test
  public void lifecycle_createSession() {
    InMemorySessionService sessionService = new InMemorySessionService();

    Single<Session> sessionSingle = sessionService.createSession("app-name", "user-id");

    Session session = sessionSingle.blockingGet();

    assertThat(session.id()).isNotNull();
    assertThat(session.appName()).isEqualTo("app-name");
    assertThat(session.userId()).isEqualTo("user-id");
    assertThat(session.state()).isEmpty();
  }

  @Test
  public void lifecycle_getSession() {
    InMemorySessionService sessionService = new InMemorySessionService();

    Session session = sessionService.createSession("app-name", "user-id").blockingGet();

    Session retrievedSession =
        sessionService
            .getSession(session.appName(), session.userId(), session.id(), Optional.empty())
            .blockingGet();

    assertThat(retrievedSession).isNotNull();
    assertThat(retrievedSession.id()).isEqualTo(session.id());
  }

  @Test
  public void lifecycle_listSessions() {
    InMemorySessionService sessionService = new InMemorySessionService();

    Session session = sessionService.createSession("app-name", "user-id").blockingGet();

    ListSessionsResponse response =
        sessionService.listSessions(session.appName(), session.userId()).blockingGet();

    assertThat(response.sessions()).hasSize(1);
    assertThat(response.sessions().get(0).id()).isEqualTo(session.id());
  }

  @Test
  public void lifecycle_deleteSession() {
    InMemorySessionService sessionService = new InMemorySessionService();

    Session session = sessionService.createSession("app-name", "user-id").blockingGet();

    sessionService.deleteSession(session.appName(), session.userId(), session.id()).blockingAwait();

    assertThat(
            sessionService
                .getSession(session.appName(), session.userId(), session.id(), Optional.empty())
                .blockingGet())
        .isNull();
  }

  @Test
  public void appendEvent_withStateDelta_mutatesStateCorrectly() {
    ConcurrentMap<String, Object> startingState1 = new ConcurrentHashMap<>();
    startingState1.put("key_to_be_removed", "value_to_be_removed");
    ConcurrentMap<String, Object> startingState2 = new ConcurrentHashMap<>();
    startingState2.put("key_to_be_removed", "value_to_be_removed");

    ConcurrentMap<String, Object> stateDelta = new ConcurrentHashMap<>();
    State expectedFinalState = new State(startingState1, stateDelta);
    expectedFinalState.remove("key_to_be_removed");
    expectedFinalState.put("new_key", "new_value");

    InMemorySessionService sessionService = new InMemorySessionService();
    Session session =
        sessionService
            .createSession("app-name", "user-id", startingState2, /* sessionId= */ null)
            .blockingGet();

    var unused =
        sessionService
            .appendEvent(
                session,
                Event.builder()
                    .actions(EventActions.builder().stateDelta(stateDelta).build())
                    .build())
            .blockingGet();

    assertThat(session.state().entrySet()).containsExactlyElementsIn(expectedFinalState.entrySet());
  }
}
