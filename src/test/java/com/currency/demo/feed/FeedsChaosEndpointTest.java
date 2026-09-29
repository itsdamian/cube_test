package com.currency.demo.feed;

import com.currency.demo.support.IntegrationTest;
import com.currency.demo.support.MutableClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedsChaosEndpointTest {

    /** Default configuration: the endpoint does not exist at all. */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class DefaultProfile extends IntegrationTest {

        @Autowired
        MockMvc mvc;

        @Autowired
        ApplicationContext context;

        @Test
        void feedsEndpointIsNotAvailable() throws Exception {
            assertThat(context.getBeansOfType(FeedsChaosEndpoint.class)).as("bean not even registered").isEmpty();
            mvc.perform(get("/actuator/feeds")).andExpect(status().isNotFound());
            mvc.perform(post("/actuator/feeds/coinbase/block")).andExpect(status().isNotFound());
        }
    }

    /**
     * chaos profile, with a FeedManager whose exchanges are fakes and whose clock we control:
     * block -> failover to kraken -> unblock -> switch back after the recovery period.
     */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"test", "chaos"})
    class ChaosProfile extends IntegrationTest {

        static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-29T09:00:00Z"));
        static final Map<String, FakeFeedClient> FAKES = new HashMap<>();

        @TestConfiguration
        static class FakeFeeds {
            @Bean
            FeedManager feedManager() {
                return new FeedManager((parser, listener) -> {
                    FakeFeedClient fake = new FakeFeedClient(parser.sourceName(), listener, CLOCK);
                    FAKES.put(parser.sourceName(), fake);
                    return fake;
                }, new CoinbaseMessageParser(), new KrakenMessageParser(), mock(TickPublisher.class),
                        mock(FeedStatusPublisher.class), CLOCK, Duration.ofSeconds(10), Duration.ofSeconds(15));
            }
        }

        @Autowired
        MockMvc mvc;

        @Autowired
        FeedManager manager;

        private void run(int seconds) {
            for (int i = 0; i < seconds; i++) {
                CLOCK.advance(Duration.ofSeconds(1));
                FAKES.values().forEach(FakeFeedClient::tick); // a blocked fake delivers nothing
                manager.check();
            }
        }

        @Test
        void blockSwitchesToBackupAndUnblockSwitchesBack() throws Exception {
            run(2);
            mvc.perform(get("/actuator/feeds"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeSource").value("coinbase"));

            mvc.perform(post("/actuator/feeds/coinbase/block").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clients[0].source").value("coinbase"))
                    .andExpect(jsonPath("$.clients[0].blockMode").value("DISCONNECT"))
                    .andExpect(jsonPath("$.clients[0].connected").value(false));
            run(1);
            assertThat(manager.activeSource()).isEqualTo("kraken");

            mvc.perform(post("/actuator/feeds/coinbase/unblock").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clients[0].blockMode").value("NONE"));
            run(1);   // first healthy check: the recovery timer starts here
            run(14);
            assertThat(manager.activeSource()).isEqualTo("kraken");
            run(1);   // 15 s of healthy primary
            assertThat(manager.activeSource()).isEqualTo("coinbase");
        }

        @Test
        void silentBlockKeepsConnectionButGoesStaleAndFailsOver() throws Exception {
            run(2);
            assertThat(manager.activeSource()).isEqualTo("coinbase");

            mvc.perform(post("/actuator/feeds/coinbase/block").contentType(MediaType.APPLICATION_JSON).param("mode", "silent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clients[0].blockMode").value("SILENT"))
                    .andExpect(jsonPath("$.clients[0].connected").value(true));
            run(10);
            assertThat(manager.activeSource()).isEqualTo("kraken");

            mvc.perform(post("/actuator/feeds/coinbase/unblock").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            run(1);   // recovery timer starts
            run(15);
            assertThat(manager.activeSource()).isEqualTo("coinbase");
        }

        @Test
        void unknownSourceIs404AndUnknownActionOrModeIs400() throws Exception {
            mvc.perform(post("/actuator/feeds/binance/block").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
            mvc.perform(post("/actuator/feeds/coinbase/explode").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
            mvc.perform(post("/actuator/feeds/coinbase/block").contentType(MediaType.APPLICATION_JSON).param("mode", "loud")).andExpect(status().isBadRequest());
        }
    }
}
