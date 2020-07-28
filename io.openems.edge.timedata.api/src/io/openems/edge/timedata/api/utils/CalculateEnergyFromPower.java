package io.openems.edge.timedata.api.utils;

import java.time.Duration;
import java.time.Instant;

import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;

/**
 * Calculates the value for energy channels in [Wh] from Power values in [W].
 * 
 * <p>
 * 
 * This is commonly used to calculate SymmetricEss or SymmetricMeter
 * ActiveChargePower and ActiveDischargePower from ActivePower channels.
 */
public class CalculateEnergyFromPower {

	/**
	 * Available States.
	 * 
	 * <p>
	 * 
	 * IMPLEMENTATION NOTE: we are using a custom StateMachine here and not the
	 * generic implementation in 'io.openems.edge.common.statemachine', because one
	 * State-Machine per EnergyCalculator object is required, which is not possible
	 * in the generic static enum implementation.
	 */
	private static enum State {
		TIMEDATA_QUERY_NOT_STARTED, TIMEDATA_QUERY_IS_RUNNING, CALCULATE_ENERGY_OPERATION;
	}

	/**
	 * Keeps the current State.
	 */
	private State state = State.TIMEDATA_QUERY_NOT_STARTED;

	private final TimedataProvider component;

	/**
	 * Keeps the target {@link ChannelId} of the Energy channel.
	 */
	private final ChannelId channelId;

	/**
	 * BaseCumulatedEnergy keeps the energy in [Wh]. It is initialized during
	 * TIMEDATA_QUERY_* states.
	 */
	private Long baseCumulatedEnergy = null;

	/**
	 * ContinuousCumulatedEnergy keeps the exceeding energy in [Wmsec]. It is
	 * continuously updated during CALCULATE_ENERGY_OPERATION state.
	 */
	private long continuousCumulatedEnergy = 0l;

	/**
	 * Keeps the timestamp of the last data.
	 */
	private Instant lastTimestamp = null;

	/**
	 * Keeps the last power value.
	 */
	private Integer lastPower = null;

	public CalculateEnergyFromPower(TimedataProvider component, ChannelId channelId) {
		this.component = component;
		this.channelId = channelId;
	}

	/**
	 * Calculate the Energy and update the Channel.
	 * 
	 * @param power the latest power value in [W]
	 */
	public void update(Integer power) {
		switch (this.state) {
		case TIMEDATA_QUERY_NOT_STARTED:
			this.initializeCumulatedEnergyFromTimedata();
			break;

		case TIMEDATA_QUERY_IS_RUNNING:
			// wait for result
			break;

		case CALCULATE_ENERGY_OPERATION:
			this.calculateEnergy();
			break;
		}

		// keep last data for next run
		this.lastTimestamp = Instant.now();
		this.lastPower = power;
	}

	/**
	 * Initialize cumulated energy value from from Timedata service.
	 */
	private void initializeCumulatedEnergyFromTimedata() {
		Timedata timedata = this.component.getTimedata();
		if (timedata == null) {
			// Wait for Timedata service to appear
			this.state = State.TIMEDATA_QUERY_NOT_STARTED;

		} else {
			// do not query Timedata twice
			this.state = State.TIMEDATA_QUERY_IS_RUNNING;

			timedata.getLatestValue(new ChannelAddress(this.component.id(), this.channelId.id()))
					.thenAccept(cumulatedEnergyOpt -> {
						this.state = State.CALCULATE_ENERGY_OPERATION;

						if (cumulatedEnergyOpt.isPresent()) {
							try {
								this.baseCumulatedEnergy = TypeUtils.getAsType(OpenemsType.LONG,
										cumulatedEnergyOpt.get());
							} catch (IllegalArgumentException e) {
								this.baseCumulatedEnergy = 0l;
							}
						} else {
							this.baseCumulatedEnergy = 0l;
						}
					});
		}
	}

	/**
	 * Calculate the cumulated energy.
	 */
	private void calculateEnergy() {
		if (this.lastTimestamp == null || this.lastPower == null || this.baseCumulatedEnergy == null) {
			// data is not available

		} else {
			// calculate duration since last value
			long duration /* [msec] */ = Duration.between(this.lastTimestamp, Instant.now()).toMillis();

			// calculate energy since last run in [Wmsec]
			long continuousEnergy /* [Wmsec] */ = this.lastPower /* [W] */ * duration /* [msec] */;

			// add to continuous cumulated energy
			this.continuousCumulatedEnergy += continuousEnergy;

			// Update base energy if 1 Wh passed
			if (this.continuousCumulatedEnergy > 3_600_000 /* 1 Wh */) {
				this.baseCumulatedEnergy += this.continuousCumulatedEnergy / 3_600_000;
				this.continuousCumulatedEnergy %= 3_600_000;
			}
		}

		// update 'cumulatedEnergy'
		this.component.channel(this.channelId).setNextValue(this.baseCumulatedEnergy);
	}
}
