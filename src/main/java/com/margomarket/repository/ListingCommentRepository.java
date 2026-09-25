package com.margomarket.repository;

import com.margomarket.model.ListingComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingCommentRepository extends JpaRepository<ListingComment, Long> {

    @EntityGraph(attributePaths = {"author"})
    Page<ListingComment> findByListingIdOrderByIdDesc(Long listingId, Pageable pageable);
}
