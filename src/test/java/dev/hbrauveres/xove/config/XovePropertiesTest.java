package dev.hbrauveres.xove.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The room size, from XOVE_ROOM_SEATS (spec 0060). */
class XovePropertiesTest {

    private static XoveProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("xove", XoveProperties.class);
    }

    @Test
    void withoutTheSettingTheRoomHas20Seats() {
        assertThat(bind(Map.of()).room().seats()).isEqualTo(20);
    }

    @Test
    void theSettingSetsTheSeats() {
        assertThat(bind(Map.of("xove.room.seats", "3")).room().seats()).isEqualTo(3);
        assertThat(bind(Map.of("xove.room.seats", "1")).room().seats()).isEqualTo(1);
        assertThat(bind(Map.of("xove.room.seats", "20")).room().seats()).isEqualTo(20);
    }

    @Test
    void anythingButAWholeNumberFrom1To20StopsTheApi() {
        for (String wrong : new String[] {"0", "21", "-3"}) {
            assertThatThrownBy(() -> bind(Map.of("xove.room.seats", wrong)))
                    .as(wrong)
                    .isInstanceOf(BindException.class)
                    .rootCause().hasMessage("XOVE_ROOM_SEATS must be a whole number from 1 to 20, not " + wrong);
        }
        // Spring's own start-up report names the property and the value.
        for (String wrong : new String[] {"abc", "2.5"}) {
            assertThatThrownBy(() -> bind(Map.of("xove.room.seats", wrong)))
                    .as(wrong)
                    .isInstanceOf(BindException.class)
                    .hasMessageContaining("xove.room.seats");
        }
    }
}
