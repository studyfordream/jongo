/*
 * Copyright (C) 2011 Benoit GUEROUT <bguerout at gmail dot com> and Yves AMSELLEM <amsellem dot yves at gmail dot com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jongo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import org.fest.assertions.Condition;
import org.jongo.util.DBObjectResultHandler;
import org.jongo.util.JongoTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class CommandTest extends JongoTestCase {

    private Jongo jongo;
    private MongoCollection collection;

    @Before
    public void setUp() throws Exception {
        jongo = getJongo();
        collection = createEmptyCollection("friends");
    }

    @After
    public void tearDown() throws Exception {
        dropCollection("friends");
    }

    @Test
    public void canRunACommand() throws Exception {
        DBObject result = jongo.runCommand("{ serverStatus: 1 }").map(new DBObjectResultHandler());

        assertThat(result).isNotNull();
        assertThat(result.get("version")).isNotNull();
        assertThat(result.get("ok")).isEqualTo(1.0);
    }

    @Test
    public void canRunACommandWithParameter() throws Exception {

        collection.withConcern(WriteConcern.SAFE).insert("{test:1}");

        DBObject result = jongo.runCommand("{ count: #}", "friends").map(new DBObjectResultHandler());

        assertThat(result.get("n")).isEqualTo(1.0);
    }

    @Test
    public void canRunAGeoNearCommand() throws Exception {

        MongoCollection safeCollection = collection.withConcern(WriteConcern.SAFE);
        safeCollection.insert("{loc:{lat:48.690833,lng:9.140556}, name:'Paris'}");
        safeCollection.ensureIndex("{loc:'2d'}");

        GeoNearResult geoNearResult = jongo.runCommand("{ geoNear : 'friends', near : [48.690,9.140], spherical: true}")
                .throwOnError()
                .as(GeoNearResult.class);

        List<Location> locations = geoNearResult.locations;
        assertThat(locations.size()).isEqualTo(1);
        assertThat(locations.get(0).dis).satisfies(new Condition<Double>() {
            @Override
            public boolean matches(Double value) {
                return value instanceof Double && value > 1.7E-5 && value < 1.8E-5;
            }
        });
        assertThat(locations.get(0).getName()).isEqualTo("Paris");
    }

    @Test
    public void canRunACommandAs() throws Exception {
        ServerStatus status = jongo.runCommand("{ serverStatus: 1 }").as(ServerStatus.class);

        assertThat(status.host).isNotNull();
        assertThat(status.ok).isEqualTo("1.0");
    }

    @Test
    public void canRunInvalidCommand() throws Exception {
        ServerStatus status = jongo.runCommand("{forceerror:1}").as(ServerStatus.class);

        assertThat(status.ok).isEqualTo("0.0");
    }

    @Test
    public void mustForceExceptionToBeThrownOnInvalidCommand() throws Exception {
        try {
            jongo.runCommand("{forceerror:1}").throwOnError().as(ServerStatus.class);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("forced error");
        }
    }

    private static class ServerStatus {
        String ok, host;
    }

    private static class GeoNearResult {
        @JsonProperty("results")
        List<Location> locations;
    }

    private static class Location {

        double dis;

        /**
         * Real Location document is contained into 'obj' property
         * Jackson doesn't support nested mapping. see http://jira.codehaus.org/browse/JACKSON-781
         */
        @JsonProperty("obj")
        NestedLocation nestedLocation;

        public String getName() {
            return nestedLocation.locationName;
        }
    }

    private static class NestedLocation {
        @JsonProperty("name")
        String locationName;
    }
}