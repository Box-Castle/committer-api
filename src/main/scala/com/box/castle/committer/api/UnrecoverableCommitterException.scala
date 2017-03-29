package com.box.castle.committer.api

/**
 * Any class implementing the Committer interface can throw this exception to indicate that it has
 * encountered an unrecoverable exception and further operations should not be retried by the Castle framework.
 */
class UnrecoverableCommitterException(msg: String, t: Throwable = null) extends UnrecoverableException(msg, t)
