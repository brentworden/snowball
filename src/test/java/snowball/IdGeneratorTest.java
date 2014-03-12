/*
Copyright (c) 2014, Brent Worden
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

 * Neither the name of Brent Worden nor the names of the
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package snowball;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;

public class IdGeneratorTest {

	// worker that collects the ids generated
	private static class CollectionWorker implements Runnable {
		private final IdGenerator generator;

		private final long[] ids;

		public CollectionWorker(int count, IdGenerator generator) {
			super();
			this.generator = generator;
			this.ids = new long[count];
		}

		public long[] getIds() {
			return ids;
		}

		@Override
		public void run() {
			for (int i = 0; i < ids.length; ++i) {
				ids[i] = generator.nextId();
			}
		}
	}

	private static interface CountRunnable extends Runnable {
		long getCount();

		void stop();
	}

	// worker that counts the number of ids generated
	private static class CountWorker implements CountRunnable {
		private long count = 0;

		private final IdGenerator generator;

		private volatile boolean running = true;

		public CountWorker(IdGenerator generator) {
			super();
			this.generator = generator;
		}

		@Override
		public long getCount() {
			return count;
		}

		@Override
		public void run() {
			while (running) {
				generator.nextId();
				++count;
			}
		}

		@Override
		public void stop() {
			running = false;
		}
	}

	// worker that counts the number of ids generated
	private static class UuidCountWorker implements CountRunnable {
		private long count = 0;

		private volatile boolean running = true;

		@Override
		public long getCount() {
			return count;
		}

		@Override
		public void run() {
			while (running) {
				UUID.randomUUID();
				++count;
			}
		}

		@Override
		public void stop() {
			running = false;
		}
	}

	private static class WaitRunNotify implements Runnable {
		private final Runnable delegate;

		private final CountDownLatch done;

		private final CountDownLatch start;

		public WaitRunNotify(CountDownLatch start, CountDownLatch done,
				Runnable delegate) {
			super();
			this.start = start;
			this.done = done;
			this.delegate = delegate;
		}

		@Override
		public void run() {
			try {
				// wait until the start is signaled
				start.await();

				// execute delegate
				delegate.run();

				// signal completion
				done.countDown();
			} catch (InterruptedException ex) {
				// ignore
			}
		}
	}

	private static final IdGenerator generator = new IdGenerator(75);

	private static final int numberOfIdsPerThread = 234;

	private static final int numberOfThreads = 567;

	private void assetStrictlyIncreasing(long[] ids) {
		for (int i = 0; i < ids.length - 1; ++i) {
			assertTrue(ids[i] < ids[i + 1]);
		}
	}

	private void runMultiThreadCorrectness() throws InterruptedException {
		// setup the workers
		CollectionWorker[] workers = new CollectionWorker[numberOfThreads];
		for (int i = 0; i < workers.length; ++i) {
			workers[i] = new CollectionWorker(numberOfIdsPerThread, generator);
		}

		// create the barriers
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(workers.length);

		// start the workers
		for (CollectionWorker worker : workers) {
			new Thread(new WaitRunNotify(start, done, worker)).start();
		}
		start.countDown();

		// wait for the results
		done.await();

		long[] allIds = {};
		for (CollectionWorker worker : workers) {
			// single threaded ids should be strictly increasing in order
			long[] ids = worker.getIds();
			assetStrictlyIncreasing(ids);

			// collect all the ids
			allIds = ArrayUtils.addAll(allIds, ids);
		}

		// all sorted ids should be strictly increasing in order
		Arrays.sort(allIds);
		assetStrictlyIncreasing(allIds);
	}

	private void runMultiThreadThroughput(String test, double expectedThroughput)
			throws InterruptedException {
		// setup the workers
		CountWorker[] workers = new CountWorker[numberOfThreads];
		for (int i = 0; i < workers.length; ++i) {
			workers[i] = new CountWorker(generator);
		}

		// create the barriers
		CountDownLatch wait = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(workers.length);

		// start the workers
		for (CountWorker worker : workers) {
			new Thread(new WaitRunNotify(wait, done, worker)).start();
		}

		// time work
		long start = System.currentTimeMillis();
		wait.countDown();
		Thread.sleep(100L);
		for (CountWorker worker : workers) {
			worker.stop();
		}
		long end = System.currentTimeMillis();

		// wait for the results
		done.await();

		// compute results
		long count = 0L;
		for (CountWorker worker : workers) {
			count += worker.getCount();
		}

		long duration = end - start;
		double idsPerSecond = (double) count / (double) duration * 1000.0;
		String msg = String
				.format("%s MULTI THREAD THROUGHPUT:  %d ids in %d milliseconds (%f ids / second).",
						test, count, duration, idsPerSecond);
		assertTrue(msg, idsPerSecond > expectedThroughput);
	}

	public void runSingleThreadCorrectness() {
		long[] ids = new long[numberOfIdsPerThread];

		for (int i = 0; i < ids.length; ++i) {
			long id = generator.nextId();
			ids[i] = id;
		}

		// single threaded ids should be strictly increasing in order
		assetStrictlyIncreasing(ids);
	}

	private void runSingleThreadThroughput(String test, CountRunnable worker,
			double expectedThroughput) throws InterruptedException {
		Thread t = new Thread(worker);

		// time work
		long start = System.currentTimeMillis();
		t.start();
		Thread.sleep(100L);
		worker.stop();
		long end = System.currentTimeMillis();

		// compute results
		long count = worker.getCount();

		long duration = end - start;
		double idsPerSecond = (double) count / (double) duration * 1000.0;
		String msg = String
				.format("%s SINGLE THREAD THROUGHPUT:  %d ids in %d milliseconds (%f ids / second).",
						test, count, duration, idsPerSecond);
		assertTrue(msg, idsPerSecond > expectedThroughput);
	}

	private void runSingleThreadThroughput(String test,
			double expectedThroughput) throws InterruptedException {
		CountWorker worker = new CountWorker(generator);
		runSingleThreadThroughput(test, worker, expectedThroughput);
	}

	@Test
	public void testSingleNodeMultiThreadCorrectness()
			throws InterruptedException {
		runMultiThreadCorrectness();
	}

	@Test
	public void testSingleNodeMultiThreadedThroughput()
			throws InterruptedException {
		runMultiThreadThroughput("SINGLE NODE", 100000.0);
	}

	@Test
	public void testSingleNodeSingleThreadCorrectness() {
		runSingleThreadCorrectness();
	}

	@Test
	public void testSingleNodeSingleThreadThroughput()
			throws InterruptedException {
		runSingleThreadThroughput("SINGLE NODE", 100000.0);
	}

	@Test
	public void testUuidThroughput() throws InterruptedException {
		UuidCountWorker worker = new UuidCountWorker();
		runSingleThreadThroughput("UUID", worker, 1000.0);
	}
}
