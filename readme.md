Quick example of building a Thrift-based client and server in Java with Finagle.

The examples provided with Finagle itself are geared toward Scala, so this code covers some stumbling blocks I encountered while getting up and running.

This is just a quick program I run through IntelliJ to create a server and clients, which issue a series of blocking and non-blocking requests, all while being quite chatty about it, to demonstrate the basic flow of a Finagle server.

Notes:

* The Finagle pom points to several internal Twitter locations.  I've excluded those references that caused compilation to fail.

* Finagle uses a custom thrift compiler that emits Finagle-specific classes.  It can be downloaded from here: https://github.com/mariusaeriksen/thrift-0.5.0-finagle and is built and used in the usual way.

* I'm moving the generated thrift code into java/main just to avoid hassles with maven finding it, not for a finagle reason

* thrift:libthrift:pom:0.5.0 from Twitter doesn't have a pom associated with it, so Maven will complain about not being able to find one, but this doesn't prevent compilation or cause problems.

* One needs to include the whole finagle package in maven.  Just including finagle-thrift doesn't actually bring in the thrift package necessary for compilation

