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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

public class IdGenerator {

	private static class IdState {

		/**
		 * 2013-04-01T23:59:59.999Z
		 */
		private static final DateTime epoch = new DateTime(2013, 4, 1, 23, 59,
				59, 999, DateTimeZone.UTC);

		private static final long maxNodeId;

		private static final long maxSequenceId;

		private static final long maxThreadId;

		private static final long nodeIdBits;

		private static final long nodeShift;

		private static final long sequenceIdBits;

		private static final long threadIdBits;

		private static final long threadShift;

		private static final ConcurrentMap<Long, Long> threadToTimeMap = new ConcurrentHashMap<Long, Long>();

		private static final long timeShift;

		static {
			// 63-bits for an id broken into time bits, node bits, thread bits,
			// and sequence bits

			// node id
			nodeIdBits = 10L; // 1024 maximum nodes
			maxNodeId = -1L ^ (-1L << nodeIdBits);

			// thread id
			threadIdBits = 8L; // 256 maximum threads per node
			maxThreadId = -1L ^ (-1L << threadIdBits);

			// sequence id
			sequenceIdBits = 4L; // 16 maximum ids per thread per millisecond
			maxSequenceId = -1L ^ (-1L << sequenceIdBits);

			// bit-wise shifts needed to create guids
			threadShift = sequenceIdBits;
			nodeShift = threadIdBits + threadShift;
			timeShift = nodeIdBits + nodeShift;

			// assume 41 time bits to allow for about 70 years of ids
			assert 64 > 41 + nodeIdBits + threadIdBits + sequenceIdBits;
		}

		private static long nextTimeId(long currentTimeId, long threadId) {
			// seed thread-time map if not already done
			Long lastThreadTime = Long.valueOf(0);
			Long mapKey = Long.valueOf(threadId);
			threadToTimeMap.putIfAbsent(mapKey, lastThreadTime);

			long nextTimeId;
			Long nextThreadTime;
			boolean updated;
			do {
				// last time for this thread
				lastThreadTime = threadToTimeMap.get(mapKey);
				long lastTimeThreadId = lastThreadTime.longValue();

				// get next time
				DateTime now = DateTime.now();
				Duration duration = new Duration(epoch, now);
				nextTimeId = duration.getMillis();

				// adjust for current state
				if (nextTimeId <= currentTimeId) {
					nextTimeId = currentTimeId + 1L;
				}
				if (nextTimeId <= lastTimeThreadId) {
					nextTimeId = lastTimeThreadId + 1L;
				}

				// new thread time for map
				nextThreadTime = Long.valueOf(nextTimeId);

				// continue until map is updated successfully
				updated = threadToTimeMap.replace(mapKey, lastThreadTime,
						nextThreadTime);
			} while (!updated);

			return nextTimeId;
		}

		private long currentId = -1;

		private long maxId = -1;

		private final long nodeId;

		private final long threadId;

		private long timeId;

		public IdState(long threadId, long nodeId) {
			super();
			this.threadId = threadId & maxThreadId;
			this.nodeId = nodeId & maxNodeId;
		}

		private long makeGuid(long timeId, long nodeId, long threadId,
				long sequence) {
			return (timeId << timeShift) | (nodeId << nodeShift)
					| (threadId << threadShift) | sequence;
		}

		public long nextId() {
			// increment currentId
			++currentId;

			if (currentId >= maxId) {
				rollover();
			}

			return currentId;
		}

		private void rollover() {
			// time portion
			timeId = nextTimeId(timeId, threadId);

			currentId = makeGuid(timeId, nodeId, threadId, 0);
			maxId = makeGuid(timeId, nodeId, threadId, maxSequenceId);
		}
	}

	private final ThreadLocal<IdState> STATE = new ThreadLocal<IdState>() {

		private final AtomicLong atomicThreadId = new AtomicLong(0);

		@Override
		protected IdState initialValue() {
			long threadId = atomicThreadId.getAndIncrement();
			return new IdState(threadId, nodeId);
		}
	};

	private final long nodeId;

	public IdGenerator(long nodeId) {
		super();
		this.nodeId = nodeId;
	}

	public long nextId() {
		return STATE.get().nextId();
	}

}
