package com.yo1no.gramarye.magic.definition.store;

/**
 * Exact future package-private, product-owned, pure thread-precondition surface.
 * This top-level class is final and has no public or protected surface.
 */
final class ProductThreadPrecondition {
    private ProductThreadPrecondition() {
    }

    static Decision classify(long expectedLogicThreadId, long observedThreadId) {
        return expectedLogicThreadId > 0L && expectedLogicThreadId == observedThreadId
                ? Decision.ALLOWED
                : Decision.WRONG_THREAD;
    }

    enum Decision {
        ALLOWED,
        WRONG_THREAD
    }
}

/*
Exact placement:
  src/main/java/com/yo1no/gramarye/magic/definition/store/
  ProductThreadPrecondition.java

Constructor/factory invariant:
  no instance is constructible outside the class; there is no factory and no retained state.

Parameter/return invariant:
  both parameters are primitive long values; nullable parameters = 0; Object payload = 0;
  callback = 0; Throwable = 0. A non-positive expectedLogicThreadId fails closed.

Exact product operation 1 binding:
  public latestReference(server, skillId)
  -> package-private latestReference(server, skillId, Thread.currentThread().threadId())
  -> requireServerThread(server, observedThreadId)
  -> ProductThreadPrecondition.classify(
         server.getRunningThread().threadId(), observedThreadId)
  -> WRONG_THREAD maps in SkillDefinitionStoreService to
     new SkillSubsystemLifecycleException(Code.WRONG_THREAD)
  -> ALLOWED enters installedAdapter marker/cache access.

Exact product operation 2 binding:
  audit(capture)
  -> package-private audit(capture, Thread.currentThread().threadId())
  -> compute the closed observed-thread decision before capture mutation with
     ProductThreadPrecondition.classify(
         serverIdentity.getRunningThread().threadId(), observedThreadId)
  -> carry Decision into Captured.claim(this, decision)
  -> Claimed.requireActive(this, decision)
  -> requireCaptureBinding(..., decision)
  -> retain the existing exact-server, owner, captured-creation-Thread reference,
     and same-tick binding checks; MinecraftServer.serverThread is final and the
     audit owner was constructed only after the same-server-thread precondition
  -> WRONG_THREAD maps in P4E1GroupedStoreAudit to
     new BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH")
     after existing consume/move/clear and failed-claim cleanup
  -> ALLOWED continues to same-tick validation and audit.

Exact GameTest call shape:
  long expectedThreadId = server.getRunningThread().threadId();
  helper.assertTrue(
      ProductThreadPrecondition.classify(expectedThreadId, expectedThreadId)
          == ProductThreadPrecondition.Decision.ALLOWED,
      "shared product thread gate must allow the server logic thread");
  helper.assertTrue(
      ProductThreadPrecondition.classify(expectedThreadId, 0L)
          == ProductThreadPrecondition.Decision.WRONG_THREAD,
      "shared product thread gate must reject the synthetic no-thread observation");
  isolated.latestReference(server, skillId, 0L);
  exactThreadOwner.audit(wrongThreadCapture, 0L);

The two exact typed exceptions are caught only by their exact exception types for assertion.
No Thread object is constructed, no Thread is started, and no Thread ID is stored in a field,
result, queue, diagnostic, persistent object, or callback. Public top-level type delta = 0.
*/
