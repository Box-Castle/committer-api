package com.box.castle.committer.api

abstract class UnrecoverableException(msg: String, t: Throwable) extends RuntimeException(msg, t)
