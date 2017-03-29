package com.box.castle.committer.api

import kafka.message.Message
import scala.concurrent.duration.FiniteDuration

sealed trait CommitResult

/**
 * User committer successfully committed entire batch
 */
case class BatchCommittedSuccessfully(metadata: Option[String] = None) extends CommitResult

/**
 * User committer successfully committed a portion of the message batch.
 * Send this message when the framework should re-send the uncommitted portion
 * of the original batch after a certain delay
 * @param retryAfter Duration to retry after
 * @param messageBatch message batch or a portion of a message batch to retry with.
 *                     The batch must contain AT LEAST one message
 */
case class RetryBatch(retryAfter: FiniteDuration, messageBatch: IndexedSeq[Message], metadata: Option[String] = None) extends CommitResult {
  require(messageBatch.nonEmpty)
}
