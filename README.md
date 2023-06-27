# Missing

* Proper `/` handling in Uri.Path (or maybe not, to be analyzed)
* CacheConditionDirectives
* CodingDirectives
* directory listing in FileAndResourceDirectives
* RangeDirectives
* checkSameOrigin in HeaderDirectives
* handling of multipart forms in FormFieldDirectives (but I don't like it anyway)
* Some of how akka configures things
  * withSizeLimit
  * withoutSizeLimit
  * requestEntityEmpty
  * requestEntityPresent
  * rejectEmptyResponse
  * extractRequestTimeout
  * withRequestTimeoutResponse
* AttributeDirectives
* FramedEntityStreamingDirectives
* WebSocketDirectives in large part
* Testkit needed significant changes
  * Not async anymore
  * Chunks not supported
  * Request building incomplete (missing some minor header methods)
  * All websocket thingies
  * Some logic of transparent headers and default host info