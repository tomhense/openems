package io.openems.edge.timeofusetariff.corrently;

import static io.openems.edge.timeofusetariff.api.utils.TimeOfUseTariffUtils.generateDebugLog;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;
import io.openems.common.utils.ThreadPoolUtils;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.meta.Meta;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;
import io.openems.edge.timeofusetariff.api.utils.TimeOfUseTariffUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "TimeOfUseTariff.Corrently", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TimeOfUseTariffCorrentlyImpl extends AbstractOpenemsComponent
		implements TimeOfUseTariff, OpenemsComponent, TimeOfUseTariffCorrently {

	private static final String CORRENTLY_API_URL = "https://api.corrently.io/v2.0/gsi/marketdata?zip=";

	private final Logger log = LoggerFactory.getLogger(TimeOfUseTariffCorrentlyImpl.class);
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicReference<ImmutableSortedMap<ZonedDateTime, Float>> prices = new AtomicReference<>(
			ImmutableSortedMap.of());

	@Reference
	private Meta meta;

	@Reference
	private ComponentManager componentManager;

	private Config config = null;
	private ZonedDateTime updateTimeStamp = null;

	public TimeOfUseTariffCorrentlyImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				TimeOfUseTariffCorrently.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());

		if (!config.enabled()) {
			return;
		}
		this.config = config;
		this.executor.schedule(this.task, 0, TimeUnit.SECONDS);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
		ThreadPoolUtils.shutdownAndAwaitTermination(this.executor, 0);
	}

	private final Runnable task = () -> {

		/*
		 * Update Map of prices
		 */
		var client = new OkHttpClient();
		var request = new Request.Builder() //
				.url(CORRENTLY_API_URL + this.config.zipcode() + "&resolution=900") //
				.build();
		int httpStatusCode;
		try (var response = client.newCall(request).execute()) {
			httpStatusCode = response.code();

			if (!response.isSuccessful()) {
				throw new IOException("Unexpected code " + response);
			}

			// Parse the response for the prices
			this.prices.set(TimeOfUseTariffCorrentlyImpl.parsePrices(response.body().string()));

			// store the time stamp
			this.updateTimeStamp = ZonedDateTime.now();

		} catch (IOException | OpenemsNamedException e) {
			this.logWarn(this.log, "Unable to Update Corrently Time-Of-Use Price: " + e.getMessage());
			httpStatusCode = 0;
		}

		this.channel(TimeOfUseTariffCorrently.ChannelId.HTTP_STATUS_CODE).setNextValue(httpStatusCode);

		/*
		 * Schedule next price update for 2 pm
		 */
		var now = ZonedDateTime.now();
		var nextRun = now.withHour(14).truncatedTo(ChronoUnit.HOURS);
		if (now.isAfter(nextRun)) {
			nextRun = nextRun.plusDays(1);
		}

		var duration = Duration.between(now, nextRun);
		var delay = duration.getSeconds();

		this.executor.schedule(this.task, delay, TimeUnit.SECONDS);
	};

	@Override
	public TimeOfUsePrices getPrices() {
		// return empty TimeOfUsePrices if data is not yet available.
		if (this.updateTimeStamp == null) {
			return TimeOfUsePrices.empty(ZonedDateTime.now());
		}

		return TimeOfUseTariffUtils.getNext24HourPrices(Clock.systemDefaultZone() /* can be mocked for testing */,
				this.prices.get(), this.updateTimeStamp);
	}

	/**
	 * Parse the Corrently JSON to the Price Map.
	 *
	 * @param jsonData the Corrently JSON
	 * @return the Price Map
	 * @throws OpenemsNamedException on error
	 */
	public static ImmutableSortedMap<ZonedDateTime, Float> parsePrices(String jsonData) throws OpenemsNamedException {
		var result = new TreeMap<ZonedDateTime, Float>();

		var line = JsonUtils.parseToJsonObject(jsonData);
		var data = JsonUtils.getAsJsonArray(line, "data");

		for (JsonElement element : data) {

			var marketPrice = JsonUtils.getAsFloat(element, "marketprice");
			var startTimestampLong = JsonUtils.getAsLong(element, "start_timestamp");

			// Converting Long time stamp to ZonedDateTime.
			var startTimeStamp = ZonedDateTime //
					.ofInstant(Instant.ofEpochMilli(startTimestampLong), ZoneId.systemDefault())
					.truncatedTo(ChronoUnit.MINUTES);
			// Adding the values in the Map.
			result.put(startTimeStamp, marketPrice);
		}
		return ImmutableSortedMap.copyOf(result);
	}

	@Override
	public String debugLog() {
		return generateDebugLog(this, this.meta.getCurrency());
	}
}
