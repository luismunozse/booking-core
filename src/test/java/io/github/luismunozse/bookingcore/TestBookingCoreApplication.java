package io.github.luismunozse.bookingcore;

import org.springframework.boot.SpringApplication;

public class TestBookingCoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(BookingCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
