package io.github.luismunozse.bookingcore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingCoreApplicationTests {

	@Test
	void contextLoads() {
	}

}
