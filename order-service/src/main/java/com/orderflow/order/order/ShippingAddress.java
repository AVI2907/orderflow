package com.orderflow.order.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Stored as columns on the orders table. Nullable because orders placed before this feature have none. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {

    @Column(name = "shipping_full_name", length = 100)
    private String fullName;

    @Column(name = "shipping_line1", length = 200)
    private String line1;

    @Column(name = "shipping_line2", length = 200)
    private String line2;

    @Column(name = "shipping_city", length = 100)
    private String city;

    @Column(name = "shipping_state", length = 100)
    private String state;

    @Column(name = "shipping_postal_code", length = 20)
    private String postalCode;

    @Column(name = "shipping_country", length = 60)
    private String country;
}
