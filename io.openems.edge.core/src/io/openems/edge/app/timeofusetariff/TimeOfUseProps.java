package io.openems.edge.app.timeofusetariff;

import java.util.ArrayList;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import io.openems.common.types.EdgeConfig;
import io.openems.common.types.EdgeConfig.Component;
import io.openems.common.utils.JsonUtils;
import io.openems.common.utils.JsonUtils.JsonObjectBuilder;
import io.openems.edge.core.appmanager.ConfigurationTarget;

public final class TimeOfUseProps {

	private TimeOfUseProps() {
	}

	/**
	 * Creates the commonly used components for a Time-of-Use.
	 * 
	 * @param target                    the {@link ConfigurationTarget}
	 * @param ctrlEssTimeOfUseTariffId  The id of the ToU controller.
	 * @param controllerAlias           the alias of the ToU controller.
	 * @param providerFactoryId         the factoryId of the ToU provider.
	 * @param providerAlias             the alias of the ToU provider.
	 * @param timeOfUseTariffProviderId the id of the ToU provider.
	 * @param additionalProperties      Consumer for additional configuration of the
	 *                                  provider.
	 * @return the components.
	 */
	public static final ArrayList<Component> getComponents(//
			final ConfigurationTarget target, //
			final String ctrlEssTimeOfUseTariffId, //
			final String controllerAlias, //
			final String providerFactoryId, //
			final String providerAlias, //
			final String timeOfUseTariffProviderId, //
			final Consumer<JsonObjectBuilder> additionalProperties //
	) {
		final var controllerProperties = JsonUtils.buildJsonObject() //
				.addProperty("ess.id", "ess0")//
				.onlyIf(target == ConfigurationTarget.ADD, b -> b.addProperty("controlMode", "DELAY_DISCHARGE"));

		var providerProperties = JsonUtils.buildJsonObject().onlyIf(additionalProperties != null, additionalProperties);

		return Lists.newArrayList(//
				new EdgeConfig.Component(ctrlEssTimeOfUseTariffId, controllerAlias, "Controller.Ess.Time-Of-Use-Tariff",
						controllerProperties.build()), //
				new EdgeConfig.Component(timeOfUseTariffProviderId, providerAlias, providerFactoryId,
						providerProperties.build())//
		);
	}

}
