package ${{values.javaPackageName}};

import java.time.LocalDate;

public record Booking(String bookingNumber, LocalDate bookingFrom, LocalDate bookingTo, Customer customer) {}