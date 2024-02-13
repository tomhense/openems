package io.openems.edge.bridge.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;

import io.openems.common.utils.ReflectionUtils;
import io.openems.edge.bridge.http.api.BridgeHttp;
import io.openems.edge.common.event.EdgeEventConstants;

public class BridgeHttpImplTest {

	private DummyUrlFetcher fetcher;
	private CycleSubscriber cycleSubscriber;
	private BridgeHttp bridgeHttp;

	@Before
	public void before() throws Exception {
		this.cycleSubscriber = new CycleSubscriber();
		this.bridgeHttp = new BridgeHttpImpl();
		ReflectionUtils.setAttribute(BridgeHttpImpl.class, this.bridgeHttp, "cycleSubscriber", this.cycleSubscriber);

		this.fetcher = new DummyUrlFetcher();
		this.fetcher.addUrlHandler(url -> {
			return switch (url) {
			case "dummy" -> "success";
			case "error" -> throw new RuntimeException();
			default -> null;
			};
		});
		ReflectionUtils.setAttribute(BridgeHttpImpl.class, this.bridgeHttp, "urlFetcher", this.fetcher);

		((BridgeHttpImpl) this.bridgeHttp).activate();
	}

	@After
	public void after() throws Exception {
		((BridgeHttpImpl) this.bridgeHttp).deactivate();
	}

	@Test
	public void test() throws Exception {
		final var callCount = new AtomicInteger(0);
		final var future = new CompletableFuture<Void>();
		this.bridgeHttp.subscribe(3, "dummy", t -> {
			assertEquals("success", t);
			callCount.incrementAndGet();
			future.complete(null);
		});

		assertEquals(0, callCount.get());
		this.nextCycle();
		assertEquals(0, callCount.get());
		this.nextCycle();
		assertEquals(0, callCount.get());
		this.nextCycle();

		// wait until finished
		future.get();
		assertEquals(1, callCount.get());
	}

	@Test
	public void testNotRunningMultipleTimes() throws Exception {
		final var callCount = new AtomicInteger(0);
		final var lock = new Object();

		final var waitUntilContinueHandler = new CompletableFuture<Void>();
		this.bridgeHttp.subscribeEveryCycle("dummy", t -> {
			assertEquals("success", t);
			callCount.incrementAndGet();
			synchronized (lock) {
				lock.notify();

			}
			try {
				waitUntilContinueHandler.get();
			} catch (InterruptedException | ExecutionException e) {
				assertTrue(false);
			}
		});

		final var waitForTaskFinish = new CompletableFuture<Void>();
		this.fetcher.setOnTaskFinished(() -> waitForTaskFinish.complete(null));
		synchronized (lock) {
			assertEquals(0, callCount.get());
			this.nextCycle();
			lock.wait();
		}

		synchronized (lock) {
			assertEquals(1, callCount.get());
			this.nextCycle();
			assertEquals(1, callCount.get());
			this.nextCycle();
			assertEquals(1, callCount.get());
		}
		waitUntilContinueHandler.complete(null);

		// wait until last request is finished
		waitForTaskFinish.get();
		synchronized (lock) {
			this.nextCycle();
			lock.wait();
		}
		assertEquals(2, callCount.get());
	}

	@Test
	public void testRequestFail() throws Exception {
		final var error = new CompletableFuture<Throwable>();
		this.bridgeHttp.subscribeEveryCycle("error", t -> {
			// empty
		}, error::complete);

		this.nextCycle();
		assertNotNull(error.get());
	}

	private void nextCycle() {
		this.cycleSubscriber
				.handleEvent(new Event(EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE, new HashMap<>()));
	}

}
