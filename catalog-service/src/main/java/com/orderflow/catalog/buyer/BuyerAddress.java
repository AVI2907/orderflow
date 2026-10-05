package com.orderflow.catalog.buyer;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A buyer's saved default delivery address. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyerAddress {
    @Column(length = 100) private String fullName;
    @Column(length = 200) private String line1;
    @Column(length = 200) private String line2;
    @Column(length = 100) private String city;
    @Column(length = 100) private String state;
    @Column(length = 20)  private String postalCode;
    @Column(length = 100) private String country;
}
