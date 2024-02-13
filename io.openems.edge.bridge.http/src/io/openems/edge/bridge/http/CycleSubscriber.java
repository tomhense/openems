package io.openems.edge.bridge.http;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;

import io.openems.edge.common.event.EdgeEventConstants;

@Component(//
		scope = ServiceScope.SINGLETON, //
		service = { CycleSubscriber.class, EventHandler.class } //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
})
public class CycleSubscriber implements EventHandler {

	private final List<Consumer<Event>> eventHandler = new LinkedList<>();

	@Override
	public void handleEvent(Event event) {
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE -> {
			this.eventHandler.forEach(t -> t.accept(event));
		}
		}
	}

	/**
	 * Subscribes to the events of the topics this component is subscribed to.
	 * 
	 * @param eventHandler the handler to execute on every event
	 */
	public void subscribe(Consumer<Event> eventHandler) {
		this.eventHandler.add(eventHandler);
	}

	/**
	 * Unsubscribes a event handler.
	 * 
	 * @param eventHandler the handler to remove
	 * @return true if the handler was successfully removed; if the handler was not
	 *         found returs false
	 */
	public boolean unsubscribe(Consumer<Event> eventHandler) {
		return this.eventHandler.remove(eventHandler);
	}

}
