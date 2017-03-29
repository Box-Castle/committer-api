package com.box.castle.committer.api

import java.io.Closeable

import kafka.message.Message

import scala.concurrent.{ExecutionContext, Future}

trait Committer extends Closeable {

  /**
   * This method is called by the Castle framework to commit a batch of messages.  The committer can signal whether it
   * succeeded or needs some other action to take place by returning a subtype of CommitResult, please look at
   * CommitResult to see what return signals are available.
   *
   * Any exception thrown by this method that is NonFatal is considered to be retryable.  The strategy used
   * by Castle when retrying committer failures is specified by CommitterFactory.recoverableExceptionRetryStrategy,
   * which can be overridden with a custom strategy.  The default strategy will retry indefinitely.
   *
   * If the committer encounters an unrecoverable exception, it should throw an UnrecoverableCommitterException, which
   * will cause the Castle framework to stop running this committer.
   *
   * The Castle framework does not call commit directly, instead it calls commitAsync, which by default wraps this
   * synchronous commit method in a future.  If you wish, you can override commitAsync instead of this method,
   * in which case, the following implementation for this method is recommended:
   *
   * override commit(messageBatch: IndexedSeq[Message]): CommitResult =
   * throw new UnrecoverableCommitterException("Synchronous commit should never be called")
   *
   * @param messageBatch - batch of messages to commit
   * @return CommitResult
   */
  def commit(messageBatch: IndexedSeq[Message], metadata: Option[String]): CommitResult

  /**
   * This method is thread safe if the following conditions are met:
   * - Your CommitterFactory creates a *NEW* instance of the Committer each time an instance is requested
   * - Your Committer stores all state in instance members
   *
   * You are responsible for guaranteeing thread safety yourself if:
   * - the CommitterFactory re-uses Committer instances
   * - the Committer accesses/modifies global state from within the Committer code
   *
   * @param messageBatch - batch of messages to commit
   * @param execctx - execution context that is shared between all instances of this committer
   * @return CommitResult
   */
  def commitAsync(messageBatch: IndexedSeq[Message], metadata: Option[String])(implicit execctx: ExecutionContext): Future[CommitResult] = {
    Future(commit(messageBatch, metadata))
  }

  /**
   * This method will be called synchronously during shutdown causing the Castle framework to wait until it exits
   * before continuing with the shutdown sequence for the maximum time specified in CastleConfig.gracefulShutdownTimeout
   */
  override def close(): Unit = {
    // No-op
  }

  /**
   * This is the method that will be called by the underlying castle infrastruture when a heartbeat occurs
   * to enable heartbeat, "heartbeatCadenceInMillis" must be set in your committer's config (see BoxCastleConfig.scala)
   *
   * heartbeat is only called when there is no data fetched from the stream. In other words if
   * commit is constantly called, there will be no heartbeat
   * @return updated metadata
   */
  def heartbeat(metadata: Option[String]): Option[String] = {
    //no-op
    metadata
  }

}
