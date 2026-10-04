package com.orderflow.catalog.product;

import com.orderflow.catalog.seller.SellerStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Store search queries. Filtering happens in the database, not in Java. */
public interface ProductSearchRepository extends Repository<Product, UUID> {

    @Query("""
        select p from Product p join fetch p.seller s
        where s.status = :status
          and (lower(p.name) like :q escape '\\' or lower(coalesce(p.description, '')) like :q escape '\\')
          and (:category = '' or lower(coalesce(p.category, '')) = :category)
          and p.price between :minPrice and :maxPrice
          and p.stockQuantity >= :minStock
        """)
    List<Product> search(@Param("q") String q,
                         @Param("category") String category,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("minStock") int minStock,
                         @Param("status") SellerStatus status,
                         Sort sort);

    @Query("""
        select distinct p.category from Product p
        where p.seller.status = :status and p.category is not null and p.category <> ''
        order by p.category
        """)
    List<String> categories(@Param("status") SellerStatus status);
}
