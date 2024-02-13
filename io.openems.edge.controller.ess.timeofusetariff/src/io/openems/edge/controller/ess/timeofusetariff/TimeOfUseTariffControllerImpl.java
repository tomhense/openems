package io.openems.edge.controller.ess.timeofusetariff;

import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.BALANCING;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.CHARGE;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.DELAY_DISCHARGE;
import static io.openems.edge.controller.ess.timeofusetariff.optimizer.Utils.calculateCharge;
import static io.openems.edge.controller.ess.timeofusetariff.optimizer.Utils.essMaxChargePower;
import static io.openems.edge.controller.ess.timeofusetariff.optimizer.Utils.getEssMinSoc;
import static io.openems.edge.controller.ess.timeofusetariff.optimizer.Utils.postprocessRunState;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.session.Role;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.JsonApi;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.common.user.User;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.ess.emergencycapacityreserve.ControllerEssEmergencyCapacityReserve;
import io.openems.edge.controller.ess.limittotaldischarge.ControllerEssLimitTotalDischarge;
import io.openems.edge.controller.ess.timeofusetariff.jsonrpc.GetScheduleRequest;
import io.openems.edge.controller.ess.timeofusetariff.optimizer.Context;
import io.openems.edge.controller.ess.timeofusetariff.optimizer.Optimizer;
import io.openems.edge.controller.ess.timeofusetariff.optimizer.Period;
import io.openems.edge.controller.ess.timeofusetariff.optimizer.Utils;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.Phase;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.predictor.api.manager.PredictorManager;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateActiveTime;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.Time-Of-Use-Tariff", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TimeOfUseTariffControllerImpl extends AbstractOpenemsComponent
		implements TimeOfUseTariffController, Controller, OpenemsComponent, TimedataProvider, JsonApi {

	/** The hard working Worker. */
	private final Optimizer optimizer;

	/** Delayed Time is aggregated also after restart of OpenEMS. */
	private final CalculateActiveTime calculateDelayedTime = new CalculateActiveTime(this,
			TimeOfUseTariffController.ChannelId.DELAYED_TIME);

	/** Charged Time is aggregated also after restart of OpenEMS. */
	private final CalculateActiveTime calculateChargedTime = new CalculateActiveTime(this,
			TimeOfUseTariffController.ChannelId.CHARGED_TIME);

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private PredictorManager predictorManager;

	@Reference
	private TimeOfUseTariff timeOfUseTariff;

	@Reference
	private Sum sum;

	@Reference(policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata;

	@Reference(policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.MULTIPLE, //
			target = "(&(enabled=true)(isReserveSocEnabled=true))")
	private List<ControllerEssEmergencyCapacityReserve> ctrlEmergencyCapacityReserves = new CopyOnWriteArrayList<>();

	@Reference(policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.MULTIPLE, //
			target = "(enabled=true)")
	private List<ControllerEssLimitTotalDischarge> ctrlLimitTotalDischarges = new CopyOnWriteArrayList<>();

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private ManagedSymmetricEss ess;

	private Config config = null;

	public TimeOfUseTariffControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				TimeOfUseTariffController.ChannelId.values() //
		);

		// Prepare Optimizer and Context
		this.optimizer = new Optimizer(() -> Context.create() //
				.clock(this.componentManager.getClock()) //
				.predictorManager(this.predictorManager) //
				.timeOfUseTariff(this.timeOfUseTariff) //
				.ess(this.ess) //
				.ctrlEmergencyCapacityReserves(this.ctrlEmergencyCapacityReserves) //
				.ctrlLimitTotalDischarges(this.ctrlLimitTotalDischarges) //
				.controlMode(this.config.controlMode()) //
				.maxChargePowerFromGrid(this.config.maxChargePowerFromGrid()) //
				.build());
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		if (this.applyConfig(config)) {
			this.optimizer.activate(this.id());
		}
	}

	@Modified
	private void modified(ComponentContext context, Config config) {
		super.modified(context, config.id(), config.alias(), config.enabled());
		if (this.applyConfig(config)) {
			this.optimizer.modified(this.id());
		}
	}

	private synchronized boolean applyConfig(Config config) {
		this.config = config;

		// update filter for 'ess'
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "ess", config.ess_id())) {
			return false;
		}

		if (!config.enabled()) {
			this.optimizer.deactivate();
			return false;
		}

		return true;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.optimizer.deactivate();
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		// Mode given from the configuration.
		switch (this.config.mode()) {
		case AUTOMATIC -> this.modeAutomatic();
		case OFF -> this.modeOff();
		}

		this.updateVisualizationChannels();
	}

	private Period getCurrentPeriod() {
		return this.optimizer.getCurrentPeriod();
	}

	private StateMachine getCurrentPeriodState() {
		var period = this.getCurrentPeriod();
		if (period != null) {
			return period.state();
		}
		return BALANCING; // Default Fallback
	}

	/**
	 * Apply the Schedule.
	 *
	 * @throws OpenemsNamedException on error
	 */
	private void modeAutomatic() throws OpenemsNamedException {
		// Evaluate current state
		final var state = postprocessRunState(
				getEssMinSoc(this.ctrlLimitTotalDischarges, this.ctrlEmergencyCapacityReserves), //
				this.ess.getSoc().get(), //
				this.getCurrentPeriodState());
		this._setStateMachine(state);

		// Update the timer.
		this.calculateChargedTime.update(state == CHARGE);
		this.calculateDelayedTime.update(state == DELAY_DISCHARGE);

		// Get and apply ActivePower Less-or-Equals Set-Point
		var activePower = switch (state) {
		case CHARGE -> calculateCharge(this.ess, this.sum, essMaxChargePower(this.optimizer.getParams(), this.ess),
				this.config.maxChargePowerFromGrid());
		case DELAY_DISCHARGE -> 0;
		case BALANCING -> null;
		};

		if (activePower != null) {
			this.ess.setActivePowerLessOrEquals(this.ess.getPower().fitValueIntoMinMaxPower(this.id(), this.ess,
					Phase.ALL, Pwr.ACTIVE, activePower));
		}
	}

	/**
	 * Apply the mode OFF logic. Sets the Default values to the channels, if the
	 * Mode is 'OFF'.
	 */
	private void modeOff() {
		// Update the timer.
		this.calculateChargedTime.update(false);
		this.calculateDelayedTime.update(false);

		// Default State Machine.
		this._setStateMachine(BALANCING);
	}

	/**
	 * This is only to visualize data for better debugging.
	 */
	private void updateVisualizationChannels() {
		final Float quarterlyPrice;
		var period = this.getCurrentPeriod();
		if (period == null) {
			// Values are not available.
			quarterlyPrice = this.timeOfUseTariff.getPrices().getValues()[0];

		} else {
			// First period is always the current period.
			quarterlyPrice = period.price();
		}

		// Set the channels
		this._setQuarterlyPrices(quarterlyPrice);
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public CompletableFuture<? extends JsonrpcResponseSuccess> handleJsonrpcRequest(User user, JsonrpcRequest request)
			throws OpenemsNamedException {
		user.assertRoleIsAtLeast("handleJsonrpcRequest", Role.GUEST);
		return switch (request.getMethod()) {
		case GetScheduleRequest.METHOD -> this.handleGetScheduleRequest(user, GetScheduleRequest.from(request));
		default -> throw OpenemsError.JSONRPC_UNHANDLED_METHOD.exception(request.getMethod());
		};
	}

	/**
	 * Handles a {@link GetScheduleRequest}.
	 *
	 * @param user    the User
	 * @param request the GetScheduleRequest
	 * @return the Future JSON-RPC Response
	 * @throws OpenemsNamedException on error
	 */
	private CompletableFuture<? extends JsonrpcResponseSuccess> handleGetScheduleRequest(User user,
			GetScheduleRequest request) throws OpenemsNamedException {
		return CompletableFuture.completedFuture(Utils.handleGetScheduleRequest(this.optimizer, request.getId(),
				this.timedata, this.id(), ZonedDateTime.now(this.componentManager.getClock())));
	}

	@Override
	public String debugLog() {
		var b = new StringBuilder() //
				.append(this.getStateMachine()); //
		if (this.getCurrentPeriod() == null) {
			b.append("|No Schedule available");
		}
		return b.toString();
	}
}
