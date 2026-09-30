package com.orderflow.catalog.seller;

import com.orderflow.catalog.buyer.BuyerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SellerUserDetailsService implements UserDetailsService {

    private final SellerRepository sellerRepository;
    private final BuyerRepository buyerRepository;
    private final String adminEmail;
    private final String adminPasswordHash;

    public SellerUserDetailsService(
            SellerRepository sellerRepository,
            BuyerRepository buyerRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.email}") String adminEmail,
            @Value("${admin.password}") String adminPassword) {
        this.sellerRepository = sellerRepository;
        this.buyerRepository = buyerRepository;
        this.adminEmail = adminEmail;
        this.adminPasswordHash = passwordEncoder.encode(adminPassword);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (adminEmail.equalsIgnoreCase(email)) {
            return User.builder()
                    .username(adminEmail)
                    .password(adminPasswordHash)
                    .roles("ADMIN")
                    .build();
        }

        var seller = sellerRepository.findByEmail(email);
        if (seller.isPresent()) {
            return User.builder()
                    .username(seller.get().getEmail())
                    .password(seller.get().getPasswordHash())
                    .roles("SELLER")
                    .build();
        }

        var buyer = buyerRepository.findByEmail(email);
        if (buyer.isPresent()) {
            return User.builder()
                    .username(buyer.get().getEmail())
                    .password(buyer.get().getPasswordHash())
                    .roles("BUYER")
                    .build();
        }

        throw new UsernameNotFoundException("No account with email: " + email);
    }
}
