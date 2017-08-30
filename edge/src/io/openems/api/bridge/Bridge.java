/*******************************************************************************
 * OpenEMS - Open Source Energy Management System
 * Copyright (c) 2016, 2017 FENECON GmbH and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *   FENECON GmbH - initial API and implementation and initial documentation
 *******************************************************************************/
package io.openems.api.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.api.device.Device;
import io.openems.api.scheduler.Scheduler;
import io.openems.api.thing.Thing;
import io.openems.core.ThingRepository;
import io.openems.core.utilities.Mutex;

public abstract class Bridge extends Thread implements Thing {
	public final static String THINGID_PREFIX = "_bridge";
	private static int instanceCounter = 0;
	private Scheduler scheduler;
	private int readOtherTaskIndex = 0;
	private int readOtherTaskCount = 0;
	private AtomicBoolean isWriteTriggered = new AtomicBoolean(false);
	private final AtomicBoolean initialize = new AtomicBoolean(true);
	private final AtomicBoolean isStopped = new AtomicBoolean(false);
	private long cycleStart = 0;
	private Mutex initializedMutex = new Mutex(false);
	private final AtomicBoolean isInitialized = new AtomicBoolean(false);
	protected final List<Device> devices = Collections.synchronizedList(new LinkedList<Device>());
	protected final Logger log;

	/**
	 * Initialize the Thread with a name
	 *
	 * @param name
	 */
	public Bridge() {
		log = LoggerFactory.getLogger(this.getClass());
		setName(THINGID_PREFIX + instanceCounter++);
	}

	@Override
	public String id() {
		return getName();
	}

	protected List<BridgeReadTask> getRequiredReadTasks() {
		List<BridgeReadTask> readTasks = new ArrayList<>();
		for (Device d : devices) {
			readTasks.addAll(d.getRequiredReadTasks());
		}
		return readTasks;
	}

	protected List<BridgeReadTask> getReadTasks() {
		List<BridgeReadTask> readTasks = new ArrayList<>();
		for (Device d : devices) {
			readTasks.addAll(d.getReadTasks());
		}
		return readTasks;
	}

	protected List<BridgeWriteTask> getWriteTasks() {
		List<BridgeWriteTask> writeTasks = new ArrayList<>();
		for (Device d : devices) {
			writeTasks.addAll(d.getWriteTasks());
		}
		return writeTasks;
	}

	public synchronized void addDevice(Device device) {
		this.devices.add(device);
	}

	public synchronized final void addDevices(Device... devices) {
		for (Device device : devices) {
			addDevice(device);
		}
	}

	public synchronized final void addDevices(List<Device> devices) {
		for (Device device : devices) {
			addDevice(device);
		}
	}

	public synchronized void removeDevice(Device device) {
		this.devices.remove(device);
	}

	public synchronized List<Device> getDevices() {
		return Collections.unmodifiableList(this.devices);
	}

	public void triggerWrite() {
		// set the Write-flag
		isWriteTriggered.set(true);
	}

	/**
	 * This method is called when the Thread stops. Use it to close resources.
	 */
	protected abstract void dispose();

	/**
	 * This method is called once before {@link forever()} and every time after {@link restart()} method was called. Use
	 * it to (re)initialize everything.
	 *
	 * @return false on initialization error
	 */
	protected abstract boolean initialize();

	/**
	 * Little helper method: Sleep and don't let yourself interrupt by a ForceRun-Flag. It is not making sense anyway,
	 * because something is wrong with the setup if we landed here.
	 *
	 * @param duration
	 *            in seconds
	 */
	private long bridgeExceptionSleep(long duration) {
		if (duration < 60) {
			duration += 1;
		}
		long targetTime = System.nanoTime() + (duration * 1000000);
		do {
			try {
				long thisDuration = (targetTime - System.nanoTime()) / 1000000;
				if (thisDuration > 0) {
					Thread.sleep(thisDuration);
				}
			} catch (InterruptedException e1) {}
		} while (targetTime > System.nanoTime());
		return duration;
	}

	/**
	 * Executes the Thread. Calls {@link forever} till the Thread gets interrupted.
	 */
	@Override
	public final void run() {
		long bridgeExceptionSleep = 1; // seconds
		this.initialize.set(true);
		while (!isStopped.get()) {
			cycleStart = System.currentTimeMillis();
			try {
				/*
				 * Initialize Bridge
				 */
				while (initialize.get()) {
					// get Scheduler
					this.scheduler = ThingRepository.getInstance().getSchedulers().iterator().next();
					boolean initSuccessful = initialize();
					if (initSuccessful) {
						isInitialized.set(true);
						initializedMutex.release();
						initialize.set(false);
					} else {
						initializedMutex.awaitOrTimeout(10000, TimeUnit.MILLISECONDS);
					}
				}

				this.readOtherTaskCount = 0;
				List<BridgeReadTask> readTasks = this.getReadTasks();
				List<BridgeReadTask> requiredReadTasks = this.getRequiredReadTasks();
				List<BridgeWriteTask> writeTasks = this.getWriteTasks();
				// calculate startTime to run the read
				long sleep = getNextReadTime() - System.currentTimeMillis() - 10;
				if (sleep > 0) {
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {
						log.error("sleep failed.", e);
					}
				} else {
					log.warn("cycleTime smaller than required time: " + sleep);
				}
				// run all tasks to read required Channels
				for (BridgeReadTask task : requiredReadTasks) {
					task.runTask();
				}
				long timeUntilWrite = scheduler.getCycleStartTime() + scheduler.getRequiredTime() + 10;
				if (readTasks.size() > 0) {
					// calculate time until write
					// run tasks for not required channels
					if (timeUntilWrite - System.currentTimeMillis() > 0) {
						readOther(readTasks, timeUntilWrite);
					}
				}
				// execute write Tasks
				boolean written = false;
				while (!written) {
					if (isWriteTriggered.get()) {
						for (BridgeWriteTask task : writeTasks) {
							task.runTask();
						}
						isWriteTriggered.set(false);
						written = true;
					} else {
						try {
							Thread.sleep(20l);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				// execute additional readTasks if time left
				if (readTasks.size() > 0) {
					if (getNextReadTime() - 10 - System.currentTimeMillis() > 0) {
						readOther(readTasks, getNextReadTime() - 10);
					}
				}
				// Everything went ok: reset bridgeExceptionSleep
				bridgeExceptionSleep = 1;
			} catch (Throwable e) {
				/*
				 * Handle Bridge-Exceptions
				 */
				log.error("Bridge-Exception! Retry later: ", e);
				bridgeExceptionSleep = bridgeExceptionSleep(bridgeExceptionSleep);
			}
		}
		dispose();
		System.out.println("BridgeWorker was interrupted. Exiting gracefully...");
	}

	private void readOther(List<BridgeReadTask> tasks, long timeFinished) {
		BridgeReadTask nextReadTask = null;
		if (readOtherTaskCount + 1 < tasks.size()) {
			nextReadTask = tasks.get(readOtherTaskIndex);
		} else {
			nextReadTask = null;
		}
		while (nextReadTask != null) {
			if (System.currentTimeMillis() + nextReadTask.getRequiredTime() >= timeFinished) {
				break;
			}
			nextReadTask.runTask();
			readOtherTaskCount++;
			readOtherTaskIndex++;
			readOtherTaskIndex %= tasks.size();
			if (readOtherTaskCount + 1 < tasks.size()) {
				nextReadTask = tasks.get(readOtherTaskIndex);
			} else {
				nextReadTask = null;
			}
		}
	}

	private long getWriteTime() {
		long time = 0l;
		for (BridgeWriteTask task : getWriteTasks()) {
			time += task.getRequiredTime();
		}
		return time;
	}

	private long getNextReadTime() {
		long readRequiredStartTime = scheduler.getNextCycleStart() - 10;
		for (BridgeReadTask task : getRequiredReadTasks()) {
			readRequiredStartTime -= task.getRequiredTime();
		}
		return readRequiredStartTime;
	}

	public long getRequiredCycleTime() {
		long time = 50;
		for (BridgeReadTask task : getRequiredReadTasks()) {
			time += task.getRequiredTime();
		}
		time += scheduler.getRequiredTime();
		for (BridgeWriteTask task : getWriteTasks()) {
			time += task.getRequiredTime();
		}
		long maxReadOtherTime = 0;
		for (BridgeReadTask task : getReadTasks()) {
			if (task.getRequiredTime() > maxReadOtherTime) {
				maxReadOtherTime = task.getRequiredTime();
			}
		}
		time += maxReadOtherTime * 2;
		return time;
	}

	public void shutdown() {
		isStopped.set(true);
	}

	/**
	 * Triggers a call of {@link initialization()} in the next loop. Call this, if the configuration changes.
	 */
	public final void triggerInitialize() {
		initialize.set(true);
		initializedMutex.release();
	}
}
