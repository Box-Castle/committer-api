package com.box.castle.committer.api

import java.io.Closeable
import java.util.concurrent.TimeUnit

import com.box.castle.retry.RetryStrategy
import com.box.castle.retry.strategies.{TruncatedBinaryExponentialBackoffStrategy, RandomMinMaxStrategy}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
 * The CommitterFactory is expected to provide an instance of a committer for a given topic, partition, and id.
 *
 * There will only be one instance of this CommitterFactory for the entire process, therefore thread safety concerns
 * must be considered depending on which create method you override.
 *
 * The committer factory *MUST* have a constructor that takes a single argument of type Map[String,String], it may have
 * other constructors as long as this one is present.
 *
 * To provide an instance of the committer in a synchronous manner the committer factory *MUST* implement the
 * create() method, this method call is guaranteed to be thread safe.
 *
 * To provide an instance of the committer in an asynchronous manner the committer factory should implement the
 * createAsync() method.  When overriding createAsync() it is the responsibility of the implementer to guarantee
 * thread safety because createAsync() will be called by multiple threads simultaneously.  When overriding
 * createAsync(), the following create() implementation is recommended:
 *
 *     override def create(topic: String, partition: Int): Committer =
 *       throw new UnrecoverableCommitterFactoryException("Synchronous create should never be called")
 *
 * If both create() and createAsync() methods are implemented, the create() method is ignored, only createAsync() is
 * called by the framework.
 */
trait CommitterFactory extends Closeable {

  private[this] val createLock = new Object()

  /**
   * All non-fatal exceptions thrown by this method will be retried except for UnrecoverableException and its
   * subclasses, which include: UnrecoverableCommitterException and UnrecoverableCommitterFactoryException.
   *
   * @param topic - please see the description in createAsync
   * @param partition - please see the description in createAsync
   * @param id - please see the description in createAsync
   */
  def create(topic: String, partition: Int, id: Int): Committer

  /**
   * All non-fatal exceptions thrown by this method will be retried except for UnrecoverableException and its
   * subclasses, which include: UnrecoverableCommitterException and UnrecoverableCommitterFactoryException.
   *
   * @param topic - the Kafka topic associated with the committer
   * @param partition - the Kafka partition of the topic associated with the committer
   * @param id - the id associated with the committer, this id depends on the parallelism factor.
   *             If the parallelism factor is set to 1, then for each topic and partition createAsync is called
   *             just once and the passed in id will be 0
   *             If the parallelism factor is set to 2, then for each topic and partition createAsync is called
   *             2 times, in the first call the id will be 0, in the second call the id will be 1
   *             and so on and so forth
   *
   *             The point of this id is to allow the committer implementation to have a systematic way to tell apart
   *             different instances of the committer associated with the same topic and partition when the parallelism
   *             factor is set to more than 1, if the parallelism factor is set to 1, there is not much utility in this id
   *
   * @param execctx
   * @return
   */
  def createAsync(topic: String, partition: Int, id: Int)(implicit execctx: ExecutionContext): Future[Committer] = {
    Future {
      createLock synchronized {
        create(topic, partition, id)
      }
    }
  }

  /**
   * This method will be called synchronously during shutdown causing the Castle framework to wait until it exits
   * before continuing with the shutdown sequence for the maximum time specified in CastleConfig.gracefulShutdownTimeout
   *
   * The call to close is *NOT* thread safe, if you access global state here, make sure to synchronize
   */
  override def close(): Unit = {
    // No-op
  }

  /**
   * Specify the strategy to use when backing off due to having no data in Kafka to consume
   *
   * By default we will retry indefinitely
   *
   * @return
   */
  def noDataBackoffStrategy: RetryStrategy = DefaultNoDataBackoffStrategy

  /**
   * Specify the strategy to use when retrying recoverable exceptions thrown by the Committer.
   * All non-fatal exceptions are considered to be recoverable unless they are a subtype of UnrecoverableException.
   *
   * By default we will retry indefinitely
   * @return
   */
  def recoverableExceptionRetryStrategy: RetryStrategy = DefaultRecoverableExceptionRetryStrategy

  /**
   * Specify the strategy to use when retrying recoverable exceptions thrown by the create() method of the factory.
   *
   * All non-fatal exceptions are considered to be recoverable unless they are a subtype of UnrecoverableException.
   *
   * By default we will retry indefinitely
   * @return
   */
  def createCommitterExceptionRetryStrategy: RetryStrategy = DefaultRecoverableExceptionRetryStrategy

  /**
    * Creates a filter which would filter topics that the committer should process
    *
    * @return
    */
  def createTopicFilter(): TopicFilter = {
    new TopicFilter()
  }

  val DefaultNoDataBackoffStrategy =
    RandomMinMaxStrategy(FiniteDuration(5, TimeUnit.SECONDS), FiniteDuration(10, TimeUnit.SECONDS))

  val DefaultRecoverableExceptionRetryStrategy =
    RandomMinMaxStrategy(FiniteDuration(1, TimeUnit.SECONDS), FiniteDuration(16, TimeUnit.SECONDS))

  val DefaultCreateCommitterExceptionRetryStrategy = TruncatedBinaryExponentialBackoffStrategy()
}
