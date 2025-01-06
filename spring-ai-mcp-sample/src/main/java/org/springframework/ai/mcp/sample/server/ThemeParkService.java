/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.mcp.sample.server;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

public class ThemeParkService {

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	public record Destination(String id, String name, DestinationPark[] parks) {
	}

	public record DestinationPark(String id, String name) {
	}

	public record Destinations(Destination[] destinations) {
	}

	public record Park(String entityId, String parkName, String parentDestinationEntityId,
			String parentDestinationName) {
	}

	public record LiveEntity(String id, String name, LiveData[] liveData) {
	}

	public record ShowTime(String type, String startTime, String endTime) {
	}

	public record LiveData(String id, String name, String entityType, String status, ShowTime[] showtimes,
			EntityQueue queue) {
	}

	public record EntityQueue(EntityQueueEntry STANDBY, EntityQueueEntry RETURN_TIME, EntityQueueEntry SINGLE_RIDER) {
	}

	public record EntityQueueEntry(int waitTime, String state, String returnStart, String returnEnd) {
	}

	public ThemeParkService(RestClient.Builder restClientBuilder) {
		this.restClient = restClientBuilder.baseUrl("https://api.themeparks.wiki/v1").build();
		this.objectMapper = new ObjectMapper();
	}

	@Cacheable("parks")
	public String getParks() {
		var destinations = restClient.get().uri("/destinations").retrieve().body(Destinations.class);

		List<Destination> destinationsList = List.of(destinations.destinations());
		List<Park> parks = new ArrayList<>();
		destinationsList.forEach(destination -> {
			for (DestinationPark park : destination.parks()) {
				parks.add(new Park(park.id(), park.name(), destination.id(), destination.name()));
			}
		});

		try {
			return objectMapper.writeValueAsString(parks);
		}
		catch (Exception e) {
			return "[]";
		}
	}

	public String getEntitySchedule(String entityId) {
		return restClient.get().uri("/entity/{entityId}/schedule", entityId).retrieve().body(String.class);
	}

	public String getEntityLive(String entityId) {
		var liveEntity = restClient.get().uri("/entity/{entityId}/live", entityId).retrieve().body(LiveEntity.class);

		try {
			return objectMapper.writeValueAsString(liveEntity);
		}
		catch (Exception e) {
			return "{}";
		}
	}

	public String getEntityChildren(String entityId) {
		return restClient.get().uri("/entity/{entityId}/children", entityId).retrieve().body(String.class);
	}

}
