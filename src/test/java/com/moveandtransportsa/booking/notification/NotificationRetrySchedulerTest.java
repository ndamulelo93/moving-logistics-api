package com.moveandtransportsa.booking.notification;

import com.moveandtransportsa.booking.domain.notification.NotificationLog;
import com.moveandtransportsa.booking.domain.notification.NotificationStatus;
import com.moveandtransportsa.booking.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationRetryScheduler}.
 *
 * Covers:
 * - Requirement 17.2: Retry up to 3 times with exponential backoff.
 * - Requirement 17.3: Mark FAILED after all retry attempts are exhausted.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private NotificationRetryScheduler notificationRetryScheduler;

    // -----------------------------------------------------------------------
    // Helper builders
    // -----------------------------------------------------------------------

    /**
     * Builds a PENDING NotificationLog with the given attempt count and last attempt time.
     */
    private NotificationLog buildLog(short attemptCount, Instant lastAttemptAt) {
        return NotificationLog.builder()
                .id(1L)
                .eventType("QUOTED")
                .status(NotificationStatus.PENDING)
                .attemptCount(attemptCount)
                .lastAttemptAt(lastAttemptAt)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // Requirement 17.2 — retries with exponential backoff
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 17.2
     *
     * When a retryable log has never been attempted (lastAttemptAt is null),
     * the scheduler should dispatch it immediately.
     */
    @Test
    void retryFailed_logWithNullLastAttempt_dispatchesImmediately() {
        // Arrange: log with no previous attempt
        NotificationLog log = buildLog((short) 0, null);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: dispatcher was called once
        verify(notificationDispatcher, times(1)).dispatch(log);
    }

    /**
     * Validates: Requirements 17.2
     *
     * When a log has 0 prior attempts and the backoff window (2^0 * 30s = 30s) has elapsed,
     * the scheduler should dispatch it.
     */
    @Test
    void retryFailed_firstAttemptBackoffElapsed_dispatches() {
        // Arrange: last attempt was 31 seconds ago — backoff of 30s has elapsed
        Instant lastAttempt = Instant.now().minusSeconds(31);
        NotificationLog log = buildLog((short) 0, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert
        verify(notificationDispatcher, times(1)).dispatch(log);
    }

    /**
     * Validates: Requirements 17.2
     *
     * When a log has 0 prior attempts and the backoff window (30s) has NOT elapsed,
     * the scheduler should skip it.
     */
    @Test
    void retryFailed_firstAttemptBackoffNotElapsed_skips() {
        // Arrange: last attempt was only 10 seconds ago — backoff of 30s has not elapsed
        Instant lastAttempt = Instant.now().minusSeconds(10);
        NotificationLog log = buildLog((short) 0, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: dispatcher was NOT called
        verify(notificationDispatcher, never()).dispatch(any());
    }

    /**
     * Validates: Requirements 17.2
     *
     * After 1 failed attempt, the backoff is 2^1 * 30s = 60s.
     * When 61 seconds have elapsed since the last attempt, the scheduler should dispatch.
     */
    @Test
    void retryFailed_secondAttemptBackoffElapsed_dispatches() {
        // Arrange: 1 prior attempt, last attempt 61 seconds ago (backoff = 60s)
        Instant lastAttempt = Instant.now().minusSeconds(61);
        NotificationLog log = buildLog((short) 1, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert
        verify(notificationDispatcher, times(1)).dispatch(log);
    }

    /**
     * Validates: Requirements 17.2
     *
     * After 1 failed attempt, the backoff is 2^1 * 30s = 60s.
     * When only 30 seconds have elapsed, the scheduler should skip it.
     */
    @Test
    void retryFailed_secondAttemptBackoffNotElapsed_skips() {
        // Arrange: 1 prior attempt, last attempt 30 seconds ago (backoff = 60s, not elapsed)
        Instant lastAttempt = Instant.now().minusSeconds(30);
        NotificationLog log = buildLog((short) 1, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: dispatcher was NOT called
        verify(notificationDispatcher, never()).dispatch(any());
    }

    /**
     * Validates: Requirements 17.2
     *
     * After 2 failed attempts, the backoff is 2^2 * 30s = 120s.
     * When 121 seconds have elapsed since the last attempt, the scheduler should dispatch.
     */
    @Test
    void retryFailed_thirdAttemptBackoffElapsed_dispatches() {
        // Arrange: 2 prior attempts, last attempt 121 seconds ago (backoff = 120s)
        Instant lastAttempt = Instant.now().minusSeconds(121);
        NotificationLog log = buildLog((short) 2, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert
        verify(notificationDispatcher, times(1)).dispatch(log);
    }

    /**
     * Validates: Requirements 17.2
     *
     * After 2 failed attempts, the backoff is 2^2 * 30s = 120s.
     * When only 60 seconds have elapsed, the scheduler should skip it.
     */
    @Test
    void retryFailed_thirdAttemptBackoffNotElapsed_skips() {
        // Arrange: 2 prior attempts, last attempt 60 seconds ago (backoff = 120s, not elapsed)
        Instant lastAttempt = Instant.now().minusSeconds(60);
        NotificationLog log = buildLog((short) 2, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: dispatcher was NOT called
        verify(notificationDispatcher, never()).dispatch(any());
    }

    /**
     * Validates: Requirements 17.2
     *
     * When multiple retryable logs are present and all have elapsed backoffs,
     * the scheduler dispatches each one.
     */
    @Test
    void retryFailed_multipleRetryableLogs_dispatchesAll() {
        // Arrange: two logs both past their backoff windows
        NotificationLog log1 = buildLog((short) 0, Instant.now().minusSeconds(31));
        NotificationLog log2 = buildLog((short) 1, Instant.now().minusSeconds(61));
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log1, log2));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: both dispatched
        verify(notificationDispatcher, times(1)).dispatch(log1);
        verify(notificationDispatcher, times(1)).dispatch(log2);
    }

    /**
     * Validates: Requirements 17.2
     *
     * When multiple retryable logs are present but only some have elapsed backoffs,
     * the scheduler dispatches only the eligible ones.
     */
    @Test
    void retryFailed_mixedBackoffLogs_dispatchesOnlyEligible() {
        // Arrange: log1 is eligible (backoff elapsed), log2 is not (backoff not elapsed)
        NotificationLog log1 = buildLog((short) 0, Instant.now().minusSeconds(31));
        NotificationLog log2 = buildLog((short) 1, Instant.now().minusSeconds(10)); // 60s backoff, only 10s elapsed
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log1, log2));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: only log1 dispatched
        verify(notificationDispatcher, times(1)).dispatch(log1);
        verify(notificationDispatcher, never()).dispatch(log2);
    }

    // -----------------------------------------------------------------------
    // Requirement 17.3 — does not retry logs already marked FAILED
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 17.3
     *
     * Logs already marked FAILED are not returned by findRetryable (the repository
     * query filters them out). The scheduler should not dispatch anything when
     * the repository returns an empty list.
     */
    @Test
    void retryFailed_noRetryableLogs_doesNotDispatch() {
        // Arrange: repository returns empty list (all logs are SENT or exhausted)
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of());

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: dispatcher never called
        verify(notificationDispatcher, never()).dispatch(any());
    }

    /**
     * Validates: Requirements 17.3
     *
     * The scheduler queries the repository with maxAttempts=3, ensuring logs
     * with 3 or more attempts are excluded from the retryable set.
     */
    @Test
    void retryFailed_queriesRepositoryWithMaxAttemptsThree() {
        // Arrange
        when(notificationLogRepository.findRetryable(anyInt())).thenReturn(List.of());

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: repository queried with exactly 3 as the max attempts threshold
        verify(notificationLogRepository).findRetryable(3);
    }

    /**
     * Validates: Requirements 17.3
     *
     * A log with attemptCount=2 (one retry remaining) and elapsed backoff is dispatched.
     * This is the last eligible retry before the log would be marked FAILED.
     */
    @Test
    void retryFailed_logAtMaxMinusOneAttempts_stillDispatches() {
        // Arrange: 2 prior attempts (one retry left), backoff of 120s has elapsed
        Instant lastAttempt = Instant.now().minusSeconds(121);
        NotificationLog log = buildLog((short) 2, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: dispatcher called — this is the final allowed retry
        verify(notificationDispatcher, times(1)).dispatch(log);
    }

    // -----------------------------------------------------------------------
    // Exponential backoff formula verification
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 17.2
     *
     * Verifies the exponential backoff formula: backoff = 2^attemptCount * 30s.
     * At attempt 0: 30s, attempt 1: 60s, attempt 2: 120s.
     * A log dispatched exactly at the boundary (backoff + 1ms) should be eligible.
     */
    @Test
    void retryFailed_backoffBoundaryCondition_dispatchesAtExactBoundary() {
        // Arrange: attempt 1, backoff = 60s; last attempt was exactly 60s + 1ms ago
        Instant lastAttempt = Instant.now().minusMillis(60_001);
        NotificationLog log = buildLog((short) 1, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: dispatched because backoff has elapsed
        verify(notificationDispatcher, times(1)).dispatch(log);
    }

    /**
     * Validates: Requirements 17.2
     *
     * A log dispatched just before the backoff boundary should NOT be eligible.
     */
    @Test
    void retryFailed_backoffBoundaryCondition_skipsJustBeforeBoundary() {
        // Arrange: attempt 1, backoff = 60s; last attempt was 59s ago (not yet elapsed)
        Instant lastAttempt = Instant.now().minusSeconds(59);
        NotificationLog log = buildLog((short) 1, lastAttempt);
        when(notificationLogRepository.findRetryable(3)).thenReturn(List.of(log));

        // Act
        notificationRetryScheduler.retryFailed();

        // Assert: NOT dispatched because backoff has not elapsed
        verify(notificationDispatcher, never()).dispatch(any());
    }
}
